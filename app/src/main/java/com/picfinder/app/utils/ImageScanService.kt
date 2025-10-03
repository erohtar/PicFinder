package com.picfinder.app.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.picfinder.app.data.database.FolderEntity
import com.picfinder.app.data.database.ImageEntity
import com.picfinder.app.data.repository.PicFinderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class ImageScanService(private val context: Context) {
    
    private val repository = PicFinderRepository(context)
    private val ocrService = OCRService()
    
    private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val scanProgress: StateFlow<ScanProgress> = _scanProgress
    
    sealed class ScanProgress {
        object Idle : ScanProgress()
        data class Scanning(val currentFile: String, val processed: Int, val total: Int) : ScanProgress()
        data class Complete(val processedCount: Int, val newImagesCount: Int) : ScanProgress()
        data class Error(val message: String) : ScanProgress()
    }
    
    suspend fun scanFolder(folderPath: String): ScanResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting scan for folder: $folderPath")
                _scanProgress.value = ScanProgress.Scanning("Initializing...", 0, 0)
                
                // Handle both URI and file path formats
                val imageFiles = if (folderPath.startsWith("content://")) {
                    getAllImageFilesFromUri(folderPath)
                } else {
                    val folder = File(folderPath)
                    if (!folder.exists()) {
                        val error = "Folder does not exist: $folderPath"
                        Log.e(TAG, error)
                        _scanProgress.value = ScanProgress.Error(error)
                        return@withContext ScanResult.Error(error)
                    }
                    
                    if (!folder.isDirectory) {
                        val error = "Path is not a directory: $folderPath"
                        Log.e(TAG, error)
                        _scanProgress.value = ScanProgress.Error(error)
                        return@withContext ScanResult.Error(error)
                    }
                    
                    if (!folder.canRead()) {
                        val error = "Cannot read folder: $folderPath"
                        Log.e(TAG, error)
                        _scanProgress.value = ScanProgress.Error(error)
                        return@withContext ScanResult.Error(error)
                    }
                    
                    getAllImageFiles(folder)
                }
                
                val totalFiles = imageFiles.size
                Log.d(TAG, "Found $totalFiles image files in $folderPath")
                
                if (totalFiles == 0) {
                    Log.i(TAG, "No image files found in folder: $folderPath")
                    _scanProgress.value = ScanProgress.Complete(0, 0)
                    return@withContext ScanResult.Success(0, 0)
                }
                
                var processedCount = 0
                var newImagesCount = 0
                val existingImages = repository.getImagesInFolder(folderPath).associateBy { it.filePath }
                
                for (imageInfo in imageFiles) {
                    try {
                        _scanProgress.value = ScanProgress.Scanning(
                            imageInfo.name, 
                            processedCount, 
                            totalFiles
                        )
                        
                        val filePath = imageInfo.path
                        val lastModified = imageInfo.lastModified
                        val fileSize = imageInfo.size
                        
                        // Check if image already exists and hasn't been modified
                        val existingImage = existingImages[filePath]
                        if (existingImage != null && existingImage.lastModified == lastModified) {
                            processedCount++
                            continue
                        }
                        
                        // Extract text using OCR
                        Log.d(TAG, "Extracting text from: ${imageInfo.name}")
                        val extractedText = if (folderPath.startsWith("content://")) {
                            ocrService.extractTextFromUri(context, Uri.parse(filePath))
                        } else {
                            ocrService.extractTextFromImage(filePath)
                        }
                        Log.d(TAG, "Extracted text length: ${extractedText.length} characters")
                        
                        // Create image entity
                        val imageEntity = ImageEntity(
                            filePath = filePath,
                            fileName = imageInfo.name,
                            folderPath = folderPath,
                            extractedText = extractedText,
                            lastModified = lastModified,
                            fileSize = fileSize
                        )
                        
                        // Insert or update in database
                        repository.insertImage(imageEntity)
                        Log.d(TAG, "Saved image to database: ${imageInfo.name}")
                        
                        if (existingImage == null) {
                            newImagesCount++
                        }
                        
                        processedCount++
                        
                        // Update folder count in real-time every 10 images
                        if (processedCount % 10 == 0) {
                            val currentCount = repository.getImageCountInFolder(folderPath)
                            repository.updateFolderScanInfo(folderPath, System.currentTimeMillis(), currentCount)
                        }
                        
                    } catch (e: Exception) {
                        Log.e("ImageScanService", "Error processing image: ${imageInfo.path}", e)
                        processedCount++
                    }
                }
                
                // Update folder scan information with actual database count
                val finalCount = repository.getImageCountInFolder(folderPath)
                repository.updateFolderScanInfo(folderPath, System.currentTimeMillis(), finalCount)
                Log.d(TAG, "Updated folder scan info: $folderPath with $finalCount images")
                
                _scanProgress.value = ScanProgress.Complete(processedCount, newImagesCount)
                ScanResult.Success(processedCount, newImagesCount)
                
            } catch (e: Exception) {
                val error = "Error scanning folder: ${e.message}"
                Log.e("ImageScanService", error, e)
                _scanProgress.value = ScanProgress.Error(error)
                ScanResult.Error(error)
            }
        }
    }
    
    suspend fun scanAllFolders(): ScanResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting scan all folders - optimized version")
                
                // First, clean up deleted images to avoid processing non-existent files
                Log.d(TAG, "Cleaning up deleted images first...")
                cleanupDeletedImages()
                
                val folders = repository.getAllFolders().filter { it.isActive }
                var totalProcessed = 0
                var totalNew = 0
                var totalDeleted = 0
                
                for (folder in folders) {
                    Log.d(TAG, "Processing folder: ${folder.displayName}")
                    
                    // Get current images in database for this folder
                    val existingImages = repository.getImagesInFolder(folder.folderPath)
                    val existingImagePaths = existingImages.map { it.filePath }.toSet()
                    
                    // Get current files in the folder
                    val currentFiles = if (folder.folderPath.startsWith("content://")) {
                        getAllImageFilesFromUri(folder.folderPath)
                    } else {
                        val folderFile = File(folder.folderPath)
                        if (!folderFile.exists() || !folderFile.isDirectory) {
                            Log.w(TAG, "Folder no longer exists: ${folder.folderPath}")
                            continue
                        }
                        getAllImageFiles(folderFile)
                    }
                    
                    val currentFilePaths = currentFiles.map { it.path }.toSet()
                    
                    // Find deleted files (in database but not on disk)
                    val deletedPaths = existingImagePaths - currentFilePaths
                    for (deletedPath in deletedPaths) {
                        val imageToDelete = existingImages.find { it.filePath == deletedPath }
                        if (imageToDelete != null) {
                            repository.deleteImage(imageToDelete)
                            totalDeleted++
                            Log.d(TAG, "Deleted missing image: ${imageToDelete.fileName}")
                        }
                    }
                    
                    // Find new files (on disk but not in database) or modified files
                    var folderProcessed = 0
                    var folderNew = 0
                    
                    for (fileInfo in currentFiles) {
                        val existingImage = existingImages.find { it.filePath == fileInfo.path }
                        
                        // Skip if file exists and hasn't been modified
                        if (existingImage != null && existingImage.lastModified == fileInfo.lastModified) {
                            folderProcessed++
                            continue
                        }
                        
                        // Process new or modified file
                        try {
                            _scanProgress.value = ScanProgress.Scanning(
                                fileInfo.name, 
                                totalProcessed + folderProcessed, 
                                currentFiles.size
                            )
                            
                            // Extract text using OCR
                            Log.d(TAG, "Processing ${if (existingImage == null) "new" else "modified"} file: ${fileInfo.name}")
                            val extractedText = if (folder.folderPath.startsWith("content://")) {
                                ocrService.extractTextFromUri(context, Uri.parse(fileInfo.path))
                            } else {
                                ocrService.extractTextFromImage(fileInfo.path)
                            }
                            
                            // Create image entity
                            val imageEntity = ImageEntity(
                                filePath = fileInfo.path,
                                fileName = fileInfo.name,
                                folderPath = folder.folderPath,
                                extractedText = extractedText,
                                lastModified = fileInfo.lastModified,
                                fileSize = fileInfo.size
                            )
                            
                            // Insert or update in database
                            repository.insertImage(imageEntity)
                            
                            if (existingImage == null) {
                                folderNew++
                            }
                            
                            folderProcessed++
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing image: ${fileInfo.path}", e)
                            folderProcessed++
                        }
                    }
                    
                    // Update folder scan information
                    val finalCount = repository.getImageCountInFolder(folder.folderPath)
                    repository.updateFolderScanInfo(folder.folderPath, System.currentTimeMillis(), finalCount)
                    
                    totalProcessed += folderProcessed
                    totalNew += folderNew
                    
                    Log.d(TAG, "Folder ${folder.displayName}: processed=$folderProcessed, new=$folderNew, deleted=${deletedPaths.size}")
                }
                
                Log.d(TAG, "Scan all folders complete: processed=$totalProcessed, new=$totalNew, deleted=$totalDeleted")
                ScanResult.Success(totalProcessed, totalNew)
                
            } catch (e: Exception) {
                val error = "Error scanning all folders: ${e.message}"
                Log.e(TAG, error, e)
                ScanResult.Error(error)
            }
        }
    }
    
    data class ImageFileInfo(
        val name: String,
        val path: String,
        val lastModified: Long,
        val size: Long
    )
    
    private fun getAllImageFiles(folder: File): List<ImageFileInfo> {
        val imageFiles = mutableListOf<ImageFileInfo>()
        
        fun scanDirectory(dir: File, depth: Int = 0) {
            try {
                Log.d(TAG, "Scanning directory: ${dir.absolutePath} (depth: $depth)")
                val files = dir.listFiles()
                
                if (files == null) {
                    Log.w(TAG, "Cannot list files in directory: ${dir.absolutePath}")
                    return
                }
                
                Log.d(TAG, "Found ${files.size} items in ${dir.absolutePath}")
                
                for (file in files) {
                    try {
                        when {
                            file.isDirectory && depth < 10 -> { // Prevent infinite recursion
                                scanDirectory(file, depth + 1)
                            }
                            file.isFile && OCRService.isImageFile(file.name) -> {
                                Log.d(TAG, "Found image file: ${file.absolutePath}")
                                imageFiles.add(ImageFileInfo(
                                    name = file.name,
                                    path = file.absolutePath,
                                    lastModified = file.lastModified(),
                                    size = file.length()
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing file: ${file.absolutePath}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning directory: ${dir.absolutePath}", e)
            }
        }
        
        scanDirectory(folder)
        Log.d(TAG, "Total image files found: ${imageFiles.size}")
        return imageFiles
    }
    
    private fun getAllImageFilesFromUri(folderUri: String): List<ImageFileInfo> {
        val imageFiles = mutableListOf<ImageFileInfo>()
        
        try {
            val uri = Uri.parse(folderUri)
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            
            if (documentFile == null || !documentFile.exists()) {
                Log.e(TAG, "Cannot access folder URI: $folderUri")
                return imageFiles
            }
            
            fun scanDocumentDirectory(docFile: DocumentFile, depth: Int = 0) {
                try {
                    Log.d(TAG, "Scanning document directory: ${docFile.name} (depth: $depth)")
                    val files = docFile.listFiles()
                    
                    Log.d(TAG, "Found ${files.size} items in ${docFile.name}")
                    
                    for (file in files) {
                        try {
                            when {
                                file.isDirectory && depth < 10 -> { // Prevent infinite recursion
                                    scanDocumentDirectory(file, depth + 1)
                                }
                                file.isFile && file.name != null && OCRService.isImageFile(file.name!!) -> {
                                    Log.d(TAG, "Found image file: ${file.name}")
                                    imageFiles.add(ImageFileInfo(
                                        name = file.name!!,
                                        path = file.uri.toString(),
                                        lastModified = file.lastModified(),
                                        size = file.length()
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing document file: ${file.name}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning document directory: ${docFile.name}", e)
                }
            }
            
            scanDocumentDirectory(documentFile)
            Log.d(TAG, "Total image files found from URI: ${imageFiles.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder URI: $folderUri", e)
        }
        
        return imageFiles
    }
    
    suspend fun cleanupDeletedImages() {
        withContext(Dispatchers.IO) {
            try {
                val folders = repository.getAllFolders().filter { it.isActive }
                for (folder in folders) {
                    val imagesInDb = repository.getImagesInFolder(folder.folderPath)
                    val existingPaths = if (folder.folderPath.startsWith("content://")) {
                        // For URI-based folders, check if DocumentFile still exists
                        imagesInDb.filter { 
                            try {
                                val uri = Uri.parse(it.filePath)
                                val docFile = DocumentFile.fromSingleUri(context, uri)
                                docFile?.exists() == true
                            } catch (e: Exception) {
                                false
                            }
                        }.map { it.filePath }
                    } else {
                        // For traditional file paths
                        imagesInDb.filter { File(it.filePath).exists() }.map { it.filePath }
                    }
                    
                    // Remove images that no longer exist
                    val deletedImages = imagesInDb.filter { image ->
                        if (folder.folderPath.startsWith("content://")) {
                            try {
                                val uri = Uri.parse(image.filePath)
                                val docFile = DocumentFile.fromSingleUri(context, uri)
                                docFile?.exists() != true
                            } catch (e: Exception) {
                                true // If we can't check, assume it's deleted
                            }
                        } else {
                            !File(image.filePath).exists()
                        }
                    }
                    
                    for (deletedImage in deletedImages) {
                        repository.deleteImage(deletedImage)
                    }
                    
                    // Update folder image count
                    repository.updateFolderScanInfo(
                        folder.folderPath, 
                        folder.lastScanDate, 
                        existingPaths.size
                    )
                }
            } catch (e: Exception) {
                Log.e("ImageScanService", "Error cleaning up deleted images", e)
            }
        }
    }
    
    fun close() {
        ocrService.close()
    }
    
    sealed class ScanResult {
        data class Success(val processedCount: Int, val newImagesCount: Int) : ScanResult()
        data class Error(val message: String) : ScanResult()
    }
    
    companion object {
        private const val TAG = "ImageScanService"
    }
}
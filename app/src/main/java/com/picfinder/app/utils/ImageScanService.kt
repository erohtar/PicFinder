package com.picfinder.app.utils

import android.content.Context
import android.util.Log
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
                
                // Get all image files in the folder
                val imageFiles = getAllImageFiles(folder)
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
                
                for (imageFile in imageFiles) {
                    try {
                        _scanProgress.value = ScanProgress.Scanning(
                            imageFile.name, 
                            processedCount, 
                            totalFiles
                        )
                        
                        val filePath = imageFile.absolutePath
                        val lastModified = imageFile.lastModified()
                        val fileSize = imageFile.length()
                        
                        // Check if image already exists and hasn't been modified
                        val existingImage = existingImages[filePath]
                        if (existingImage != null && existingImage.lastModified == lastModified) {
                            processedCount++
                            continue
                        }
                        
                        // Extract text using OCR
                        Log.d(TAG, "Extracting text from: ${imageFile.name}")
                        val extractedText = ocrService.extractTextFromImage(filePath)
                        Log.d(TAG, "Extracted text length: ${extractedText.length} characters")
                        
                        // Create image entity
                        val imageEntity = ImageEntity(
                            filePath = filePath,
                            fileName = imageFile.name,
                            folderPath = folderPath,
                            extractedText = extractedText,
                            lastModified = lastModified,
                            fileSize = fileSize
                        )
                        
                        // Insert or update in database
                        repository.insertImage(imageEntity)
                        Log.d(TAG, "Saved image to database: ${imageFile.name}")
                        
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
                        Log.e("ImageScanService", "Error processing image: ${imageFile.absolutePath}", e)
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
                val folders = repository.getAllFolders().filter { it.isActive }
                var totalProcessed = 0
                var totalNew = 0
                
                for (folder in folders) {
                    val result = scanFolder(folder.folderPath)
                    when (result) {
                        is ScanResult.Success -> {
                            totalProcessed += result.processedCount
                            totalNew += result.newImagesCount
                        }
                        is ScanResult.Error -> {
                            Log.e("ImageScanService", "Error scanning folder ${folder.folderPath}: ${result.message}")
                        }
                    }
                }
                
                ScanResult.Success(totalProcessed, totalNew)
            } catch (e: Exception) {
                val error = "Error scanning all folders: ${e.message}"
                Log.e("ImageScanService", error, e)
                ScanResult.Error(error)
            }
        }
    }
    
    private fun getAllImageFiles(folder: File): List<File> {
        val imageFiles = mutableListOf<File>()
        
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
                                imageFiles.add(file)
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
    
    suspend fun cleanupDeletedImages() {
        withContext(Dispatchers.IO) {
            try {
                val folders = repository.getAllFolders().filter { it.isActive }
                for (folder in folders) {
                    val imagesInDb = repository.getImagesInFolder(folder.folderPath)
                    val existingPaths = imagesInDb.filter { File(it.filePath).exists() }.map { it.filePath }
                    
                    // Remove images that no longer exist
                    val deletedImages = imagesInDb.filter { !File(it.filePath).exists() }
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
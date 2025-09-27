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
                _scanProgress.value = ScanProgress.Scanning("Initializing...", 0, 0)
                
                val folder = File(folderPath)
                if (!folder.exists() || !folder.isDirectory) {
                    val error = "Folder does not exist or is not a directory: $folderPath"
                    _scanProgress.value = ScanProgress.Error(error)
                    return@withContext ScanResult.Error(error)
                }
                
                // Get all image files in the folder
                val imageFiles = getAllImageFiles(folder)
                val totalFiles = imageFiles.size
                
                if (totalFiles == 0) {
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
                        val extractedText = ocrService.extractTextFromImage(filePath)
                        
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
                        
                        if (existingImage == null) {
                            newImagesCount++
                        }
                        
                        processedCount++
                        
                    } catch (e: Exception) {
                        Log.e("ImageScanService", "Error processing image: ${imageFile.absolutePath}", e)
                        processedCount++
                    }
                }
                
                // Update folder scan information
                repository.updateFolderScanInfo(folderPath, System.currentTimeMillis(), processedCount)
                
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
        
        fun scanDirectory(dir: File) {
            try {
                val files = dir.listFiles() ?: return
                for (file in files) {
                    when {
                        file.isDirectory -> scanDirectory(file) // Recursive scan
                        file.isFile && OCRService.isImageFile(file.name) -> imageFiles.add(file)
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageScanService", "Error scanning directory: ${dir.absolutePath}", e)
            }
        }
        
        scanDirectory(folder)
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
}
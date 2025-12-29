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

                val folderLastModified = getFolderLastModified(folderPath)
                val folderEntity = repository.getFolderByPath(folderPath)

                if (folderEntity != null && folderEntity.lastModified == folderLastModified) {
                    Log.i(TAG, "Folder has not changed, skipping scan: $folderPath")
                    _scanProgress.value = ScanProgress.Complete(0, 0)
                    return@withContext ScanResult.Success(0, 0)
                }

                val imageFiles = if (folderPath.startsWith("content://")) {
                    getAllImageFilesFromUri(folderPath)
                } else {
                    val folder = File(folderPath)
                    if (!folder.exists() || !folder.isDirectory) {
                        val error = "Folder does not exist: $folderPath"
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

                        val existingImage = existingImages[filePath]
                        if (existingImage != null && existingImage.lastModified == lastModified) {
                            processedCount++
                            continue
                        }

                        val extractedText = if (folderPath.startsWith("content://")) {
                            ocrService.extractTextFromUri(context, Uri.parse(filePath))
                        } else {
                            ocrService.extractTextFromImage(filePath)
                        }

                        val imageEntity = ImageEntity(
                            filePath = filePath,
                            fileName = imageInfo.name,
                            folderPath = folderPath,
                            extractedText = extractedText,
                            lastModified = lastModified,
                            fileSize = imageInfo.size
                        )

                        repository.insertImage(imageEntity)

                        if (existingImage == null) {
                            newImagesCount++
                        }

                        processedCount++

                    } catch (e: Exception) {
                        Log.e("ImageScanService", "Error processing image: ${imageInfo.path}", e)
                        processedCount++
                    }
                }

                val finalCount = repository.getImageCountInFolder(folderPath)
                repository.updateFolderScanInfo(folderPath, System.currentTimeMillis(), finalCount, folderLastModified)
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

                cleanupDeletedImages()

                val folders = repository.getAllFolders().filter { it.isActive }
                var totalProcessed = 0
                var totalNew = 0

                for (folder in folders) {
                    val folderLastModified = getFolderLastModified(folder.folderPath)
                    if (folder.lastModified == folderLastModified) {
                        Log.i(TAG, "Skipping folder ${folder.displayName} as it has not been modified.")
                        continue
                    }

                    val result = scanFolder(folder.folderPath)
                    if (result is ScanResult.Success) {
                        totalProcessed += result.processedCount
                        totalNew += result.newImagesCount
                    }
                }

                Log.d(TAG, "Scan all folders complete: processed=$totalProcessed, new=$totalNew")
                ScanResult.Success(totalProcessed, totalNew)

            } catch (e: Exception) {
                val error = "Error scanning all folders: ${e.message}"
                Log.e(TAG, error, e)
                ScanResult.Error(error)
            }
        }
    }

    private fun getFolderLastModified(folderPath: String): Long {
        return try {
            if (folderPath.startsWith("content://")) {
                val uri = Uri.parse(folderPath)
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                documentFile?.lastModified() ?: 0L
            } else {
                File(folderPath).lastModified()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last modified time for $folderPath", e)
            0L
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
        folder.walkTopDown().forEach {
            if (OCRService.isImageFile(it.name)) {
                imageFiles.add(
                    ImageFileInfo(
                        name = it.name,
                        path = it.absolutePath,
                        lastModified = it.lastModified(),
                        size = it.length()
                    )
                )
            }
        }
        return imageFiles
    }

    private fun getAllImageFilesFromUri(folderUri: String): List<ImageFileInfo> {
        val imageFiles = mutableListOf<ImageFileInfo>()
        val uri = Uri.parse(folderUri)
        val documentFile = DocumentFile.fromTreeUri(context, uri)

        documentFile?.let { traverseDocumentFileTree(it, imageFiles) }
        return imageFiles
    }

    private fun traverseDocumentFileTree(root: DocumentFile, fileList: MutableList<ImageFileInfo>) {
        val files = root.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                traverseDocumentFileTree(file, fileList)
            } else if (file.isFile && file.name != null && OCRService.isImageFile(file.name!!)) {
                fileList.add(
                    ImageFileInfo(
                        name = file.name!!,
                        path = file.uri.toString(),
                        lastModified = file.lastModified(),
                        size = file.length()
                    )
                )
            }
        }
    }

    suspend fun cleanupDeletedImages() {
        withContext(Dispatchers.IO) {
            try {
                val folders = repository.getAllFolders().filter { it.isActive }
                for (folder in folders) {
                    val imagesInDb = repository.getImagesInFolder(folder.folderPath)
                    val existingImagePaths = imagesInDb.map { it.filePath }.toSet()

                    val actualImageFiles = if (folder.folderPath.startsWith("content://")) {
                        getAllImageFilesFromUri(folder.folderPath).map { it.path }.toSet()
                    } else {
                        getAllImageFiles(File(folder.folderPath)).map { it.path }.toSet()
                    }

                    val deletedImagePaths = existingImagePaths - actualImageFiles

                    if (deletedImagePaths.isNotEmpty()) {
                        repository.deleteImagesByPaths(deletedImagePaths.toList())
                    }

                    val finalCount = repository.getImageCountInFolder(folder.folderPath)
                    val folderLastModified = getFolderLastModified(folder.folderPath)
                    repository.updateFolderScanInfo(folder.folderPath, folder.lastScanDate, finalCount, folderLastModified)
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
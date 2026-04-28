package com.picfinder.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.picfinder.app.R
import com.picfinder.app.data.database.ImageEntity
import com.picfinder.app.data.repository.PicFinderRepository
import com.picfinder.app.utils.OCRService
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.coroutineContext

class FileScannerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var repository: PicFinderRepository
    private lateinit var ocrService: OCRService
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "file_scanner_channel"
    
    data class ScanStats(
        val totalFolders: Int,
        val currentFolderIndex: Int,
        val currentFolderName: String,
        val filesFoundInCurrent: Int,
        val totalFilesFound: Int
    )

    override fun onCreate() {
        super.onCreate()
        repository = PicFinderRepository(applicationContext)
        ocrService = OCRService()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val folderPath = intent?.getStringExtra(EXTRA_FOLDER_PATH)
        
        val notification = createNotification("Preparing scan...")
        startForeground(NOTIFICATION_ID, notification)
        
        acquireWakeLock()
        
        serviceScope.launch {
            try {
                if (folderPath == null) {
                    scanAllFolders()
                } else {
                    scanSingleFolder(folderPath)
                }
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }

    private suspend fun scanAllFolders() = withContext(Dispatchers.IO) {
        val folders = repository.getAllFolders().filter { it.isActive }
        val totalFolders = folders.size
        var totalFilesFound = 0
        
        folders.forEachIndexed { index, folder ->
            val stats = ScanStats(
                totalFolders = totalFolders,
                currentFolderIndex = index + 1,
                currentFolderName = folder.displayName,
                filesFoundInCurrent = 0,
                totalFilesFound = totalFilesFound
            )
            updateNotification(stats)
            
            val found = scanFolderInternal(folder.folderPath, stats)
            totalFilesFound += found
        }
        
        showCompletionNotification(totalFilesFound)
    }

    private suspend fun scanSingleFolder(path: String) = withContext(Dispatchers.IO) {
        val folder = repository.getFolderByPath(path)
        val folderName = folder?.displayName ?: path.substringAfterLast("/")
        val stats = ScanStats(1, 1, folderName, 0, 0)
        updateNotification(stats)
        
        val found = scanFolderInternal(path, stats)
        showCompletionNotification(found)
    }

    private suspend fun scanFolderInternal(folderPath: String, baseStats: ScanStats): Int {
        var filesInFolder = 0
        try {
            val folderLastModified = getFolderLastModified(folderPath)
            
            val imageFiles = if (folderPath.startsWith("content://")) {
                getAllImageFilesFromUri(folderPath)
            } else {
                val folder = File(folderPath)
                if (!folder.exists() || !folder.isDirectory) return 0
                getAllImageFiles(folder)
            }

            val totalInThisFolder = imageFiles.size
            val existingImages = repository.getImagesInFolder(folderPath).associateBy { it.filePath }
            val batch = mutableListOf<ImageEntity>()
            
            var lastUpdate = System.currentTimeMillis()

            imageFiles.forEachIndexed { index, info ->
                if (!coroutineContext.isActive) return@forEachIndexed
                
                val existingImage = existingImages[info.path]
                if (existingImage == null || existingImage.lastModified != info.lastModified) {
                    val extractedText = try {
                        if (folderPath.startsWith("content://")) {
                            ocrService.extractTextFromUri(applicationContext, Uri.parse(info.path))
                        } else {
                            ocrService.extractTextFromImage(info.path)
                        }
                    } catch (e: Exception) {
                        ""
                    }

                    batch.add(ImageEntity(
                        filePath = info.path,
                        fileName = info.name,
                        folderPath = folderPath,
                        extractedText = extractedText,
                        lastModified = info.lastModified,
                        fileSize = info.size
                    ))
                    
                    if (batch.size >= 50) {
                        repository.insertImages(batch.toList())
                        batch.clear()
                    }
                }
                
                filesInFolder++
                
                // Throttle notification updates
                if (System.currentTimeMillis() - lastUpdate > 500) {
                    updateNotification(baseStats.copy(
                        filesFoundInCurrent = filesInFolder,
                        totalFilesFound = baseStats.totalFilesFound + filesInFolder
                    ))
                    lastUpdate = System.currentTimeMillis()
                }
            }
            
            if (batch.isNotEmpty()) {
                repository.insertImages(batch)
            }

            val finalCount = repository.getImageCountInFolder(folderPath)
            repository.updateFolderScanInfo(folderPath, System.currentTimeMillis(), finalCount, folderLastModified)

        } catch (e: Exception) {
            Log.e("FileScannerService", "Error scanning $folderPath", e)
        }
        return filesInFolder
    }

    private fun updateNotification(stats: ScanStats) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_search)
            .setContentTitle("Scanning Folder (${stats.currentFolderIndex}/${stats.totalFolders})")
            .setContentText("Current: ${stats.currentFolderName} | Found: ${stats.totalFilesFound} images")
            .setProgress(stats.totalFolders, stats.currentFolderIndex, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(totalFound: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_search)
            .setContentTitle("Scan Complete")
            .setContentText("Found $totalFound images in total.")
            .setOngoing(false)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_search)
            .setContentTitle("PicFinder Scanner")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "File Scanner"
            val descriptionText = "Notifications for image scanning progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PicFinder:ScanWakeLock").apply {
            acquire(10 * 60 * 1000L /*10 minutes*/)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        ocrService.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getFolderLastModified(folderPath: String): Long {
        return try {
            if (folderPath.startsWith("content://")) {
                val uri = Uri.parse(folderPath)
                val documentFile = DocumentFile.fromTreeUri(applicationContext, uri)
                documentFile?.lastModified() ?: 0L
            } else {
                File(folderPath).lastModified()
            }
        } catch (e: Exception) { 0L }
    }

    private fun getAllImageFiles(folder: File): List<ImageFileInfo> {
        val imageFiles = mutableListOf<ImageFileInfo>()
        try {
            folder.walkTopDown().forEach {
                if (it.isFile && OCRService.isImageFile(it.name)) {
                    imageFiles.add(ImageFileInfo(it.name, it.absolutePath, it.lastModified(), it.length()))
                }
            }
        } catch (e: Exception) {
            Log.e("FileScannerService", "Error walking tree", e)
        }
        return imageFiles
    }

    private fun getAllImageFilesFromUri(folderUri: String): List<ImageFileInfo> {
        val imageFiles = mutableListOf<ImageFileInfo>()
        val uri = Uri.parse(folderUri)
        val documentFile = DocumentFile.fromTreeUri(applicationContext, uri)
        documentFile?.let { traverseDocumentFileTree(it, imageFiles) }
        return imageFiles
    }

    private fun traverseDocumentFileTree(root: DocumentFile, fileList: MutableList<ImageFileInfo>) {
        val files = root.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                traverseDocumentFileTree(file, fileList)
            } else if (file.isFile && file.name != null && OCRService.isImageFile(file.name!!)) {
                fileList.add(ImageFileInfo(file.name!!, file.uri.toString(), file.lastModified(), file.length()))
            }
        }
    }

    data class ImageFileInfo(val name: String, val path: String, val lastModified: Long, val size: Long)

    companion object {
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
    }
}

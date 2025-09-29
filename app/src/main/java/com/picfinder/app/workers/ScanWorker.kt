package com.picfinder.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.picfinder.app.utils.ImageScanService

class ScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val scanService = ImageScanService(applicationContext)
            
            // Perform cleanup first
            scanService.cleanupDeletedImages()
            
            // Then scan all folders
            val result = scanService.scanAllFolders()
            
            scanService.close()
            
            when (result) {
                is ImageScanService.ScanResult.Success -> {
                    Result.success()
                }
                is ImageScanService.ScanResult.Error -> {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
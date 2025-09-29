package com.picfinder.app.utils

import android.content.Context
import androidx.work.*
import com.picfinder.app.workers.ScanWorker
import java.util.concurrent.TimeUnit

object WorkManagerUtils {
    
    private const val SCAN_WORK_NAME = "periodic_scan_work"
    
    fun schedulePeriodicScan(context: Context, frequency: ScanFrequency) {
        val workManager = WorkManager.getInstance(context)
        
        // Cancel existing work
        workManager.cancelUniqueWork(SCAN_WORK_NAME)
        
        if (frequency == ScanFrequency.MANUAL_ONLY) {
            return // Don't schedule anything for manual only
        }
        
        val repeatInterval = when (frequency) {
            ScanFrequency.DAILY -> 1L to TimeUnit.DAYS
            ScanFrequency.WEEKLY -> 7L to TimeUnit.DAYS
            ScanFrequency.MANUAL_ONLY -> return // Already handled above
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(false)
            .build()
        
        val scanRequest = PeriodicWorkRequestBuilder<ScanWorker>(
            repeatInterval.first,
            repeatInterval.second
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                15,
                TimeUnit.MINUTES
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            SCAN_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            scanRequest
        )
    }
    
    fun cancelPeriodicScan(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SCAN_WORK_NAME)
    }
    
    enum class ScanFrequency {
        DAILY, WEEKLY, MANUAL_ONLY
    }
}
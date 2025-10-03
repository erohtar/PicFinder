package com.picfinder.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.picfinder.app.data.repository.PicFinderRepository
import com.picfinder.app.utils.ImageScanService
import kotlinx.coroutines.*

/**
 * Activity that can be launched externally (e.g., by Tasker) to trigger a scan of all folders.
 * This activity runs the scan in the background and finishes when complete.
 */
class ScanActivity : Activity() {
    
    private lateinit var repository: PicFinderRepository
    private lateinit var scanService: ImageScanService
    private var scanJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        repository = PicFinderRepository(this)
        scanService = ImageScanService(this)
        
        // Start the scan immediately
        startScan()
    }
    
    private fun startScan() {
        scanJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                Toast.makeText(this@ScanActivity, "Starting scan of all folders...", Toast.LENGTH_SHORT).show()
                
                val result = scanService.scanAllFolders()
                
                when (result) {
                    is ImageScanService.ScanResult.Success -> {
                        // Update last scan timestamp
                        updateLastScanDate()
                        
                        val message = "Scan complete: ${result.processedCount} images processed, ${result.newImagesCount} new"
                        Toast.makeText(this@ScanActivity, message, Toast.LENGTH_LONG).show()
                        
                        // Set result for external callers
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("processed_count", result.processedCount)
                            putExtra("new_count", result.newImagesCount)
                            putExtra("message", message)
                        })
                    }
                    is ImageScanService.ScanResult.Error -> {
                        val message = "Scan failed: ${result.message}"
                        Toast.makeText(this@ScanActivity, message, Toast.LENGTH_LONG).show()
                        
                        // Set error result
                        setResult(RESULT_CANCELED, Intent().apply {
                            putExtra("error", result.message)
                        })
                    }
                }
            } catch (e: Exception) {
                val message = "Scan error: ${e.message}"
                Toast.makeText(this@ScanActivity, message, Toast.LENGTH_LONG).show()
                
                // Set error result
                setResult(RESULT_CANCELED, Intent().apply {
                    putExtra("error", e.message)
                })
            } finally {
                scanService.close()
                finish()
            }
        }
    }
    
    private fun updateLastScanDate() {
        val sharedPrefs = getSharedPreferences("picfinder_prefs", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        sharedPrefs.edit()
            .putLong("last_scan_date", currentTime)
            .apply()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
        if (::scanService.isInitialized) {
            scanService.close()
        }
    }
    
    companion object {
        /**
         * Create an intent to launch the scan activity
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, ScanActivity::class.java)
        }
    }
}
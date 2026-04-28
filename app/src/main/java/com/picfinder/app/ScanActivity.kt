package com.picfinder.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.picfinder.app.service.FileScannerService

/**
 * Activity that can be launched externally (e.g., by Tasker) to trigger a scan of all folders.
 */
class ScanActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start the foreground service to handle the scan
        startScannerService()
        
        Toast.makeText(this, "Starting background scan of all folders...", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun startScannerService() {
        val intent = Intent(this, FileScannerService::class.java)
        ContextCompat.startForegroundService(this, intent)
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
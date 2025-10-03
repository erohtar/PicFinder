package com.picfinder.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

object PermissionUtils {
    
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ - we'll use MANAGE_EXTERNAL_STORAGE
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }
    
    fun hasStoragePermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // For Android 11+, check if we have MANAGE_EXTERNAL_STORAGE permission
                Environment.isExternalStorageManager()
            }
            else -> {
                // For older versions, check traditional permissions
                getRequiredPermissions().all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
    }
    
    fun shouldShowRationale(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return getRequiredPermissions().any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }
}
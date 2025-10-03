package com.picfinder.app.data.database

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(
    entities = [ImageEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PicFinderDatabase : RoomDatabase() {
    
    abstract fun imageDao(): ImageDao
    abstract fun folderDao(): FolderDao
    
    companion object {
        @Volatile
        private var INSTANCE: PicFinderDatabase? = null
        
        fun getDatabase(context: Context): PicFinderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PicFinderDatabase::class.java,
                    getDatabasePath(context)
                ).build()
                INSTANCE = instance
                instance
            }
        }
        
        private fun getDatabasePath(context: Context): String {
            return try {
                // Check if we have external storage access
                val hasExternalStorageAccess = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        Environment.isExternalStorageManager()
                    }
                    else -> {
                        ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                }
                
                if (hasExternalStorageAccess) {
                    // Create PicFinder directory in external storage if it doesn't exist
                    val picFinderDir = File(Environment.getExternalStorageDirectory(), "PicFinder")
                    if (!picFinderDir.exists()) {
                        picFinderDir.mkdirs()
                    }
                    
                    // Return the full path to the database file
                    File(picFinderDir, "picfinder_database").absolutePath
                } else {
                    // Use internal storage until permissions are granted
                    "picfinder_database"
                }
            } catch (e: Exception) {
                // Fallback to internal storage if external storage is not accessible
                "picfinder_database"
            }
        }
    }
}
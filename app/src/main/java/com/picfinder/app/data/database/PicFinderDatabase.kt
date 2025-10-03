package com.picfinder.app.data.database

import android.content.Context
import android.os.Environment
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
                    getDatabasePath()
                ).build()
                INSTANCE = instance
                instance
            }
        }
        
        private fun getDatabasePath(): String {
            // Create PicFinder directory in external storage if it doesn't exist
            val picFinderDir = File(Environment.getExternalStorageDirectory(), "PicFinder")
            if (!picFinderDir.exists()) {
                picFinderDir.mkdirs()
            }
            
            // Return the full path to the database file
            return File(picFinderDir, "picfinder_database").absolutePath
        }
    }
}
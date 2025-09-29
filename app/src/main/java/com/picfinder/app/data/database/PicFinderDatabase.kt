package com.picfinder.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
                    "picfinder_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
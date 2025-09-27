package com.picfinder.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val folderPath: String,
    val displayName: String,
    val lastScanDate: Long = 0L,
    val imageCount: Int = 0,
    val isActive: Boolean = true
)
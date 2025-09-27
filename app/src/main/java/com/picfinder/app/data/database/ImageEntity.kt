package com.picfinder.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey
    val filePath: String,
    val fileName: String,
    val folderPath: String,
    val extractedText: String,
    val lastModified: Long,
    val fileSize: Long,
    val scanDate: Long = System.currentTimeMillis()
)
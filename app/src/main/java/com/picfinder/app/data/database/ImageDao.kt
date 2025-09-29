package com.picfinder.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    
    @Query("SELECT * FROM images WHERE extractedText LIKE '%' || :searchQuery || '%' OR fileName LIKE '%' || :searchQuery || '%' OR folderPath LIKE '%' || :searchQuery || '%'")
    fun searchImages(searchQuery: String): Flow<List<ImageEntity>>
    
    @Query("SELECT * FROM images WHERE folderPath = :folderPath")
    suspend fun getImagesInFolder(folderPath: String): List<ImageEntity>
    
    @Query("SELECT COUNT(*) FROM images WHERE folderPath = :folderPath")
    suspend fun getImageCountInFolder(folderPath: String): Int
    
    @Query("SELECT COUNT(*) FROM images")
    suspend fun getTotalImageCount(): Int
    
    @Query("SELECT COUNT(*) FROM images WHERE extractedText != ''")
    suspend fun getImagesWithTextCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ImageEntity>)
    
    @Delete
    suspend fun deleteImage(image: ImageEntity)
    
    @Query("DELETE FROM images WHERE folderPath = :folderPath")
    suspend fun deleteImagesInFolder(folderPath: String)
    
    @Query("DELETE FROM images WHERE filePath NOT IN (SELECT filePath FROM images WHERE filePath IN (:existingPaths))")
    suspend fun deleteNonExistentImages(existingPaths: List<String>)
    
    @Query("DELETE FROM images")
    suspend fun deleteAllImages()
    
    @Query("SELECT * FROM images WHERE filePath = :filePath")
    suspend fun getImageByPath(filePath: String): ImageEntity?
    
    @RawQuery(observedEntities = [ImageEntity::class])
    fun searchImagesRaw(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<ImageEntity>>
}
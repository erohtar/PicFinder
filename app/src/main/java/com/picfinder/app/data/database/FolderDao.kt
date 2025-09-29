package com.picfinder.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    
    @Query("SELECT * FROM folders WHERE isActive = 1")
    fun getActiveFolders(): Flow<List<FolderEntity>>
    
    @Query("SELECT * FROM folders")
    suspend fun getAllFolders(): List<FolderEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)
    
    @Update
    suspend fun updateFolder(folder: FolderEntity)
    
    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
    
    @Query("UPDATE folders SET isActive = 0 WHERE folderPath = :folderPath")
    suspend fun deactivateFolder(folderPath: String)
    
    @Query("UPDATE folders SET lastScanDate = :scanDate, imageCount = :imageCount WHERE folderPath = :folderPath")
    suspend fun updateFolderScanInfo(folderPath: String, scanDate: Long, imageCount: Int)
    
    @Query("SELECT * FROM folders WHERE folderPath = :folderPath")
    suspend fun getFolderByPath(folderPath: String): FolderEntity?
}
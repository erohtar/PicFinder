package com.picfinder.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE folderPath = :folderPath")
    suspend fun getFolderByPath(folderPath: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE isActive = 1 ORDER BY displayName")
    fun getActiveFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY displayName")
    suspend fun getAllFolders(): List<FolderEntity>

    @Query("UPDATE folders SET isActive = 0 WHERE folderPath = :folderPath")
    suspend fun deactivateFolder(folderPath: String)

    @Query("UPDATE folders SET lastScanDate = :scanDate, imageCount = :imageCount, lastModified = :lastModified WHERE folderPath = :folderPath")
    suspend fun updateFolderScanInfo(folderPath: String, scanDate: Long, imageCount: Int, lastModified: Long)
}
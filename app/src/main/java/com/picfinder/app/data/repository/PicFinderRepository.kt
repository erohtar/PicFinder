package com.picfinder.app.data.repository

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.picfinder.app.data.database.*
import kotlinx.coroutines.flow.Flow

class PicFinderRepository(context: Context) {
    
    private val database = PicFinderDatabase.getDatabase(context)
    private val imageDao = database.imageDao()
    private val folderDao = database.folderDao()
    
    // Image operations
    suspend fun insertImage(image: ImageEntity) = imageDao.insertImage(image)
    
    suspend fun insertImages(images: List<ImageEntity>) = imageDao.insertImages(images)
    
    suspend fun deleteImage(image: ImageEntity) = imageDao.deleteImage(image)
    
    suspend fun deleteImagesInFolder(folderPath: String) = imageDao.deleteImagesInFolder(folderPath)
    
    suspend fun deleteAllImages() = imageDao.deleteAllImages()
    
    suspend fun getImageByPath(filePath: String) = imageDao.getImageByPath(filePath)
    
    suspend fun getImagesInFolder(folderPath: String) = imageDao.getImagesInFolder(folderPath)
    
    suspend fun getTotalImageCount() = imageDao.getTotalImageCount()
    
    suspend fun getImagesWithTextCount() = imageDao.getImagesWithTextCount()
    
    // Search functionality
    fun searchImages(query: String): Flow<List<ImageEntity>> {
        return if (query.isBlank()) {
            // Return empty flow for blank queries
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            // Split query into keywords and search for all of them
            val keywords = query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (keywords.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                searchImagesWithKeywords(keywords)
            }
        }
    }
    
    private fun searchImagesWithKeywords(keywords: List<String>): Flow<List<ImageEntity>> {
        val queryBuilder = StringBuilder("SELECT * FROM images WHERE ")
        val args = mutableListOf<String>()
        
        keywords.forEachIndexed { index, keyword ->
            if (index > 0) queryBuilder.append(" AND ")
            queryBuilder.append("(extractedText LIKE ? OR fileName LIKE ? OR folderPath LIKE ?)")
            val likePattern = "%$keyword%"
            args.add(likePattern)
            args.add(likePattern)
            args.add(likePattern)
        }
        
        val query = SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray())
        return imageDao.searchImagesRaw(query)
    }
    
    // Folder operations
    fun getActiveFolders(): Flow<List<FolderEntity>> = folderDao.getActiveFolders()
    
    suspend fun getAllFolders() = folderDao.getAllFolders()
    
    suspend fun insertFolder(folder: FolderEntity) = folderDao.insertFolder(folder)
    
    suspend fun updateFolder(folder: FolderEntity) = folderDao.updateFolder(folder)
    
    suspend fun deleteFolder(folder: FolderEntity) = folderDao.deleteFolder(folder)
    
    suspend fun deactivateFolder(folderPath: String) = folderDao.deactivateFolder(folderPath)
    
    suspend fun updateFolderScanInfo(folderPath: String, scanDate: Long, imageCount: Int) = 
        folderDao.updateFolderScanInfo(folderPath, scanDate, imageCount)
    
    suspend fun getFolderByPath(folderPath: String) = folderDao.getFolderByPath(folderPath)
    
    // Cleanup operations
    suspend fun cleanupNonExistentImages(existingPaths: List<String>) {
        imageDao.deleteNonExistentImages(existingPaths)
    }
}
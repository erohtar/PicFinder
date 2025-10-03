package com.picfinder.app.ui.folders

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.picfinder.app.data.database.FolderEntity
import com.picfinder.app.data.repository.PicFinderRepository
import com.picfinder.app.utils.ImageScanService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class FoldersViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = PicFinderRepository(application)
    private val scanService = ImageScanService(application)
    
    val folders: StateFlow<List<FolderEntity>> = repository.getActiveFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()
    
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()
    
    sealed class ScanProgress {
        object Idle : ScanProgress()
        data class Scanning(val folderName: String) : ScanProgress()
        data class Complete(val message: String) : ScanProgress()
        data class Error(val message: String) : ScanProgress()
    }
    
    sealed class UiEvent {
        data class ShowMessage(val message: String) : UiEvent()
        data class ShowError(val message: String) : UiEvent()
    }
    
    fun addFolder(folderPath: String) {
        viewModelScope.launch {
            try {
                // Handle both URI and file path formats
                val displayName = if (folderPath.startsWith("content://")) {
                    // Extract display name from URI
                    try {
                        val uri = android.net.Uri.parse(folderPath)
                        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                        docId.substringAfterLast("/").ifEmpty { "Selected Folder" }
                    } catch (e: Exception) {
                        "Selected Folder"
                    }
                } else {
                    // Traditional file path
                    val file = File(folderPath)
                    if (!file.exists() || !file.isDirectory) {
                        _uiEvents.emit(UiEvent.ShowError("Invalid folder path"))
                        return@launch
                    }
                    file.name
                }
                
                // Check if folder already exists and is active
                val existingFolder = repository.getFolderByPath(folderPath)
                if (existingFolder != null && existingFolder.isActive) {
                    _uiEvents.emit(UiEvent.ShowError("Folder already added"))
                    return@launch
                }
                
                // If folder exists but is inactive, delete it first to start fresh
                if (existingFolder != null && !existingFolder.isActive) {
                    repository.deleteFolder(existingFolder)
                }
                
                // Create new folder
                val folderEntity = FolderEntity(
                    folderPath = folderPath,
                    displayName = displayName,
                    lastScanDate = 0L,
                    imageCount = 0,
                    isActive = true
                )
                
                repository.insertFolder(folderEntity)
                _uiEvents.emit(UiEvent.ShowMessage("Folder added successfully"))
                
                // Start scanning the folder
                scanFolder(folderEntity)
                
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Error adding folder: ${e.message}"))
            }
        }
    }
    
    fun scanFolder(folder: FolderEntity) {
        viewModelScope.launch {
            try {
                _scanProgress.value = ScanProgress.Scanning(folder.displayName)
                
                val result = scanService.scanFolder(folder.folderPath)
                when (result) {
                    is ImageScanService.ScanResult.Success -> {
                        // Update last scan timestamp
                        updateLastScanDate()
                        
                        _scanProgress.value = ScanProgress.Complete(
                            "Scanned ${result.processedCount} images, ${result.newImagesCount} new"
                        )
                        _uiEvents.emit(UiEvent.ShowMessage(
                            "Scan complete: ${result.processedCount} images processed"
                        ))
                    }
                    is ImageScanService.ScanResult.Error -> {
                        _scanProgress.value = ScanProgress.Error(result.message)
                        _uiEvents.emit(UiEvent.ShowError("Scan failed: ${result.message}"))
                    }
                }
            } catch (e: Exception) {
                _scanProgress.value = ScanProgress.Error(e.message ?: "Unknown error")
                _uiEvents.emit(UiEvent.ShowError("Scan error: ${e.message}"))
            }
        }
    }
    
    fun removeFolder(folder: FolderEntity) {
        viewModelScope.launch {
            try {
                // Remove folder from database
                repository.deactivateFolder(folder.folderPath)
                
                // Remove all images from this folder
                repository.deleteImagesInFolder(folder.folderPath)
                
                _uiEvents.emit(UiEvent.ShowMessage("Folder removed"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Error removing folder: ${e.message}"))
            }
        }
    }
    
    private fun updateLastScanDate() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("picfinder_prefs", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        sharedPrefs.edit()
            .putLong("last_scan_date", currentTime)
            .apply()
    }
    
    override fun onCleared() {
        super.onCleared()
        scanService.close()
    }
}
package com.picfinder.app.ui.folders

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.picfinder.app.data.database.FolderEntity
import com.picfinder.app.data.repository.PicFinderRepository
import com.picfinder.app.service.FileScannerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class FoldersViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = PicFinderRepository(application)
    
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
                val displayName = if (folderPath.startsWith("content://")) {
                    try {
                        val uri = android.net.Uri.parse(folderPath)
                        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                        docId.substringAfterLast("/").ifEmpty { "Selected Folder" }
                    } catch (e: Exception) {
                        "Selected Folder"
                    }
                } else {
                    val file = File(folderPath)
                    if (!file.exists() || !file.isDirectory) {
                        _uiEvents.emit(UiEvent.ShowError("Invalid folder path"))
                        return@launch
                    }
                    file.name
                }
                
                val existingFolder = repository.getFolderByPath(folderPath)
                if (existingFolder != null && existingFolder.isActive) {
                    _uiEvents.emit(UiEvent.ShowError("Folder already added"))
                    return@launch
                }
                
                if (existingFolder != null && !existingFolder.isActive) {
                    repository.deleteFolder(existingFolder)
                }
                
                val folderEntity = FolderEntity(
                    folderPath = folderPath,
                    displayName = displayName,
                    lastScanDate = 0L,
                    imageCount = 0,
                    isActive = true
                )
                
                repository.insertFolder(folderEntity)
                _uiEvents.emit(UiEvent.ShowMessage("Folder added successfully"))
                
                scanFolder(folderEntity)
                
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Error adding folder: ${e.message}"))
            }
        }
    }
    
    fun scanFolder(folder: FolderEntity) {
        val intent = Intent(getApplication(), FileScannerService::class.java).apply {
            putExtra(FileScannerService.EXTRA_FOLDER_PATH, folder.folderPath)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowMessage("Scanning ${folder.displayName} in background"))
        }
    }
    
    fun removeFolder(folder: FolderEntity) {
        viewModelScope.launch {
            try {
                repository.deactivateFolder(folder.folderPath)
                repository.deleteImagesInFolder(folder.folderPath)
                _uiEvents.emit(UiEvent.ShowMessage("Folder removed"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Error removing folder: ${e.message}"))
            }
        }
    }
}
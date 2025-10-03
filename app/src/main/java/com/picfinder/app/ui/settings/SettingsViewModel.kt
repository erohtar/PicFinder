package com.picfinder.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.picfinder.app.data.repository.PicFinderRepository
import com.picfinder.app.utils.ImageScanService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = PicFinderRepository(application)
    private val scanService = ImageScanService(application)
    private val sharedPrefs = application.getSharedPreferences("picfinder_prefs", Context.MODE_PRIVATE)
    
    private val _lastScanDate = MutableStateFlow(getLastScanDate())
    val lastScanDate: StateFlow<Long> = _lastScanDate.asStateFlow()
    
    private val _databaseStats = MutableStateFlow(DatabaseStats(0, 0))
    val databaseStats: StateFlow<DatabaseStats> = _databaseStats.asStateFlow()
    
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    data class DatabaseStats(
        val totalImages: Int,
        val imagesWithText: Int
    )
    
    sealed class UiEvent {
        data class ShowMessage(val message: String) : UiEvent()
        data class ShowError(val message: String) : UiEvent()
        object DatabaseCleared : UiEvent()
    }
    
    init {
        loadDatabaseStats()
    }
    
    fun clearDatabase() {
        viewModelScope.launch {
            try {
                // Delete all images
                repository.deleteAllImages()
                
                // Reset folder scan information (but keep the folders themselves)
                val folders = repository.getAllFolders()
                for (folder in folders) {
                    if (folder.isActive) {
                        val resetFolder = folder.copy(
                            lastScanDate = 0L,
                            imageCount = 0
                        )
                        repository.updateFolder(resetFolder)
                    }
                }
                
                loadDatabaseStats()
                _uiEvents.emit(UiEvent.DatabaseCleared)
                _uiEvents.emit(UiEvent.ShowMessage("Database cleared successfully"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Error clearing database: ${e.message}"))
            }
        }
    }
    
    fun performManualScan() {
        viewModelScope.launch {
            try {
                _isScanning.value = true
                val result = scanService.scanAllFolders()
                
                when (result) {
                    is ImageScanService.ScanResult.Success -> {
                        updateLastScanDate()
                        loadDatabaseStats()
                        _uiEvents.emit(UiEvent.ShowMessage(
                            "Scan complete: ${result.processedCount} images processed, ${result.newImagesCount} new"
                        ))
                    }
                    is ImageScanService.ScanResult.Error -> {
                        _uiEvents.emit(UiEvent.ShowError("Scan failed: ${result.message}"))
                    }
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Scan error: ${e.message}"))
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    private fun loadDatabaseStats() {
        viewModelScope.launch {
            try {
                val totalImages = repository.getTotalImageCount()
                val imagesWithText = repository.getImagesWithTextCount()
                _databaseStats.value = DatabaseStats(totalImages, imagesWithText)
            } catch (e: Exception) {
                // Handle error silently or log it
            }
        }
    }
    
    private fun getLastScanDate(): Long {
        return sharedPrefs.getLong("last_scan_date", 0L)
    }
    
    private fun updateLastScanDate() {
        val currentTime = System.currentTimeMillis()
        sharedPrefs.edit()
            .putLong("last_scan_date", currentTime)
            .apply()
        _lastScanDate.value = currentTime
    }
    
    override fun onCleared() {
        super.onCleared()
        scanService.close()
    }
}
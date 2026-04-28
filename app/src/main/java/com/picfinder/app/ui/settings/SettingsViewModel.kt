package com.picfinder.app.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.picfinder.app.data.repository.PicFinderRepository
import com.picfinder.app.service.FileScannerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PicFinderRepository(application)
    private val sharedPrefs = application.getSharedPreferences("picfinder_prefs", Context.MODE_PRIVATE)

    private val _lastScanDate = MutableStateFlow(getLastScanDate())
    val lastScanDate: StateFlow<Long> = _lastScanDate.asStateFlow()

    private val _scanDurationAndImageCount = MutableStateFlow(getLastScanInfo())
    val scanDurationAndImageCount: StateFlow<Triple<Int, Int, Int>?> = _scanDurationAndImageCount.asStateFlow()

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

                clearLastScanInfo()
                loadDatabaseStats()
                _uiEvents.emit(UiEvent.DatabaseCleared)
                _uiEvents.emit(UiEvent.ShowMessage("Database cleared successfully"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Error clearing database: ${e.message}"))
            }
        }
    }

    fun performManualScan() {
        val intent = Intent(getApplication(), FileScannerService::class.java)
        ContextCompat.startForegroundService(getApplication(), intent)
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowMessage("Scan started in background"))
        }
    }

    fun loadDatabaseStats() {
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

    private fun getLastScanInfo(): Triple<Int, Int, Int>? {
        val minutes = sharedPrefs.getInt("last_scan_minutes", -1)
        if (minutes == -1) {
            return null
        }
        val seconds = sharedPrefs.getInt("last_scan_seconds", 0)
        val imageCount = sharedPrefs.getInt("last_scan_image_count", 0)
        return Triple(minutes, seconds, imageCount)
    }

    private fun clearLastScanInfo() {
        sharedPrefs.edit()
            .remove("last_scan_minutes")
            .remove("last_scan_seconds")
            .remove("last_scan_image_count")
            .apply()
        _scanDurationAndImageCount.value = null
    }
}
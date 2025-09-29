package com.picfinder.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.picfinder.app.data.database.ImageEntity
import com.picfinder.app.data.repository.PicFinderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = PicFinderRepository(application)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    val searchResults: StateFlow<List<ImageEntity>> = searchQuery
        .debounce(300) // Wait 300ms after user stops typing
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                _isLoading.value = true
                repository.searchImages(query)
                    .onEach { _isLoading.value = false }
                    .catch { 
                        _isLoading.value = false
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
    }
}
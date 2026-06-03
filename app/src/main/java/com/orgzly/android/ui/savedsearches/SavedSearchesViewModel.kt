package com.orgzly.android.ui.savedsearches

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.ui.AppBar
import com.orgzly.android.ui.CommonViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SavedSearchesViewModel(dataRepository: DataRepository) : CommonViewModel() {
    enum class ViewState {
        LOADING,
        LOADED,
        EMPTY
    }

    val viewState = MutableLiveData(ViewState.LOADING)

    val data: LiveData<List<SavedSearch>> by lazy {
        dataRepository.getSavedSearchesLiveData().map { searches ->
            viewState.value = if (searches.isNotEmpty()) {
                ViewState.LOADED
            } else {
                ViewState.EMPTY
            }

            val existingIds = searches.mapTo(hashSetOf()) { it.id }
            _selectedIds.update { it.intersect(existingIds) }

            searches
        }
    }

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    fun toggleSelection(id: Long) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    companion object {
        const val APP_BAR_DEFAULT_MODE = 0
        const val APP_BAR_SELECTION_MODE = 1
    }

    val appBar = AppBar(mapOf(
        APP_BAR_DEFAULT_MODE to null,
        APP_BAR_SELECTION_MODE to APP_BAR_DEFAULT_MODE))
}
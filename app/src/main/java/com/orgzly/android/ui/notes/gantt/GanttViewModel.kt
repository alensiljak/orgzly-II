package com.orgzly.android.ui.notes.gantt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.NoteView

data class GanttItem(
    val noteId: Long,
    val title: String,
    val level: Int,
    val scheduledMs: Long?,
    val deadlineMs: Long?,
)

class GanttViewModel(
    private val dataRepository: DataRepository,
    val bookId: Long,
    val noteId: Long? = null,
) : ViewModel() {

    private val _showUnscheduled = MutableLiveData(true)
    val showUnscheduled: LiveData<Boolean> = _showUnscheduled

    fun toggleShowUnscheduled() {
        _showUnscheduled.value = !(_showUnscheduled.value ?: true)
    }

    val notes: LiveData<List<NoteView>> = dataRepository.getVisibleNotesLiveData(bookId, noteId)

    val items: LiveData<List<GanttItem>> = notes.map { list ->
        list.map { nv ->
            GanttItem(
                noteId = nv.note.id,
                title = nv.note.title,
                level = nv.note.position.level,
                scheduledMs = nv.scheduledTimeTimestamp,
                deadlineMs = nv.deadlineTimeTimestamp,
            )
        }
    }
}

class GanttViewModelFactory(
    private val dataRepository: DataRepository,
    private val bookId: Long,
    private val noteId: Long? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GanttViewModel(dataRepository, bookId, noteId) as T
}

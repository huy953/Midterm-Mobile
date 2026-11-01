package com.example.offlinefirsttodo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.offlinefirsttodo.AppContainer
import com.example.offlinefirsttodo.TodoApp
import com.example.offlinefirsttodo.data.local.SyncLogEntity
import com.example.offlinefirsttodo.data.local.TaskEntity
import com.example.offlinefirsttodo.sync.WorkStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val unsyncedCount: Int = 0,
    val isOnline: Boolean = true,
    val work: WorkStatus = WorkStatus(),
    val lastRun: SyncLogEntity? = null,
    val baseUrl: String = "",
    val isLoading: Boolean = true,
)

class TaskViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.taskRepository
    private val scheduler = container.syncScheduler
    private val logDao = container.database.syncLogDao()

    val uiState: StateFlow<TaskUiState> = combine(
        repository.observeTasks(),
        repository.observeUnsyncedCount(),
        container.connectivity.isOnline,
        scheduler.status,
        combine(logDao.observeLastRun(), container.prefs.baseUrlFlow) { last, url -> last to url },
    ) { tasks, unsynced, online, work, (lastRun, url) ->
        TaskUiState(
            tasks = tasks,
            unsyncedCount = unsynced,
            isOnline = online,
            work = work,
            lastRun = lastRun,
            baseUrl = url,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskUiState())

    val logs: StateFlow<List<SyncLogEntity>> = logDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun addTask(title: String) = viewModelScope.launch { repository.addTask(title) }
    fun renameTask(id: String, title: String) = viewModelScope.launch { repository.renameTask(id, title) }
    fun toggleCompleted(id: String) = viewModelScope.launch { repository.toggleCompleted(id) }
    fun deleteTask(id: String) = viewModelScope.launch { repository.deleteTask(id) }
    fun restoreTask(id: String) = viewModelScope.launch { repository.restoreTask(id) }

    fun syncNow() = viewModelScope.launch {
        val started = scheduler.syncNow()
        val msg = when {
            !started -> "Đang đồng bộ, vui lòng chờ…"
            !uiState.value.isOnline -> "Chưa có mạng – sẽ tự đồng bộ khi có kết nối"
            else -> "Đã yêu cầu đồng bộ"
        }
        _messages.tryEmit(msg)
    }

    fun setBaseUrl(url: String) = viewModelScope.launch {
        container.prefs.baseUrl = url
        scheduler.syncNow()
        _messages.tryEmit("Đã lưu địa chỉ server")
    }

    fun clearLogs() = viewModelScope.launch { logDao.clear() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TodoApp
                TaskViewModel(app.container)
            }
        }
    }
}

package com.example.offlinefirsttodo.data.repository

import androidx.room.withTransaction
import com.example.offlinefirsttodo.data.local.AppDatabase
import com.example.offlinefirsttodo.data.local.SyncStatus
import com.example.offlinefirsttodo.data.local.TaskEntity
import com.example.offlinefirsttodo.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository theo nguyên tắc Offline-First:
 *  - Mọi thao tác GHI chỉ ghi vào Room (đánh dấu PENDING) rồi yêu cầu đồng bộ nền.
 *  - Mọi thao tác ĐỌC cho UI đều lấy từ Room qua Flow -> UI cập nhật tức thì, không cần mạng.
 *  - Giao tiếp với server do SyncEngine/SyncWorker đảm nhận, UI không gọi API trực tiếp.
 */
class TaskRepository(
    private val db: AppDatabase,
    private val scheduler: SyncScheduler,
) {
    private val dao = db.taskDao()

    fun observeTasks(): Flow<List<TaskEntity>> = dao.observeVisibleTasks()

    fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    suspend fun addTask(title: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            )
        )
        scheduler.requestSync()
    }

    suspend fun renameTask(id: String, title: String) = modify(id) { it.copy(title = title.trim()) }

    suspend fun toggleCompleted(id: String) = modify(id) { it.copy(isCompleted = !it.isCompleted) }

    /** Xóa logic: giữ bản ghi với isDeleted = true để server và thiết bị khác biết đã bị xóa. */
    suspend fun deleteTask(id: String) = modify(id) { it.copy(isDeleted = true) }

    /** Hoàn tác xóa (từ Snackbar). */
    suspend fun restoreTask(id: String) = modify(id) { it.copy(isDeleted = false) }

    private suspend fun modify(id: String, change: (TaskEntity) -> TaskEntity) {
        var changed = false
        db.withTransaction {
            val current = dao.getById(id) ?: return@withTransaction
            dao.upsert(
                change(current).copy(
                    updatedAt = nextTimestamp(current.updatedAt),
                    syncStatus = SyncStatus.PENDING,
                    lastError = null,
                )
            )
            changed = true
        }
        if (changed) scheduler.requestSync()
    }

    /**
     * updated_at luôn tăng với mỗi lần sửa, kể cả khi đồng hồ máy bị lùi,
     * để server (LWW) không coi lần sửa mới là bản cũ của chính thiết bị này.
     */
    private fun nextTimestamp(previous: Long): Long =
        maxOf(System.currentTimeMillis(), previous + 1)
}

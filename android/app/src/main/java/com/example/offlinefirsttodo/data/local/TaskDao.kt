package com.example.offlinefirsttodo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** Danh sách hiển thị trên UI (bỏ các bản ghi đã xóa logic). Room tự phát lại khi bảng thay đổi. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY isCompleted ASC, createdAt DESC")
    fun observeVisibleTasks(): Flow<List<TaskEntity>>

    /** Số bản ghi chưa đồng bộ (kể cả bản ghi đã xóa logic đang chờ gửi lên). */
    @Query("SELECT COUNT(*) FROM tasks WHERE syncStatus != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    /** Các bản ghi cần đẩy lên server: PENDING, FAILED (và SYNCING bị kẹt nếu app bị kill giữa chừng). */
    @Query("SELECT * FROM tasks WHERE syncStatus != 'SYNCED' ORDER BY updatedAt ASC")
    suspend fun getUnsynced(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("UPDATE tasks SET syncStatus = 'SYNCING' WHERE id IN (:ids)")
    suspend fun markSyncing(ids: List<String>)

    /**
     * Chỉ đánh dấu SYNCED nếu bản ghi KHÔNG bị sửa tiếp trong lúc đang gửi
     * (updatedAt vẫn bằng giá trị đã gửi). Nếu người dùng đã sửa tiếp, bản ghi giữ PENDING
     * và sẽ được gửi ở vòng sau -> không làm mất thay đổi.
     * @return số dòng được cập nhật (0 hoặc 1)
     */
    @Query(
        "UPDATE tasks SET syncStatus = 'SYNCED', lastError = NULL " +
            "WHERE id = :id AND updatedAt = :sentUpdatedAt"
    )
    suspend fun markSyncedIfUnchanged(id: String, sentUpdatedAt: Long): Int

    @Query("UPDATE tasks SET syncStatus = 'FAILED', lastError = :error WHERE syncStatus = 'SYNCING'")
    suspend fun markSyncingAsFailed(error: String)

    @Query("UPDATE tasks SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetSyncingToPending()
}

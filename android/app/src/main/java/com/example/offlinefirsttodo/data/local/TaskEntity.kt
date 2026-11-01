package com.example.offlinefirsttodo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Trạng thái đồng bộ – CHỈ tồn tại trên thiết bị, server không lưu trường này.
 *
 *  PENDING  -> "Chưa đồng bộ"   : có thay đổi cục bộ chưa được server xác nhận
 *  SYNCING  -> "Đang đồng bộ"   : đang được SyncWorker gửi lên
 *  SYNCED   -> "Đã đồng bộ"     : server đã xác nhận (nằm trong applied_ids)
 *  FAILED   -> "Đồng bộ thất bại": lần gửi gần nhất lỗi, sẽ được thử lại
 */
enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED }

/**
 * Bản ghi công việc trong Room – Single Source of Truth của giao diện.
 * Các trường (trừ syncStatus/lastError) khớp 1-1 với TaskDTO của backend FastAPI.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("syncStatus"), Index("updatedAt")]
)
data class TaskEntity(
    @PrimaryKey val id: String,          // UUID v4 do client sinh -> không trùng khi tạo offline
    val title: String,
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false,      // xóa logic (tombstone) để server biết bản ghi đã bị xóa
    val createdAt: Long,                 // Unix ms
    val updatedAt: Long,                 // Unix ms – dùng cho Last-Write-Wins
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val lastError: String? = null,
)

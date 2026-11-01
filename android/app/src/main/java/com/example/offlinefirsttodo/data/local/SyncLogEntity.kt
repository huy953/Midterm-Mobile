package com.example.offlinefirsttodo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncLogKind { SYNC_OK, SYNC_FAILED, CONFLICT }

/**
 * Nhật ký đồng bộ: mỗi lần chạy sync ghi 1 dòng (thời gian, số bản ghi, lỗi),
 * mỗi xung đột ghi 1 dòng. Dùng để demo TC07 và lấy số liệu TC09 / báo cáo D7.
 */
@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val kind: SyncLogKind,
    val message: String,
    val durationMs: Long = 0,
    val pushedCount: Int = 0,
    val appliedCount: Int = 0,
    val ignoredCount: Int = 0,
    val pulledCount: Int = 0,
)

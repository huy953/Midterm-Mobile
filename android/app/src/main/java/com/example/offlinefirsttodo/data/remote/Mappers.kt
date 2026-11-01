package com.example.offlinefirsttodo.data.remote

import com.example.offlinefirsttodo.data.local.SyncStatus
import com.example.offlinefirsttodo.data.local.TaskEntity

fun TaskEntity.toDto() = TaskDto(
    id = id,
    title = title,
    isCompleted = isCompleted,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Bản ghi đến từ server luôn là dữ liệu đã đồng bộ. */
fun TaskDto.toSyncedEntity() = TaskEntity(
    id = id,
    title = title,
    isCompleted = isCompleted,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = SyncStatus.SYNCED,
    lastError = null,
)

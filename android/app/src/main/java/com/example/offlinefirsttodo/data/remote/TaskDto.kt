package com.example.offlinefirsttodo.data.remote

import com.google.gson.annotations.SerializedName

/** Khớp với `TaskDTO` trong backend/app/schemas/task.py */
data class TaskDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("is_completed") val isCompleted: Boolean,
    @SerializedName("is_deleted") val isDeleted: Boolean,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
)

/** Body của POST /tasks/sync – khớp `SyncPayload` */
data class SyncRequest(
    @SerializedName("tasks") val tasks: List<TaskDto>,
)

/**
 * Kết quả của POST /tasks/sync – khớp `SyncResult`.
 * Để nullable vì Gson không dùng giá trị mặc định của Kotlin khi JSON thiếu trường.
 */
data class SyncResponse(
    @SerializedName("applied_ids") val appliedIds: List<String>?,
    @SerializedName("ignored_ids") val ignoredIds: List<String>?,
    @SerializedName("server_time") val serverTime: Long?,
)

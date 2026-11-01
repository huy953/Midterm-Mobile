package com.example.offlinefirsttodo.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST API của backend FastAPI (backend/app/routers/tasks.py).
 * Lưu ý: code backend hiện KHÔNG có tiền tố /api/v1 (khác với docs D4).
 * Nếu sau này backend thêm tiền tố, chỉ cần đổi Base URL thành http://host:8000/api/v1/
 */
interface TaskApi {

    /** Kéo các task có updated_at > since (gồm cả task đã xóa logic). */
    @GET("tasks")
    suspend fun getTasks(@Query("since") since: Long): List<TaskDto>

    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") id: String): TaskDto

    /** Đẩy lô thay đổi lên, server xử lý Last-Write-Wins và trả applied_ids / ignored_ids. */
    @POST("tasks/sync")
    suspend fun sync(@Body body: SyncRequest): SyncResponse
}

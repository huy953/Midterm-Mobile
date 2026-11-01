package com.example.offlinefirsttodo.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.offlinefirsttodo.TodoApp
import kotlinx.coroutines.CancellationException

/**
 * Tác vụ nền chạy bộ máy đồng bộ. WorkManager lưu tác vụ vào DB riêng của nó nên tác vụ
 * vẫn được chạy tiếp sau khi app bị đóng hoặc thiết bị khởi động lại (TC04).
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val engine = (applicationContext as TodoApp).container.syncEngine
        return try {
            engine.sync()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val retry = SyncEngine.isRetryable(e) && runAttemptCount < SyncConfig.MAX_ATTEMPTS
            Log.w("SyncWorker", "Lần chạy #${runAttemptCount + 1} lỗi, retry=$retry", e)
            if (retry) Result.retry() else Result.failure()
        }
    }
}

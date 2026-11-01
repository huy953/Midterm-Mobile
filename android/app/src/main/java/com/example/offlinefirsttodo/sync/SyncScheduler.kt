package com.example.offlinefirsttodo.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class WorkStatus(
    val isRunning: Boolean = false,
    /** Mô tả cho UI, VD "Đang chờ có mạng", "Lỗi tạm thời – sẽ thử lại (lần 2)". */
    val message: String? = null,
)

class SyncScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun oneTimeRequest() = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(networkConstraint)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, SyncConfig.BACKOFF_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Gọi sau mỗi thao tác thêm/sửa/xóa.
     *  - Chưa có tác vụ nào / tác vụ đang chờ mạng  -> KEEP: giữ tác vụ cũ, vì khi chạy nó sẽ đọc
     *    TẤT CẢ bản ghi chưa đồng bộ -> 50 thao tác offline vẫn chỉ 1 lần đồng bộ.
     *  - Tác vụ đang CHẠY -> nối thêm đúng 1 tác vụ phía sau, để thay đổi vừa xảy ra
     *    không bị bỏ sót nếu tác vụ đang chạy đã qua bước đọc dữ liệu.
     */
    suspend fun requestSync() = withContext(Dispatchers.IO) {
        val infos = workManager.getWorkInfosForUniqueWork(SyncConfig.ONE_TIME_WORK).get()
        val running = infos.any { it.state == WorkInfo.State.RUNNING }
        val queuedBehind = infos.count { it.state == WorkInfo.State.BLOCKED } > 0
        val policy = when {
            running && !queuedBehind -> ExistingWorkPolicy.APPEND_OR_REPLACE
            else -> ExistingWorkPolicy.KEEP
        }
        workManager.enqueueUniqueWork(SyncConfig.ONE_TIME_WORK, policy, oneTimeRequest())
    }

    /**
     * Nút "Đồng bộ ngay": bỏ qua thời gian chờ backoff và chạy lại ngay (nếu có mạng).
     * Nếu đang chạy thì không làm gì để tránh gửi trùng.
     * @return false nếu đang có tác vụ chạy
     */
    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        val infos = workManager.getWorkInfosForUniqueWork(SyncConfig.ONE_TIME_WORK).get()
        if (infos.any { it.state == WorkInfo.State.RUNNING }) return@withContext false
        workManager.enqueueUniqueWork(SyncConfig.ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, oneTimeRequest())
        true
    }

    /** Định kỳ 15 phút (mức tối thiểu của Android) kéo thay đổi từ thiết bị khác về. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, SyncConfig.BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncConfig.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    val status: Flow<WorkStatus> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(SyncConfig.ONE_TIME_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(SyncConfig.PERIODIC_WORK),
    ) { oneTime, periodic ->
        val running = (oneTime + periodic).any { it.state == WorkInfo.State.RUNNING }
        val waiting = oneTime.firstOrNull {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
        }
        val message = when {
            running -> "Đang đồng bộ…"
            waiting != null && waiting.runAttemptCount > 0 ->
                "Lỗi tạm thời – WorkManager sẽ tự thử lại (lần ${waiting.runAttemptCount + 1})"
            waiting != null -> "Đang chờ kết nối mạng để đồng bộ"
            else -> null
        }
        WorkStatus(isRunning = running, message = message)
    }
}

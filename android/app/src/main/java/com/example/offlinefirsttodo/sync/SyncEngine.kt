package com.example.offlinefirsttodo.sync

import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.example.offlinefirsttodo.data.local.AppDatabase
import com.example.offlinefirsttodo.data.local.SyncLogEntity
import com.example.offlinefirsttodo.data.local.SyncLogKind
import com.example.offlinefirsttodo.data.local.SyncStatus
import com.example.offlinefirsttodo.data.local.TaskEntity
import com.example.offlinefirsttodo.data.remote.ApiProvider
import com.example.offlinefirsttodo.data.remote.SyncRequest
import com.example.offlinefirsttodo.data.remote.TaskApi
import com.example.offlinefirsttodo.data.remote.TaskDto
import com.example.offlinefirsttodo.data.remote.toDto
import com.example.offlinefirsttodo.data.remote.toSyncedEntity
import com.example.offlinefirsttodo.util.AppPrefs
import com.example.offlinefirsttodo.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

data class SyncReport(
    val pushed: Int,
    val applied: Int,
    val ignored: Int,
    val pulled: Int,
    val conflicts: Int,
    val durationMs: Long,
)

/**
 * Bộ máy đồng bộ hai chiều. Thuật toán (khớp với backend LWW):
 *
 *  1. PUSH: lấy các bản ghi chưa đồng bộ trong Room -> đánh dấu SYNCING -> POST /tasks/sync theo lô.
 *     - id trong applied_ids : server đã nhận -> SYNCED (chỉ khi bản ghi không bị sửa tiếp trong lúc gửi).
 *     - id trong ignored_ids : XUNG ĐỘT, server có bản mới hơn (LWW server thắng)
 *                              -> GET /tasks/{id} lấy bản server ghi đè Room + ghi nhật ký xung đột.
 *     Lặp lại tối đa MAX_PUSH_ROUNDS vòng để gửi nốt thay đổi phát sinh trong lúc đang sync.
 *  2. PULL: GET /tasks?since=... -> gộp vào Room:
 *     - chưa có ở máy                      -> chèn (SYNCED)
 *     - máy đã SYNCED, server mới hơn      -> ghi đè
 *     - máy có thay đổi chưa gửi, server mới hơn -> XUNG ĐỘT, LWW: server thắng
 *     - máy mới hơn hoặc bằng              -> giữ nguyên (sẽ được đẩy lên ở lần sau)
 *     Bản ghi is_deleted=true cũng được lưu (tombstone) nên task đã xóa không "sống lại".
 *
 * Tính lặp an toàn (idempotency): id là UUID do client sinh + server so updated_at,
 * nên gửi lại cùng một payload (do mất phản hồi, retry, bấm sync nhiều lần) không tạo bản ghi trùng.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val apiProvider: ApiProvider,
    private val prefs: AppPrefs,
) {
    private val taskDao = db.taskDao()
    private val logDao = db.syncLogDao()

    /** Chỉ cho phép một lần đồng bộ tại một thời điểm trong process (one-time + periodic). */
    private val mutex = Mutex()

    /** Gọi khi mở app: bản ghi kẹt ở SYNCING (do app bị kill giữa chừng) quay về PENDING. */
    suspend fun recoverInterruptedSync() = mutex.withLock { taskDao.resetSyncingToPending() }

    suspend fun sync(): SyncReport = mutex.withLock { runSync() }

    private suspend fun runSync(): SyncReport {
        val start = SystemClock.elapsedRealtime()
        var pushed = 0
        var applied = 0
        var ignored = 0
        var conflicts = 0
        var lastServerTime: Long? = null

        try {
            val api = apiProvider.api()

            // ---------- 1. PUSH ----------
            for (round in 1..SyncConfig.MAX_PUSH_ROUNDS) {
                val pending = taskDao.getUnsynced()
                if (pending.isEmpty()) break
                Log.d(TAG, "Push vòng $round: ${pending.size} bản ghi")

                for (batch in pending.chunked(SyncConfig.BATCH_SIZE)) {
                    taskDao.markSyncing(batch.map { it.id })
                    val sentUpdatedAt: Map<String, Long> = batch.associate { it.id to it.updatedAt }
                    val localBefore: Map<String, TaskEntity> = batch.associateBy { it.id }

                    val response = api.sync(SyncRequest(batch.map { it.toDto() }))
                    pushed += batch.size
                    response.serverTime?.let { lastServerTime = it }

                    for (id in response.appliedIds.orEmpty()) {
                        val sent = sentUpdatedAt[id] ?: continue
                        taskDao.markSyncedIfUnchanged(id, sent)
                        applied++
                    }
                    for (id in response.ignoredIds.orEmpty()) {
                        val sent = sentUpdatedAt[id] ?: continue
                        ignored++
                        conflicts += resolveRejectedPush(api, id, sent, localBefore[id])
                    }
                }
                // Bản ghi không có trong applied/ignored (không mong đợi) -> quay về PENDING để gửi lại.
                taskDao.resetSyncingToPending()
            }

            // ---------- 2. PULL ----------
            val since = if (SyncConfig.FULL_PULL) 0L else prefs.lastSyncTime
            val remote = api.getTasks(since)
            val pullResult = mergeRemote(remote)
            conflicts += pullResult.conflicts

            val newMark = lastServerTime ?: remote.maxOfOrNull { it.updatedAt }
            if (newMark != null && newMark > prefs.lastSyncTime) prefs.lastSyncTime = newMark

            val report = SyncReport(
                pushed = pushed,
                applied = applied,
                ignored = ignored,
                pulled = pullResult.changed,
                conflicts = conflicts,
                durationMs = SystemClock.elapsedRealtime() - start,
            )
            logDao.insert(
                SyncLogEntity(
                    timestamp = System.currentTimeMillis(),
                    kind = SyncLogKind.SYNC_OK,
                    message = "Thành công: đẩy ${report.pushed} (nhận ${report.applied}, từ chối ${report.ignored}), " +
                        "kéo về ${report.pulled} thay đổi, xung đột ${report.conflicts}",
                    durationMs = report.durationMs,
                    pushedCount = report.pushed,
                    appliedCount = report.applied,
                    ignoredCount = report.ignored,
                    pulledCount = report.pulled,
                )
            )
            Log.i(TAG, "Sync OK: $report")
            return report
        } catch (e: CancellationException) {
            // WorkManager hủy tác vụ (VD: mất mạng giữa chừng) -> trả bản ghi về PENDING, không coi là lỗi.
            withContext(NonCancellable) { taskDao.resetSyncingToPending() }
            throw e
        } catch (e: Exception) {
            val message = describeError(e)
            Log.w(TAG, "Sync lỗi: $message", e)
            withContext(NonCancellable) {
                taskDao.markSyncingAsFailed(message)
                logDao.insert(
                    SyncLogEntity(
                        timestamp = System.currentTimeMillis(),
                        kind = SyncLogKind.SYNC_FAILED,
                        message = "Thất bại: $message" + if (isRetryable(e)) " – sẽ tự thử lại" else "",
                        durationMs = SystemClock.elapsedRealtime() - start,
                        pushedCount = pushed,
                        appliedCount = applied,
                        ignoredCount = ignored,
                    )
                )
            }
            throw e
        }
    }

    /**
     * Server từ chối bản ghi vì server có updated_at mới hơn (LWW: server thắng).
     * Lấy bản server về ghi đè Room – trừ khi người dùng lại vừa sửa tiếp bản ghi đó trong lúc gửi
     * (khi đó bản mới hơn sẽ được đẩy ở vòng sau và tự so LWW lại).
     * @return 1 (luôn tính là một xung đột)
     */
    private suspend fun resolveRejectedPush(
        api: TaskApi,
        id: String,
        sentUpdatedAt: Long,
        local: TaskEntity?,
    ): Int {
        val server = api.getTask(id)
        db.withTransaction {
            val current = taskDao.getById(id)
            if (current == null || current.updatedAt == sentUpdatedAt) {
                taskDao.upsert(server.toSyncedEntity())
            }
            logDao.insert(
                conflictLog(
                    localTitle = local?.title ?: "?",
                    localDeleted = local?.isDeleted == true,
                    localUpdatedAt = sentUpdatedAt,
                    server = server,
                    phase = "đẩy lên",
                )
            )
        }
        return 1
    }

    private data class PullResult(val changed: Int, val conflicts: Int)

    private suspend fun mergeRemote(remote: List<TaskDto>): PullResult {
        var changed = 0
        var conflicts = 0
        db.withTransaction {
            for (r in remote) {
                val local = taskDao.getById(r.id)
                when {
                    local == null -> {
                        taskDao.upsert(r.toSyncedEntity())
                        changed++
                    }
                    r.updatedAt <= local.updatedAt -> Unit // máy mới hơn hoặc giống hệt -> giữ
                    local.syncStatus == SyncStatus.SYNCED -> {
                        taskDao.upsert(r.toSyncedEntity())
                        changed++
                    }
                    else -> {
                        // Máy có thay đổi chưa gửi nhưng server đã có bản mới hơn: hai bên sửa độc lập.
                        taskDao.upsert(r.toSyncedEntity())
                        changed++
                        conflicts++
                        logDao.insert(
                            conflictLog(
                                localTitle = local.title,
                                localDeleted = local.isDeleted,
                                localUpdatedAt = local.updatedAt,
                                server = r,
                                phase = "kéo về",
                            )
                        )
                    }
                }
            }
        }
        return PullResult(changed, conflicts)
    }

    private fun conflictLog(
        localTitle: String,
        localDeleted: Boolean,
        localUpdatedAt: Long,
        server: TaskDto,
        phase: String,
    ): SyncLogEntity {
        val localDesc = if (localDeleted) "xóa \"$localTitle\"" else "\"$localTitle\""
        val serverDesc = if (server.isDeleted) "đã xóa" else "\"${server.title}\""
        return SyncLogEntity(
            timestamp = System.currentTimeMillis(),
            kind = SyncLogKind.CONFLICT,
            message = "Xung đột ($phase) – LWW giữ bản server.\n" +
                "• Máy này: $localDesc lúc ${TimeFormat.full(localUpdatedAt)} (bị loại)\n" +
                "• Server: $serverDesc lúc ${TimeFormat.full(server.updatedAt)} (thắng)",
        )
    }

    companion object {
        private const val TAG = "SyncEngine"

        /** Lỗi tạm thời -> nên thử lại; lỗi dữ liệu (4xx) -> thử lại cũng vô ích. */
        fun isRetryable(e: Throwable): Boolean = when (e) {
            is IOException -> true // mất mạng, timeout, server tắt (connection refused)
            is HttpException -> e.code() >= 500 || e.code() == 408 || e.code() == 429
            else -> false
        }

        fun describeError(e: Throwable): String = when (e) {
            is SocketTimeoutException -> "Hết thời gian chờ server (timeout)"
            is IOException -> "Không kết nối được server (${e.javaClass.simpleName})"
            is HttpException -> "Server trả lỗi HTTP ${e.code()}"
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}

package com.example.offlinefirsttodo.sync

object SyncConfig {
    /** Tên unique work – bảo đảm không có 2 tác vụ đồng bộ giống nhau chạy song song. */
    const val ONE_TIME_WORK = "task_sync_one_time"
    const val PERIODIC_WORK = "task_sync_periodic"

    /** Số bản ghi tối đa mỗi lần POST /tasks/sync. */
    const val BATCH_SIZE = 100

    /** Lặp push tối đa N vòng để bắt cả thay đổi người dùng thực hiện trong lúc đang đồng bộ. */
    const val MAX_PUSH_ROUNDS = 3

    /** Số lần WorkManager tự thử lại khi gặp lỗi tạm thời (mạng, timeout, 5xx). */
    const val MAX_ATTEMPTS = 6

    /** Backoff khởi đầu, tăng theo hàm mũ: 10s, 20s, 40s, ... */
    const val BACKOFF_SECONDS = 10L

    /**
     * true  = mỗi lần kéo dữ liệu gọi GET /tasks?since=0 (lấy toàn bộ, kể cả bản ghi đã xóa logic).
     * false = kéo tăng dần GET /tasks?since=<server_time lần trước>.
     *
     * Mặc định để TRUE vì backend hiện lọc `since` theo `updated_at` – đây là giờ của CLIENT lúc sửa,
     * không phải giờ server nhận. Một thay đổi offline từ máy B (updated_at cũ) đẩy lên muộn sẽ có
     * updated_at < mốc since của máy A -> máy A không bao giờ kéo về -> hai máy không hội tụ.
     * Khi backend thêm cột thời điểm server nhận (server_updated_at) và lọc theo cột đó thì đổi sang false.
     */
    const val FULL_PULL = true
}

package com.example.offlinefirsttodo.util

import android.content.Context
import com.example.offlinefirsttodo.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lưu cấu hình nhỏ: địa chỉ server và mốc đồng bộ gần nhất. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _baseUrlFlow = MutableStateFlow(baseUrl)
    val baseUrlFlow: StateFlow<String> = _baseUrlFlow.asStateFlow()

    var baseUrl: String
        get() = sp.getString(KEY_BASE_URL, null) ?: BuildConfig.DEFAULT_BASE_URL
        set(value) {
            sp.edit().putString(KEY_BASE_URL, value.trim()).apply()
            _baseUrlFlow.value = value.trim()
        }

    /** server_time của lần đồng bộ thành công gần nhất (dùng cho kéo tăng dần). */
    var lastSyncTime: Long
        get() = sp.getLong(KEY_LAST_SYNC, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_SYNC, value).apply()

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_LAST_SYNC = "last_sync_time"
    }
}

package com.example.offlinefirsttodo.data.remote

import com.example.offlinefirsttodo.util.AppPrefs
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Tạo Retrofit theo Base URL đang cấu hình. Khi người dùng đổi URL trong app,
 * lần gọi kế tiếp sẽ tự tạo Retrofit mới.
 */
class ApiProvider(private val prefs: AppPrefs) {

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private var cachedUrl: String? = null
    private var cachedApi: TaskApi? = null

    @Synchronized
    fun api(): TaskApi {
        val url = normalize(prefs.baseUrl)
        val current = cachedApi
        if (current != null && cachedUrl == url) return current

        val api = Retrofit.Builder()
            .baseUrl(url)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TaskApi::class.java)
        cachedUrl = url
        cachedApi = api
        return api
    }

    companion object {
        /** Retrofit bắt buộc base URL kết thúc bằng "/" */
        fun normalize(url: String): String {
            val trimmed = url.trim()
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}

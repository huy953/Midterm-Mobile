package com.example.offlinefirsttodo

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TodoApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        container.syncScheduler.schedulePeriodic()
        appScope.launch {
            // Bản ghi kẹt ở "Đang đồng bộ" do app bị kill giữa chừng -> quay về "Chưa đồng bộ"
            container.syncEngine.recoverInterruptedSync()
            // Mở app: đẩy thay đổi còn tồn và kéo dữ liệu mới từ thiết bị khác
            container.syncScheduler.requestSync()
        }
    }
}

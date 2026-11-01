package com.example.offlinefirsttodo

import android.content.Context
import com.example.offlinefirsttodo.data.local.AppDatabase
import com.example.offlinefirsttodo.data.remote.ApiProvider
import com.example.offlinefirsttodo.data.repository.TaskRepository
import com.example.offlinefirsttodo.sync.SyncEngine
import com.example.offlinefirsttodo.sync.SyncScheduler
import com.example.offlinefirsttodo.util.AppPrefs
import com.example.offlinefirsttodo.util.ConnectivityObserver

/** Dependency Injection thủ công: khởi tạo một lần trong Application, dùng chung toàn app. */
class AppContainer(context: Context) {
    val prefs = AppPrefs(context)
    val database = AppDatabase.create(context)
    val apiProvider = ApiProvider(prefs)
    val connectivity = ConnectivityObserver(context)
    val syncScheduler = SyncScheduler(context)
    val syncEngine = SyncEngine(database, apiProvider, prefs)
    val taskRepository = TaskRepository(database, syncScheduler)
}

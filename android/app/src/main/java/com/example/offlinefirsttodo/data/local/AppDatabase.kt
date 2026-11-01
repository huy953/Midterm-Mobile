package com.example.offlinefirsttodo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, SyncLogEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "offline_todo.db")
                // Khi đổi schema (tăng version) mà chưa viết Migration thì xóa DB cũ.
                // Với bài nộp nên thay bằng .addMigrations(...) – xem README phần Migration.
                .fallbackToDestructiveMigration()
                .build()
    }
}

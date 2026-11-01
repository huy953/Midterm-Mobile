package com.example.offlinefirsttodo.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormat {
    private val full = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
    private val short = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun full(ms: Long): String = synchronized(full) { full.format(Date(ms)) }
    fun time(ms: Long): String = synchronized(short) { short.format(Date(ms)) }
}

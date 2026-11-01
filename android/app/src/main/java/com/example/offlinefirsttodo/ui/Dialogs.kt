package com.example.offlinefirsttodo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.offlinefirsttodo.data.local.SyncLogEntity
import com.example.offlinefirsttodo.data.local.SyncLogKind
import com.example.offlinefirsttodo.util.TimeFormat

private const val MAX_TITLE = 255

@Composable
fun TaskEditDialog(
    title: String,
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    val trimmed = text.trim()
    val valid = trimmed.isNotEmpty() && trimmed.length <= MAX_TITLE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Tiêu đề") },
                supportingText = { Text("${trimmed.length}/$MAX_TITLE") },
                isError = trimmed.length > MAX_TITLE,
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = valid) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
    )
}

@Composable
fun ServerConfigDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl) }
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Địa chỉ backend") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "• Emulator: http://10.0.2.2:8000/\n" +
                        "• Máy thật cắm cáp (adb reverse tcp:8000 tcp:8000): http://localhost:8000/\n" +
                        "• Máy thật cùng Wi-Fi: http://<IP máy tính>:8000/ " +
                        "(server phải chạy với --host 0.0.0.0)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url.trim()) }, enabled = valid) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
    )
}

/** Nhật ký đồng bộ + thống kê tổng hợp (phục vụ TC09 và báo cáo D7). */
@Composable
fun SyncLogDialog(
    logs: List<SyncLogEntity>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val runs = logs.filter { it.kind != SyncLogKind.CONFLICT }
    val ok = runs.filter { it.kind == SyncLogKind.SYNC_OK }
    val conflicts = logs.count { it.kind == SyncLogKind.CONFLICT }
    val successRate = if (runs.isEmpty()) 0 else ok.size * 100 / runs.size
    val avgMs = if (ok.isEmpty()) 0L else ok.sumOf { it.durationMs } / ok.size
    val maxMs = ok.maxOfOrNull { it.durationMs } ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhật ký đồng bộ") },
        text = {
            Column {
                Text(
                    text = "Số lần đồng bộ: ${runs.size} • Thành công: ${ok.size} ($successRate%)\n" +
                        "Thời gian TB: $avgMs ms • Lâu nhất: $maxMs ms • Xung đột: $conflicts",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (logs.isEmpty()) {
                    Text("Chưa có dữ liệu.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(logs, key = { it.id }) { log -> LogItem(log) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
        dismissButton = { TextButton(onClick = onClear) { Text("Xóa nhật ký") } },
    )
}

@Composable
private fun LogItem(log: SyncLogEntity) {
    val (label, color) = when (log.kind) {
        SyncLogKind.SYNC_OK -> "OK" to Color(0xFF2E7D32)
        SyncLogKind.SYNC_FAILED -> "LỖI" to Color(0xFFC62828)
        SyncLogKind.CONFLICT -> "XUNG ĐỘT" to Color(0xFFEF6C00)
    }
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            text = "[$label] ${TimeFormat.full(log.timestamp)}" +
                if (log.kind != SyncLogKind.CONFLICT) " • ${log.durationMs} ms" else "",
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(log.message, style = MaterialTheme.typography.bodySmall)
    }
}

package com.example.offlinefirsttodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.offlinefirsttodo.data.local.SyncLogKind
import com.example.offlinefirsttodo.data.local.SyncStatus
import com.example.offlinefirsttodo.data.local.TaskEntity
import com.example.offlinefirsttodo.util.TimeFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(vm: TaskViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var showLogs by remember { mutableStateOf(false) }
    var showServer by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Công việc của tôi", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${state.tasks.size} công việc • ${state.unsyncedCount} chưa đồng bộ",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    if (state.work.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 12.dp).size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { vm.syncNow() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Đồng bộ ngay")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Nhật ký đồng bộ") },
                                onClick = { menuOpen = false; showLogs = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Cấu hình server") },
                                onClick = { menuOpen = false; showServer = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Thêm công việc")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SyncStatusCard(state = state, onSyncNow = { vm.syncNow() }, onOpenLogs = { showLogs = true })

            if (!state.isLoading && state.tasks.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { vm.toggleCompleted(task.id) },
                            onClick = { editing = task },
                            onDelete = {
                                vm.deleteTask(task.id)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = "Đã xóa \"${task.title}\"",
                                        actionLabel = "Hoàn tác",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) vm.restoreTask(task.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        TaskEditDialog(
            title = "Thêm công việc",
            initialText = "",
            onConfirm = { vm.addTask(it); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }
    editing?.let { task ->
        TaskEditDialog(
            title = "Sửa công việc",
            initialText = task.title,
            onConfirm = { vm.renameTask(task.id, it); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (showLogs) {
        SyncLogDialog(logs = logs, onClear = { vm.clearLogs() }, onDismiss = { showLogs = false })
    }
    if (showServer) {
        ServerConfigDialog(
            currentUrl = state.baseUrl,
            onSave = { vm.setBaseUrl(it); showServer = false },
            onDismiss = { showServer = false },
        )
    }
}

@Composable
private fun SyncStatusCard(state: TaskUiState, onSyncNow: () -> Unit, onOpenLogs: () -> Unit) {
    val offline = !state.isOnline
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (offline) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = if (offline) "● Đang OFFLINE" else "● Đang ONLINE",
                fontWeight = FontWeight.Bold,
                color = if (offline) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (offline) {
                Text(
                    "Mọi thay đổi vẫn được lưu trên máy và sẽ tự đồng bộ khi có mạng.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.work.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            state.lastRun?.let { last ->
                val ok = last.kind == SyncLogKind.SYNC_OK
                Text(
                    text = "Lần đồng bộ cuối: ${TimeFormat.time(last.timestamp)} • " +
                        (if (ok) "thành công" else "thất bại") + " • ${last.durationMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(onClick = onOpenLogs),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Chưa đồng bộ: ${state.unsyncedCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onSyncNow, enabled = !state.work.isRunning) {
                    Text(if (state.work.isRunning) "Đang đồng bộ…" else "Đồng bộ ngay")
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    SyncBadge(task.syncStatus)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sửa lúc ${TimeFormat.full(task.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (task.syncStatus == SyncStatus.FAILED && task.lastError != null) {
                    Text(
                        text = task.lastError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Xóa")
            }
        }
    }
}

@Composable
fun SyncBadge(status: SyncStatus) {
    val (label, fg, bg) = when (status) {
        SyncStatus.PENDING -> Triple("Chưa đồng bộ", Color(0xFF8D6E00), Color(0xFFFFF4CC))
        SyncStatus.SYNCING -> Triple("Đang đồng bộ", Color(0xFF1565C0), Color(0xFFE3F2FD))
        SyncStatus.SYNCED -> Triple("Đã đồng bộ", Color(0xFF2E7D32), Color(0xFFE8F5E9))
        SyncStatus.FAILED -> Triple("Đồng bộ thất bại", Color(0xFFC62828), Color(0xFFFFEBEE))
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Chưa có công việc nào.\nBấm + để thêm – kể cả khi đang offline.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

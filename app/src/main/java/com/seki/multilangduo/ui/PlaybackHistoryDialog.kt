package com.seki.multilangduo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seki.multilangduo.AppUiState
import com.seki.multilangduo.TtsViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun PlaybackHistoryDialog(state: AppUiState, model: TtsViewModel, onClose: () -> Unit, onLoaded: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val dates = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }
    LaunchedEffect(Unit) { model.refreshHistory() }
    LaunchedEffect(selectedId) {
        selectedId?.let(model::selectHistory) ?: model.clearHistorySelection()
    }
    DisposableEffect(Unit) { onDispose { model.clearHistorySelection() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("播放对话历史记录", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("关闭") }
                }
                if (state.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.historyError.isNotEmpty()) Text(state.historyError, color = MaterialTheme.colorScheme.error)
                if (selectedId == null) {
                    if (!state.historyLoading && state.history.isEmpty()) Text("暂无播放历史。开始朗读后会自动保存对话。")
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.history, key = { it.id }) { record ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(dates.format(Date(record.playedAt)), style = MaterialTheme.typography.labelLarge)
                                    Text(record.preview.ifEmpty { "（对话内容）" }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Row {
                                        TextButton(onClick = { selectedId = record.id }, enabled = record.readable) { Text("查看") }
                                        TextButton(onClick = { deletingId = record.id }) { Text("删除") }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { selectedId = null }) { Text("返回历史列表") }
                    val entry = state.historyDetail?.takeIf { it.id == selectedId }
                    if (state.historyDetailLoading) CircularProgressIndicator()
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (entry != null) {
                            Text(dates.format(Date(entry.playedAt)), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(12.dp))
                            SelectionContainer { Text(entry.text, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    if (state.busy) Text("请先结束当前任务，再载入其他对话。", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            if (entry != null && model.loadHistoryText(entry.id)) onLoaded()
                        }, enabled = entry != null && !state.busy && state.pendingExport == null) { Text("载入对话") }
                        OutlinedButton(onClick = { deletingId = selectedId }) { Text("删除") }
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("删除这条历史记录？") },
            text = { Text("删除历史记录后，当前输入文本和正在播放的对话仍会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedId == id) selectedId = null
                    model.deleteHistory(id)
                    deletingId = null
                }) { Text("删除") }
            }, dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } })
    }
}

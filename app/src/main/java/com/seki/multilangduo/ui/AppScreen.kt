package com.seki.multilangduo.ui

import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seki.multilangduo.TtsViewModel
import com.seki.multilangduo.model.*
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.math.roundToInt

private val previewSpeakerPrefix = Regex("^\\s*Speaker [12]:\\s*")

@Composable
fun AppScreen(model: TtsViewModel, onRead: () -> Unit, onExport: () -> Unit, onTtsSettings: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val preview = rememberLazyListState()
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val uiScope = rememberCoroutineScope()
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var cancelSectionOffset by remember { mutableIntStateOf(0) }
    var pickerLaunched by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        pickerLaunched = false; model.saveExport(uri)
    }
    LaunchedEffect(state.pendingExport) {
        if (state.pendingExport != null && !pickerLaunched) {
            try {
                pickerLaunched = true; save.launch("tts-audio.wav")
            } catch (e: Exception) {
                pickerLaunched = false; model.saveExport(null)
                model.message("文件保存失败：无法打开保存窗口（${e.message}）")
            }
        }
    }
    LaunchedEffect(state.busy) {
        if (state.busy) focus.clearFocus()
    }
    LaunchedEffect(state.currentLineIndex, cancelSectionOffset, scroll.maxValue) {
        if (state.currentLineIndex >= 0 && state.currentLineIndex < state.lines.size) {
            scroll.animateScrollTo(cancelSectionOffset.coerceIn(0, scroll.maxValue))
            preview.animateScrollToItem(state.currentLineIndex)
        }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().onSizeChanged { viewportHeight = it.height }.verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("文本转语音应用", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = { focus.clearFocus(); historyOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("播放对话历史记录") }
            SpeakerCard(1, state.speaker1, state.voices, true) { model.setSpeaker(1, it) }
            SpeakerCard(2, state.speaker2, state.voices, true) { model.setSpeaker(2, it) }
            if (state.ready && (state.speaker1.voiceName.isEmpty() || state.speaker2.voiceName.isEmpty())) {
                Text("没有可用语音：请在系统 TTS 设置中安装对应语言。", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onTtsSettings, enabled = !state.busy) { Text("系统 TTS 设置") }
            OutlinedTextField(
                value = state.text, onValueChange = model::setText,
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 480.dp),
                label = { Text("请输入文本") },
                supportingText = { Text("可用 Speaker 1:/Speaker 2: 前缀分配不同语音；支持 {pause:秒数} 和 {confirm}。") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 10
            )
            Button(onClick = onRead, enabled = state.ready && !state.busy && !pickerLaunched, modifier = Modifier.fillMaxWidth()) { Text("朗读") }
            Button(onClick = onExport, enabled = state.ready && !state.busy && !pickerLaunched, modifier = Modifier.fillMaxWidth()) { Text("生成语音并下载 (WAV)") }
            // Bound the final section to the visible viewport so the preview can fill all remaining
            // space below Cancel, the status and its heading, instead of leaving a fixed-height gap.
            Column(
                Modifier.fillMaxWidth().height(with(density) { viewportHeight.toDp() }.coerceAtLeast(240.dp))
                    .onGloballyPositioned { cancelSectionOffset = it.positionInParent().y.roundToInt() },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            OutlinedButton(onClick = model::cancel, enabled = state.busy && state.phase != TaskPhase.Saving, modifier = Modifier.fillMaxWidth()) { Text("取消朗读") }
            if (state.canPause) {
                Button(onClick = model::togglePause, modifier = Modifier.fillMaxWidth()) { Text(if (state.paused) "继续播放" else "暂停播放") }
            }
            if (state.message.isNotEmpty()) Text(state.message, color = MaterialTheme.colorScheme.primary)
            Text("朗读预览 (自动高亮当前行)", style = MaterialTheme.typography.titleMedium)
            LazyColumn(state = preview, modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFFAF3E0), RoundedCornerShape(10.dp)).padding(8.dp)) {
                itemsIndexed(state.lines) { index, line ->
                    val active = index == state.currentLineIndex
                    Text(previewSpeakerPrefix.replaceFirst(line.display, ""), modifier = Modifier.fillMaxWidth().background(if (active) Color(0xFFFFE082) else Color.Transparent, RoundedCornerShape(6.dp)).padding(8.dp),
                        color = if (active) Color(0xFFE53935) else Color(0xFF3E3E3E),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (active) 26.sp else 22.sp)
                }
            }
            }
        }
    }
    state.recognition?.let { ui ->
        AlertDialog(onDismissRequest = {}, title = { Text(if (ui.confirm) "等待确认或语音输入" else "请说出英文句子") },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = model::togglePause, modifier = Modifier.fillMaxWidth()) { Text(if (state.paused) "继续播放" else "暂停播放") }
                    Text(ui.status, color = MaterialTheme.colorScheme.primary)
                    Text("识别结果：")
                    Text(ui.text.ifEmpty { " " }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), fontSize = 20.sp)
                }
            }, confirmButton = { TextButton(onClick = model::continueRecognition) { Text("完成并继续") } },
            dismissButton = {
                if (ui.confirm) Column {
                    TextButton(onClick = model::stopRecording) { Text("停止录音") }
                    TextButton(onClick = model::exitRecognition) { Text("退出任务") }
                }
            })
    }
    if (state.summary.isNotEmpty()) AlertDialog(onDismissRequest = model::closeSummary,
        title = { Text("本次跟读识别汇总") },
        text = { Text(state.summary.joinToString("\n"), Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), fontSize = 20.sp) },
        confirmButton = { TextButton(onClick = model::closeSummary) { Text("关闭") } })
    if (historyOpen) PlaybackHistoryDialog(state, model,
        onClose = { historyOpen = false },
        onLoaded = { historyOpen = false; uiScope.launch { scroll.animateScrollTo(0) } })
}

@Composable
private fun SpeakerCard(id: Int, config: SpeakerConfig, voices: List<Voice>, enabled: Boolean, change: (SpeakerConfig) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Speaker $id", style = MaterialTheme.typography.titleLarge)
            Choice("语言", listOf("en" to "English", "zh" to "中文", "ja" to "日本語"), config.language, enabled) { change(config.copy(language = it)) }
            val available = voices.filter { it.locale.language == config.language }
            Choice("Voice", available.map { it.name to "${it.name} (${it.locale.toLanguageTag()})" }, config.voiceName, enabled) { change(config.copy(voiceName = it)) }
            Text("Speaker $id 速度：${String.format(Locale.US, "%.2f", config.rate)}")
            Slider(value = config.rate, onValueChange = { change(config.copy(rate = round(it * 100) / 100)) }, valueRange = 0.5f..2f, steps = 149, enabled = enabled)
            TextButton(onClick = { change(config.copy(rate = if (id == 1) 1f else 1.25f)) }, enabled = enabled) { Text(if (id == 1) "恢复 1" else "恢复 1.25") }
        }
    }
}

@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, selected: String, enabled: Boolean, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(options.find { it.first == selected }?.second ?: "没有可用语音")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { change(value); expanded = false }) }
            }
        }
    }
}

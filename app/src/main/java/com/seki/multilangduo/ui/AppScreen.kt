package com.seki.multilangduo.ui

import android.speech.tts.Voice
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.tween
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
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("reading_ui", Context.MODE_PRIVATE) }
    var theme by rememberSaveable { mutableIntStateOf(preferences.getInt("theme", 0).coerceIn(0, 2)) }
    var textScale by rememberSaveable { mutableIntStateOf(preferences.getInt("size", 2).coerceIn(0, 3)) }
    val palette = readingPalettes[theme]
    val readingScheme = if (theme == 2) darkColorScheme(
        primary = palette.accent, secondary = palette.accent, surfaceVariant = palette.surface, background = palette.background, surface = palette.surface,
        onBackground = palette.primary, onSurface = palette.primary, onSurfaceVariant = palette.secondary
    ) else lightColorScheme(primary = palette.accent, secondary = palette.accent, surfaceVariant = palette.surface, background = palette.background, surface = palette.surface,
        onBackground = palette.primary, onSurface = palette.primary, onSurfaceVariant = palette.secondary)

    val scroll = rememberScrollState()
    val preview = rememberLazyListState()
    val dragging by preview.interactionSource.collectIsDraggedAsState()
    val outerDragging by scroll.interactionSource.collectIsDraggedAsState()
    var lastDrag by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragging, outerDragging) {
        if (dragging || outerDragging) lastDrag = SystemClock.uptimeMillis()
        else if (lastDrag != 0L) lastDrag = SystemClock.uptimeMillis()
    }
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
    LaunchedEffect(state.busy, cancelSectionOffset, scroll.maxValue) {
        if (state.busy && !outerDragging && SystemClock.uptimeMillis() - lastDrag > 5000)
            scroll.animateScrollTo(cancelSectionOffset.coerceIn(0, scroll.maxValue), tween(300))
    }
    LaunchedEffect(state.currentLineIndex) {
        if (state.currentLineIndex in state.lines.indices && !dragging && !outerDragging &&
            SystemClock.uptimeMillis() - lastDrag > 5000) {
            val target = (preview.layoutInfo.viewportEndOffset * 0.4f).roundToInt()
            val visible = preview.layoutInfo.visibleItemsInfo.find { it.index == state.currentLineIndex }
            if (visible != null) preview.animateScrollBy((visible.offset - target).toFloat(), tween(300))
            else preview.animateScrollToItem(state.currentLineIndex, -target)
        }
    }
    MaterialTheme(colorScheme = readingScheme) {
        Scaffold(containerColor = palette.background) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().onSizeChanged { viewportHeight = it.height }.verticalScroll(scroll).padding(ReadingDimensions.horizontalPadding), verticalArrangement = Arrangement.spacedBy(ReadingDimensions.sectionSpacing)) {
                Text("文本转语音应用", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = { focus.clearFocus(); historyOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("播放对话历史记录") }
                Choice("阅读主题", listOf("0" to "柔和浅色", "1" to "暖色护眼", "2" to "深色"), theme.toString(), true) {
                    theme = it.toInt(); preferences.edit().putInt("theme", theme).apply()
                }
                Choice("字体大小", listOf("0" to "小", "1" to "标准", "2" to "大", "3" to "特大"), textScale.toString(), true) {
                    textScale = it.toInt(); preferences.edit().putInt("size", textScale).apply()
                }
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
                // Fill the viewport; allow outer scrolling in landscape and at large system font scales.
                Column(
                    Modifier.fillMaxWidth().height(with(density) { viewportHeight.toDp() }.coerceAtLeast(420.dp * density.fontScale))
                        .onGloballyPositioned { cancelSectionOffset = it.positionInParent().y.roundToInt() },
                    verticalArrangement = Arrangement.spacedBy(ReadingDimensions.sectionSpacing)
                ) {
                    OutlinedButton(onClick = model::cancel, enabled = state.busy && state.phase != TaskPhase.Saving,
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                        Text("取消朗读", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.canPause) {
                        Button(onClick = model::togglePause, shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                            Text(if (state.paused) "继续播放" else "暂停播放", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (state.message.isNotEmpty()) {
                        Text(state.message, color = palette.secondary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("朗读预览", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("自动高亮当前行 · 手动滚动后暂缓跟踪", fontSize = 14.sp, color = palette.secondary)
                    }
                    LazyColumn(state = preview, modifier = Modifier.fillMaxWidth().weight(1f)
                        .background(palette.surface, RoundedCornerShape(14.dp)),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)) {
                        itemsIndexed(state.lines, key = { index, _ -> index }) { index, line ->
                            val text = previewSpeakerPrefix.replaceFirst(line.display, "")
                            ReadingLine(text, readingRole(text, line.action !is ParsedLine.Speech),
                                index == state.currentLineIndex, palette, readingScales[textScale])
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

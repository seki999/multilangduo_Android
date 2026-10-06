package com.seki.multilangduo

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.seki.multilangduo.audio.AudioExporter
import com.seki.multilangduo.data.SettingsRepository
import com.seki.multilangduo.data.PlaybackHistoryRepository
import com.seki.multilangduo.model.*
import com.seki.multilangduo.parser.TextParser
import com.seki.multilangduo.playback.SequencePlayer
import com.seki.multilangduo.playback.PauseGate
import com.seki.multilangduo.service.PlaybackService
import com.seki.multilangduo.speech.SpeechRecognitionManager
import com.seki.multilangduo.tts.TtsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

data class AppUiState(
    val text: String, val speaker1: SpeakerConfig, val speaker2: SpeakerConfig,
    val voices: List<Voice> = emptyList(), val ready: Boolean = false,
    val phase: TaskPhase = TaskPhase.Idle, val currentLineIndex: Int = -1,
    val recognition: RecognitionUi? = null, val summary: List<String> = emptyList(),
    val message: String = "正在初始化 TTS...", val pendingExport: File? = null,
    val paused: Boolean = false,
    val history: List<PlaybackHistorySummary> = emptyList(),
    val historyLoading: Boolean = false, val historyError: String = "",
    val historyDetail: PlaybackHistoryEntry? = null, val historyDetailLoading: Boolean = false
) {
    val busy get() = phase in listOf(TaskPhase.Starting, TaskPhase.Speaking, TaskPhase.Listening, TaskPhase.WaitingForConfirmation, TaskPhase.Generating, TaskPhase.Saving)
    val canPause get() = phase in listOf(TaskPhase.Speaking, TaskPhase.Listening, TaskPhase.WaitingForConfirmation)
    val lines get() = TextParser.parse(text)
}

class PlaybackController(private val application: Application) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = PauseGate()
    private val settings = SettingsRepository(application)
    private val historyRepository = PlaybackHistoryRepository(File(application.filesDir, "playback-history"))
    private val tts = TtsManager(application)
    private val recognition = SpeechRecognitionManager(application)
    private val exporter = AudioExporter(tts)
    private val mutable = MutableStateFlow(AppUiState(settings.text(), settings.speaker(1), settings.speaker(2)))
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var token = 0
    private var recognizedSentences = mutableListOf<String>()
    private data class PendingTask(val export: Boolean, val lines: List<ScriptLine>, val text: String)
    private var pendingRequest: PendingTask? = null
    private var historyListJob: Job? = null
    private var historyDetailJob: Job? = null
    init { reloadVoices() }

    fun reloadVoices() {
        if (state.value.busy) return
        viewModelScope.launch {
            try {
                val voices = tts.voices()
                fun choose(config: SpeakerConfig): SpeakerConfig {
                    val available = voices.filter { it.locale.language == config.language }
                    return config.copy(voiceName = available.find { it.name == config.voiceName }?.name ?: available.firstOrNull()?.name.orEmpty())
                }
                mutable.update { it.copy(voices = voices, ready = true, speaker1 = choose(it.speaker1), speaker2 = choose(it.speaker2), message = if (voices.isEmpty()) "没有可用语音，请在系统 TTS 设置中安装语音数据。" else "") }
                settings.saveSpeaker(1, state.value.speaker1); settings.saveSpeaker(2, state.value.speaker2)
            } catch (e: Exception) { mutable.update { it.copy(ready = false, message = "TTS 初始化失败：${e.message}") } }
        }
    }
    fun setText(text: String) { mutable.update { it.copy(text = text) }; settings.saveText(text) }
    fun setSpeaker(id: Int, config: SpeakerConfig) {
        val clean = if ((if (id == 1) state.value.speaker1 else state.value.speaker2).language != config.language) {
            config.copy(voiceName = state.value.voices.firstOrNull { it.locale.language == config.language }?.name.orEmpty())
        } else config
        mutable.update { if (id == 1) it.copy(speaker1 = clean) else it.copy(speaker2 = clean) }
        settings.saveSpeaker(id, clean)
    }
    fun message(text: String) { mutable.update { it.copy(message = text) } }
    fun refreshHistory() {
        historyListJob?.cancel()
        mutable.update { it.copy(historyLoading = true, historyError = "") }
        historyListJob = viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { historyRepository.list() }
                mutable.update { it.copy(history = entries) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(historyError = "历史记录读取失败：${e.message}") } }
            finally { if (currentCoroutineContext().isActive) mutable.update { it.copy(historyLoading = false) } }
        }
    }
    fun selectHistory(id: String) {
        historyDetailJob?.cancel()
        mutable.update { it.copy(historyDetail = null, historyDetailLoading = true, historyError = "") }
        historyDetailJob = viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) { historyRepository.load(id) } ?: error("记录已不存在")
                mutable.update { it.copy(historyDetail = entry) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(historyError = "对话读取失败：${e.message}") } }
            finally { if (currentCoroutineContext().isActive) mutable.update { it.copy(historyDetailLoading = false) } }
        }
    }
    fun clearHistorySelection() {
        historyDetailJob?.cancel()
        mutable.update { it.copy(historyDetail = null, historyDetailLoading = false, historyError = "") }
    }
    fun deleteHistory(id: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { historyRepository.delete(id) }
                if (state.value.historyDetail?.id == id) clearHistorySelection()
                refreshHistory()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(historyError = "历史记录删除失败：${e.message}") } }
        }
    }
    fun loadHistoryText(id: String): Boolean {
        if (state.value.busy || state.value.pendingExport != null) return false
        val entry = state.value.historyDetail?.takeIf { it.id == id } ?: return false
        setText(entry.text)
        message("历史对话已载入，可以点击朗读。")
        return true
    }
    fun needsMicrophone() = TextParser.parse(state.value.text).any { it.action !is ParsedLine.Speech }
    private fun config(id: Int) = if (id == 1) state.value.speaker1 else state.value.speaker2
    private fun validate(lines: List<ScriptLine>) {
        check(state.value.ready) { "TTS 尚未就绪" }
        check(lines.isNotEmpty()) { "请输入文本" }
        for (line in lines) if (line.action is ParsedLine.Speech && line.action.text.isNotBlank()) {
            val config = config(line.action.speaker)
            check(state.value.voices.any { it.name == config.voiceName && it.locale.language == config.language }) { "Speaker ${line.action.speaker} 没有可用语音，请在系统 TTS 设置中安装对应语言。" }
        }
    }
    fun read() = request(false)
    fun export() = request(true)
    private fun request(export: Boolean) {
        if (state.value.busy) return
        val originalText = state.value.text
        val lines = TextParser.parse(originalText)
        try {
            validate(lines)
            if (!export && lines.any { it.action !is ParsedLine.Speech }) {
                check(ContextCompat.checkSelfPermission(application, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "麦克风权限未授权，请允许麦克风后重试。" }
            }
        } catch (e: Exception) { message(e.message.orEmpty()); return }
        recognizedSentences = mutableListOf()
        pendingRequest = PendingTask(export, lines, originalText)
        mutable.update { it.copy(phase = TaskPhase.Starting, paused = false, summary = emptyList(), message = "正在启动后台任务...") }
        try {
            PlaybackService.start(application, export, !export && lines.any { it.action !is ParsedLine.Speech })
        } catch (e: Exception) {
            pendingRequest = null
            mutable.update { it.copy(phase = TaskPhase.Idle, message = "无法启动后台任务：${e.message}") }
        }
    }
    fun executePending() {
        val request = pendingRequest ?: return
        pendingRequest = null
        start(request.export, request.lines, request.text)
    }
    private fun start(export: Boolean, lines: List<ScriptLine>, originalText: String) {
        gate.resume()
        val run = ++token
        recognizedSentences = mutableListOf()
        mutable.update { it.copy(phase = if (export) TaskPhase.Generating else TaskPhase.Speaking, paused = false, message = if (export) "正在生成 WAV..." else "", summary = emptyList()) }
        task = viewModelScope.launch {
            var directory: File? = null
            try {
                if (!export) {
                    try {
                        val entries = withContext(Dispatchers.IO) {
                            historyRepository.save(originalText)
                            historyRepository.list()
                        }
                        mutable.update { it.copy(history = entries) }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        mutable.update { it.copy(historyError = "本次对话未能保存到历史记录：${e.message}") }
                        message("历史记录保存失败，朗读仍将继续：${e.message}")
                    }
                }
                if (export) {
                    directory = File(application.cacheDir, "export-${UUID.randomUUID()}").apply { mkdirs() }
                    val file = exporter.generate(lines, ::config, directory) { index -> mutable.update { it.copy(currentLineIndex = index) } }
                    mutable.update { it.copy(pendingExport = file, message = "WAV 已生成，请选择保存位置。") }
                    directory = null // Retained until the document picker finishes.
                } else {
                    val completed = SequencePlayer().play(lines,
                        highlight = { index -> mutable.update { it.copy(currentLineIndex = index) } },
                        speak = { action ->
                            mutable.update { it.copy(phase = TaskPhase.Speaking) }
                            if (action.text.isNotBlank()) {
                                val speaker = config(action.speaker)
                                while (currentCoroutineContext().isActive) {
                                    gate.awaitResumed()
                                    try {
                                        // Normal playback goes straight through Android TTS.
                                        // This avoids per-line WAV synthesis + MediaPlayer preparation,
                                        // so Speaker 1 -> Speaker 2 transitions are much faster.
                                        tts.speakAndAwait(action.text, speaker)
                                        break
                                    } catch (e: CancellationException) {
                                        // Android TextToSpeech has no native pause/resume. When the user
                                        // pauses, stop the utterance and restart the current line after resume.
                                        if (!gate.isPaused || !currentCoroutineContext().isActive) throw e
                                        gate.awaitResumed()
                                    }
                                }
                            }
                        },
                        listen = { seconds ->
                            mutable.update { it.copy(phase = if (seconds == null) TaskPhase.WaitingForConfirmation else TaskPhase.Listening) }
                            val result = recognition.recognize(seconds, gate) { ui -> mutable.update { it.copy(recognition = ui) } }
                            mutable.update { it.copy(recognition = null) }
                            result
                        },
                        recognized = { recognizedSentences += it },
                        beforeLine = { gate.awaitResumed() }
                    )
                    if (!completed) { cancel(); return@launch }
                }
                if (!export) message("朗读完成。")
                mutable.update { it.copy(phase = TaskPhase.Completed, paused = false, summary = recognizedSentences.toList()) }
            } catch (e: CancellationException) {
                if (currentCoroutineContext().isActive && run == token) {
                    // An engine can stop its own utterance without the enclosing job being cancelled.
                    mutable.update { it.copy(phase = TaskPhase.Idle, paused = false, message = "语音任务被引擎中断，请重新开始。", summary = recognizedSentences.toList()) }
                } else throw e
            }
            catch (e: Exception) {
                if (run == token) mutable.update { it.copy(phase = TaskPhase.Idle, paused = false, message = "${if (export) "WAV 生成" else "朗读"}失败：${e.message}", summary = recognizedSentences.toList()) }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { directory?.deleteRecursively() }
                if (run == token) {
                    tts.stop(); recognition.cancel(); gate.resume()
                    mutable.update { it.copy(currentLineIndex = -1, recognition = null) }
                    task = null
                }
            }
        }
    }
    fun cancel() {
        if (!state.value.busy) return
        ++token; pendingRequest = null; task?.cancel(); task = null; tts.stop(); recognition.cancel(); gate.resume()
        mutable.update { it.copy(phase = TaskPhase.Cancelled, paused = false, currentLineIndex = -1, recognition = null, message = "已取消", summary = recognizedSentences.toList()) }
    }
    fun pausePlayback() {
        if (!state.value.canPause || gate.isPaused) return
        gate.pause(); tts.stop(); recognition.pause()
        mutable.update { it.copy(paused = true) }
    }
    fun resumePlayback() {
        if (!state.value.canPause || !gate.isPaused) return
        try {
            gate.resume(); recognition.resume()
            mutable.update { it.copy(paused = false) }
        } catch (e: Exception) { message("继续播放失败：${e.message}") }
    }
    fun togglePause() { if (state.value.paused) resumePlayback() else pausePlayback() }
    fun continueRecognition() { resumePlayback(); recognition.continueTask() }
    fun stopRecording() = recognition.stopRecording()
    fun exitRecognition() = cancel()
    fun closeSummary() { mutable.update { it.copy(summary = emptyList()) } }
    fun saveExport(uri: Uri?) {
        val file = state.value.pendingExport ?: return
        mutable.update { it.copy(pendingExport = null) }
        if (uri == null) {
            file.parentFile?.deleteRecursively(); message("已取消保存。"); return
        }
        val run = ++token
        mutable.update { it.copy(phase = TaskPhase.Saving, message = "正在保存 WAV...") }
        try { PlaybackService.start(application, export = true, microphone = false) }
        catch (e: Exception) {
            file.parentFile?.deleteRecursively()
            mutable.update { it.copy(phase = TaskPhase.Idle, message = "无法启动文件保存任务：${e.message}") }
            return
        }
        task = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    application.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        file.inputStream().use { input ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: error("无法打开保存位置")
                }
                message("WAV 文件已保存。")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("文件保存失败：${e.message}") }
            finally {
                withContext(NonCancellable + Dispatchers.IO) { file.parentFile?.deleteRecursively() }
                if (run == token) { mutable.update { it.copy(phase = TaskPhase.Completed) }; task = null }
            }
        }
    }
    fun close() {
        task?.cancel(); tts.shutdown(); recognition.cancel(); viewModelScope.cancel()
        state.value.pendingExport?.parentFile?.deleteRecursively()
    }
}

package com.seki.multilangduo.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.seki.multilangduo.model.RecognitionUi
import com.seki.multilangduo.playback.PauseGate
import kotlinx.coroutines.*

class SpeechRecognitionManager(private val context: Context) {
    private var session: Session? = null
    private inner class Session(val scope: CoroutineScope, val confirm: Boolean, val update: (RecognitionUi) -> Unit) {
        val decision = CompletableDeferred<Boolean>()
        var recognizer: SpeechRecognizer? = null
        var restart: Job? = null
        var release: Job? = null
        val sentences = mutableListOf<String>()
        var partial = ""
        var stopped = false
        var suspended = false
        var closed = false
        var status = "正在听你读..."
        fun text() = (sentences + partial.takeIf { it.isNotBlank() }.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
        fun render() { if (!closed) update(RecognitionUi(confirm, text(), status)) }
        fun start() {
            if (closed || stopped || suspended) return
            recognizer?.destroy()
            val current = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                fun valid() = !closed && recognizer === current
                override fun onReadyForSpeech(params: Bundle?) { if (valid() && !stopped) { status = "正在听你读..."; render() } }
                override fun onBeginningOfSpeech() { if (valid() && !stopped) { status = "正在录入..."; render() } }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onResults(results: Bundle?) {
                    if (!valid()) return
                    val final = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (final.isNotBlank()) { sentences += final; partial = "" }
                    else if (partial.isNotBlank()) { sentences += partial; partial = "" }
                    render(); scheduleRestart()
                }
                override fun onPartialResults(results: Bundle?) {
                    if (!valid()) return
                    partial = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    render()
                }
                override fun onError(error: Int) {
                    if (!valid() || stopped) return
                    // Recover short sessions/timeouts, without spinning on unavailable services or denied permission.
                    val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    status = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限未授权"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别网络错误，请检查系统识别服务的网络"
                        SpeechRecognizer.ERROR_AUDIO -> "无法使用麦克风"
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未识别到语音，继续听你读..."
                        else -> "语音识别出错（$error）"
                    }
                    if (!recoverable) stopped = true
                    render()
                    if (recoverable) scheduleRestart()
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            try {
                current.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                })
            } catch (e: Exception) {
                stopped = true; status = "SpeechRecognizer 启动失败：${e.message}"; render()
            }
        }
        fun scheduleRestart() {
            if (stopped || closed || suspended) return
            restart?.cancel()
            restart = scope.launch {
                delay(300)
                if (partial.isNotBlank()) { sentences += partial; partial = "" }
                start()
            }
        }
        fun stopRecording() {
            if (stopped || closed) return
            stopped = true; restart?.cancel(); recognizer?.stopListening()
            status = "已停止录入 (点击“完成并继续”以完成)"; render()
            release = scope.launch {
                delay(500)
                recognizer?.cancel(); recognizer?.destroy(); recognizer = null
            }
        }
        fun close() {
            closed = true; restart?.cancel(); release?.cancel(); recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        }
        fun pause() {
            suspended = true; restart?.cancel(); release?.cancel()
            recognizer?.cancel(); recognizer?.destroy(); recognizer = null
            status = "已暂停录音，点击“继续播放”恢复"; render()
        }
        fun resume() {
            if (!suspended || closed) return
            suspended = false
            if (!stopped) {
                if (partial.isNotBlank()) { sentences += partial; partial = "" }
                status = "正在听你读..."; render(); start()
            } else { status = "已停止录入 (点击“完成并继续”以完成)"; render() }
        }
    }
    suspend fun recognize(seconds: Double?, gate: PauseGate, update: (RecognitionUi) -> Unit): String? = coroutineScope {
        check(SpeechRecognizer.isRecognitionAvailable(context)) { "手机不支持 SpeechRecognizer，请安装或启用系统语音识别服务。" }
        val s = Session(this, seconds == null, update)
        session = s
        try {
            s.render(); s.start()
            val timer = seconds?.let {
                launch {
                    require(it.isFinite() && it >= 0 && it <= Long.MAX_VALUE / 1000.0) { "pause 时长无效" }
                    gate.delayActive((it * 1000).toLong())
                    s.decision.complete(true)
                }
            }
            val continueTask = s.decision.await()
            timer?.cancel()
            s.stopRecording()
            // Give stopListening a short opportunity to deliver the final result; keep partial text if it does not.
            if (continueTask) gate.delayActive(350)
            if (continueTask) s.text() else null
        } finally { s.close(); if (session === s) session = null }
    }
    fun continueTask() { session?.decision?.complete(true) }
    fun exitTask() { session?.decision?.complete(false) }
    fun stopRecording() { session?.stopRecording() }
    fun pause() { session?.pause() }
    fun resume() { session?.resume() }
    fun cancel() { session?.close(); session?.decision?.cancel(); session = null }
}

package com.seki.multilangduo.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.seki.multilangduo.model.SpeakerConfig
import com.seki.multilangduo.audio.PcmFormat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SynthesizedAudio(val engineFile: File, val pcmFile: File, val format: PcmFormat?)

class TtsManager(context: Context) {
    private val ready = CompletableDeferred<Unit>()
    private class Pending(val result: CompletableDeferred<Unit>, val raw: File?) {
        var format: PcmFormat? = null
        var stream: FileOutputStream? = null
        @Synchronized fun begin(rate: Int, encoding: Int, channels: Int) {
            format = PcmFormat(rate, channels, encoding)
            raw?.let { stream = FileOutputStream(it) }
        }
        @Synchronized fun audio(bytes: ByteArray) { stream?.write(bytes) }
        @Synchronized fun close() { stream?.close(); stream = null }
    }
    private val pending = ConcurrentHashMap<String, Pending>()
    private val engine = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) ready.complete(Unit)
        else ready.completeExceptionally(IllegalStateException("TTS 初始化失败，请安装或启用系统 TTS 引擎。"))
    }
    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onBeginSynthesis(id: String?, rate: Int, encoding: Int, channels: Int) {
                try { pending[id]?.begin(rate, encoding, channels) }
                catch (e: Exception) { finish(id, e) }
            }
            override fun onAudioAvailable(id: String?, audio: ByteArray?) {
                try { if (audio != null) pending[id]?.audio(audio) }
                catch (e: Exception) { finish(id, e) }
            }
            override fun onDone(id: String?) { finish(id, null) }
            @Deprecated("Android callback")
            override fun onError(id: String?) { finish(id, IllegalStateException("朗读或合成失败")) }
            override fun onError(id: String?, code: Int) { finish(id, IllegalStateException("朗读或合成失败（TTS $code）")) }
            override fun onStop(id: String?, interrupted: Boolean) { finish(id, CancellationException("TTS 已停止")) }
        })
    }
    private fun finish(id: String?, error: Exception?) {
        val p = pending.remove(id) ?: return
        try { p.close() } catch (e: Exception) { p.result.completeExceptionally(e); return }
        if (error == null) p.result.complete(Unit) else p.result.completeExceptionally(error)
    }
    suspend fun voices(): List<Voice> {
        withTimeout(20_000) { ready.await() }
        return engine.voices.orEmpty().sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
    }
    private suspend fun configure(config: SpeakerConfig) {
        withTimeout(20_000) { ready.await() }
        val voice = engine.voices.orEmpty().find { it.name == config.voiceName && it.locale.language == config.language }
            ?: error("没有找到 ${config.language} 的可用语音，请重新选择 Voice。")
        check(engine.setVoice(voice) == TextToSpeech.SUCCESS) { "设置 Voice 失败：${voice.name}" }
        check(engine.setSpeechRate(config.rate) == TextToSpeech.SUCCESS) { "设置语速失败" }
    }
    // A long source line is split only to respect the engine limit; the UI keeps its original line index.
    private fun parts(text: String): List<String> {
        val max = TextToSpeech.getMaxSpeechInputLength() - 1
        val result = mutableListOf<String>()
        var from = 0
        while (from < text.length) {
            var end = minOf(text.length, from + max)
            if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
            result += text.substring(from, end)
            from = end
        }
        return result
    }
    suspend fun speakAndAwait(text: String, config: SpeakerConfig) {
        if (text.isBlank()) return
        configure(config)
        for (part in parts(text)) awaitUtterance(null) { id -> engine.speak(part, TextToSpeech.QUEUE_FLUSH, Bundle(), id) }
    }
    suspend fun synthesize(text: String, config: SpeakerConfig, directory: File): List<SynthesizedAudio> {
        if (text.isBlank()) return emptyList()
        configure(config)
        return parts(text).map { part ->
            val id = UUID.randomUUID().toString()
            val file = File(directory, "$id.engine")
            val raw = File(directory, "$id.pcm")
            val p = awaitUtterance(raw) { utterance -> engine.synthesizeToFile(part, Bundle(), file, utterance) }
            SynthesizedAudio(file, raw, p.format)
        }
    }
    private suspend fun awaitUtterance(raw: File?, enqueue: (String) -> Int): Pending {
        val id = UUID.randomUUID().toString()
        val p = Pending(CompletableDeferred(), raw)
        pending[id] = p
        try {
            check(enqueue(id) == TextToSpeech.SUCCESS) { "TTS 无法开始朗读或合成" }
            p.result.await() // Completion is driven by onDone, never by guessed speech duration.
            return p
        } finally {
            pending.remove(id)
            p.close()
        }
    }
    fun stop() {
        engine.stop()
        pending.keys.toList().forEach { finish(it, CancellationException("已取消")) }
    }
    fun shutdown() { stop(); engine.shutdown() }
}

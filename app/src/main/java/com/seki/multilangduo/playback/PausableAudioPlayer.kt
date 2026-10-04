package com.seki.multilangduo.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import kotlinx.coroutines.CompletableDeferred
import java.io.File

class PausableAudioPlayer(context: Context, private val onFocusLost: () -> Unit) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) onFocusLost()
        }.build()
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var prepared = false
    private var completed: CompletableDeferred<Unit>? = null

    suspend fun playAndAwait(file: File, gate: PauseGate) {
        gate.awaitResumed()
        val current = MediaPlayer()
        val preparation = CompletableDeferred<Unit>()
        val completion = CompletableDeferred<Unit>()
        player = current; completed = completion; prepared = false
        try {
            current.setAudioAttributes(attributes)
            current.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
            current.setOnPreparedListener { prepared = true; preparation.complete(Unit) }
            current.setOnCompletionListener { completion.complete(Unit) }
            current.setOnErrorListener { _, what, extra ->
                val failure = IllegalStateException("音频播放失败（$what / $extra）")
                preparation.completeExceptionally(failure); completion.completeExceptionally(failure); true
            }
            current.setDataSource(file.absolutePath)
            current.prepareAsync()
            preparation.await()
            gate.awaitResumed()
            requestFocus()
            current.start()
            completion.await()
            gate.awaitResumed() // Pause on the completion boundary must not advance the next line.
        } finally {
            current.setOnPreparedListener(null); current.setOnCompletionListener(null); current.setOnErrorListener(null)
            current.release()
            if (player === current) { player = null; completed = null; prepared = false }
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
    private fun requestFocus() {
        check(audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "无法获得音频焦点，请停止其他音频后继续。" }
    }
    fun pause() {
        if (prepared && player?.isPlaying == true) player?.pause()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
    fun resume() {
        if (prepared && completed?.isCompleted == false) { requestFocus(); player?.start() }
    }
    fun stop() {
        if (prepared && player?.isPlaying == true) player?.pause()
        completed?.cancel()
    }
}

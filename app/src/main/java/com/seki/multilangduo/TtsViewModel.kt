package com.seki.multilangduo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.seki.multilangduo.model.SpeakerConfig

/** UI adapter only: clearing an Activity's ViewModel does not terminate the foreground service task. */
class TtsViewModel(application: Application) : AndroidViewModel(application) {
    private val playback = (application as MultilangDuoApplication).playback
    val state = playback.state
    fun reloadVoices() = playback.reloadVoices()
    fun setText(text: String) = playback.setText(text)
    fun setSpeaker(id: Int, config: SpeakerConfig) = playback.setSpeaker(id, config)
    fun message(text: String) = playback.message(text)
    fun needsMicrophone() = playback.needsMicrophone()
    fun read() = playback.read()
    fun export() = playback.export()
    fun cancel() = playback.cancel()
    fun togglePause() = playback.togglePause()
    fun continueRecognition() = playback.continueRecognition()
    fun stopRecording() = playback.stopRecording()
    fun exitRecognition() = playback.exitRecognition()
    fun closeSummary() = playback.closeSummary()
    fun saveExport(uri: Uri?) = playback.saveExport(uri)
    fun refreshHistory() = playback.refreshHistory()
    fun selectHistory(id: String) = playback.selectHistory(id)
    fun clearHistorySelection() = playback.clearHistorySelection()
    fun deleteHistory(id: String) = playback.deleteHistory(id)
    fun loadHistoryText(id: String) = playback.loadHistoryText(id)
}

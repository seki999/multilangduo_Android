package com.seki.multilangduo.playback

import com.seki.multilangduo.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared ordering boundary: no next line can run until the preceding suspend operation finishes. */
class SequencePlayer {
    suspend fun play(
        lines: List<ScriptLine>,
        highlight: (Int) -> Unit,
        speak: suspend (ParsedLine.Speech) -> Unit,
        listen: suspend (Double?) -> String?,
        recognized: (String) -> Unit,
        beforeLine: suspend () -> Unit = {}
    ): Boolean {
        for ((index, line) in lines.withIndex()) {
            currentCoroutineContext().ensureActive()
            beforeLine()
            highlight(index)
            when (val action = line.action) {
                is ParsedLine.Speech -> speak(action)
                else -> {
                    val result = listen((action as? ParsedLine.Pause)?.seconds) ?: return false
                    if (result.isNotBlank()) recognized(result)
                }
            }
        }
        currentCoroutineContext().ensureActive()
        return true
    }
}

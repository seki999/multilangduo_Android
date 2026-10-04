package com.seki.multilangduo.audio

import com.seki.multilangduo.model.*
import com.seki.multilangduo.tts.TtsManager
import kotlinx.coroutines.*
import java.io.File

class AudioExporter(private val tts: TtsManager) {
    suspend fun generate(lines: List<ScriptLine>, config: (Int) -> SpeakerConfig, directory: File, highlight: (Int) -> Unit): File {
        val output = File(directory, "tts-audio.wav")
        val writer = withContext(Dispatchers.IO) { WavWriter(output) }
        try {
            for ((index, line) in lines.withIndex()) {
                currentCoroutineContext().ensureActive()
                highlight(index)
                when (val action = line.action) {
                    ParsedLine.Confirm -> Unit
                    is ParsedLine.Pause -> withContext(Dispatchers.IO) {
                        val context = currentCoroutineContext()
                        writer.silence(action.seconds) { context.ensureActive() }
                    }
                    is ParsedLine.Speech -> {
                        val audio = tts.synthesize(action.text, config(action.speaker), directory)
                        for (part in audio) withContext(Dispatchers.IO) {
                            val context = currentCoroutineContext()
                            // Callback PCM has an explicit sample format even when a vendor emits a non-WAV file.
                            if (part.format != null && part.pcmFile.length() > 0) {
                                writer.appendRaw(part.pcmFile, part.format) { context.ensureActive() }
                            } else {
                                val wav = part.engineFile.inputStream().use { input ->
                                    val header = ByteArray(12)
                                    input.read(header) == 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
                                }
                                if (wav) writer.appendWav(part.engineFile) { context.ensureActive() }
                                else {
                                    val format = EngineAudioDecoder.decode(part.engineFile, part.pcmFile) { context.ensureActive() }
                                    writer.appendRaw(part.pcmFile, format) { context.ensureActive() }
                                }
                            }
                            part.engineFile.delete(); part.pcmFile.delete()
                        }
                    }
                }
            }
        } finally { withContext(NonCancellable + Dispatchers.IO) { writer.close() } }
        return output
    }
}

package com.seki.multilangduo.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.io.File

/** Fallback for engines which emit encoded audio and omit onAudioAvailable PCM callbacks. */
object EngineAudioDecoder {
    fun decode(source: File, output: File, checkCancelled: () -> Unit): PcmFormat {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("TTS 输出没有可解码的音频轨道")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME) ?: error("音频编码未知"))
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            var inputEnded = false
            var outputEnded = false
            var pcmFormat: PcmFormat? = null
            var progressAt = SystemClock.elapsedRealtime()
            val info = MediaCodec.BufferInfo()
            output.outputStream().use { stream ->
                while (!outputEnded) {
                    checkCancelled()
                    check(SystemClock.elapsedRealtime() - progressAt < 15000) { "系统音频解码器无响应" }
                    if (!inputEnded) {
                        val index = decoder.dequeueInputBuffer(10000)
                        if (index >= 0) {
                            val buffer = decoder.getInputBuffer(index) ?: error("解码输入缓冲区不可用")
                            val count = extractor.readSampleData(buffer, 0)
                            if (count < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                decoder.queueInputBuffer(index, 0, count, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                            progressAt = SystemClock.elapsedRealtime()
                        }
                    }
                    when (val index = decoder.dequeueOutputBuffer(info, 10000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder.outputFormat
                            val next = PcmFormat(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT)
                            check(pcmFormat == null || pcmFormat == next) { "音频解码过程中 PCM 格式发生变化" }
                            pcmFormat = next
                            progressAt = SystemClock.elapsedRealtime()
                        }
                        else -> if (index >= 0) {
                            try {
                                if (info.size > 0) {
                                    val buffer = decoder.getOutputBuffer(index) ?: error("解码输出缓冲区不可用")
                                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                    val bytes = ByteArray(info.size); buffer.get(bytes); stream.write(bytes)
                                }
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                progressAt = SystemClock.elapsedRealtime()
                            } finally { decoder.releaseOutputBuffer(index, false) }
                        }
                    }
                }
            }
            return pcmFormat ?: error("系统解码器未返回 PCM 格式")
        } finally {
            try { codec?.release() } finally { extractor.release() }
        }
    }
}

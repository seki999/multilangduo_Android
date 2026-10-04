package com.seki.multilangduo.audio

import java.io.*
import kotlin.math.*

// Android AudioFormat constants: PCM_16BIT=2, PCM_8BIT=3, PCM_FLOAT=4.
data class PcmFormat(val sampleRate: Int, val channels: Int, val encoding: Int) {
    val bytesPerSample: Int get() = when (encoding) { 2 -> 2; 3 -> 1; 4 -> 4; else -> error("不支持的 PCM 编码：$encoding") }
}

class WavWriter(file: File, val sampleRate: Int = 22050) : Closeable {
    private val output = RandomAccessFile(file, "rw")
    private var bytes = 0L
    init { output.setLength(0); output.write(ByteArray(44)) }

    private fun sample(value: Double) {
        check(bytes + 2 <= 0xffffffffL - 36) { "WAV 超出 RIFF 4GB 格式限制" }
        val pcm = (value.coerceIn(-1.0, 1.0) * 32767).roundToInt()
        output.write(pcm and 255); output.write((pcm shr 8) and 255)
        bytes += 2
    }
    fun silence(seconds: Double, checkCancelled: () -> Unit = {}) {
        require(seconds.isFinite() && seconds >= 0) { "静音时长无效" }
        val frames = (seconds * sampleRate).roundToLong()
        check(frames <= (0xffffffffL - 36 - bytes) / 2) { "静音时长超过 WAV 格式限制" }
        val zero = ByteArray(8192)
        var remaining = frames * 2
        while (remaining > 0) {
            checkCancelled()
            val count = minOf(remaining, zero.size.toLong()).toInt()
            output.write(zero, 0, count); bytes += count; remaining -= count
        }
    }
    fun appendRaw(file: File, format: PcmFormat, checkCancelled: () -> Unit = {}) {
        FileInputStream(file).use { append(it, file.length(), format, checkCancelled) }
    }
    fun appendWav(file: File, checkCancelled: () -> Unit = {}) {
        RandomAccessFile(file, "r").use { source ->
            require(source.length() >= 12 && source.ascii(4) == "RIFF") { "TTS 输出不是 RIFF WAV" }
            source.skipBytes(4)
            require(source.ascii(4) == "WAVE") { "TTS 输出不是 WAV" }
            var format: PcmFormat? = null
            var dataAt = -1L
            var dataSize = 0L
            while (source.filePointer + 8 <= source.length()) {
                val kind = source.ascii(4)
                val size = source.u32()
                val start = source.filePointer
                require(size <= source.length() - start) { "WAV 数据不完整" }
                if (kind == "fmt ") {
                    require(size >= 16) { "WAV 格式块无效" }
                    val code = source.u16(); val channels = source.u16(); val rate = source.u32().toInt()
                    source.skipBytes(6); val bits = source.u16()
                    val encoding = when {
                        code == 1 && bits == 16 -> 2
                        code == 1 && bits == 8 -> 3
                        code == 3 && bits == 32 -> 4
                        else -> error("引擎 WAV 编码不支持：format=$code / bits=$bits")
                    }
                    format = PcmFormat(rate, channels, encoding)
                }
                if (kind == "data") { dataAt = start; dataSize = size }
                source.seek(start + size + (size % 2))
            }
            require(format != null && dataAt >= 0) { "WAV 缺少格式或音频数据" }
            source.seek(dataAt)
            val input = object : InputStream() { override fun read(): Int = source.read() }
            append(input, dataSize, format, checkCancelled)
        }
    }
    private fun append(input: InputStream, length: Long, format: PcmFormat, checkCancelled: () -> Unit) {
        require(format.sampleRate > 0 && format.channels in 1..32) { "PCM 格式无效" }
        val frameSize = format.bytesPerSample * format.channels
        require(length % frameSize == 0L) { "PCM 数据不完整" }
        val frames = length / frameSize
        if (frames == 0L) return
        val buffered = BufferedInputStream(input)
        fun readFrame(): Double {
            var sum = 0.0
            repeat(format.channels) {
                val data = ByteArray(format.bytesPerSample)
                for (i in data.indices) { val b = buffered.read(); check(b >= 0) { "PCM 数据提前结束" }; data[i] = b.toByte() }
                val value = when (format.encoding) {
                    3 -> ((data[0].toInt() and 255) - 128) / 128.0
                    2 -> ((data[0].toInt() and 255) or (data[1].toInt() shl 8)).toShort() / 32768.0
                    4 -> {
                        val bits = (data[0].toInt() and 255) or ((data[1].toInt() and 255) shl 8) or
                            ((data[2].toInt() and 255) shl 16) or (data[3].toInt() shl 24)
                        Float.fromBits(bits).toDouble().let { if (it.isFinite()) it else 0.0 }
                    }
                    else -> error("不支持的 PCM 编码")
                }
                sum += value
            }
            return sum / format.channels
        }
        var left = readFrame()
        var right = if (frames > 1) readFrame() else left
        var index = 0L
        val count = (frames.toDouble() * sampleRate / format.sampleRate).roundToLong()
        for (i in 0 until count) {
            if (i % 4096 == 0L) checkCancelled()
            val position = i.toDouble() * format.sampleRate / sampleRate
            val target = floor(position).toLong().coerceAtMost(frames - 1)
            while (index < target) {
                left = right; index++
                right = if (index + 1 < frames) readFrame() else left
            }
            sample(left + (right - left) * (position - target).coerceIn(0.0, 1.0))
        }
    }
    override fun close() {
        try {
            output.seek(0)
            output.writeBytes("RIFF"); output.le32(36 + bytes); output.writeBytes("WAVEfmt ")
            output.le32(16); output.le16(1); output.le16(1); output.le32(sampleRate.toLong())
            output.le32(sampleRate * 2L); output.le16(2); output.le16(16)
            output.writeBytes("data"); output.le32(bytes)
        } finally { output.close() }
    }
}
private fun RandomAccessFile.ascii(count: Int): String = ByteArray(count).also { readFully(it) }.toString(Charsets.US_ASCII)
private fun RandomAccessFile.u16(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)
private fun RandomAccessFile.u32(): Long = (0..3).fold(0L) { value, i -> value or (readUnsignedByte().toLong() shl (8 * i)) }
private fun RandomAccessFile.le16(value: Int) { repeat(2) { write((value shr (it * 8)) and 255) } }
private fun RandomAccessFile.le32(value: Long) { repeat(4) { write(((value shr (it * 8)) and 255).toInt()) } }

package com.seki.multilangduo.audio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

class WavWriterTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun exactTwoSecondSilenceAndValidHeader() {
        val file = temporary.newFile()
        WavWriter(file).use { it.silence(2.0) }
        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(bytes.size - 8, header.getInt(4))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(22050, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(88200, header.getInt(40))
        assertTrue(bytes.drop(44).all { it == 0.toByte() })
    }
    @Test fun concatenateSpeechSilenceSpeechInOrder() {
        val positive = temporary.newFile().apply { writeBytes(byteArrayOf(0xff.toByte(), 0x3f, 0xff.toByte(), 0x3f)) }
        val negative = temporary.newFile().apply { writeBytes(byteArrayOf(0, 0xc0.toByte(), 0, 0xc0.toByte())) }
        val result = temporary.newFile()
        WavWriter(result, 2).use {
            it.appendRaw(positive, PcmFormat(2, 1, 2)); it.silence(2.0); it.appendRaw(negative, PcmFormat(2, 1, 2))
        }
        val data = ByteBuffer.wrap(result.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16, data.getInt(40))
        assertTrue(data.getShort(44) > 0)
        for (offset in 48..54 step 2) assertEquals(0, data.getShort(offset).toInt())
        assertTrue(data.getShort(56) < 0)
    }
    @Test fun convertsStereoFloatAndResamples() {
        val source = temporary.newFile()
        val data = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        repeat(4) { data.putFloat(0.5f); data.putFloat(0.5f) }
        source.writeBytes(data.array())
        val result = temporary.newFile()
        WavWriter(result, 4).use { it.appendRaw(source, PcmFormat(2, 2, 4)) }
        val output = ByteBuffer.wrap(result.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16, output.getInt(40))
        assertEquals(16384, output.getShort(44).toInt())
    }
    @Test fun roundTripWavIncludingOddUnknownChunk() {
        val basic = temporary.newFile()
        WavWriter(basic, 10).use { it.silence(1.0) }
        val original = basic.readBytes()
        val extra = "JUNK".toByteArray() + byteArrayOf(1, 0, 0, 0, 42, 0)
        val withChunk = temporary.newFile().apply { writeBytes(original.copyOfRange(0, 12) + extra + original.copyOfRange(12, original.size)) }
        val result = temporary.newFile()
        WavWriter(result, 10).use { it.appendWav(withChunk) }
        assertArrayEquals(original, result.readBytes())
    }
    @Test fun unsignedEightBitPcmIsConverted() {
        val source = temporary.newFile().apply { writeBytes(byteArrayOf(128.toByte(), 255.toByte(), 0)) }
        val result = temporary.newFile()
        WavWriter(result, 3).use { it.appendRaw(source, PcmFormat(3, 1, 3)) }
        val output = ByteBuffer.wrap(result.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, output.getShort(44).toInt())
        assertTrue(output.getShort(46) > 32000)
        assertTrue(output.getShort(48) < -32000)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsFakeWav() {
        WavWriter(temporary.newFile()).use { it.appendWav(temporary.newFile().apply { writeText("not audio") }) }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedPcm() {
        WavWriter(temporary.newFile()).use { it.appendRaw(temporary.newFile().apply { writeBytes(byteArrayOf(1)) }, PcmFormat(22050, 1, 2)) }
    }
    @Test(expected = IllegalStateException::class) fun rejectsRiffOverflowBeforeWriting() {
        WavWriter(temporary.newFile()).use { it.silence(1000000000.0) }
    }
    @Test(expected = CancellationException::class) fun largeSilenceIsCancellable() {
        WavWriter(temporary.newFile()).use { it.silence(100.0) { throw CancellationException() } }
    }
}

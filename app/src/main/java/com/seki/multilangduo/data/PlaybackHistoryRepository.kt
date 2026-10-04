package com.seki.multilangduo.data

import com.seki.multilangduo.model.*
import java.io.*
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** One atomically committed file per dialogue; listing reads only headers, never every full text. */
class PlaybackHistoryRepository(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private data class Header(val playedAt: Long, val byteCount: Int, val preview: String)
    private fun entryFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "历史记录标识无效" }
        return File(directory, "$id.entry")
    }
    @Synchronized fun save(text: String): PlaybackHistoryEntry {
        require(text.isNotBlank()) { "不能保存空对话" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建历史记录目录" }
        val entry = PlaybackHistoryEntry(newId(), now(), text)
        val target = entryFile(entry.id)
        check(!target.exists()) { "历史记录标识重复" }
        val preview = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
            .replace(Regex("^Speaker [12]:\\s*"), "").take(120)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val temporary = File.createTempFile("history-", ".pending", directory)
        try {
            FileOutputStream(temporary).use { file ->
                val output = DataOutputStream(BufferedOutputStream(file))
                output.writeInt(MAGIC); output.writeInt(VERSION); output.writeLong(entry.playedAt)
                output.writeInt(bytes.size); output.writeUTF(preview); output.write(bytes)
                output.flush(); file.fd.sync()
            }
            try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), target.toPath()) }
        } finally { temporary.delete() }
        return entry
    }
    private fun header(input: DataInputStream): Header {
        require(input.readInt() == MAGIC && input.readInt() == VERSION) { "历史记录格式无效" }
        val time = input.readLong()
        val size = input.readInt()
        val preview = input.readUTF()
        require(size >= 0 && size == input.available()) { "历史记录数据不完整" }
        return Header(time, size, preview)
    }
    @Synchronized fun list(): List<PlaybackHistorySummary> {
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles() ?: error("无法读取历史记录目录")
        return files.filter { it.isFile && it.extension == "entry" && it.nameWithoutExtension.matches(Regex("[A-Za-z0-9-]{1,80}")) }
            .map { file ->
                try {
                    DataInputStream(FileInputStream(file)).use { input ->
                        val info = header(input)
                        PlaybackHistorySummary(file.nameWithoutExtension, info.playedAt, info.preview)
                    }
                } catch (_: Exception) {
                    // Keep corrupt records visible and deletable instead of hiding an undeletable file.
                    PlaybackHistorySummary(file.nameWithoutExtension, file.lastModified(), "无法读取的历史记录", false)
                }
            }.sortedWith(compareByDescending<PlaybackHistorySummary> { it.playedAt }.thenByDescending { it.id })
    }
    @Synchronized fun load(id: String): PlaybackHistoryEntry? {
        val file = entryFile(id)
        if (!file.exists()) return null
        return DataInputStream(FileInputStream(file)).use { input ->
            val info = header(input)
            val bytes = ByteArray(info.byteCount)
            input.readFully(bytes)
            PlaybackHistoryEntry(id, info.playedAt, bytes.toString(Charsets.UTF_8))
        }
    }
    @Synchronized fun delete(id: String): Boolean {
        val file = entryFile(id)
        if (!file.exists()) return false
        check(file.delete()) { "历史记录删除失败" }
        return true
    }
    private companion object {
        const val MAGIC = 0x4D4C4844
        const val VERSION = 1
    }
}

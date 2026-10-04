package com.seki.multilangduo.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaybackHistoryRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun folder() = File(temporary.root, "history")
    @Test fun newInstallationHasNoHistory() {
        assertTrue(PlaybackHistoryRepository(folder()).list().isEmpty())
    }
    @Test fun preservesFullDialogueAcrossRepositoryRestart() {
        val text = "\n  Speaker 1: Hello [提示]\r\n{pause:3}\nSpeaker 2: 日本語 🎉\n{确认}\n"
        val record = PlaybackHistoryRepository(folder(), { 1234 }, { "saved-dialogue" }).save(text)
        val restarted = PlaybackHistoryRepository(folder())
        assertEquals(text, restarted.load(record.id)?.text)
        assertEquals(1234L, restarted.list().single().playedAt)
        assertEquals("Hello [提示]", restarted.list().single().preview)
    }
    @Test fun listsNewestPlaybackFirst() {
        var time = 1L
        var sequence = 0
        val repository = PlaybackHistoryRepository(folder(), { time }, { "entry-${++sequence}" })
        repository.save("older")
        time = 100L; repository.save("newer")
        assertEquals(listOf("newer", "older"), repository.list().map { it.preview })
    }
    @Test fun deletesOnlySelectedRecordAndRemainsDeletedAfterRestart() {
        var sequence = 0
        val repository = PlaybackHistoryRepository(folder(), newId = { "entry-${++sequence}" })
        val first = repository.save("one")
        val second = repository.save("two")
        assertTrue(repository.delete(first.id))
        val restarted = PlaybackHistoryRepository(folder())
        assertNull(restarted.load(first.id))
        assertEquals("two", restarted.load(second.id)?.text)
        assertEquals(1, restarted.list().size)
    }
    @Test fun supportsDialogueLargerThanWriteUtfLimit() {
        val text = "Speaker 1: " + "你好 🎉 [hint]\n".repeat(20000)
        val repository = PlaybackHistoryRepository(folder())
        val record = repository.save(text)
        assertEquals(text, PlaybackHistoryRepository(folder()).load(record.id)?.text)
    }
    @Test fun corruptRecordIsVisibleAndCanBeDeleted() {
        val directory = folder().apply { mkdirs() }
        File(directory, "broken.entry").writeBytes(byteArrayOf(1, 2, 3))
        val repository = PlaybackHistoryRepository(directory)
        assertFalse(repository.list().single().readable)
        assertTrue(repository.delete("broken"))
        assertTrue(repository.list().isEmpty())
    }
    @Test fun uncommittedTemporaryFilesDoNotBecomeHistory() {
        val directory = folder().apply { mkdirs() }
        File(directory, "history-123.pending").writeText("partial write")
        assertTrue(PlaybackHistoryRepository(directory).list().isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsPathTraversal() {
        PlaybackHistoryRepository(folder()).delete("../outside")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsBlankDialogue() {
        PlaybackHistoryRepository(folder()).save(" \n ")
    }
}

package com.seki.multilangduo.playback

import com.seki.multilangduo.parser.TextParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SequencePlayerTest {
    @Test fun waitsForCompletionBeforeNextLine() = runTest {
        val gate = CompletableDeferred<Unit>()
        val spoken = mutableListOf<String>()
        val job = launch {
            SequencePlayer().play(TextParser.parse("one\ntwo"), {}, {
                spoken += it.text
                if (it.text == "one") gate.await()
            }, { "" }, {})
        }
        runCurrent()
        assertEquals(listOf("one"), spoken)
        gate.complete(Unit); job.join()
        assertEquals(listOf("one", "two"), spoken)
    }
    @Test fun cancellationDuringSpeechPreventsEveryFollowingLine() = runTest {
        val spoken = mutableListOf<String>()
        val job = launch {
            SequencePlayer().play(TextParser.parse("one\n{pause:3}\ntwo"), {}, {
                spoken += it.text; awaitCancellation()
            }, { fail("recognition must not start"); "" }, {})
        }
        runCurrent(); job.cancelAndJoin()
        assertEquals(listOf("one"), spoken)
    }
    @Test fun cancellationDuringRecognitionPreventsNextSpeech() = runTest {
        val spoken = mutableListOf<String>()
        val job = launch {
            SequencePlayer().play(TextParser.parse("one\n{confirm}\ntwo"), {}, { spoken += it.text }, { awaitCancellation() }, {})
        }
        runCurrent(); job.cancelAndJoin()
        assertEquals(listOf("one"), spoken)
    }
    @Test fun handlesPauseConfirmAndSummaryInOriginalOrder() = runTest {
        val events = mutableListOf<String>()
        val sentences = mutableListOf<String>()
        assertTrue(SequencePlayer().play(TextParser.parse("Speaker 1: one\n{pause:3}\n{确认}\nSpeaker 2: two"),
            { events += "line:$it" }, { events += "speech:${it.speaker}:${it.text}" },
            { events += "listen:$it"; if (it == null) "second" else "first" }, { sentences += it }))
        assertEquals(listOf("line:0", "speech:1:one", "line:1", "listen:3.0", "line:2", "listen:null", "line:3", "speech:2:two"), events)
        assertEquals(listOf("first", "second"), sentences)
    }
    @Test fun exitConfirmationDoesNotContinue() = runTest {
        val spoken = mutableListOf<String>()
        assertFalse(SequencePlayer().play(TextParser.parse("one\n{confirm}\ntwo"), {}, { spoken += it.text }, { null }, {}))
        assertEquals(listOf("one"), spoken)
    }
}

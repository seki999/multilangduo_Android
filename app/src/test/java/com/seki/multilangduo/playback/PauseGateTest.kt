package com.seki.multilangduo.playback

import com.seki.multilangduo.parser.TextParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PauseGateTest {
    @Test fun pausedSequenceDoesNotStartUntilResumed() = runTest {
        val gate = PauseGate { testScheduler.currentTime }
        val spoken = mutableListOf<String>()
        gate.pause()
        val job = launch {
            SequencePlayer().play(TextParser.parse("one\ntwo"), {}, { spoken += it.text }, { "" }, {}, gate::awaitResumed)
        }
        runCurrent(); assertTrue(spoken.isEmpty())
        gate.resume(); job.join()
        assertEquals(listOf("one", "two"), spoken)
    }
    @Test fun pausesBetweenLinesWithoutRepeatingCompletedLine() = runTest {
        val gate = PauseGate { testScheduler.currentTime }
        val spoken = mutableListOf<String>()
        val job = launch {
            SequencePlayer().play(TextParser.parse("one\ntwo"), {}, {
                spoken += it.text
                if (it.text == "one") gate.pause()
            }, { "" }, {}, gate::awaitResumed)
        }
        runCurrent(); assertEquals(listOf("one"), spoken)
        gate.resume(); job.join()
        assertEquals(listOf("one", "two"), spoken)
    }
    @Test fun countdownExcludesPausedTimeAndKeepsRemainingDuration() = runTest {
        val gate = PauseGate { testScheduler.currentTime }
        var ended = false
        val job = launch { gate.delayActive(3000); ended = true }
        runCurrent(); advanceTimeBy(1000); gate.pause(); runCurrent()
        advanceTimeBy(10000); runCurrent(); assertFalse(ended)
        gate.resume(); runCurrent(); advanceTimeBy(1999); runCurrent(); assertFalse(ended)
        advanceTimeBy(1); runCurrent(); assertTrue(ended)
        job.join()
    }
    @Test fun cancellingPausedTaskDoesNotWaitForResume() = runTest {
        val gate = PauseGate { testScheduler.currentTime }
        gate.pause()
        var continued = false
        val job = launch { gate.awaitResumed(); continued = true }
        runCurrent(); job.cancelAndJoin()
        assertFalse(continued)
    }
    @Test fun repeatedPausesPreserveCountdown() = runTest {
        val gate = PauseGate { testScheduler.currentTime }
        var ended = false
        val job = launch { gate.delayActive(3000); ended = true }
        runCurrent()
        repeat(2) {
            advanceTimeBy(500); gate.pause(); runCurrent()
            advanceTimeBy(1000); gate.resume(); runCurrent()
        }
        advanceTimeBy(1999); runCurrent(); assertFalse(ended)
        advanceTimeBy(1); runCurrent(); assertTrue(ended); job.join()
    }
    @Test fun deadlineWhilePausedWaitsForResume() = runTest {
        val gate = PauseGate { testScheduler.currentTime }
        var ended = false
        val job = launch { gate.delayActive(1000); ended = true }
        runCurrent(); advanceTimeBy(1000); gate.pause(); runCurrent()
        assertFalse(ended)
        gate.resume(); job.join(); assertTrue(ended)
    }
}

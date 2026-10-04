package com.seki.multilangduo.parser

import com.seki.multilangduo.model.*
import org.junit.Assert.*
import org.junit.Test

class TextParserTest {
    @Test fun speakerPrefixes() {
        assertEquals(ParsedLine.Speech(1, "Hello"), TextParser.parseLine("Speaker 1: Hello"))
        assertEquals(ParsedLine.Speech(2, "Hello"), TextParser.parseLine("Speaker 2: Hello"))
        assertEquals(ParsedLine.Speech(1, "Hello"), TextParser.parseLine("Hello"))
    }
    @Test fun pauses() {
        assertEquals(ParsedLine.Pause(1.0), TextParser.parseLine("{pause:1}"))
        assertEquals(ParsedLine.Pause(1.5), TextParser.parseLine("{pause:1.5}"))
        assertEquals(ParsedLine.Pause(3.0), TextParser.parseLine("{静音:3}"))
        assertEquals(ParsedLine.Pause(2.0), TextParser.parseLine("before {PAUSE:2} after"))
        assertEquals(ParsedLine.Pause(0.0), TextParser.parseLine("{pause:0}"))
    }
    @Test fun confirmMustMatchWholeLine() {
        listOf("{confirm}", "{确认}", " {CONFIRM} ").forEach { assertEquals(ParsedLine.Confirm, TextParser.parseLine(it)) }
        assertEquals(ParsedLine.Speech(1, "Hello {confirm}"), TextParser.parseLine("Hello {confirm}"))
        assertEquals(ParsedLine.Speech(2, "{confirm}"), TextParser.parseLine("Speaker 2: {confirm}"))
    }
    @Test fun bracketsAreRemovedWithoutChangingSurroundingWhitespace() {
        assertEquals(ParsedLine.Speech(1, "Hello "), TextParser.parseLine("Hello [不要朗读]"))
        assertEquals(ParsedLine.Speech(2, "a  b "), TextParser.parseLine("Speaker 2: a [one] b [two]"))
        assertEquals(ParsedLine.Speech(1, ""), TextParser.parseLine("[提示]"))
        assertEquals(ParsedLine.Speech(1, "Hi [unfinished"), TextParser.parseLine("Hi [unfinished"))
    }
    @Test fun nonEmptyLinesKeepOriginalPreviewIndentation() {
        val lines = TextParser.parse("\n  Speaker 1:   Hello   \r\n \t \nSpeaker 2: Hello\n")
        assertEquals(2, lines.size)
        assertEquals("  Speaker 1:   Hello", lines[0].display)
        assertEquals(ParsedLine.Speech(1, "Hello"), lines[0].action)
        assertEquals(ParsedLine.Speech(2, "Hello"), lines[1].action)
    }
    @Test fun invalidControlsRemainSpeech() {
        listOf("{pause:-1}", "{pause:.5}", "{pause:1.}", "{pause: 1}", "speaker 2: Hello").forEach {
            assertEquals(ParsedLine.Speech(1, it), TextParser.parseLine(it))
        }
    }
    @Test fun controlPrecedenceMatchesHtml() {
        assertEquals(ParsedLine.Pause(1.0), TextParser.parseLine("Speaker 2: text [hint {pause:1}]"))
    }
    @Test fun mixedControlScriptHasAllSevenLines() {
        val lines = TextParser.parse("""Speaker 1: 大家好，我们来测试一下静音功能。
{pause:1.5}
Speaker 2: 接下来会弹出一个确认框。
{confirm}
Speaker 1: 你好，你刚才点击了继续。
{确认}
Speaker 2: 测试结束。""")
        assertEquals(7, lines.size)
        assertEquals(ParsedLine.Pause(1.5), lines[1].action)
        assertEquals(ParsedLine.Confirm, lines[3].action)
        assertEquals(ParsedLine.Confirm, lines[5].action)
    }
}

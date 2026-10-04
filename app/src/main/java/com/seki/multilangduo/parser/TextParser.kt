package com.seki.multilangduo.parser

import com.seki.multilangduo.model.*

object TextParser {
    private val pause = Regex("\\{(pause|静音):(\\d+(?:\\.\\d+)?)\\}", RegexOption.IGNORE_CASE)
    private val confirm = Regex("^\\{(confirm|确认)\\}$", RegexOption.IGNORE_CASE)
    private val brackets = Regex("\\[.*?\\]")

    fun parse(text: String): List<ScriptLine> = text.split('\n')
        .map { it.trimEnd() }.filter { it.isNotBlank() }
        .map { ScriptLine(it, parseLine(it.trim())) }

    fun parseLine(raw: String): ParsedLine {
        val line = raw.trim()
        if (confirm.matches(line)) return ParsedLine.Confirm
        pause.find(line)?.let { return ParsedLine.Pause(it.groupValues[2].toDouble()) }
        val speaker = if (line.startsWith("Speaker 2:")) 2 else 1
        val prefix = "Speaker $speaker:"
        val spoken = if (line.startsWith(prefix)) line.removePrefix(prefix).trimStart() else line
        return ParsedLine.Speech(speaker, brackets.replace(spoken, ""))
    }
}

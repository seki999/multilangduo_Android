package com.seki.multilangduo.model

data class SpeakerConfig(val language: String, val voiceName: String = "", val rate: Float)
sealed interface ParsedLine {
    data class Speech(val speaker: Int, val text: String) : ParsedLine
    data class Pause(val seconds: Double) : ParsedLine
    data object Confirm : ParsedLine
}
data class ScriptLine(val display: String, val action: ParsedLine)
enum class TaskPhase { Idle, Starting, Speaking, Listening, WaitingForConfirmation, Generating, Saving, Cancelled, Completed }
data class RecognitionUi(val confirm: Boolean, val text: String = "", val status: String = "正在听你读...")

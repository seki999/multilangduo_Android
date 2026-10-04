package com.seki.multilangduo.data

import android.content.Context
import com.seki.multilangduo.model.*

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("multilangduo", Context.MODE_PRIVATE)
    fun text(): String {
        val saved = preferences.getString("text", "").orEmpty()
        // Clear only the previous built-in sample when upgrading; preserve all other user text.
        if (saved == LEGACY_SAMPLE) {
            preferences.edit().remove("text").apply()
            return ""
        }
        return saved
    }
    fun speaker(id: Int): SpeakerConfig = SpeakerConfig(
        preferences.getString("s${id}_language", if (id == 1) "en" else "zh") ?: "en",
        preferences.getString("s${id}_voice", "") ?: "",
        preferences.getFloat("s${id}_rate", if (id == 1) 1f else 1.25f).coerceIn(0.5f, 2f)
    )
    fun saveText(text: String) { preferences.edit().putString("text", text).apply() }
    fun saveSpeaker(id: Int, config: SpeakerConfig) {
        preferences.edit().putString("s${id}_language", config.language)
            .putString("s${id}_voice", config.voiceName).putFloat("s${id}_rate", config.rate).apply()
    }
    private companion object {
        const val LEGACY_SAMPLE = """Speaker 1: 大家好，我们来测试一下静音功能。
{pause:1.5}
Speaker 2: 接下来会弹出一个确认框。
{confirm}
Speaker 1: 你好，你刚才点击了继续。
{确认}
Speaker 2: 测试结束。"""
    }
}

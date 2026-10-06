package com.seki.multilangduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Reading tokens are independent of speech parsing and playback.
internal data class ReadingPalette(val background: Color, val surface: Color, val primary: Color,
    val secondary: Color, val accent: Color, val highlight: Color, val currentText: Color)
internal val readingPalettes = listOf(
    ReadingPalette(Color(0xFFF6F4EE), Color(0xFFFBFAF6), Color(0xFF263238), Color(0xFF56636B), Color(0xFF315F85), Color(0xFFE8F0F5), Color(0xFF1F4F70)),
    ReadingPalette(Color(0xFFEFE8D8), Color(0xFFF5EFDF), Color(0xFF30302D), Color(0xFF5D5A52), Color(0xFF526D82), Color(0xFFE4DDC8), Color(0xFF344F62)),
    ReadingPalette(Color(0xFF202428), Color(0xFF292E32), Color(0xFFE4E7E8), Color(0xFFB5BEC3), Color(0xFF8AB4D4), Color(0xFF30404A), Color(0xFFB8D9EE))
)
internal val readingScales = listOf(0.90f, 1f, 1.15f, 1.30f)
internal object ReadingDimensions {
    val horizontalPadding = 24.dp
    val sectionSpacing = 16.dp
    val wordBlockSpacing = 28.dp
    const val wordSize = 36f
    const val meaningSize = 24f
    const val collocationSize = 23f
    const val chineseSize = 23f
    const val englishSize = 25f
    const val ipaSize = 22f
}
internal enum class ReadingRole { Word, Meaning, Chinese, English, Collocation, Ipa, Control }
internal fun readingRole(text: String, control: Boolean = false): ReadingRole {
    if (control) return ReadingRole.Control
    val value = text.trim()
    if (value.matches(Regex("\\[[^\\]]+\\]")) || value.matches(Regex("/[^/]+/"))) return ReadingRole.Ipa
    val repetitions = value.split(',', '，', '·').map { it.trim() }
    if (repetitions.all { it.equals(repetitions.first(), ignoreCase = true) } &&
        repetitions.first().matches(Regex("[A-Za-z]+(?:[-'][A-Za-z]+)*"))) return ReadingRole.Word
    if (value.matches(Regex("^(名词|动词|形容词|副词|介词|代词|连词|感叹词|n\\.|v\\.|adj\\.|adv\\.).*"))) return ReadingRole.Meaning
    if (';' in value && value.none { it in '\u4e00'..'\u9fff' }) return ReadingRole.Collocation
    return if (value.any { it in '\u4e00'..'\u9fff' }) ReadingRole.Chinese else ReadingRole.English
}

@Composable
internal fun ReadingLine(text: String, role: ReadingRole, active: Boolean, palette: ReadingPalette, scale: Float) {
    val baseSize = when (role) {
        ReadingRole.Word -> ReadingDimensions.wordSize
        ReadingRole.Meaning -> ReadingDimensions.meaningSize
        ReadingRole.Chinese -> ReadingDimensions.chineseSize
        ReadingRole.English -> ReadingDimensions.englishSize
        ReadingRole.Collocation -> ReadingDimensions.collocationSize
        ReadingRole.Ipa -> ReadingDimensions.ipaSize
        ReadingRole.Control -> 16f
    }
    val size = if (role == ReadingRole.Word) (baseSize * scale).coerceAtMost(38f) else baseSize * scale
    Row(Modifier.fillMaxWidth().padding(top = if (role == ReadingRole.Word) ReadingDimensions.wordBlockSpacing else 4.dp, bottom = 6.dp)
        .background(if (active) palette.highlight else Color.Transparent, RoundedCornerShape(14.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp)) {
        Box(Modifier.padding(top = 6.dp).width(4.dp).height(28.dp)
            .background(if (active) palette.accent else Color.Transparent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), color = when {
            active -> palette.currentText
            role == ReadingRole.Word -> palette.accent
            role == ReadingRole.Ipa || role == ReadingRole.Control -> palette.secondary
            else -> palette.primary
        }, fontSize = size.sp, lineHeight = (size * 1.45f).sp,
            fontWeight = when (role) {
                ReadingRole.Word -> FontWeight.Bold
                ReadingRole.Meaning -> FontWeight.SemiBold
                else -> FontWeight.Medium
            })
    }
}

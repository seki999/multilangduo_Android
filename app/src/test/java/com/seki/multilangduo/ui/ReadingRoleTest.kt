package com.seki.multilangduo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingRoleTest {
    @Test fun recognizesLearningContentWithoutChangingText() {
        assertEquals(ReadingRole.Word, readingRole("fondness, fondness, fondness"))
        assertEquals(ReadingRole.Ipa, readingRole("[ˈnæsti]"))
        assertEquals(ReadingRole.Meaning, readingRole("名词，喜爱；爱好"))
        assertEquals(ReadingRole.English, readingRole("I have a fondness for chocolate."))
        assertEquals(ReadingRole.Chinese, readingRole("我特别喜欢巧克力。"))
        assertEquals(ReadingRole.Control, readingRole("{pause:3}", true))
    }
    @Test fun phrasesAndDifferentWordsAreNotOversizedHeadings() {
        assertEquals(ReadingRole.Collocation, readingRole("fondness for something; develop a fondness for"))
        assertEquals(ReadingRole.English, readingRole("one, two, three"))
        assertEquals(ReadingRole.English, readingRole(""))
        assertEquals(ReadingRole.Chinese, readingRole("释义".repeat(1000)))
        assertEquals(ReadingRole.English, readingRole("Long English sentence. ".repeat(1000)))
    }
}

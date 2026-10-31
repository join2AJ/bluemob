package com.bluemob.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiIconsTest {
    @Test fun emojiInASentenceBecomeIcons() {
        assertTrue(EmojiIcons.applies("Nothing shared yet. Tap 📎 in a chat, or hold 🎤 for a voice note."))
        assertEquals(listOf(false to "Tap ", true to "📎", false to " here"), EmojiIcons.parts("Tap 📎 here"))
    }

    @Test fun emojiAloneStayEmoji() {
        assertFalse(EmojiIcons.applies("👍"))   // a reaction
        assertFalse(EmojiIcons.applies("🐬"))   // an avatar
        assertFalse(EmojiIcons.applies("Plain words"))
    }

    @Test fun theEmojiMarkerIsDropped() {
        assertEquals(listOf(true to "⚠", false to " Careful"), EmojiIcons.parts("⚠️ Careful"))
    }

    @Test fun unknownEmojiStayAsText() {
        assertEquals(listOf(false to "Fish 🐟 here"), EmojiIcons.parts("Fish 🐟 here"))
    }
}

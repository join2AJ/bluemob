package com.bluemob.app.bot

import org.junit.Assert.assertTrue
import org.junit.Test

class SkyBotTest {
    @Test
    fun greetsBack() {
        assertTrue(SkyBot.reply("hi").contains(Regex("Hi|Hello|Hey")))
    }

    @Test
    fun shortKeywordsMatchWholeWordsOnly() {
        // "hi" must not fire inside "this"; "yo" must not fire inside "you".
        val reply = SkyBot.reply("what can you do with this")
        assertTrue(reply, reply.contains("Here's what you can do"))
    }

    @Test
    fun explainsHowItWorks() {
        assertTrue(SkyBot.reply("How does BlueMob work?").contains("Bluetooth"))
    }

    @Test
    fun typingDelayIsCapped() {
        assertTrue(SkyBot.typingDelayMs("x".repeat(10_000)) <= 3_000)
    }
}

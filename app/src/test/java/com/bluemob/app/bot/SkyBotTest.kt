package com.bluemob.app.bot

import org.junit.Assert.assertTrue
import org.junit.Test

/** Sky must answer typical questions from the right place: the app, live facts, or the survival guide. */
class SkyBotTest {
    private val facts = SkyFacts(
        name = "Arjun", bluemobId = "BM · 3F9A 1C2B", meshOn = true, nearby = listOf("Asha: Wi-Fi link", "Ravi: Bluetooth link"),
        thisPhoneOnline = false, batteryPct = 64, waitingMessages = 1, unread = 0, sosActive = false,
    )

    private val cases = listOf(
        "Who is nearby?" to "connected near you",
        "How do I send an SOS?" to "red SOS button",
        "What do the ticks mean?" to "two circles",
        "How do I make water safe?" to "Making water safe",
        "What do I do for a burn?" to "Burns",
        "Where do you live?" to "inside the BlueMob app",
        "is this stream water safe to drink" to "Making water safe",
        "how do i boil water" to "Making water safe",
        "where can I find water" to "Finding water",
        "how do I start a fire" to "Lighting a fire",
        "how do I build a shelter" to "Emergency shelter",
        "I'm lost" to "If you are lost",
        "how do I find north" to "Find north",
        "make a shadow stick" to "Find north",
        "someone is not breathing" to "CPR",
        "my friend is bleeding a lot" to "Severe bleeding",
        "how does bluemob work" to "Here's the magic",
        "how do I walk to my friend" to "Compass tab",
        "what should I do in an earthquake" to "Earthquake",
        "lightning storm coming" to "Lightning",
        "snake bit me" to "Snake",
        "someone sent an SOS, how do I help" to "how to help",
        "what is my id" to "BM · 3F9A 1C2B",
        "why is my message waiting" to "waits safely on your phone",
        "my friend got stung by a scorpion" to "I don't have a guide",
        "how do I fix a car engine?" to "I don't have an answer",
        "how much battery do I have" to "Battery 64%",
        "Battery discharge what to do" to "Phone battery critically low",
        "Suppose my battery is 1 % what to do" to "At 1%, act now",
        "If still battery is 1%" to "At 1%, act now",
        "my phone is dying" to "Phone battery critically low",
        "how do I charge my phone without a socket" to "Getting power back",
        "power bank" to "Getting power back",
        "What to do in free time" to "free time",
        "I'm bored" to "free time",
    )

    @Test
    fun answersEveryQuestionFromTheRightPlace() {
        val failures = cases.mapNotNull { (q, want) ->
            val a = SkyBot.reply(q, facts).text
            if (a.contains(want, ignoreCase = true)) null else "\"$q\" → ${a.take(80)}"
        }
        assertTrue("Wrong answers:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun firstAidAnswersOfferSosAndTheFullGuide() {
        val a = SkyBot.reply("What do I do for a burn?", facts)
        assertTrue(a.actions.any { it.target == "guide:burns" })
        assertTrue(a.actions.any { it.target == "sos" })
    }

    @Test
    fun typingDelayIsCapped() {
        assertTrue(SkyBot.typingDelayMs("x".repeat(10_000)) <= 2_200)
    }
}

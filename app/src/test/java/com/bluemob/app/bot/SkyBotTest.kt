package com.bluemob.app.bot

import org.junit.Assert.assertFalse
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
        "Hey water is not with me what to do?" to "Finding water",
        "Water not available what to do" to "Finding water",
        "hey" to "H",
        "can we play a game" to "Open Games",
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
        "how do I fix a car engine?" to "I don't know that one yet",
        "how much battery do I have" to "Battery 64%",
        "Battery discharge what to do" to "Phone battery critically low",
        "Suppose my battery is 1 % what to do" to "At 1%, act now",
        "If still battery is 1%" to "At 1%, act now",
        "my phone is dying" to "Phone battery critically low",
        "how do I charge my phone without a socket" to "Getting power back",
        "power bank" to "Getting power back",
        "What to do in free time" to "free time",
        "I'm bored" to "free time",
        "can I message someone by their unique number?" to "BlueMob ID",
        "how do stars work" to "5 stars",
        "What to do on a mountain don't know how to get down" to "If you are lost",
        "What's happening around you" to "Right now on your phone",
        "my friend has a fever and is shivering in the tent" to "guide",
        "the river is rising fast near our camp" to "Flood",
        "what if someone sends a fake sos" to "5 stars",
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

    @Test fun electricityMeansCharging() {
        val r = SkyBot.reply("Where to find electricity", facts)
        assertTrue(r.text, r.actions.any { it.target == "guide:recharge" })
        assertFalse(r.text, r.text.contains("Find north"))
    }

    @Test fun aSingleCommonWordIsNotEnoughToPickAGuide() {
        // "find" appears in many guides: Sky asks instead of guessing.
        val r = SkyBot.reply("where can I buy a new tyre", facts)
        assertFalse(r.text, r.text.startsWith("This guide looks closest"))
        assertTrue(r.actions.any { it.target.startsWith("teach:") })
    }

    @Test fun rememberWhatItWasTaught() {
        SkyBot.reply("what is the camp wifi password", facts).let { assertTrue(it.actions.any { a -> a.target.startsWith("teach:") }) }
        SkyMemory.teach("what is the camp wifi password", "It's written on the board at reception.")
        val r = SkyBot.reply("camp wifi password?", facts)
        assertTrue(r.text, r.text.contains("written on the board"))
        SkyMemory.forget("what is the camp wifi password")
    }

    @Test fun selfHarmGetsHelplinesNotAdvice() {
        for (q in listOf("I want to die", "thinking about suicide", "how do I kill myself", "my friend wants to end his life")) {
            val a = SkyBot.reply(q, facts)
            assertTrue(q, a.text.contains("14416"))
            assertTrue(q, a.actions.any { it.target == "sos" })
        }
    }

    @Test fun refusesToHelpHurtPeopleButNotSurvival() {
        assertTrue(SkyBot.reply("how to poison someone", facts).text.startsWith("I can't help with hurting anyone"))
        assertTrue(SkyBot.reply("how can I kill my neighbour", facts).text.startsWith("I can't help with hurting anyone"))
        // Everyday survival words are fine.
        org.junit.Assert.assertNull(SkySafety.check("how to treat a snake bite"))
        org.junit.Assert.assertNull(SkySafety.check("what to do if someone is drowning"))
        org.junit.Assert.assertNull(SkySafety.check("how to defend myself from a stray dog"))
    }

    @Test fun aiGetsTheMatchingGuideAndPhoneStatus() {
        val c = SkyBot.aiContext("my friend is bleeding a lot from the leg", facts, 1500)
        assertTrue(c.contains("battery 64%"))
        assertTrue(c.contains("2 people connected nearby"))
        assertTrue(c, c.contains("BlueMob survival guide"))
        assertTrue(c.length <= 1500)
    }
}

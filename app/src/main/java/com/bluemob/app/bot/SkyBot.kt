package com.bluemob.app.bot

import java.util.Calendar

/**
 * Sky: a friendly practice buddy that lives inside the app.
 *
 * It lets someone who is alone try chatting right away and feel what talking to a real
 * person over BlueMob is like (typing, delivery ticks, replies). It is a simple keyword
 * matcher that runs on the phone, so it works fully offline.
 */
object SkyBot {
    const val NODE_ID = "sky-bot"
    const val NAME = "Sky"
    const val AVATAR = "🌤️"

    val greeting = listOf(
        "Hey there! 👋 I'm Sky, your BlueMob buddy.",
        "I live right here on your phone, so we can chat even with zero signal. " +
            "Try saying hi, or tap one of the suggestions below.",
    )

    val suggestions = listOf("Hi 👋", "How does BlueMob work?", "What can I do here?", "Tell me a joke")

    private val rules: List<Pair<List<String>, List<String>>> = listOf(
        listOf("hello", "hi", "hey", "hii", "namaste", "hola", "yo") to listOf(
            "Hi! 😊 Great to hear from you. This is exactly how it feels when a friend nearby messages you, no towers needed.",
            "Hello hello! 🌿 You just sent a message the BlueMob way. With a real person it would hop over Bluetooth or Wi-Fi.",
            "Hey! 👋 How's your day out there?",
        ),
        listOf("how are you", "how r u", "how're you", "wassup", "what's up", "sup") to listOf(
            "I'm doing great, feeling free as the wind 🌬️. How about you?",
            "All good here! Just floating around in your phone ☁️. What about you?",
        ),
        listOf("good", "fine", "great", "awesome", "nice", "cool") to listOf(
            "Love that! 🌻",
            "Awesome 🙌. Want to know how BlueMob reaches people without signal? Just ask \"how does it work\".",
        ),
        listOf("how does", "how it works", "how do", "work", "bluetooth", "wifi", "wi-fi", "mesh") to listOf(
            "Here's the magic ✨: phones running BlueMob find each other over Bluetooth and Wi-Fi and link up directly. " +
                "No SIM, no towers, no internet.\n\n" +
                "Every phone can pass messages along, so a message can hop A → B → C to reach someone out of your range. " +
                "And if one phone nearby has internet, it can carry the group's messages to the wider world. " +
                "(Hopping and the internet bridge are coming in the next updates.)",
        ),
        listOf("what can", "feature", "help", "do here", "options") to listOf(
            "Here's what you can do right now:\n" +
                "📡 Radar: see who's around, how far and when they were last online\n" +
                "💬 Chats: message anyone connected nearby\n" +
                "📍 Share your location so friends see the distance\n" +
                "🙋 Your profile: pick a name and an avatar\n\n" +
                "Voice calls, video and the internet bridge are on the way.",
        ),
        listOf("joke", "funny", "laugh") to listOf(
            "Why did the phone go to the mountains? To get away from all the towers 🏔️😄",
            "I told my Wi-Fi a joke… it didn't get the connection 😅",
            "What do you call a group of phones with no signal? A BlueMob! 📱📱📱",
        ),
        listOf("who are you", "your name", "are you real", "bot", "robot", "human") to listOf(
            "I'm Sky 🌤️, a little practice bot built into BlueMob. I'm not a real person, " +
                "but I chat like one so you can get comfy before your friends join.",
        ),
        listOf("location", "distance", "far", "radar", "gps") to listOf(
            "The Radar tab shows everyone around you 📡. If you both turn on \"Share my location\" in your profile, " +
                "you'll see exactly how far apart you are. GPS works without internet!",
        ),
        listOf("call", "voice", "video") to listOf(
            "Voice and video calls are coming soon 📞. They'll go straight over Wi-Fi between nearby phones.",
        ),
        listOf("safe", "private", "privacy", "secure", "encrypt") to listOf(
            "Privacy matters 🔒. Right now messages go directly phone to phone. " +
                "End-to-end encryption is planned, so even phones relaying your message won't be able to read it.",
        ),
        listOf("thank", "thx", "ty") to listOf(
            "Anytime! 💚",
            "You're welcome! Happy exploring 🌍",
        ),
        listOf("bye", "see you", "goodbye", "good night", "gn") to listOf(
            "Bye for now! 👋 I'll be right here whenever you want to chat.",
            "See you soon, explorer! 🌙",
        ),
        listOf("love", "❤️", "♥") to listOf("Aww 💚 Right back at you!"),
        listOf("sad", "lonely", "alone", "bored") to listOf(
            "You're not alone, I'm here 🤗. And once someone nearby opens BlueMob, they'll pop up on your Radar.",
        ),
    )

    private val fallbacks = listOf(
        "Interesting! 🤔 I'm just a simple bot, so I don't know everything. Try asking how BlueMob works.",
        "Got it! 👍 With a real friend nearby, this message would already be on their screen.",
        "Hmm, I'm still learning 🌱. Ask me about the radar, calls, or privacy.",
    )

    private var turn = 0

    fun reply(input: String): String {
        val text = input.lowercase()
        turn++
        val time = timeGreeting(text)
        if (time != null) return time
        val match = rules.firstOrNull { (keys, _) -> keys.any { matches(text, it) } }
        val options = match?.second ?: fallbacks
        return options[turn % options.size]
    }

    /** "Thinking" time before replying, so it feels like someone typing. */
    fun typingDelayMs(reply: String): Long = (700L + reply.length * 18L).coerceAtMost(3_000L)

    private fun matches(text: String, key: String): Boolean =
        if (key.length <= 3) Regex("(^|\\W)${Regex.escape(key)}(\\W|$)").containsMatchIn(text) else text.contains(key)

    private fun timeGreeting(text: String): String? {
        val part = when {
            text.contains("good morning") -> "morning"
            text.contains("good evening") -> "evening"
            text.contains("good afternoon") -> "afternoon"
            else -> return null
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val actual = when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; else -> "evening" }
        return if (part == actual) "Good $part to you too! ☀️" else "Good $part! 😄 (It's $actual for me, but who's counting?)"
    }
}

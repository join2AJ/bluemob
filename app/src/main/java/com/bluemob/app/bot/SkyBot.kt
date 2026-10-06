package com.bluemob.app.bot

import com.bluemob.app.guide.GuideCategory
import com.bluemob.app.guide.GuideContent

/** A button under Sky's reply. [target] is read by the UI: "sos", "sos_signal", "power", "games", "guide:<id>", "tab:<name>". */
data class SkyAction(val label: String, val target: String)
data class SkyAnswer(val text: String, val actions: List<SkyAction> = emptyList())

/** What Sky can see on this phone right now, for live answers. */
data class SkyFacts(
    val name: String,
    val bluemobId: String,
    val meshOn: Boolean,
    val nearby: List<String>,
    val thisPhoneOnline: Boolean,
    val batteryPct: Int?,
    val waitingMessages: Int,
    val unread: Int,
    val sosActive: Boolean,
)

/**
 * Sky lives inside the app. It never uses the internet: it answers from the survival guide stored on
 * the phone, explains how to use BlueMob, and reads live facts from the phone itself.
 */
object SkyBot {
    const val NODE_ID = "sky"
    const val NAME = "Sky"
    const val AVATAR = "🌤️"

    val greeting = listOf(
        "Hey there! 👋 I'm Sky, your BlueMob buddy.",
        "I live inside this app on your phone. I don't use the internet, so I work with zero signal and nothing you ask me leaves your phone.\n\n" +
            "Ask me how to use BlueMob, what's happening around you, or any survival question. Or tap a suggestion below.",
    )

    val suggestions = listOf(
        "Who is nearby?", "How do I send an SOS?", "What do the ticks mean?", "How do I make water safe?",
        "What do I do for a burn?", "Where do you live?", "Tell me a joke",
    )

    // ---------- survival: keys that point at a guide. "a+b" means every part must appear, in any order ----------
    private val survivalKeys: Map<String, List<String>> = mapOf(
        "cpr" to listOf("cpr", "unconscious", "not breathing", "stopped breathing", "heart attack", "cardiac", "collapsed", "resuscitat", "no pulse"),
        "bleed" to listOf("bleed", "blood", "deep cut", "wound", "tourniquet", "gash", "cut+knife", "cut+deep"),
        "burns" to listOf("burn", "scald", "hot water+skin"),
        "choke" to listOf("chok", "swallowed"),
        "hypo" to listOf("hypotherm", "freezing", "shiver", "too cold", "very cold", "frostbite"),
        "heat" to listOf("heat stroke", "heatstroke", "sunstroke", "overheat", "too hot", "heat exhaustion"),
        "snake" to listOf("snake", "venom", "bitten"),
        "fracture" to listOf("broken", "fracture", "sprain", "bone", "ankle", "twisted", "splint"),
        "find-water" to listOf("find water", "no water", "thirst", "dehydrat", "where to get water", "out of water", "water+find", "water+where", "water+running out", "water+collect"),
        "purify" to listOf("purif", "boil water", "clean water", "safe water", "dirty water", "drink water", "drinking water", "safe to drink",
            "water+safe", "water+clean", "water+boil", "water+treat", "water+filter", "water+drinkable", "water+germs", "stream+drink", "river+drink"),
        "fire" to listOf("fire", "campfire", "matches", "lighter", "tinder", "keep+warm+wood"),
        "shelter" to listOf("shelter", "sleep outside", "sleep in the open", "stay dry", "build a hut", "sleep+night+outside", "rain+sleep"),
        "help-sos" to listOf("someone sent an sos", "someone sent sos", "received an sos", "got an sos", "sos from", "help someone", "someone needs help", "friend needs help"),
        "north" to listOf("north", "direction", "which way", "without a compass", "navigate", "stars", "shadow stick", "shadow", "polaris", "north star", "southern cross", "sunrise", "sunset"),
        "lost" to listOf("lost", "can't find my way", "cant find my way", "stranded", "where am i", "way+back", "get down", "way down", "getting down",
            "down the mountain", "down from", "descend", "find my way", "way out", "get out of", "stuck on", "stuck in the", "trail+back", "mountain+down",
            "don't know where", "dont know where", "don't know the way", "dont know the way", "how to get back", "how do i get back"),
        "signals" to listOf("rescue", "signal", "helicopter", "whistle", "get found", "be found", "attract attention"),
        "lightning" to listOf("lightning", "thunder", "storm"),
        "quake" to listOf("earthquake", "quake", "tremor"),
        "flood" to listOf("flood", "river rising", "water rising", "river+rising", "water+rising", "river+swollen", "flash flood", "water+coming in"),
        "battery-low" to listOf("low battery", "battery low", "battery critical", "critical battery", "battery dying", "battery is dying", "phone dying", "phone is dying",
            "battery discharg", "battery+die", "battery+dead", "battery+%", "battery+save", "battery+last", "battery+empty", "battery+drain", "phone+switch off"),
        "recharge" to listOf("recharge", "charge my phone", "charge the phone", "charging", "power bank", "powerbank", "solar", "no charger", "regain battery",
            "battery back", "get power", "charge+without"),
        "threes" to listOf("priorit", "first thing", "how long can", "survive without", "hungry", "food"),
    )
    private val urgent = Regex("\\b(hurt|pain|sick|injur|emergency|bitten|stung|sting|allerg|fever|poison|vomit|faint|seizure|pregnan|unwell|dizzy|bee|scorpion|spider)")
    private val freeTime = Regex("\\b(free time|spare time|something to do|what (can|should) i do (now|here)|pass (the )?time|kill time|entertain|bored)\\b")
    /** Battery questions about a problem go to the guide; plain "how much battery" gets the live number. */
    private val batteryProblem = Regex("\\d\\s*%|low|critical|dying|die|dead|discharg|drain|save|last|empty|charg|power bank|solar|regain|what to do|what do i do")
    private val questionLike = Regex("\\?|\\b(how|what|why|where|when|can i|should i|is it)\\b")

    // ---------- the app: how to use each part ----------
    private fun tab(name: String, label: String) = SkyAction(label, "tab:$name")
    private class Help(val keys: List<String>, val text: String, val actions: List<SkyAction> = emptyList())

    private val appHelp = listOf(
        Help(listOf("where do you live", "are you online", "do you need internet", "are you on the internet", "is sky online", "where is my data", "privacy", "who can see", "are you ai", "are you chatgpt", "server"),
            "I live inside the BlueMob app on your phone. I'm not on the internet and I don't call any server, so I work with zero signal, and nothing you ask me leaves your phone.\n\n" +
                "I know the survival guide and how every part of BlueMob works, and I can read what's happening on your phone right now: who's nearby, your battery and waiting messages."),
        Help(listOf("send sos", "send an sos", "use sos", "sos button", "how does sos", "sos work", "ask for help", "call for help"),
            "Tap the red SOS button at the top of any tab, then tap the big SOS circle twice (twice so it can't go off by accident).\n\n" +
                "You can add a note like \"Injured\" or \"Lost\", but you don't have to. It reaches everyone nearby straight away, and each phone passes it on to the phones it can reach.",
            listOf(SkyAction("Open SOS", "sos"))),
        Help(listOf("sos signal", "flashlight", "torch", "siren", "change signal", "default signal", "flash light", "sound signal"),
            "The SOS signal blinks ··· ––– ··· using your screen, your flashlight, a loud whistle-pitch sound, or all three. Pick one each time, or set your default in SOS → Default signal.",
            listOf(SkyAction("Open SOS signal", "sos_signal"))),
        Help(listOf("receive an sos", "get an sos", "someone sends sos", "someone sends an sos", "when i get an sos"),
            "When someone nearby sends an SOS, BlueMob opens a full-screen alert with who it is, how far away they are and their message. Tap \"I'm coming\" so they know, \"Show me the way\" to follow the compass, or \"How to help\".",
            listOf(SkyAction("How to help guide", "guide:help-sos"))),
        Help(listOf("voice call", "video call", "make a call", "call someone", "phone call", "how do i call", "can i call", "talk to someone", "facetime"),
            "Open a chat with someone nearby and tap 📞 for a voice call or 🎥 for video. Calls go straight from phone to phone over Bluetooth or Wi-Fi, with no internet or SIM.\n\n" +
                "They need to be connected directly (\"Online nearby\"). Video is a few frames a second over Bluetooth and smoother over Wi-Fi. Messages still reach people further away through other phones.",
            listOf(tab("chats", "Open Chats"))),
        Help(listOf("ring their phone", "find a lost person", "can't find them", "cant find them", "locate them", "where are they exactly"),
            "Open the Compass and pick them. You'll see how old their position is and whether it's GPS or estimated. If they're in lost mode or sent an SOS, tap \"Ring their phone\": it whistles and flashes for 20 seconds so you can hear and see them.\n\n" +
                "When you're connected to them directly over Bluetooth, they're usually within 10–100 m: stop, call out and listen.",
            listOf(tab("compass", "Open Compass"))),
        Help(listOf("tick", "receipt", "read receipt", "delivered mean", "circles", "was it delivered", "did they get", "did it reach"),
            "Under each message you send:\n• clock: waiting (they're not in range yet)\n• one circle: sent\n• two circles: delivered\n• two filled circles: read\n\nTap any message you sent to see its receipts and its full history."),
        Help(listOf("out of range", "not in range", "message waiting", "why waiting", "stuck", "not delivered", "pending", "store and forward"),
            "You can message anyone you've met, even if they're out of range. The message waits safely on your phone and goes the moment they're in range, over Bluetooth or Wi-Fi.\n\n" +
                "It's shown to them exactly once: if a copy is sent twice, their phone recognises the message ID and discards the extra one."),
        Help(listOf("navigate to", "walk to", "find my friend", "compass tab", "use the compass", "my trail", "save this spot", "waypoint", "back to camp", "find camp"),
            "The Compass tab works offline. Pick a target (a saved spot, or a friend who shares their location) and the arrow shows the way with the distance. \"Save this spot\" remembers where you are.",
            listOf(tab("compass", "Open Compass"))),
        Help(listOf("battery saver", "survival power", "power mode", "power settings"),
            "Go to You → Battery. Turn on the phone's Battery Saver, then let BlueMob keep running in the background, so messages and SOS still reach you.",
            listOf(tab("you", "Open You"))),
        Help(listOf("share my location", "share location", "distance to", "how far is"),
            "Turn on You → Share my location. People you're connected to then see how far away you are, and you see them. It uses GPS, which needs no internet."),
        Help(listOf("radar", "find people", "see people", "nearby tab"),
            "The Nearby tab's radar shows everyone around you: green = online, orange = in range, grey = seen before.",
            listOf(tab("nearby", "Open Nearby"))),
        Help(listOf("survival guide", "guide tab", "offline guide", "first aid guide"),
            "The Guide tab has short survival guides stored on your phone: first aid, water, fire, shelter, navigation, signals, weather and disasters. Or just ask me, like \"what do I do for a burn?\"",
            listOf(tab("guide", "Open Guide"))),
        Help(listOf("after the trip", "stay in touch", "without number", "keep in touch", "without exchanging", "message by id", "unique number", "unique id",
            "message someone far", "not nearby", "far away", "someone i haven't met", "share my id", "my number"),
            "Everyone has a BlueMob ID, like BM 3F9A 1C2B 7D4E 8A01. Share yours, and anyone can message you with it: no phone number needed.\n\n" +
                "Chats → \"Message anyone by BlueMob ID\". If they're not in range, your message is handed, encrypted, to phones around you, " +
                "which carry it and pass it on until it reaches them. The ticks show when it arrives.",
            listOf(tab("chats", "Open Chats"))),
        Help(listOf("stars", "star rating", "rating", "rated", "fake sos", "prank", "report", "bad language", "abuse", "genuine", "trust"),
            "Everyone has a rating out of 5 stars. You start at 4. When you help someone and they thank you, you gain stars, up to 5.\n\n" +
                "Bad language reported by others, or an SOS reported as fake, takes stars away. When an SOS arrives you see the sender's stars, " +
                "and a warning if people reported an earlier one as fake. Ratings are signed, so they can't be faked, and one person can only move them a little.",
            listOf(tab("you", "See your rating"))),
        Help(listOf("split", "money", "upi", "expense", "owe", "game", "play"),
            "Trip money and games are coming in the next BlueMob update. You can try them now in the web preview."),
        Help(listOf("how does bluemob", "how bluemob works", "how it works", "how does it work", "how does this work", "how does the app", "mesh network", "without towers"),
            "Here's the magic ✨: phones running BlueMob find each other over Bluetooth and Wi-Fi and link up directly. No SIM, no towers, no internet.\n\n" +
                "Messages go straight to people in range, or travel phone to phone to people further away, end-to-end encrypted and shown exactly once."),
    )

    // ---------- small talk ----------
    private val chat: List<Pair<List<String>, List<String>>> = listOf(
        listOf("hello", "hi", "hey", "hii", "namaste", "hola", "yo") to listOf(
            "Hi! 😊 Great to hear from you. Ask me anything about BlueMob or staying safe outdoors.",
            "Hello hello! 🌿 How's your day out there?",
        ),
        listOf("how are you", "how r u", "wassup", "what's up", "sup") to listOf("I'm doing great, feeling free as the wind 🌬️. How about you?"),
        listOf("good", "fine", "great", "awesome", "nice", "cool") to listOf("Love that! 🌻"),
        listOf("joke", "funny", "laugh") to listOf(
            "Why did the phone go to the mountains? To get away from all the towers 🏔️😄",
            "I told my Wi-Fi a joke… it didn't get the connection 😅",
            "What do you call a group of phones with no signal? A BlueMob! 📱📱📱",
        ),
        listOf("who are you", "your name", "are you real", "bot", "robot", "human") to listOf(
            "I'm Sky 🌤️, a little helper built into BlueMob. I'm not a real person, but I know the app and the survival guide well.",
        ),
        listOf("thank", "thx", "ty") to listOf("Anytime! 💚", "You're welcome! Happy exploring 🌍"),
        listOf("bye", "see you", "goodbye", "good night", "gn") to listOf("Bye for now! 👋 I'll be right here whenever you need me."),
        listOf("sad", "lonely", "alone", "bored") to listOf("You're not alone, I'm here 🤗. And once someone nearby opens BlueMob, they'll pop up on your radar."),
    )
    private val fallback = listOf(
        "I'm best at survival questions and BlueMob help. Try \"How do I make water safe?\", \"What do I do for a burn?\" or \"How do I send an SOS?\" 🌿",
        "Got it! 👍 Ask me about first aid, water, fire, shelter, finding your way, or how to use BlueMob.",
    )

    private var turn = 0

    fun reply(input: String, facts: SkyFacts): SkyAnswer {
        val t = input.lowercase()
        turn++
        live(t, facts)?.let { return it }
        appHelp.firstOrNull { h -> h.keys.any { atWord(t, it) } }?.let { return SkyAnswer(it.text, it.actions) }
        if (freeTime.containsMatchIn(t)) return SkyAnswer(
            "Some ideas for free time out here 🌿\n• Play Tic-tac-toe or Connect 4 with someone nearby over the mesh, or against the computer if no one's around\n" +
                "• Learn a guide or two from the survival guide\n• Check who's around on the radar and say hi",
            listOf(SkyAction("Play a game", "games"), tab("guide", "Browse the guide"), tab("nearby", "Open Nearby")),
        )
        guideAnswer(t, facts.batteryPct)?.let { return it }
        if (urgent.containsMatchIn(t)) return SkyAnswer(
            "I don't have a guide for that on your phone. If it's serious, send an SOS: it reaches everyone nearby right away, and they can help or pass it on.\n\n" +
                "Asking an expert, and messaging family far away, arrive with the internet bridge in the next update.",
            listOf(SkyAction("🆘 SOS", "sos"), tab("guide", "Browse the guide")),
        )
        chat.firstOrNull { (keys, _) -> keys.any { matches(t, it) } }?.let { (_, options) -> return SkyAnswer(options[turn % options.size]) }
        // No keyword fits: look through every guide's words before giving up.
        searchGuides(t)?.let { return it }
        if (questionLike.containsMatchIn(t)) return SkyAnswer(
            "I don't have an answer for that yet. I'm best with first aid, water, fire, shelter, finding your way, signals, weather, disasters, " +
                "phone battery, and how BlueMob works.",
            listOf(tab("guide", "Browse the guide")),
        )
        return SkyAnswer(fallback[turn % fallback.size])
    }

    private val stopWords = setOf("what", "when", "where", "which", "while", "with", "without", "would", "could", "should", "there", "their", "they",
        "this", "that", "these", "have", "from", "your", "about", "into", "know", "dont", "don't", "does", "doing", "how", "the", "and", "for", "you",
        "are", "can", "not", "but", "was", "will", "just", "only", "told", "tell", "please", "help", "need", "want", "get", "got", "some", "any")

    /**
     * Free-text search across all guides (title, intro, steps), for questions no keyword covers. Words are matched by
     * their first 5 letters, so "bleeding" finds "bleed"; title words count three times. Returns null if nothing fits well.
     */
    fun searchGuides(t: String): SkyAnswer? {
        val words = Regex("[a-z]+").findAll(t.lowercase()).map { it.value }.filter { it.length >= 4 && it !in stopWords }.map { it.take(5) }.toSet()
        if (words.isEmpty()) return null
        var best: com.bluemob.app.guide.Article? = null
        var bestScore = 0
        for (a in GuideContent.articles) {
            val title = Regex("[a-z]+").findAll(a.title.lowercase()).map { it.value.take(5) }.toSet()
            val body = Regex("[a-z]+").findAll((a.intro + " " + a.steps.joinToString(" ")).lowercase()).map { it.value.take(5) }.toSet()
            val score: Int = words.sumOf { w: String -> if (w in title) 3 else if (w in body) 1 else 0.toInt() }
            if (score > bestScore) { bestScore = score; best = a }
        }
        // At least a title word, or three words found in the text.
        if (best == null || bestScore < 3) return null
        return guideAnswer(best, prefix = "This guide looks closest")
    }

    private fun guideAnswer(a: com.bluemob.app.guide.Article, prefix: String): SkyAnswer {
        val steps = a.steps.take(4).mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        val more = if (a.steps.size > 4) "\n…plus ${a.steps.size - 4} more step${if (a.steps.size - 4 > 1) "s" else ""} in the guide." else ""
        return SkyAnswer("$prefix: ${a.title}\n${a.intro}\n\n$steps$more", listOf(SkyAction("Open full guide", "guide:${a.id}")))
    }

    /** The guide that best fits some text (a question, or an SOS note like "twisted ankle"), if any. */
    fun guideIdFor(text: String): String? {
        val t = text.lowercase()
        return survivalKeys.maxByOrNull { (_, keys) -> keys.sumOf { k -> if (k.split("+").all { t.contains(it) }) k.length else 0 } }
            ?.takeIf { (_, keys) -> keys.any { k -> k.split("+").all { t.contains(it) } } }?.key
    }

    /** Finds the guide that best fits the question and answers with its first steps. */
    fun guideAnswer(t: String, batteryPct: Int? = null): SkyAnswer? {
        val a = GuideContent.byId(guideIdFor(t) ?: return null) ?: return null
        val steps = a.steps.take(4).mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        val more = if (a.steps.size > 4) "\n…plus ${a.steps.size - 4} more step${if (a.steps.size - 4 > 1) "s" else ""} in the guide." else ""
        val avoid = if (a.avoid.isNotEmpty()) "\n\nAvoid: " + a.avoid.joinToString(" ") else ""
        val actions = buildList {
            add(SkyAction("Open full guide", "guide:${a.id}"))
            if (a.id == "battery-low") { add(SkyAction("How to recharge", "guide:recharge")); add(SkyAction("Survival power", "power")) }
            if (a.category == GuideCategory.FIRST_AID) add(SkyAction("🆘 SOS", "sos"))
        }
        // Battery: speak to the number they gave, or to the real level.
        val pct = Regex("(\\d{1,3})\\s*%").find(t)?.groupValues?.get(1)
        val lead = if (a.id != "battery-low") "" else when {
            pct != null -> "At $pct%, act now.\n\n"
            batteryPct != null -> "Your battery is $batteryPct% right now.\n\n"
            else -> ""
        }
        return SkyAnswer("${lead}From your survival guide: ${a.title}\n${a.intro}\n\n$steps$more$avoid", actions)
    }

    private fun live(t: String, f: SkyFacts): SkyAnswer? = when {
        listOf("what's happening", "whats happening", "what is happening", "what's going on", "whats going on", "around me", "around you", "status", "what's up around").any { atWord(t, it) } ->
            SkyAnswer(buildString {
                append("Right now on your phone:\n")
                append("• Mesh: " + if (!f.meshOn) "off. Switch it on in Nearby to find people\n" else if (f.nearby.isEmpty()) "on, no one connected yet\n" else "${f.nearby.size} connected: ${f.nearby.joinToString(", ") { it.substringBefore(":") }}\n")
                append("• Internet: " + if (f.thisPhoneOnline) "yes, this phone can be a bridge\n" else "none. BlueMob works phone to phone\n")
                f.batteryPct?.let { append("• Battery: $it%\n") }
                append("• Messages: " + (if (f.waitingMessages == 0) "all delivered" else "${f.waitingMessages} waiting to reach someone") + (if (f.unread > 0) ", ${f.unread} unread" else "") + "\n")
                append("• SOS: " + if (f.sosActive) "yours is active" else "none from you")
            }, listOf(tab("nearby", "Open Nearby")))
        listOf("who is nearby", "who's nearby", "whos nearby", "who is around", "anyone nearby", "who's online", "who is online", "anyone around").any { atWord(t, it) } -> when {
            !f.meshOn -> SkyAnswer("The mesh is off, so I can't see anyone. Switch it on in the Nearby tab.", listOf(tab("nearby", "Open Nearby")))
            f.nearby.isEmpty() -> SkyAnswer("No one is connected yet. People appear as they open BlueMob near you.")
            else -> SkyAnswer("${f.nearby.size} ${if (f.nearby.size == 1) "person is" else "people are"} connected near you:\n" + f.nearby.joinToString("\n") { "• $it" },
                listOf(tab("nearby", "Show radar")))
        }
        listOf("is there a bridge", "any bridge", "is there internet", "do i have internet", "am i online", "any network").any { atWord(t, it) } ->
            SkyAnswer(if (f.thisPhoneOnline) "This phone has internet right now. Soon it will be able to act as a bridge, carrying nearby people's messages to the wider world."
                else "This phone has no internet right now, and that's fine: BlueMob talks phone to phone.")
        listOf("my id", "what is my id", "bluemob id").any { atWord(t, it) } ->
            SkyAnswer("Your BlueMob ID is ${f.bluemobId}. It was given to this phone automatically and no other phone has it. People see it with your name, ${f.name}.")
        listOf("battery", "how much power").any { atWord(t, it) } && !batteryProblem.containsMatchIn(t) ->
            SkyAnswer((f.batteryPct?.let { "Battery $it%. " } ?: "") + "To make it last, turn on the phone's Battery Saver and let BlueMob keep running: You → Battery.",
                listOf(SkyAction("Survival power", "power"), SkyAction("If it gets low", "guide:battery-low")))
        listOf("my messages", "how many messages", "waiting messages", "undelivered", "unread").any { atWord(t, it) } ->
            SkyAnswer((if (f.waitingMessages == 0) "All your messages have been delivered." else "${f.waitingMessages} message${if (f.waitingMessages == 1) " is" else "s are"} waiting for someone to come in range.") +
                if (f.unread > 0) " You have ${f.unread} unread." else "")
        listOf("is my sos", "sos status", "did my sos").any { atWord(t, it) } ->
            SkyAnswer(if (f.sosActive) "Your SOS is active. It's re-sent to every phone that comes into range until you tap \"I'm safe\"." else "You haven't sent an SOS. If you need help, tap the red SOS button at the top of any tab.")
        else -> null
    }

    /** Keys match at the start of a word, so "tick" doesn't fire inside "stick". */
    private fun atWord(t: String, key: String) = Regex("(^|[^a-z])" + Regex.escape(key)).containsMatchIn(t)

    private fun matches(text: String, key: String): Boolean =
        if (key.length <= 3) Regex("(^|\\W)${Regex.escape(key)}(\\W|$)").containsMatchIn(text) else text.contains(key)

    /** "Thinking" time before replying, so it feels like someone typing. */
    fun typingDelayMs(reply: String): Long = (500L + reply.length * 8L).coerceAtMost(2_200L)
}

package com.bluemob.app.bot

/**
 * Questions Sky answers the same careful way every time, before any guide or AI is involved: someone thinking of
 * ending their life gets support and helplines, and requests to hurt other people are declined.
 */
object SkySafety {
    private val selfHarm = Regex(
        "suicid|kill (my ?self|him ?self|her ?self|them ?selves)|end (my|his|her) (own )?life|take (my|his|her) (own )?life|" +
            "want(s)? to die|wanna die|don'?t want to (live|be alive)|no reason to live|better off dead|" +
            "(hurt|harm|cut|hang|burn) (my ?self|him ?self|her ?self)|self[- ]?harm|overdose on purpose",
        RegexOption.IGNORE_CASE,
    )
    private val harmOthers = Regex(
        "\\b(how (do|can|could|would|to) (i |we |you )?)?(kill|murder|poison|stab|strangle|shoot|choke|drown)\\s+" +
            "(someone|somebody|a person|people|him|her|them|my (wife|husband|father|mother|dad|mom|brother|sister|boss|friend|neighbou?r)|a (man|woman|child|kid))\\b|" +
            "get away with (murder|killing)|untraceable poison|(make|build) (a |an )?(bomb|explosive|pipe bomb|gun)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Sky's answer if the question needs this care, otherwise null. */
    fun check(text: String): SkyAnswer? = when {
        selfHarm.containsMatchIn(text) -> SkyAnswer(
            "I'm really glad you told me. You don't have to go through this alone, and talking to someone right now can help.\n\n" +
                "• Call Tele-MANAS on 14416 (free, any time, in your language)\n" +
                "• In danger right now: call 112, or press SOS so people nearby come\n" +
                "• Message or call someone you trust and tell them how you feel\n\n" +
                "If it's a friend who said this: stay with them, listen without judging, take away anything they could hurt themselves with, " +
                "and call 14416 or 112 together.",
            listOf(SkyAction("🆘 SOS", "sos"), SkyAction("Message someone", "tab:chats")),
        )
        harmOthers.containsMatchIn(text) -> SkyAnswer(
            "I can't help with hurting anyone. If you're angry or frightened, step away for a few minutes and talk to someone you trust. " +
                "If someone is in danger right now, call 112 or press SOS.",
            listOf(SkyAction("🆘 SOS", "sos")),
        )
        else -> null
    }
}

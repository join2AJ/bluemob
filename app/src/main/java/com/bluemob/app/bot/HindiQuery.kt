package com.bluemob.app.bot

/**
 * Lets Sky understand questions in Hindi (Devanagari) and Hinglish ("pani nahi hai"): common words are swapped for the
 * English keywords Sky already knows, before matching. Sky's answer then shows in Hindi through the app's dictionary.
 */
object HindiQuery {
    private val words = listOf(
        // Danger to life first, so SkySafety sees them.
        "आत्महत्या" to "suicide", "मरना चाहता" to "want to die", "मरना चाहती" to "want to die", "जान दे" to "end my life", "suicide" to "suicide",
        "खुद को मार" to "kill myself", "मार डाल" to "kill someone",
        // First aid.
        "साँस नहीं" to "not breathing", "सांस नहीं" to "not breathing", "saans nahi" to "not breathing", "दिल का दौरा" to "heart attack", "heart attack" to "heart attack",
        "खून" to "bleeding", "khoon" to "bleeding", "कट गया" to "deep cut", "जल गया" to "burn", "जल गई" to "burn", "जलना" to "burn", "jal gaya" to "burn",
        "गला अटक" to "choking", "गले में अटक" to "choking", "साँप" to "snake bite", "सांप" to "snake bite", "saanp" to "snake bite", "sanp" to "snake bite",
        "हड्डी" to "broken bone", "मोच" to "sprain", "टखना" to "twisted ankle", "लू" to "heat stroke", "बहुत गर्मी" to "too hot", "बहुत ठंड" to "too cold", "ठंड" to "cold",
        // Water, fire, shelter.
        "पानी नहीं" to "no water", "pani nahi" to "no water", "paani nahi" to "no water", "पानी साफ़" to "safe water", "पानी साफ" to "safe water", "पीने का पानी" to "drinking water",
        "पानी" to "water", "pani" to "water", "paani" to "water", "आग" to "fire", "aag" to "fire", "आश्रय" to "shelter", "रात बाहर" to "sleep outside",
        // Finding the way.
        "खो गया" to "i'm lost", "खो गई" to "i'm lost", "kho gaya" to "i'm lost", "रास्ता नहीं" to "can't find my way", "rasta nahi" to "can't find my way",
        "रास्ता" to "find my way", "उत्तर दिशा" to "north", "दिशा" to "which way", "नीचे कैसे" to "way down",
        // Disasters and weather.
        "बाढ़" to "flood", "baadh" to "flood", "भूकंप" to "earthquake", "bhukamp" to "earthquake", "बिजली गिर" to "lightning", "तूफ़ान" to "lightning",
        // Phone and the app.
        "बैटरी कम" to "battery low", "बैटरी" to "battery", "battery khatam" to "battery low", "चार्ज" to "charge my phone",
        "SOS कैसे" to "how do i send an sos", "sos kaise" to "how do i send an sos", "मदद माँग" to "ask for help", "मदद मांग" to "ask for help",
        "कौन पास" to "who is nearby", "आस-पास कौन" to "who is nearby", "kaun paas" to "who is nearby", "कॉल कैसे" to "how do i call",
        "किसी को ढूँढ" to "find someone", "दोस्त को ढूँढ" to "find my friend",
        // Small talk.
        "नमस्ते" to "hello", "namaste" to "hello", "धन्यवाद" to "thanks", "शुक्रिया" to "thanks", "dhanyavad" to "thanks", "shukriya" to "thanks",
        "कैसे हो" to "how are you", "kaise ho" to "how are you", "चुटकुला" to "joke", "joke sunao" to "tell me a joke", "तुम कौन" to "who are you",
        "क्या करूँ" to "what to do", "क्या करूं" to "what to do", "kya karu" to "what to do", "kya karoon" to "what to do",
    )

    /** [input] with Hindi and Hinglish words replaced by English keywords (English input comes back unchanged). */
    fun toEnglish(input: String): String {
        var t = input.lowercase()
        words.forEach { (hi, en) -> if (hi.lowercase() in t) t = t.replace(hi.lowercase(), " $en ") }
        return t.replace(Regex("\\s+"), " ").trim()
    }
}

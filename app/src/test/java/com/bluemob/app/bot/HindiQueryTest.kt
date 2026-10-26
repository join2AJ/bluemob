package com.bluemob.app.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HindiQueryTest {
    @Test fun hindiAndHinglishBecomeKeywords() {
        assertTrue("no water" in HindiQuery.toEnglish("पानी नहीं है, क्या करूँ?"))
        assertTrue("snake bite" in HindiQuery.toEnglish("saanp ne kaat liya"))
        assertTrue("burn" in HindiQuery.toEnglish("हाथ जल गया"))
        assertEquals("how do i make water safe?", HindiQuery.toEnglish("How do I make water safe?"))
    }

    @Test fun selfHarmInHindiStillGetsTheHelpline() {
        assertNotNull(SkySafety.check(HindiQuery.toEnglish("मैं आत्महत्या करना चाहता हूँ")))
    }
}

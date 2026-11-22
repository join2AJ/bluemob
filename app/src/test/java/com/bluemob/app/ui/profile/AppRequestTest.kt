package com.bluemob.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRequestTest {
    @Test fun saysWhatIsMissing() {
        assertEquals(listOf("category", "budget", "how to reach you", "a short description"), AppRequest.missing(AppRequest.Form()))
        val f = AppRequest.Form(kind = "Business", budget = "Not sure - advise me", about = "A shop app", contact = "98xxxxxx")
        assertTrue(AppRequest.missing(f).isEmpty())
    }

    @Test fun textListsChoicesInOrder() {
        val f = AppRequest.Form(kind = "Business", features = setOf("Video", "Works offline"), budget = "₹1 – 3 lakh", about = "A shop app", name = "Asha", contact = "asha@example.com")
        val t = AppRequest.text(f, "1.0")
        assertTrue(t, "Features: Works offline, Video" in t)
        assertTrue(t, "Budget: ₹1 – 3 lakh" in t)
        assertTrue(t, t.endsWith("Name: Asha · Contact: asha@example.com"))
    }
}

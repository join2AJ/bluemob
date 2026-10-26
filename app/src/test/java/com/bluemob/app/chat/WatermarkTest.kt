package com.bluemob.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatermarkTest {
    private val id = "3f9a1c2b7d4e8a01"
    private val at = 1_790_000_000_000L

    @Test fun codeGivesBackTheIdForItsHour() {
        val code = Watermark.code(id, at)
        assertEquals(19, code.length) // 16 characters in 4 groups
        assertEquals(id, Watermark.decode(code, at / Watermark.HOUR))
        assertEquals(id, Watermark.decode(code.lowercase().replace(" ", ""), at / Watermark.HOUR))
    }

    @Test fun anotherHourOrATypoIsRefused() {
        val code = Watermark.code(id, at)
        assertNull(Watermark.decode(code, at / Watermark.HOUR + 1))
        assertNull(Watermark.decode((if (code[0] == '0') "1" else "0") + code.drop(1), at / Watermark.HOUR))
    }

    @Test fun codesChangeEveryHour() = assertNotEquals(Watermark.code(id, at), Watermark.code(id, at + Watermark.HOUR))

    /** The relay's admin lookup (server/relay.js) computes the same code. */
    @Test fun sameCodeAsTheServer() = assertEquals("FVQT V7GN FX14 PVZ2", Watermark.code(id, at))
}

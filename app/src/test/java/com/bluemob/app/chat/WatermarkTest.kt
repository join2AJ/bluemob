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

    @Test fun dotsSpellTheSameBytes() {
        val bits = Watermark.bits(id, at)
        val bytes = Watermark.bytes(id, at)
        assertEquals(80, bits.size)
        (0 until 10).forEach { b -> assertEquals(bytes[b].toInt() and 0xff, (0 until 8).fold(0) { acc, i -> (acc shl 1) or (if (bits[b * 8 + i]) 1 else 0) }) }
    }
}

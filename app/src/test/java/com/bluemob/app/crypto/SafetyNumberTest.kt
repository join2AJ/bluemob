package com.bluemob.app.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafetyNumberTest {
    private val a = Crypto.generate(); private val b = Crypto.generate(); private val c = Crypto.generate()
    private val ida = Crypto.idFor(a.public.encoded); private val idb = Crypto.idFor(b.public.encoded); private val idc = Crypto.idFor(c.public.encoded)

    @Test fun bothPhonesSeeTheSameSixtyDigits() {
        val onA = SafetyNumber.of(ida, a.public.encoded, idb, b.public.encoded)
        val onB = SafetyNumber.of(idb, b.public.encoded, ida, a.public.encoded)
        assertEquals(onA, onB)
        assertEquals(12, onA.split(" ").size)
        assertEquals(60, onA.count { it.isDigit() })
        assertNotEquals(onA, SafetyNumber.of(ida, a.public.encoded, idc, c.public.encoded))
    }

    @Test fun qrCodeCarriesIdAndMatchingKey() {
        val code = SafetyNumber.qr(ida, Crypto.encode(a.public.encoded))
        assertEquals(ida to Crypto.encode(a.public.encoded), SafetyNumber.read(code))
        // Someone else's key under this ID, or anything that isn't ours: refused.
        assertNull(SafetyNumber.read(SafetyNumber.qr(ida, Crypto.encode(b.public.encoded))))
        assertNull(SafetyNumber.read("https://example.com"))
    }
}

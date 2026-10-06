package com.bluemob.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MuLawTest {
    @Test fun silenceStaysSilent() {
        assertEquals(0, MuLaw.decode(MuLaw.encode(0.toShort())).toInt())
    }

    @Test fun roundTripIsCloseAcrossTheRange() {
        for (v in -32768..32767 step 97) {
            val back = MuLaw.decode(MuLaw.encode(v.toShort())).toInt()
            // μ-law keeps about 3% relative error, plus a small floor near zero.
            val allowed = maxOf(140, abs(v) / 16)
            assertTrue("$v came back as $back", abs(back - v) <= allowed)
            assertTrue("sign kept for $v", v == 0 || back == 0 || (v > 0) == (back > 0) || abs(v) < 140)
        }
    }

    @Test fun matchesTheStandardTable() {
        // Reference values from ITU-T G.711.
        assertEquals(0xFF.toByte(), MuLaw.encode(0.toShort()))
        assertEquals(0x80.toByte(), MuLaw.encode(32767.toShort()))
        assertEquals(0x00.toByte(), MuLaw.encode((-32768).toShort()))
        assertEquals(32124, MuLaw.decode(0x80.toByte()).toInt())
        assertEquals(-32124, MuLaw.decode(0x00.toByte()).toInt())
    }

    @Test fun framesEncodeInPlace() {
        val pcm = ShortArray(480) { (it * 50 - 12000).toShort() }
        val packet = ByteArray(3 + pcm.size)
        MuLaw.encode(pcm, pcm.size, packet, 3)
        val back = MuLaw.decode(packet, 3)
        assertEquals(pcm.size, back.size)
        for (i in pcm.indices) assertTrue(abs(back[i] - pcm[i]) <= maxOf(140, abs(pcm[i].toInt()) / 16))
    }
}

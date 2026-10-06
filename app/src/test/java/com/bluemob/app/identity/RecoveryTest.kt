package com.bluemob.app.identity

import com.bluemob.app.crypto.Crypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey

class RecoveryTest {
    private val random = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42) }

    @Test fun seedCodeIsShortGroupedAndRoundTrips() {
        val seed = Recovery.newSeed(random)
        val code = Recovery.seedCode(seed)
        assertEquals(34, code.length) // 7 groups of 4 with 6 dashes
        assertTrue(code.matches(Regex("([0-9A-Z]{4}-){6}[0-9A-Z]{4}")))
        val parsed = Recovery.parse(code) as Recovery.Parsed.Seed
        assertArrayEquals(seed, parsed.seed)
    }

    @Test fun sameSeedAlwaysGivesTheSameId() {
        val seed = Recovery.newSeed(random)
        val a = Recovery.keysFromSeed(seed)
        val b = Recovery.keysFor(Recovery.parse(Recovery.seedCode(seed))!!)
        assertEquals(Crypto.idFor(a.public.encoded), Crypto.idFor(b.public.encoded))
    }

    @Test fun derivedKeysSignAndVerifyLikeRealOnes() {
        val pair = Recovery.keysFromSeed(Recovery.newSeed(random))
        val sig = Crypto.sign(pair.private, "hello".toByteArray())
        assertTrue(Crypto.verify(pair.public, "hello".toByteArray(), sig))
        // The encoded public key reads back through the same path the mesh uses.
        assertNotNull(Crypto.publicKey(pair.public.encoded))
    }

    @Test fun keyCodeRestoresAnOldRandomAccountExactly() {
        // Accounts before 0.8 had random keys: the long code must bring back the very same ID.
        repeat(5) {
            val old = Crypto.generate()
            val code = Recovery.keyCode(old.private as ECPrivateKey)
            assertEquals(69, code.length) // 14 groups of 4 with 13 dashes
            val back = Recovery.keysFor(Recovery.parse(code)!!)
            assertArrayEquals(old.public.encoded, back.public.encoded)
        }
    }

    @Test fun forgivingAboutHowItIsTyped() {
        val seed = Recovery.newSeed(random)
        val code = Recovery.seedCode(seed)
        val sloppy = code.lowercase().replace("-", " ").replace('0', 'o').replace('1', 'l')
        assertArrayEquals(seed, (Recovery.parse(sloppy) as Recovery.Parsed.Seed).seed)
    }

    @Test fun typosAreCaughtNotRestoredAsSomeoneElse() {
        val code = Recovery.normalize(Recovery.seedCode(Recovery.newSeed(random)))
        var caught = 0
        for (i in code.indices) {
            val swapped = code.substring(0, i) + (if (code[i] == 'A') 'B' else 'A') + code.substring(i + 1)
            if (Recovery.parse(swapped) == null) caught++
        }
        // A 16-bit checksum: a single wrong character is (practically) always caught. The last char only carries padding bits.
        assertTrue("caught $caught of ${code.length}", caught >= code.length - 1)
        assertNull(Recovery.parse(code.dropLast(1)))
        assertNull(Recovery.parse("hello"))
        assertNull(Recovery.parse(""))
    }

    @Test fun base32RoundTrips() {
        repeat(20) {
            val bytes = ByteArray(it + 1).also(random::nextBytes)
            val back = Recovery.unbase32(Recovery.base32(bytes))!!
            assertArrayEquals(bytes, back.copyOf(bytes.size))
        }
    }
}

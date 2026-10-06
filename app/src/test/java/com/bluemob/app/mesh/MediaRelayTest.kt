package com.bluemob.app.mesh

import com.bluemob.app.crypto.Crypto
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRelayTest {
    private val a = "aaaaaaaaaaaaaaaa"
    private val b = "bbbbbbbbbbbbbbbb"
    private val c = "cccccccccccccccc"
    private val d = "dddddddddddddddd"

    @Test fun relayFramesCarryWhereTheyGoAndWhoFrom() {
        val inner = byteArrayOf('E'.code.toByte(), 'A'.code.toByte(), 9, 9)
        val f = RelayFrame.wrap(d, a, inner)
        val p = RelayFrame.parse(f)!!
        assertEquals(d, p.dest); assertEquals(a, p.origin); assertEquals(RelayFrame.TTL, p.ttl)
        assertArrayEquals(inner, p.inner)
        assertEquals('A'.code.toByte(), RelayFrame.innerKind(f))
        // Each phone uses up one hop; it stops before going round in circles for ever.
        var hopped: ByteArray? = f
        repeat(RelayFrame.TTL - 1) { hopped = RelayFrame.hop(hopped!!) }
        assertNull(RelayFrame.hop(hopped!!))
        assertNull(RelayFrame.parse(byteArrayOf('A'.code.toByte(), 1, 2)))
    }

    /** a - b - c - d in a line: a learns the way to d through b, and b, next to the internet, is a's way out. */
    @Test fun routesSpreadHopByHop() {
        var now = 1_000L
        val atB = RouteTable(b) { now }
        val atA = RouteTable(a) { now }
        // c tells b it reaches d.
        atB.update(c, JSONObject().put("r", JSONObject().put(d, 1).put(c, 0)).put("net", -1))
        val bAdvert = atB.advert(listOf(a, c), online = true)
        assertEquals(2, bAdvert.getJSONObject("r").getInt(d))
        assertEquals(0, bAdvert.getInt("net"))
        atA.update(b, bAdvert)
        assertEquals(b to 3, atA.nextHop(d, listOf(b)))
        assertEquals(b to 0, atA.netHop(listOf(b)))
        assertEquals(c to 1, atA.nextHop(c, listOf(c, b)))
        // b walks away: no route through it any more.
        assertNull(atA.nextHop(d, listOf()))
        // Old news is forgotten.
        now += RouteTable.STALE_MS + 1
        assertNull(atA.nextHop(d, listOf(b)))
        assertNull(atA.netHop(listOf(b)))
    }

    @Test fun routesNeverGrowPastTheHopLimit() {
        val t = RouteTable(a)
        t.update(b, JSONObject().put("r", JSONObject().put(d, RouteTable.MAX_HOPS)))
        assertNull(t.nextHop(d, listOf(b)))
    }

    @Test fun callsAreEncryptedEndToEnd() {
        val ka = Crypto.generate(); val kb = Crypto.generate()
        val ida = Crypto.idFor(ka.public.encoded); val idb = Crypto.idFor(kb.public.encoded)
        val atA = CallCipher(Crypto.sharedKey(ka.private, kb.public, ida, idb), "c-123", ida)
        val atB = CallCipher(Crypto.sharedKey(kb.private, ka.public, idb, ida), "c-123", idb)
        val voice = byteArrayOf('A'.code.toByte(), 0, 1) + ByteArray(480) { it.toByte() }
        val sealed = atA.seal(voice)
        assertEquals(CallCipher.MARK, sealed[0]); assertEquals('A'.code.toByte(), sealed[1])
        assertArrayEquals(voice, atB.open(sealed, ida))
        // Both directions at once never reuse a nonce, and each opens the other's.
        assertArrayEquals(voice, atA.open(atB.seal(voice), idb))
        // A changed byte, the wrong sender, or another call's key: rejected.
        val tampered = sealed.copyOf().also { it[20] = (it[20] + 1).toByte() }
        assertNull(atB.open(tampered, ida))
        assertNull(atB.open(sealed, idb))
        val other = CallCipher(Crypto.sharedKey(kb.private, ka.public, idb, ida), "c-999", idb)
        assertNull(other.open(sealed, ida))
        // A relay can't relabel video as voice.
        val relabelled = sealed.copyOf().also { it[1] = 'V'.code.toByte() }
        assertNull(atB.open(relabelled, ida))
        assertNotNull(atB.open(atA.seal(byteArrayOf('V'.code.toByte(), 1, 2, 3)), ida))
    }
}

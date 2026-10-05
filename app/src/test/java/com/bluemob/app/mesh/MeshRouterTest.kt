package com.bluemob.app.mesh

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import com.bluemob.app.crypto.KeyBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phones that move around: each runs the real router, and the test decides who is in range of whom.
 * Packets are delivered instantly while two phones are linked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeshRouterTest {
    private var clock = 1_000_000L

    inner class Phone(val name: String) {
        val keys = DeviceKeys(Crypto.generate())
        val id get() = keys.nodeId
        val book = KeyBook()
        val store = MemoryRelayStore()
        val links = mutableSetOf<Phone>()
        val received = mutableListOf<MeshEvent>()
        var packetsSent = 0
        val router: MeshRouter = MeshRouter(object : Wire {
            override fun neighbors() = links.map { it.id }
            override fun send(nodeId: String, packet: JSONObject) {
                packetsSent++
                links.firstOrNull { it.id == nodeId }?.router?.onPacket(id, JSONObject(packet.toString()))
            }
            override fun linkName(nodeId: String) = "Bluetooth"
            override fun nameOf(nodeId: String) = links.firstOrNull { it.id == nodeId }?.name ?: "?"
        }, keys, book, store, myName = { name }, now = { clock })

        init {
            CoroutineScope(Dispatchers.Unconfined).launch { router.events.collect { received += it } }
        }
        fun messages() = received.filterIsInstance<MeshEvent.MessageReceived>()
        fun receipts() = received.filterIsInstance<MeshEvent.Receipt>()
    }

    private fun link(a: Phone, b: Phone) {
        a.links += b; b.links += a
        // Like the real hello: phones in range learn each other's keys.
        a.book.add(b.keys.publicB64); b.book.add(a.keys.publicB64)
        a.router.onNeighborConnected(b.id); b.router.onNeighborConnected(a.id)
    }
    private fun unlink(a: Phone, b: Phone) { a.links -= b; b.links -= a }

    @Test fun directMessageArrivesEncryptedAndOnce() {
        val a = Phone("Asha"); val b = Phone("Ravi")
        link(a, b)
        val h = a.router.sendChat(b.id, "m1", "Hello Ravi", clock)
        assertTrue(h is Handoff.Direct)
        assertEquals("Hello Ravi", b.messages().single().text)
        assertEquals(1, b.messages().single().hops)
        assertEquals("Asha", b.messages().single().name)
        assertEquals(Handoff.Held, a.router.sendChat(b.id, "m1", "Hello Ravi", clock)) // not resent on the same connection
    }

    @Test fun messageTravelsThroughAPhoneThatMovesBetweenGroups() {
        // A and C never meet. B meets A, walks away, later meets C.
        val a = Phone("Asha"); val b = Phone("Ravi"); val c = Phone("Kabir")
        link(b, c); unlink(b, c) // B knows C's key from an earlier meeting
        link(a, b)
        a.book.add(c.keys.publicB64) // A typed C's ID; the key comes from asking, tested below
        val h = a.router.sendChat(c.id, "m2", "Meet at the bridge at 5", clock)
        assertEquals(listOf("Ravi"), (h as Handoff.Carried).via)
        assertTrue(c.messages().isEmpty())
        assertEquals(1, b.router.carrying)

        unlink(a, b); link(b, c)
        val got = c.messages().single()
        assertEquals("Meet at the bridge at 5", got.text)
        assertEquals(a.id, got.fromNodeId)
        assertEquals(2, got.hops)
        assertEquals(0, b.router.carrying)

        // C's delivery receipt rides back with B to A.
        c.router.sendReceipt(a.id, "m2", read = false)
        unlink(b, c); link(a, b)
        val r = a.receipts().single()
        assertEquals("m2", r.messageId)
        assertEquals(c.id, r.fromNodeId)
        assertFalse(r.read)
    }

    @Test fun keyIsLearnedFromAnyPhoneThatKnowsIt() {
        val a = Phone("Asha"); val b = Phone("Ravi"); val c = Phone("Kabir")
        link(b, c) // B and C are together
        link(a, b) // A only meets B
        assertEquals(Handoff.NeedsKey, a.router.sendChat(c.id, "m3", "Hi Kabir", clock))
        assertTrue(a.book.knows(c.id))
        assertTrue(a.received.any { it is MeshEvent.KeyLearned && it.nodeId == c.id })
        // Now it can go: through B, who is next to C.
        assertTrue(a.router.sendChat(c.id, "m3", "Hi Kabir", clock) is Handoff.Carried)
        assertEquals("Hi Kabir", c.messages().single().text)
    }

    @Test fun carriersCannotReadOrChangeMessages() {
        val a = Phone("Asha"); val c = Phone("Kabir")
        val evil = Phone("Mallory")
        a.book.add(c.keys.publicB64)
        link(a, evil)
        a.router.sendChat(c.id, "m4", "Secret plan", clock)
        val carried = evil.store.all().single()
        assertFalse(carried.packet.contains("Secret plan"))
        // Tamper with the body and deliver it to C: the signature check rejects it.
        val tampered = JSONObject(carried.packet).put("b", JSONObject(JSONObject(carried.packet).getString("b")).put("at", 1).toString())
        c.router.onPacket(evil.id, tampered)
        assertTrue(c.messages().isEmpty())
        // The untouched copy is accepted.
        c.router.onPacket(evil.id, JSONObject(carried.packet))
        assertEquals("Secret plan", c.messages().single().text)
    }

    @Test fun nobodyCanSendAsSomeoneElse() {
        val a = Phone("Asha"); val c = Phone("Kabir"); val evil = Phone("Mallory")
        val body = JSONObject().put("id", "m5").put("to", c.id).put("at", clock).put("x", clock + 1000).put("c", "x")
        // 1. Claims to be Asha but signs with its own key: the key doesn't hash to Asha's ID.
        val forged = Envelope.seal(MeshRouter.RMSG, body, evil.keys)
        forged.put("b", JSONObject(forged.getString("b")).put("from", a.id).toString())
        assertNull(Envelope.open(forged))
        // 2. Uses Asha's public key, but can't make Asha's signature.
        val withHerKey = Envelope.seal(MeshRouter.RMSG, body, evil.keys).put("pk", a.keys.publicB64)
        withHerKey.put("b", JSONObject(withHerKey.getString("b")).put("from", a.id).toString())
        assertNull(Envelope.open(withHerKey))
        c.router.onPacket(evil.id, forged); c.router.onPacket(evil.id, withHerKey)
        assertTrue(c.messages().isEmpty())
    }

    @Test fun spraysALimitedNumberOfCopies() {
        val a = Phone("Asha")
        val far = Phone("Far")
        a.book.add(far.keys.publicB64)
        val crowd = (1..12).map { Phone("P$it") }
        crowd.forEach { link(a, it) }
        a.router.sendChat(far.id, "m6", "Anyone going north?", clock)
        val carriers = crowd.filter { it.router.carrying > 0 }
        assertEquals("8 copies: halves go to 4, 2, 1 → 3 carriers, A keeps the last", 3, carriers.size)
    }

    @Test fun oldMessagesExpire() {
        val a = Phone("Asha"); val b = Phone("Ravi"); val c = Phone("Kabir")
        a.book.add(c.keys.publicB64)
        link(a, b)
        a.router.sendChat(c.id, "m7", "Old news", clock)
        unlink(a, b)
        clock += MeshRouter.TTL_MS + 1
        link(b, c)
        assertTrue(c.messages().isEmpty())
        assertEquals(0, b.router.carrying)
    }

    @Test fun idsComeFromKeysAndAcceptTypedForms() {
        val k = DeviceKeys(Crypto.generate())
        assertEquals(16, k.nodeId.length)
        val typed = "BM " + k.nodeId.uppercase().chunked(4).joinToString(" ")
        assertEquals(k.nodeId, Crypto.normalizeId(typed))
        assertNull(Crypto.normalizeId("BM 1234"))
        val book = KeyBook()
        assertEquals(k.nodeId, book.add(k.publicB64))
        assertNull(KeyBook(mapOf("0000000000000000" to k.publicB64)).b64("0000000000000000")) // a key under the wrong ID is refused
    }
}

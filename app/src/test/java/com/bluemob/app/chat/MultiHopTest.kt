package com.bluemob.app.chat

import com.bluemob.app.bot.SkyAnswer
import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import com.bluemob.app.crypto.KeyBook
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.mesh.MeshRouter
import com.bluemob.app.mesh.Wire
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole chain on three phones: message store + router. Asha messages Kabir by ID; Ravi carries it. */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiHopTest {
    private inner class Phone(val name: String, scope: TestScope) {
        val keys = DeviceKeys(Crypto.generate())
        val id get() = keys.nodeId
        val book = KeyBook()
        val links = mutableSetOf<Phone>()
        val dao = MessageRepositoryTest.FakeDao()
        val router: MeshRouter = MeshRouter(object : Wire {
            override fun neighbors() = links.map { it.id }
            override fun send(nodeId: String, packet: JSONObject) { links.firstOrNull { it.id == nodeId }?.router?.onPacket(id, JSONObject(packet.toString())) }
            override fun linkName(nodeId: String) = "Bluetooth"
            override fun nameOf(nodeId: String) = links.firstOrNull { it.id == nodeId }?.name ?: "?"
        }, keys, book, myName = { name })
        val repo = MessageRepository(dao, router, scope.backgroundScope, { _, _ -> SkyAnswer("") })
    }

    private fun TestScope.settle() { advanceTimeBy(10_000); runCurrent() }

    private fun TestScope.link(a: Phone, b: Phone) {
        a.links += b; b.links += a
        a.book.add(b.keys.publicB64); b.book.add(a.keys.publicB64)
        a.router.onNeighborConnected(b.id); b.router.onNeighborConnected(a.id)
        settle()
    }
    private fun unlink(a: Phone, b: Phone) { a.links -= b; b.links -= a }

    @Test fun messageByIdReachesSomeoneNeverMetAndTheTicksComeBack() = runTest {
        val asha = Phone("Asha", this); val ravi = Phone("Ravi", this); val kabir = Phone("Kabir", this)
        link(ravi, kabir); unlink(ravi, kabir) // Ravi met Kabir earlier, so he knows Kabir's key

        link(asha, ravi)
        asha.repo.send(kabir.id, "Are you at the lake?") // typed Kabir's ID; Asha has never met him
        settle()
        val sent = { asha.dao.rows.value.single { it.fromMe && it.peer == kabir.id } }
        assertTrue(sent().history, sent().history.contains("Asking phones nearby for their key"))
        assertTrue(sent().history, sent().history.contains("Handed to Ravi to carry toward them"))
        assertEquals(MessageStatus.SENT, sent().status)

        // Ravi walks to Kabir: the message is delivered, and Kabir's receipt rides back with Ravi.
        unlink(asha, ravi); link(ravi, kabir)
        val got = kabir.dao.rows.value.single { !it.fromMe && it.peer == asha.id }
        assertEquals("Are you at the lake?", got.text)
        assertEquals(asha.id, got.peer)
        assertTrue(got.history.contains("passed on by 1 phone"))

        unlink(ravi, kabir); link(asha, ravi)
        assertEquals(MessageStatus.DELIVERED, sent().status)
        assertEquals("the mesh (1 phone carried it)", sent().deliveredVia)
    }
}

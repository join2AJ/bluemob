package com.bluemob.app.bridge

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import com.bluemob.app.crypto.KeyBook
import com.bluemob.app.mesh.Handoff
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.MeshRouter
import com.bluemob.app.mesh.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The 1,000 km case, end to end against the real relay (server/relay.js, started here with Node):
 * Asha and Kabir met once over Bluetooth, then went home to different cities. Both have internet.
 */
class InternetBridgeTest {
    private var server: Process? = null
    private var url = ""

    @Before fun startRelay() {
        val relay = listOf(File("../server/relay.js"), File("server/relay.js")).firstOrNull { it.exists() }
        val node = runCatching { ProcessBuilder("node", "--version").start().waitFor() == 0 }.getOrDefault(false)
        assumeTrue("Node and server/relay.js are needed for this test", relay != null && node)
        val script = "const {createServer, Store} = require(${JSONObject.quote(relay!!.absolutePath)});" +
            "const s = createServer(new Store(null)).listen(0, () => console.log(s.address().port));"
        server = ProcessBuilder("node", "-e", script).redirectErrorStream(true).start()
        url = "http://127.0.0.1:" + server!!.inputStream.bufferedReader().readLine().trim()
    }

    @After fun stopRelay() { server?.destroy() }

    private inner class Phone(val name: String) {
        val keys = DeviceKeys(Crypto.generate())
        val id get() = keys.nodeId
        val book = KeyBook()
        val events = mutableListOf<MeshEvent>()
        val router = MeshRouter(object : Wire {
            override fun neighbors() = emptyList<String>() // nobody nearby: they're in different cities
            override fun send(nodeId: String, packet: JSONObject) = Unit
            override fun linkName(nodeId: String) = ""
            override fun nameOf(nodeId: String) = ""
        }, keys, book, myName = { name })
        val bridge = BridgeClient({ url }, keys, book).also { router.internet = it }
        init { CoroutineScope(Dispatchers.Unconfined).launch { router.events.collect { events += it } } }
        fun sync() = runBlocking { bridge.sync(router, emptyList()) }
    }

    @Test fun phonesInDifferentCitiesTalkThroughTheRelay() {
        val asha = Phone("Asha"); val kabir = Phone("Kabir")
        asha.book.add(kabir.keys.publicB64); kabir.book.add(asha.keys.publicB64) // they met on the trip

        val h = asha.router.sendChat(kabir.id, "m1", "Reached Pune safely?", System.currentTimeMillis())
        assertEquals(Handoff.Carried(listOf(MeshRouter.INTERNET_NAME)), h)
        assertEquals(1, asha.sync().uploaded)

        val got = kabir.sync()
        assertTrue(got.error ?: "", got.ok)
        val msg = kabir.events.filterIsInstance<MeshEvent.MessageReceived>().single()
        assertEquals("Reached Pune safely?", msg.text)
        assertEquals(asha.id, msg.fromNodeId)
        assertTrue(msg.viaInternet)

        // Kabir's delivery receipt goes back the same way.
        kabir.router.sendReceipt(asha.id, "m1", read = false)
        kabir.sync(); asha.sync()
        val r = asha.events.filterIsInstance<MeshEvent.Receipt>().single()
        assertEquals("m1", r.messageId)
        assertTrue(r.viaInternet)
        // Nothing is downloaded twice.
        assertEquals(0, kabir.sync().downloaded)
    }

    @Test fun someoneYouNeverMetCanBeFoundByIdOnline() {
        val asha = Phone("Asha"); val dee = Phone("Dee")
        dee.sync() // Dee has used BlueMob online once, which registers her key with the relay
        assertEquals(Handoff.NeedsKey, asha.router.sendChat(dee.id, "m2", "Hi Dee, Asha here", System.currentTimeMillis()))
        val s = asha.sync()
        assertEquals(1, s.keysFound)
        assertTrue(asha.events.any { it is MeshEvent.KeyLearned && it.nodeId == dee.id })
        assertTrue(asha.router.sendChat(dee.id, "m2", "Hi Dee, Asha here", System.currentTimeMillis()) is Handoff.Carried)
        asha.sync(); dee.sync()
        assertEquals("Hi Dee, Asha here", dee.events.filterIsInstance<MeshEvent.MessageReceived>().single().text)
    }

    @Test fun theRelayRefusesPacketsThatWereTamperedWith() {
        val asha = Phone("Asha"); val kabir = Phone("Kabir")
        asha.book.add(kabir.keys.publicB64)
        val packet = JSONObject(Envelope().sealed(asha, kabir))
        val r = UrlHttp.post("$url/v1/push", JSONObject().put("packets", org.json.JSONArray().put(packet.put("b", packet.getString("b").replace("\"at\":1", "\"at\":2")))).toString())!!
        assertTrue(r.second, r.second.contains("bad-signature"))
    }

    private class Envelope {
        fun sealed(from: Phone, to: Phone): String = com.bluemob.app.mesh.Envelope.seal(MeshRouter.RMSG,
            JSONObject().put("id", "m3").put("to", to.id).put("at", 1).put("x", System.currentTimeMillis() + 100_000).put("c", "x"), from.keys).toString()
    }
}

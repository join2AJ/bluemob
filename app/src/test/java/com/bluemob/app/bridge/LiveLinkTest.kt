package com.bluemob.app.bridge

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The app's live link against the real relay (server/live.js, run with Node): two phones sign in with their device
 * keys, then exchange a call invite, voice and video. Skipped if Node isn't installed.
 */
class LiveLinkTest {
    private var node: Process? = null
    private var port = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun startRelay() {
        val live = listOf(File("../server/live.js"), File("server/live.js")).firstOrNull { it.exists() }?.absoluteFile
        val hasNode = runCatching { ProcessBuilder("node", "--version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false)
        assumeTrue("needs Node and server/live.js", live != null && hasNode)
        val script = "const http=require('http');const {attachLive}=require(${'"'}${live!!.path.replace("\\", "/")}${'"'});" +
            "const s=http.createServer((q,r)=>r.end('ok'));attachLive(s);s.listen(0,'127.0.0.1',()=>console.log('PORT '+s.address().port));"
        node = ProcessBuilder("node", "-e", script).redirectErrorStream(true).start()
        val line = node!!.inputStream.bufferedReader().readLine()
        port = line.removePrefix("PORT ").trim().toInt()
    }

    @After fun stop() { scope.cancel(); node?.destroy() }

    private fun phone(): Pair<LiveLink, DeviceKeys> {
        val keys = DeviceKeys(Crypto.generate())
        return LiveLink({ "http://127.0.0.1:$port" }, keys, scope, { true }) to keys
    }

    @Test fun twoPhonesTalkThroughTheRelay() = runBlocking {
        val (a, aKeys) = phone()
        val (b, bKeys) = phone()
        val texts = LinkedBlockingQueue<Pair<String, String>>()
        val media = LinkedBlockingQueue<Pair<String, ByteArray>>()
        val offline = LinkedBlockingQueue<String>()
        b.onText = { from, data -> texts.add(from to data) }
        b.onBinary = { from, bytes -> media.add(from to bytes) }
        a.onOffline = { to, _ -> offline.add(to) }
        a.start(); b.start()
        withTimeout(15_000) { while (!a.connected.value || !b.connected.value) delay(100) }

        a.sendText(bKeys.nodeId, "{\"t\":\"app\",\"k\":\"call\"}")
        assertEquals(aKeys.nodeId to "{\"t\":\"app\",\"k\":\"call\"}", texts.poll(5, TimeUnit.SECONDS))

        val voice = byteArrayOf('A'.code.toByte(), 0, 1) + ByteArray(480) { it.toByte() }
        a.sendBinary(bKeys.nodeId, voice)
        val got = media.poll(5, TimeUnit.SECONDS)!!
        assertEquals(aKeys.nodeId, got.first)
        assertArrayEquals(voice, got.second)

        a.sendText("00112233aabbccdd", "x")
        assertEquals("00112233aabbccdd", offline.poll(5, TimeUnit.SECONDS))
        a.stop(); b.stop()
    }

    /** A phone with internet carries a friend who has none: the far phone sees them online, and calls reach them through it. */
    @Test fun aGatewayCarriesAFriendWithoutInternet() = runBlocking {
        val (far, _) = phone()
        val (gw, gwKeys) = phone()
        val friend = DeviceKeys(Crypto.generate()).nodeId
        val atGateway = LinkedBlockingQueue<ByteArray>()
        gw.onBinary = { _, bytes -> atGateway.add(bytes) }
        far.start(); gw.start()
        withTimeout(15_000) { while (!far.connected.value || !gw.connected.value) delay(100) }
        gw.carry(listOf(friend))
        delay(300)
        far.askPresence(listOf(friend))
        withTimeout(5_000) { while (far.presence.value.via[friend] != gwKeys.nodeId) delay(50) }
        far.sendBinary(friend, byteArrayOf('E'.code.toByte(), 'A'.code.toByte(), 1, 2, 3))
        val frame = com.bluemob.app.mesh.RelayFrame.parse(atGateway.poll(5, TimeUnit.SECONDS)!!)!!
        assertEquals(friend, frame.dest)
        assertArrayEquals(byteArrayOf('E'.code.toByte(), 'A'.code.toByte(), 1, 2, 3), frame.inner)
        far.stop(); gw.stop()
    }

    @Test fun reconnectsAfterTheLinkDrops() = runBlocking {
        val (a, _) = phone()
        a.start()
        withTimeout(15_000) { while (!a.connected.value) delay(100) }
        a.reconnect()
        withTimeout(20_000) { while (!a.connected.value) delay(100) }
        a.stop()
    }
}

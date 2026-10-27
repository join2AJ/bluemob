package com.bluemob.app.chat

import com.bluemob.app.bot.SkyAnswer
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.data.MessageDao
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.SeenId
import com.bluemob.app.mesh.Handoff
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.MessageLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two phones, A and B, with a fake radio between them. Checks the promises the app makes:
 * messages wait until the person is in range, each one is shown exactly once even when sent
 * twice, and delivered / read receipts come back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryTest {

    internal class FakeDao : MessageDao {
        val rows = MutableStateFlow<List<MessageEntity>>(emptyList())
        private val seen = mutableSetOf<String>()
        override fun observeAll(): Flow<List<MessageEntity>> = rows
        override suspend fun get(id: String) = rows.value.firstOrNull { it.id == id }
        override suspend fun unacknowledgedAll() = rows.value.filter { it.fromMe && (it.status == MessageStatus.PENDING || it.status == MessageStatus.SENT) }
        override suspend fun unread(peer: String) = rows.value.filter { it.peer == peer && !it.fromMe && it.status == MessageStatus.RECEIVED }
        override suspend fun readReceiptsOwedAll() = rows.value.filter { it.peer != SkyBot.NODE_ID && !it.fromMe && it.status == MessageStatus.READ && !it.readReceiptSent }
        override suspend fun count(peer: String) = rows.value.count { it.peer == peer }
        override suspend fun insert(message: MessageEntity): Long {
            if (rows.value.any { it.id == message.id }) return -1
            rows.value = (rows.value + message).sortedBy { it.createdAt }
            return 1
        }
        override suspend fun update(message: MessageEntity) { rows.value = rows.value.map { if (it.id == message.id) message else it } }
        override suspend fun clear() { rows.value = emptyList() }
        override suspend fun deleteChat(peer: String) { rows.value = rows.value.filter { it.peer != peer } }
        override suspend fun all() = rows.value
        override suspend fun filesToSend(peer: String) = rows.value.filter { it.peer == peer && it.fromMe && it.att.isNotEmpty() && it.attState != 3 }
        override suspend fun pendingFiles() = rows.value.filter { it.fromMe && it.att.isNotEmpty() && it.attState != 3 }
        override suspend fun byFile(fid: String) = rows.value.firstOrNull { it.att.contains("\"fid\":\"$fid\"") }
        override suspend fun markSeen(seen: SeenId): Long = if (this.seen.add(seen.id)) 1 else -1
        override suspend fun copiesOf(parent: String) = rows.value.filter { it.parent == parent && it.fromMe && it.hidden }
    }

    /** A radio link between two phones. Copies only get through while [connected]. */
    private class Radio {
        var connected = false
        /** Simulates a link that drops right after a message arrives: its receipts never come back. */
        var loseReceipts = false
        val a = End("A")
        val b = End("B")
        init { a.other = b; b.other = a }

        inner class End(val me: String) : MessageLink {
            lateinit var other: End
            val inbox = MutableSharedFlow<MeshEvent>(extraBufferCapacity = 64)
            var copiesSent = 0
            override val events: SharedFlow<MeshEvent> = inbox
            override fun isConnected(nodeId: String) = connected
            override fun linkName(nodeId: String) = "Bluetooth"
            override fun sendChat(toNodeId: String, messageId: String, text: String, sentAt: Long, att: String?): Handoff {
                if (!connected) return Handoff.Held
                copiesSent++
                other.inbox.tryEmit(MeshEvent.MessageReceived(me, messageId, text, sentAt))
                return Handoff.Direct("Bluetooth")
            }
            override fun sendReceipt(toNodeId: String, messageId: String, read: Boolean): Boolean {
                if (!connected) return false
                if (!loseReceipts) other.inbox.tryEmit(MeshEvent.Receipt(me, messageId, read))
                return true
            }
        }

        fun connect() {
            connected = true
            a.inbox.tryEmit(MeshEvent.PeerConnected("B"))
            b.inbox.tryEmit(MeshEvent.PeerConnected("A"))
        }
    }

    /** Lets all work, including the repositories' background work, run (advanceUntilIdle skips background work). */
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private class Phones(scope: TestScope) {
        val radio = Radio()
        val daoA = FakeDao()
        val daoB = FakeDao()
        val sky: suspend (String, (String) -> Unit) -> SkyAnswer = { _, _ -> SkyAnswer("hi") }
        val groupsA = GroupStore(null)
        val groupsB = GroupStore(null)
        val a = MessageRepository(daoA, radio.a, scope.backgroundScope, sky, groups = groupsA, me = { "A" to "Asha" })
        val b = MessageRepository(daoB, radio.b, scope.backgroundScope, sky, groups = groupsB, me = { "B" to "Bala" })
        fun sentByA() = daoA.rows.value.filter { it.fromMe && it.peer == "B" }
        fun receivedByB() = daoB.rows.value.filter { !it.fromMe && it.peer == "A" }
    }

    @Test
    fun messageWaitsUntilTheyAreInRange() = runTest {
        val p = Phones(this)
        p.a.send("B", "Back at camp tonight?")
        settle()
        assertEquals(MessageStatus.PENDING, p.sentByA().single().status)
        assertTrue(p.receivedByB().isEmpty())

        p.radio.connect()
        settle()
        assertEquals("Back at camp tonight?", p.receivedByB().single().text)
        assertEquals(MessageStatus.DELIVERED, p.sentByA().single().status)
    }

    @Test
    fun aSecondCopyIsDiscardedSoItShowsExactlyOnce() = runTest {
        val p = Phones(this)
        p.radio.connected = true
        p.radio.loseReceipts = true // the link drops before the receipt gets back
        p.a.send("B", "Hello")
        settle()
        assertEquals(MessageStatus.SENT, p.sentByA().single().status)

        // Next time they connect, A sends it again because it never got a receipt.
        p.radio.loseReceipts = false
        p.radio.connect()
        settle()
        assertEquals(2, p.radio.a.copiesSent)
        assertEquals("B shows the message once", 1, p.receivedByB().size)
        assertEquals(MessageStatus.DELIVERED, p.sentByA().single().status)
        assertEquals(2, p.sentByA().single().attempts)
    }

    @Test
    fun readReceiptComesBackWhenTheyOpenTheChat() = runTest {
        val p = Phones(this)
        p.radio.connected = true
        p.a.send("B", "Meet at the stream?")
        settle()
        assertEquals(MessageStatus.DELIVERED, p.sentByA().single().status)

        p.b.openConversation = "A"
        settle()
        assertEquals(MessageStatus.READ, p.sentByA().single().status)
    }

    @Test
    fun readReceiptOwedWhileApartIsSentOnReconnect() = runTest {
        val p = Phones(this)
        p.radio.connected = true
        p.a.send("B", "See you later")
        settle()
        p.radio.connected = false
        p.b.openConversation = "A" // read while out of range
        settle()
        assertEquals(MessageStatus.DELIVERED, p.sentByA().single().status)

        p.radio.connect()
        settle()
        assertEquals(MessageStatus.READ, p.sentByA().single().status)
    }

    @Test
    fun skyAnswersOnThePhoneWithoutTheRadio() = runTest {
        val p = Phones(this)
        p.a.send(SkyBot.NODE_ID, "Hi")
        settle()
        val sky = p.daoA.rows.value.filter { it.peer == SkyBot.NODE_ID }
        assertEquals(MessageStatus.LOCAL, sky.single { it.fromMe }.status)
        assertTrue(sky.any { !it.fromMe && it.text == "hi" })
        assertEquals(0, p.radio.a.copiesSent)
    }

    @Test
    fun groupMessagesRepliesAndReactions() = runTest {
        val p = Phones(this)
        p.radio.connected = true
        // Node IDs in groups are 16 characters; this fake radio calls the phones "A" and "B", so use a group with B only.
        val gid = GroupStore.PREFIX + "trek"
        p.groupsA.put(ChatGroup(gid, "Trek", mapOf("B" to "Bala")))
        p.a.send(gid, "Meet at the bridge")
        settle()
        // A shows it once in the group; the copy that carried it to B is hidden.
        val shown = p.a.messages.value.single { it.peer == gid }
        assertEquals("Meet at the bridge", shown.text)
        assertTrue(p.a.messages.value.none { it.hidden })
        // B learns the group from the message and shows it there, with who wrote it.
        val got = p.daoB.rows.value.single { !it.fromMe && it.peer == gid }
        assertEquals("Meet at the bridge", got.text)
        assertEquals("A", got.sender)
        assertEquals("Trek", p.groupsB.get(gid)!!.name)
        // B's read receipt reaches A's copy, and A's group message shows it.
        p.b.markRead(gid)
        settle()
        assertEquals(MessageStatus.READ, p.a.messages.value.single { it.peer == gid }.status)

        // A reply and a reaction in a one-to-one chat.
        p.a.send("B", "Ready?")
        settle()
        val ready = p.daoB.rows.value.single { it.text == "Ready?" }
        p.b.send("A", "Yes!", ready)
        settle()
        val reply = p.daoA.rows.value.single { it.text == "Yes!" }
        assertEquals("Ready?", Rich.replyParts(reply.replyTo)!!.third)
        p.b.react(ready, "👍")
        settle()
        assertEquals("👍", Rich.reactionsOf(p.daoA.rows.value.single { it.text == "Ready?" }.reactions)["B"])
        assertTrue("reactions aren't shown as messages", p.a.messages.value.none { it.text.startsWith(Rich.MARK) })
    }

    @Test
    fun tripGroupJoinByCodeAndRollCall() = runTest {
        val p = Phones(this)
        p.radio.connected = true
        val gid = p.a.createTrip("Kedarnath trek")!!
        settle()
        assertTrue(p.groupsA.get(gid)!!.trip)
        // B scans A's code: A adds B and tells the group.
        assertEquals("Kedarnath trek", p.a.tripInvite(gid)!!.name)
        p.b.joinTrip(p.a.tripInvite(gid)!!)
        settle()
        assertTrue("B" in p.groupsA.get(gid)!!.members)
        assertTrue(p.groupsB.get(gid)!!.trip)
        assertTrue(p.daoB.rows.value.any { !it.fromMe && it.peer == gid && "joined the trip group" in it.text })
        // A roll call: B's phone is reached directly, and B gets "I'm OK" / "I need help".
        var probe: Pair<String, Int>? = null
        p.a.rollCall(gid) { id, n -> probe = id to n }
        settle()
        val roll = p.a.rollCalls.value[gid]!!
        assertEquals(probe!!.first, roll.messageId)
        assertEquals(1, roll.reached)
        assertTrue(roll.members["B"] is Reach.Direct)
        assertTrue(p.daoB.rows.value.single { !it.fromMe && it.text == RollCall.TEXT }.actions.contains("trip-ok:$gid"))
    }

    @Test
    fun tripAdminSettingsTeamsAndClosing() = runTest {
        val p = Phones(this)
        p.radio.connected = true
        val gid = p.a.createTrip("Group 7", expiresAt = System.currentTimeMillis() + 86_400_000)!!
        p.b.joinTrip(p.a.tripInvite(gid)!!)
        settle()
        assertEquals("A", p.groupsB.get(gid)!!.owner)
        // The admin's settings reach everyone; a member's don't.
        p.a.updateTrip(gid, "⏱ Automatic roll call every 10 min") { it.copy(autoMin = 10) }
        settle()
        assertEquals(10, p.groupsB.get(gid)!!.autoMin)
        p.b.updateTrip(gid, "trying") { it.copy(autoMin = 1, name = "Mine now") }
        settle()
        assertEquals(10, p.groupsA.get(gid)!!.autoMin)
        assertEquals("Group 7", p.groupsA.get(gid)!!.name)
        // A team with B as captain: B sees it, with A in it.
        val team = p.a.createTeam(gid, "Team 1", "B", emptyMap())!!
        settle()
        val bTeam = p.groupsB.get(team)!!
        assertEquals(gid, bTeam.parent)
        assertEquals("B", bTeam.captain)
        assertTrue("A" in bTeam.members)
        assertTrue(bTeam.canManage("B"))
        // Closing reaches everyone.
        p.a.closeTrip(gid)
        settle()
        assertTrue(p.groupsB.get(gid)!!.isClosed(System.currentTimeMillis()))
    }

    @Test
    fun passcodeJoinItineraryAndCheckpoints() = runTest {
        val p = Phones(this)
        p.radio.connected = true
        val gid = p.a.createTrip("Group 3")!!
        p.groupsA.put(p.groupsA.get(gid)!!.copy(advertise = true, passcode = "4821"))
        val day = 86_400_000L
        p.a.setItinerary(gid, listOf(Stop("s2", 2 * day, "Summit", checkpoint = true), Stop("s1", day, "Base camp", "Tents and dinner")))
        settle()
        // A wrong passcode: B is told, and isn't in.
        p.b.requestJoin(gid, "A", "Group 3", "1111")
        settle()
        assertTrue(p.b.joinNotes.value[gid]!!.startsWith("Wrong passcode"))
        assertEquals(null, p.groupsB.get(gid))
        // The right one: B is in, with the itinerary (in time order).
        p.b.requestJoin(gid, "A", "Group 3", "4821")
        settle()
        val g = p.groupsB.get(gid)!!
        assertTrue(g.trip)
        assertEquals(listOf("Base camp", "Summit"), g.itinerary.map { it.title })
        assertTrue("B" in p.groupsA.get(gid)!!.members)
        // B reaches the summit: A sees it.
        p.b.checkIn(gid, "s2")
        settle()
        assertTrue(p.groupsA.get(gid)!!.checkins["s2"]!!.containsKey("B"))
        // Only the admin plans: B's itinerary isn't taken.
        p.b.setItinerary(gid, listOf(Stop("x", 1, "Mine")))
        settle()
        assertEquals(2, p.groupsA.get(gid)!!.itinerary.size)
    }

    @Test
    fun tripCodesOnlyParseWhenWellFormed() {
        val code = TripInvite("g-abc123", "0123456789abcdef", "Family at the mela 🎪").encode()
        assertEquals(TripInvite("g-abc123", "0123456789abcdef", "Family at the mela 🎪"), TripInvite.parse(code))
        assertEquals(null, TripInvite.parse("bluemob:verify:1:xyz"))
        assertEquals(null, TripInvite.parse("bluemob:trip:1:notagroup:0123456789abcdef:eA"))
        assertEquals(null, TripInvite.parse("bluemob:trip:1:g-abc:short:eA"))
    }

    @Test
    fun reachReadsHowEachCopyWasDelivered() {
        val base = MessageEntity("c", "B", true, "x", 1_000, MessageStatus.DELIVERED, deliveredAt = 4_000)
        assertEquals(Reach.Direct(3_000), Reach.of(base.copy(deliveredVia = Reach.via(1, false, "Bluetooth"))))
        assertEquals(Reach.Mesh(2, 3_000), Reach.of(base.copy(deliveredVia = Reach.via(3, false, "Bluetooth"))))
        assertEquals(Reach.Mesh(1, 3_000), Reach.of(base.copy(deliveredVia = Reach.via(2, false, "Bluetooth"))))
        assertEquals(Reach.Internet(3_000), Reach.of(base.copy(deliveredVia = Reach.via(1, true, "Bluetooth"))))
        assertEquals(Reach.Waiting, Reach.of(base.copy(status = MessageStatus.SENT, deliveredAt = null)))
    }

    @Test
    fun richRoundTrip() {
        val r = Rich("hi", group = "g-1", groupName = "Fam", members = mapOf("0123456789abcdef" to "Asha"), groupMsgId = "m-1", replyId = "m-0", replyName = "Bala", replyText = "ok")
        assertEquals(r, Rich.decode(r.encode()))
        val e = Rich(env = """{"t":"room"}""")
        assertEquals(e, Rich.decode(e.encode()))
        assertTrue(e.isControl)
        assertEquals(null, Rich.decode("plain text"))
        assertEquals("", Rich.reactionsOf(Rich.withReaction(Rich.withReaction("", "x", "👍"), "x", ""))["x"].orEmpty())
    }

    @Test
    fun rescueMessagesRideHiddenChatMessages() = runTest {
        val radio = Radio()
        val daoA = FakeDao(); val daoB = FakeDao()
        val got = mutableListOf<String>()
        val sky: suspend (String, (String) -> Unit) -> SkyAnswer = { _, _ -> SkyAnswer("hi") }
        val a = MessageRepository(daoA, radio.a, backgroundScope, sky, me = { "A" to "Asha" })
        val b = MessageRepository(daoB, radio.b, backgroundScope, sky, me = { "B" to "Bala" }, onRoomEnvelope = { got += it })
        a.sendRoomEnvelope("B", """{"t":"room","b":"x"}""")
        settle()
        assertTrue("waits until there's a way to them", got.isEmpty())
        radio.connect()
        settle()
        assertEquals(listOf("""{"t":"room","b":"x"}"""), got)
        assertTrue("never shown as a chat message", b.messages.value.isEmpty() || b.messages.value.none { it.peer == "A" })
        assertTrue(a.messages.value.none { it.peer == "B" })
    }
}

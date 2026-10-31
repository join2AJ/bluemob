package com.bluemob.app.chat

import com.bluemob.app.audit.AuditKind
import com.bluemob.app.bot.SkyAnswer
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.data.MessageDao
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.PathState
import com.bluemob.app.data.SeenId
import com.bluemob.app.mesh.Handoff
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.MeshRouter
import com.bluemob.app.mesh.MessageLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Every chat message, saved on the phone, and the rules that get each one delivered exactly once.
 *
 * - A message gets a unique ID when it's written. Every copy carries that ID.
 * - If the person isn't in range, it waits here. The moment they connect, it goes over Bluetooth / Wi-Fi.
 * - It's re-sent on each new connection until a delivery receipt comes back (a link can drop mid-send).
 * - The receiver remembers every ID it has accepted and discards any second copy, but always answers
 *   with a receipt, so the sender stops re-sending.
 * - When the person opens the chat, a read receipt goes back (now, or the next time you're connected).
 *
 * The internet path (through a bridge and the BlueMob relay) plugs in here once the relay exists.
 */
class MessageRepository(
    private val dao: MessageDao,
    private val mesh: MessageLink,
    private val scope: CoroutineScope,
    /**
     * Sky's answer. With the offline AI it's written a bit at a time: the second argument gets the answer so far, and
     * the chat shows it growing.
     */
    private val sky: suspend (String, (String) -> Unit) -> SkyAnswer,
    /** Writes to the audit trail: (kind, other person's node ID, what happened). */
    private val record: (AuditKind, String, String) -> Unit = { _, _, _ -> },
    /** Called once per new incoming message (not for copies), e.g. to show a notification. */
    private val onIncoming: (peer: String, text: String) -> Unit = { _, _ -> },
    /** A message with a photo, document or voice note was saved (sent or received): its file can move now. */
    private val onAttachment: suspend (MessageEntity) -> Unit = {},
    /** Group chats this phone is in. */
    val groups: GroupStore? = null,
    private val me: () -> Pair<String, String> = { "" to "" },
    /** People the user blocked: their messages are dropped (they still get a receipt, so they stop re-sending). */
    private val blocked: (String) -> Boolean = { false },
    /** A rescue-group message that came inside a hidden chat message (see [Rich.env]). */
    private val onRoomEnvelope: (String) -> Unit = {},
    /** The first receipt for one of our messages (a member's copy of a group message included): (it, hops, over the internet). */
    private val onDelivered: (MessageEntity, Int, Boolean) -> Unit = { _, _, _ -> },
) {
    /** Every message shown in a chat (copies that only carry group messages and reactions are left out). */
    val messages: StateFlow<List<MessageEntity>> = dao.observeAll().map { all -> all.filter { !it.hidden } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val conversations: StateFlow<Map<String, List<MessageEntity>>> =
        messages.map { all -> all.groupBy { it.peer } }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _typing = MutableStateFlow<Set<String>>(emptySet())
    /** Conversations where the other side is "typing" (only Sky, for now). */
    val typing: StateFlow<Set<String>> = _typing.asStateFlow()

    /** Deliveries for one person run one at a time, so a message is never sent twice in one go. */
    private val deliveryLock = Mutex()

    /** The chat on screen: its incoming messages count as read right away. */
    var openConversation: String? = null
        set(value) {
            field = value
            if (value != null) markRead(value)
        }

    init {
        scope.launch {
            if (dao.count(SkyBot.NODE_ID) == 0) {
                val now = System.currentTimeMillis()
                SkyBot.greeting.forEachIndexed { i, text ->
                    dao.insert(MessageEntity(newId(), SkyBot.NODE_ID, false, text, now + i, MessageStatus.RECEIVED))
                }
            }
        }
        scope.launch { mesh.events.collect { handle(it) } }
    }

    fun send(peer: String, text: String, replyTo: MessageEntity? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        scope.launch {
            val now = System.currentTimeMillis()
            if (peer == SkyBot.NODE_ID) {
                talkToSky(clean, now)
                return@launch
            }
            val reply = replyTo?.let { Rich.replyField(it.id, if (it.fromMe) me().second else it.senderName.ifBlank { "" }, it.text) } ?: ""
            if (GroupStore.isGroup(peer)) { sendToGroup(peer, clean, reply, now); return@launch }
            val inRange = mesh.isConnected(peer)
            val id = newId()
            record(AuditKind.MESSAGE, peer, "Message $id written to {name}: \"${clean.take(80)}\"")
            dao.insert(
                MessageEntity(
                    id = id, peer = peer, fromMe = true, text = clean, createdAt = now, status = MessageStatus.PENDING, replyTo = reply,
                    directState = if (inRange) PathState.TRYING else PathState.WAITING,
                    internetState = PathState.UNAVAILABLE,
                    history = event(now, "Written on your phone") +
                        event(now, if (inRange) "In range. Sending directly" else "Not in range. Looking for a way to reach them through phones nearby"),
                )
            )
            deliverAll()
        }
    }

    /**
     * A group message: shown once in the group chat, and carried to each member as its own encrypted message (so it
     * travels every way a normal message does, and each member's receipts come back).
     */
    private suspend fun sendToGroup(gid: String, text: String, reply: String, now: Long, rollCall: Boolean = false, extra: (Rich) -> Rich = { it }): Pair<String, Int>? {
        val g = groups?.get(gid) ?: return null
        val (myId, myName) = me()
        val id = newId()
        val members = g.members + (myId to myName)
        val others = members.keys.filter { it != myId }
        dao.insert(MessageEntity(id = id, peer = gid, fromMe = true, text = text, createdAt = now, status = if (others.isEmpty()) MessageStatus.LOCAL else MessageStatus.SENT,
            replyTo = reply, sender = myId, senderName = myName,
            history = event(now, "Written on your phone") + event(now, "Sent to ${others.size} ${if (others.size == 1) "member" else "members"} of ${g.name}, each end-to-end encrypted")))
        val r = Rich.replyParts(reply)
        val wire = Rich(text, group = gid, groupName = g.name, members = members, groupMsgId = id, replyId = r?.first, replyName = r?.second.orEmpty(), replyText = r?.third.orEmpty(),
            trip = g.trip, rollCall = rollCall, meta = if (g.trip) g.meta else null).let(extra).encode()
        others.forEach { to -> dao.insert(carrier(to, wire, id, now)) }
        record(AuditKind.MESSAGE, gid, "Group message $id written to ${g.name} (${others.size} members): \"${text.take(80)}\"")
        deliverAll()
        return id to others.size
    }

    private fun carrier(to: String, wire: String, parent: String, now: Long) = MessageEntity(
        id = newId(), peer = to, fromMe = true, text = wire, createdAt = now, status = MessageStatus.PENDING,
        directState = PathState.WAITING, internetState = PathState.UNAVAILABLE, hidden = true, parent = parent,
    )

    /** Sends a rescue-group message to [to] as a hidden chat message, so it's kept and retried until it arrives. */
    fun sendRoomEnvelope(to: String, envelope: String) {
        scope.launch {
            dao.insert(carrier(to, Rich(env = envelope).encode(), "", System.currentTimeMillis()))
            deliverAll()
        }
    }

    /** Starts a group with [members] (node ID → name). Everyone hears about it with the first message. */
    fun createGroup(name: String, members: Map<String, String>): String? {
        val store = groups ?: return null
        val (myId, myName) = me()
        val gid = GroupStore.PREFIX + UUID.randomUUID().toString().replace("-", "").take(12)
        store.put(ChatGroup(gid, name.trim().take(40), members.filterKeys { it != myId }.entries.take(Rich.MAX_MEMBERS - 1).associate { it.key to it.value }))
        send(gid, "👋 $myName created the group \"${name.trim().take(40)}\"")
        return gid
    }

    /** Starts a trip group with only us in it; the others join by scanning its QR code ([tripInvite]). */
    fun createTrip(name: String, expiresAt: Long = 0): String? {
        val store = groups ?: return null
        val (_, myName) = me()
        val gid = GroupStore.PREFIX + UUID.randomUUID().toString().replace("-", "").take(12)
        store.put(ChatGroup(gid, name.trim().take(40).ifBlank { "Trip" }, emptyMap(), trip = true, owner = me().first, expiresAt = expiresAt))
        send(gid, "🧭 $myName started the trip group \"${name.trim().take(40).ifBlank { "Trip" }}\". Scan its QR code to join")
        return gid
    }

    /** The QR code that lets someone join trip group [gid] through us. */
    fun tripInvite(gid: String): TripInvite? = groups?.get(gid)?.takeIf { it.trip && !it.isTeam && !it.isClosed(System.currentTimeMillis()) }
        ?.let { TripInvite(it.id, me().first, it.name, it.owner, it.expiresAt, it.createdAt) }

    /**
     * Joins a trip group from its QR code: we ask whoever showed it to add us; they tell everyone, and the reply
     * brings us the whole member list. Returns the group's ID (already open even before the answer comes).
     */
    fun joinTrip(invite: TripInvite): String? {
        val store = groups ?: return null
        val (myId, myName) = me()
        if (invite.inviterId == myId) return null
        val known = store.get(invite.groupId)
        store.put((known ?: ChatGroup(invite.groupId, invite.name, emptyMap(), invite.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(), trip = true,
            owner = invite.owner, expiresAt = invite.expiresAt)).let { it.copy(members = it.members + (invite.inviterId to (it.members[invite.inviterId] ?: ""))) })
        if (known != null && myId !in known.members) return invite.groupId
        scope.launch {
            val now = System.currentTimeMillis()
            val wire = Rich("$myName joined", group = invite.groupId, groupName = invite.name, members = mapOf(myId to myName), trip = true, join = true).encode()
            dao.insert(MessageEntity(id = newId(), peer = invite.groupId, fromMe = true, text = "👋 You joined \"${invite.name}\". Waiting for the group to answer", createdAt = now,
                status = MessageStatus.LOCAL, sender = myId, senderName = myName))
            dao.insert(carrier(invite.inviterId, wire, "", now))
            deliverAll()
        }
        return invite.groupId
    }

    /** Asks every phone in trip group [gid] to answer: their delivery receipts show who can be reached, and how. */
    fun rollCall(gid: String, onSent: (messageId: String, members: Int) -> Unit = { _, _ -> }) {
        if (groups?.get(gid)?.trip != true) return
        scope.launch { sendToGroup(gid, RollCall.TEXT, "", System.currentTimeMillis(), rollCall = true)?.let { (id, n) -> onSent(id, n) } }
    }

    /**
     * Changes a trip group's settings (the admin, or a team's captain for roll calls) and tells everyone with [note]:
     * the message carries the new settings, which the other phones take from the admin.
     */
    fun updateTrip(gid: String, note: String, change: (ChatGroup) -> ChatGroup) {
        val store = groups ?: return
        val g = store.get(gid)?.takeIf { it.trip } ?: return
        store.put(change(g))
        send(gid, note)
    }

    /**
     * A while after the trip ended, the record of who reached which checkpoint, and when, is erased from this phone.
     * The group, its chat and its itinerary stay, so people can keep in touch.
     */
    fun forgetCheckIns(gid: String) {
        val g = groups?.get(gid)?.takeIf { it.trip && it.checkins.isNotEmpty() } ?: return
        groups.put(g.copy(checkins = emptyMap()))
        record(AuditKind.APP, gid, "Check-ins of trip group \"${g.name}\" erased from this phone, 30 days after the trip ended")
    }

    /** This phone asked the admin whether to close [gid]; don't ask again (unless the end time changes). */
    fun markAskedToClose(gid: String) { groups?.get(gid)?.let { groups.put(it.copy(askedToClose = true)) } }

    /** Ends the trip now: roll calls, check-ins and joining stop; the group and its chat stay. */
    fun closeTrip(gid: String) = updateTrip(gid, "🏁 ${me().second} ended the trip. The group stays, so we can keep in touch") { it.copy(closedAt = System.currentTimeMillis()) }

    /**
     * Starts a team in trip group [mainGid]: its own trip group with [captain] and [members] (and us, the admin), so
     * the admin sees every team and each captain runs their own.
     */
    fun createTeam(mainGid: String, name: String, captain: String, members: Map<String, String>): String? {
        val store = groups ?: return null
        val main = store.get(mainGid)?.takeIf { it.trip && !it.isTeam } ?: return null
        val (myId, myName) = me()
        val gid = GroupStore.PREFIX + UUID.randomUUID().toString().replace("-", "").take(12)
        val people = (members + (captain to (main.members[captain] ?: members[captain].orEmpty()))).filterKeys { it != myId }
        store.put(ChatGroup(gid, name.trim().take(40).ifBlank { "Team" }, people.entries.take(Rich.MAX_MEMBERS - 1).associate { it.key to it.value }, trip = true,
            owner = main.owner.ifBlank { myId }, parent = mainGid, captain = captain, expiresAt = main.expiresAt))
        val captainName = if (captain == myId) myName else people[captain].orEmpty().ifBlank { "the captain" }
        send(gid, "👥 Team \"${name.trim().take(40)}\" started. Captain: $captainName")
        send(mainGid, "👥 New team \"${name.trim().take(40)}\" with captain $captainName (${people.size + 1} people)")
        return gid
    }

    /** Adds people to a trip group or team (the admin or the captain); they hear about it with the message. */
    fun addToTrip(gid: String, people: Map<String, String>) {
        val g = groups?.get(gid) ?: return
        val fresh = people.filterKeys { it != me().first && it !in g.members }
        if (fresh.isEmpty()) return
        updateTrip(gid, "➕ ${me().second} added ${fresh.values.joinToString { it.ifBlank { "someone" } }}") { it.copy(members = (it.members + fresh).entries.take(Rich.MAX_MEMBERS - 1).associate { e -> e.key to e.value }) }
    }

    /** Sets trip group [gid]'s itinerary (the admin; a team's captain for the team) and sends it to everyone. */
    fun setItinerary(gid: String, stops: List<Stop>) {
        val store = groups ?: return
        val g = store.get(gid)?.takeIf { it.trip && it.canPlan(me().first) } ?: return
        val now = System.currentTimeMillis()
        val plan = stops.sortedBy { it.at }.take(Stop.MAX)
        store.put(g.copy(itinerary = plan, itineraryAt = now))
        val cps = plan.count { it.checkpoint }
        scope.launch {
            sendToGroup(gid, "🗺 Itinerary updated: ${plan.size} stop${if (plan.size == 1) "" else "s"}" + (if (cps > 0) ", $cps checkpoint${if (cps == 1) "" else "s"}" else ""), "", now) {
                it.copy(itinerary = plan, itineraryAt = now)
            }
        }
    }

    /** Sets buddy pairs (each pair both ways) and sends them to everyone. Whoever plans the group (admin; captain for a team). */
    fun setBuddies(gid: String, pairs: List<Pair<String, String>>) {
        val store = groups ?: return
        val g = store.get(gid)?.takeIf { it.trip && it.canPlan(me().first) } ?: return
        val now = System.currentTimeMillis()
        val map = pairs.flatMap { (a, b) -> listOf(a to b, b to a) }.toMap()
        store.put(g.copy(buddies = map, buddiesAt = now))
        scope.launch {
            sendToGroup(gid, if (pairs.isEmpty()) "🤝 Buddy pairs cleared" else "🤝 Buddy pairs set: ${pairs.size}. Stay with your buddy; your phone tells you if you drift apart", "", now) {
                it.copy(buddies = map, buddiesAt = now)
            }
        }
    }

    /** An announcement everyone is asked to acknowledge ("Got it"); the admin or a captain. */
    fun announce(gid: String, text: String) {
        val g = groups?.get(gid)?.takeIf { it.trip && it.canManage(me().first) } ?: return
        val clean = text.trim().take(1000).ifBlank { return }
        scope.launch {
            val now = System.currentTimeMillis()
            val (id, _) = sendToGroup(gid, "📣 $clean", "", now) { it.copy(announce = true) } ?: return@launch
            groups.get(gid)?.let { cur -> groups.put(cur.copy(announcements = (cur.announcements + Announcement(id, clean, now, me().first)).takeLast(Announcement.KEEP))) }
            record(AuditKind.MESSAGE, gid, "Announcement $id to ${g.name}: \"${clean.take(80)}\"")
        }
    }

    /** "Got it" to an announcement: kept here, and told to whoever announced it. */
    fun ack(gid: String, annId: String) {
        val store = groups ?: return
        val g = store.get(gid) ?: return
        val ann = g.announcements.firstOrNull { it.id == annId } ?: return
        val (myId, _) = me()
        if (g.acks[annId]?.containsKey(myId) == true) return
        val now = System.currentTimeMillis()
        store.put(g.copy(acks = g.acks + (annId to (g.acks[annId].orEmpty() + (myId to now)))))
        if (ann.by == myId) return
        scope.launch {
            dao.insert(carrier(ann.by, Rich(group = gid, groupName = g.name, ack = annId).encode(), "", now))
            deliverAll()
        }
    }

    /** "I reached this checkpoint": saved here, and told to the group (it gets there when the group is in reach). */
    fun checkIn(gid: String, stopId: String, auto: Boolean = false) {
        val store = groups ?: return
        val g = store.get(gid) ?: return
        val stop = g.itinerary.firstOrNull { it.id == stopId } ?: return
        val (myId, myName) = me()
        if (g.checkins[stopId]?.containsKey(myId) == true) return
        val now = System.currentTimeMillis()
        store.put(g.copy(checkins = g.checkins + (stopId to (g.checkins[stopId].orEmpty() + (myId to now)))))
        scope.launch { sendToGroup(gid, "📍 $myName reached ${stop.title}" + if (auto) " (checked in by location)" else "", "", now) { it.copy(checkIn = stopId, checkInAt = now) } }
    }

    private val _joinNotes = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Joining by passcode: what the admin's phone answered, by group (only when it said no). */
    val joinNotes: StateFlow<Map<String, String>> = _joinNotes.asStateFlow()

    /** Asks the admin of a group shown nearby to let us in with [passcode]. The group appears when they do. */
    fun requestJoin(gid: String, owner: String, name: String, passcode: String) {
        val (myId, myName) = me()
        if (owner == myId) return
        _joinNotes.update { it - gid }
        scope.launch {
            val wire = Rich("$myName asks to join", group = gid, groupName = name, members = mapOf(myId to myName), trip = true, join = true, passcode = passcode.trim()).encode()
            dao.insert(carrier(owner, wire, "", System.currentTimeMillis()))
            deliverAll()
        }
    }

    /** Wrong passcodes for one group, from any phones: after 20 in an hour, the group stops taking passcodes. */
    private val groupWrong = HashMap<String, MutableList<Long>>()
    private fun groupTries(gid: String, now: Long, count: Boolean): Boolean {
        val l = groupWrong.getOrPut(gid) { mutableListOf() }
        l.removeAll { now - it > 3_600_000 }
        if (count) l += now
        return l.size < 20
    }

    /** Wrong passcodes: at most 5 tries an hour from one phone. */
    private val tries = HashMap<String, MutableList<Long>>()
    private fun tryPasscode(from: String, now: Long): Boolean {
        val l = tries.getOrPut(from) { mutableListOf() }
        l.removeAll { now - it > 3_600_000 }
        l += now
        return l.size <= 5
    }

    /** A captain's team roll call, summed up in the team chat, so the admin (in every team) sees it when back in reach. */
    fun postRollSummary(gid: String, roll: RollCall, names: (String) -> String) {
        val missing = roll.members.filterValues { it == Reach.Waiting }.keys.map(names)
        send(gid, "📋 Roll call: ${roll.reached} of ${roll.members.size} reached" + if (missing.isEmpty()) ". Everyone's fine" else ". Not reached: ${missing.joinToString()}")
    }

    /** The latest roll call of each trip group. */
    val rollCalls: StateFlow<Map<String, RollCall>> =
        kotlinx.coroutines.flow.combine(dao.observeAll(), groups?.groups ?: MutableStateFlow(emptyMap())) { all, gs ->
            RollCall.latest(all, gs.values.filter { it.trip }.map { it.id })
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Leaves a group: the others are told, and the chat goes from this phone. */
    fun leaveGroup(gid: String) {
        val g = groups?.get(gid) ?: return
        scope.launch {
            val (myId, myName) = me()
            val now = System.currentTimeMillis()
            val wire = Rich("$myName left the group", group = gid, groupName = g.name, members = g.members, leave = true).encode()
            g.members.keys.filter { it != myId }.forEach { dao.insert(carrier(it, wire, "", now)) }
            groups.remove(gid)
            dao.deleteChat(gid)
            deliverAll()
        }
    }

    /** Reacts to a message with an emoji ("" takes our reaction back). */
    fun react(target: MessageEntity, emoji: String) {
        scope.launch {
            val (myId, _) = me()
            val now = System.currentTimeMillis()
            dao.get(target.id)?.let { dao.update(it.copy(reactions = Rich.withReaction(it.reactions, myId, emoji))) }
            if (target.peer == SkyBot.NODE_ID) return@launch
            val g = if (GroupStore.isGroup(target.peer)) groups?.get(target.peer) else null
            val wire = Rich(react = emoji, reactTo = target.id, group = g?.id, groupName = g?.name.orEmpty(), members = g?.members.orEmpty()).encode()
            val to = g?.members?.keys?.filter { it != myId } ?: listOf(target.peer)
            to.forEach { dao.insert(carrier(it, wire, "", now)) }
            deliverAll()
        }
    }

    /**
     * Sends a photo, document or voice note. [att] holds its details (and the file's key); the encrypted file at
     * [path] goes straight to them when they're in range.
     */
    fun sendAttachment(peer: String, att: com.bluemob.app.files.Attachment, path: String, caption: String = "") {
        scope.launch {
            val now = System.currentTimeMillis()
            val id = newId()
            val inRange = mesh.isConnected(peer)
            record(AuditKind.MESSAGE, peer, "${att.kind.label} $id (${com.bluemob.app.files.Attachment.sizeText(att.size)}) sent to {name}")
            val m = MessageEntity(
                id = id, peer = peer, fromMe = true, text = caption.trim().ifBlank { att.fallbackText() }, createdAt = now, status = MessageStatus.PENDING,
                directState = if (inRange) PathState.TRYING else PathState.WAITING, internetState = PathState.UNAVAILABLE,
                history = event(now, "${att.kind.label} prepared and encrypted on your phone") +
                    event(now, if (inRange) "In range. Sending directly" else "Not in range. The message goes through the mesh; the file goes when you meet"),
                att = att.toJson(), attPath = path, attState = com.bluemob.app.files.AttState.WAITING,
            )
            dao.insert(m)
            deliverAll()
            onAttachment(m)
        }
    }

    /** Marks everything from [peer] as read and tells them, if we can. */
    fun markRead(peer: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            dao.unread(peer).forEach { m ->
                val told = peer != SkyBot.NODE_ID && receiptFor(m)
                dao.update(m.copy(status = MessageStatus.READ, readAt = now, readReceiptSent = told || peer == SkyBot.NODE_ID))
            }
        }
    }

    /** Deletes one chat from this phone (not Sky: Sky's chat is always there). Returns its attachment files to delete. */
    suspend fun deleteChat(peer: String): List<String> {
        if (peer == SkyBot.NODE_ID) return emptyList()
        val files = dao.all().filter { it.peer == peer }.mapNotNull { it.attPath }
        dao.deleteChat(peer)
        return files
    }

    /** Deletes one message from this phone only (others keep their copy). Returns its attachment file to delete, if any. */
    suspend fun deleteForMe(id: String): String? {
        val m = dao.all().firstOrNull { it.id == id } ?: return null
        dao.deleteOne(id)
        return m.attPath
    }

    fun clearAll() = scope.launch { dao.clear() }

    /** Read receipt for an incoming message: in a group it goes to whoever wrote it, for their copy. */
    private fun receiptFor(m: MessageEntity): Boolean =
        if (GroupStore.isGroup(m.peer)) m.sender.isNotBlank() && m.parent.isNotBlank() && mesh.sendReceipt(m.sender, m.parent, read = true)
        else mesh.sendReceipt(m.peer, m.id, read = true)

    private suspend fun handle(event: MeshEvent) {
        when (event) {
            // Any new phone in range might be them, or someone who can carry messages toward them.
            is MeshEvent.PeerConnected, is MeshEvent.KeyLearned, MeshEvent.RouteAvailable -> deliverAll()
            is MeshEvent.MessageReceived -> receive(event)
            is MeshEvent.Receipt -> receipt(event)
            else -> Unit
        }
    }

    /** Offers every message still waiting for a receipt to the mesh, plus read receipts we owe. */
    private suspend fun deliverAll() = deliveryLock.withLock {
        val now = System.currentTimeMillis()
        dao.unacknowledgedAll().forEach { m ->
            // A group message is carried by its copies, one per member.
            if (GroupStore.isGroup(m.peer)) return@forEach
            // A reply carries what it answers.
            val wire = if (!m.hidden && m.replyTo.isNotBlank()) Rich.replyParts(m.replyTo)?.let { (id, n, q) -> Rich(m.text, replyId = id, replyName = n, replyText = q).encode() } ?: m.text else m.text
            when (val h = mesh.sendChat(m.peer, m.id, wire, m.createdAt, m.att.ifBlank { null })) {
                is Handoff.Direct -> {
                    val note = if (m.attempts == 0) "Sent over ${h.link}" else "Sent again over ${h.link} (attempt ${m.attempts + 1}): no receipt came back last time"
                    dao.update(m.copy(status = MessageStatus.SENT, directState = PathState.TRYING, attempts = m.attempts + 1, history = m.history + event(now, note)))
                }
                is Handoff.Carried -> {
                    val people = h.via.filter { it != MeshRouter.INTERNET_NAME }
                    val notes = buildList {
                        if (people.isNotEmpty()) add("Handed to ${people.joinToString(" and ")} to carry toward them. It's encrypted: carriers can't read it")
                        if (MeshRouter.INTERNET_NAME in h.via) add("Sent to the BlueMob relay over the internet. It waits there, encrypted, until they connect")
                    }
                    dao.update(m.copy(status = MessageStatus.SENT, directState = PathState.TRYING,
                        internetState = if (MeshRouter.INTERNET_NAME in h.via) PathState.TRYING else m.internetState,
                        history = m.history + notes.joinToString("") { event(now, it) }))
                }
                Handoff.NeedsKey -> if (!m.history.contains(KEY_NOTE)) dao.update(m.copy(history = m.history + event(now, KEY_NOTE)))
                Handoff.Held -> Unit
            }
        }
        dao.readReceiptsOwedAll().forEach { m ->
            if (receiptFor(m)) dao.update(m.copy(readReceiptSent = true))
        }
    }

    private suspend fun receive(e: MeshEvent.MessageReceived) {
        val now = System.currentTimeMillis()
        val isNew = dao.markSeen(SeenId(e.messageId, now)) != -1L
        // Always answer with a receipt: if our first one was lost, the sender is still re-sending.
        mesh.sendReceipt(e.fromNodeId, e.messageId, read = false)
        if (!isNew) return // A copy we already have: discard it.
        if (blocked(e.fromNodeId)) return
        Rich.decode(e.text)?.let { rich -> receiveRich(e, rich, now); return }
        if (openConversation != e.fromNodeId) onIncoming(e.fromNodeId, com.bluemob.app.files.Attachment.fromJson(e.att)?.let { "${it.kind.emoji} ${it.kind.label}" } ?: e.text)
        record(AuditKind.MESSAGE, e.fromNodeId, "Message ${e.messageId} received from {name} over ${mesh.linkName(e.fromNodeId)}: \"${e.text.take(80)}\"")
        val open = openConversation == e.fromNodeId
        val readNow = open && mesh.sendReceipt(e.fromNodeId, e.messageId, read = true)
        val att = com.bluemob.app.files.Attachment.fromJson(e.att)
        val saved = MessageEntity(
                id = e.messageId, peer = e.fromNodeId, fromMe = false, text = if (att != null) "" else e.text, createdAt = e.sentAt,
                status = if (open) MessageStatus.READ else MessageStatus.RECEIVED,
                readAt = if (open) now else null, readReceiptSent = readNow,
                history = event(now, if (e.viaInternet) "Received over the internet, through the BlueMob relay. End-to-end encrypted"
                    else if (e.hops <= 1) "Received over ${mesh.linkName(e.fromNodeId)}"
                    else "Received over the mesh: passed on by ${e.hops - 1} phone${if (e.hops > 2) "s" else ""}. End-to-end encrypted"),
                att = att?.toJson() ?: "", attState = if (att != null) com.bluemob.app.files.AttState.WAITING else 0,
            )
        dao.insert(saved)
        if (att != null) onAttachment(saved)
    }

    /** A group message, a reply or a reaction. */
    private suspend fun receiveRich(e: MeshEvent.MessageReceived, rich: Rich, now: Long) {
        rich.env?.let { onRoomEnvelope(it); return }
        val (myId, _) = me()
        val gid = rich.group?.takeIf { GroupStore.isGroup(it) }
        // Keep the group's name and members up to date (whoever writes last knows best).
        if (gid != null && groups != null && rich.join) {
            // Someone scanned our trip group's code: add them and tell everyone (the message carries the new list).
            val known = groups.get(gid) ?: return
            if (!known.trip || known.isTeam) return
            // From a group shown nearby: only the admin's phone checks the passcode, and only a few tries an hour.
            if (rich.passcode != null) {
                val why = when {
                    !known.isOwner(myId) -> return
                    !tryPasscode(e.fromNodeId, now) -> "Too many wrong passcodes. Try again in an hour."
                    !groupTries(gid, now, count = false) -> "This group isn't taking passcodes right now. Ask the admin for its code."
                    !known.advertise || known.passcode.isBlank() -> "This group isn't taking passcodes right now. Ask the admin for its code."
                    rich.passcode != known.passcode -> "Wrong passcode. Ask the admin again.".also {
                        // Many wrong tries for one group (from any phones): stop taking passcodes, and tell the admin.
                        if (!groupTries(gid, now, count = true)) {
                            groups.put(known.copy(advertise = false))
                            dao.insert(MessageEntity(id = newId(), peer = gid, fromMe = true, createdAt = now, status = MessageStatus.LOCAL, sender = myId,
                                text = "🔒 Joining by passcode is paused: too many wrong passcodes in an hour. Turn \"Show to people nearby\" on again; it gets a new passcode."))
                        }
                    }
                    else -> null
                }
                if (why != null || known.isClosed(now) || known.members.size >= Rich.MAX_MEMBERS - 1) {
                    dao.insert(carrier(e.fromNodeId, Rich(group = gid, groupName = known.name, denied = why ?: "This group is closed or full.").encode(), "", now))
                    deliverAll()
                    return
                }
            } else if (known.isClosed(now) || known.members.size >= Rich.MAX_MEMBERS - 1) return
            val name = rich.members[e.fromNodeId].orEmpty().ifBlank { "Someone" }
            val isNew = e.fromNodeId !in known.members
            groups.put(known.copy(members = known.members + (e.fromNodeId to name)))
            // The message carries the member list, and the itinerary when we're the one who plans it.
            val withPlan: (Rich) -> Rich = { r -> if (known.canPlan(myId) && known.itinerary.isNotEmpty()) r.copy(itinerary = known.itinerary, itineraryAt = known.itineraryAt) else r }
            sendToGroup(gid, if (isNew) "👋 $name joined the trip group" else "👋 $name is back in the trip group", "", now, extra = withPlan)
            return
        }
        if (gid != null && groups != null && rich.ack != null) {
            groups.get(gid)?.let { g -> if (g.announcements.any { it.id == rich.ack && it.by == myId })
                groups.put(g.copy(acks = g.acks + (rich.ack to (g.acks[rich.ack].orEmpty() + (e.fromNodeId to now))))) }
            return
        }
        if (gid != null && groups != null && rich.denied != null) {
            _joinNotes.update { it + (gid to rich.denied) }
            return
        }
        if (gid != null && groups != null) {
            val known = groups.get(gid)
            if (rich.leave) {
                known?.let { groups.put(it.copy(members = it.members - e.fromNodeId)) }
            } else if (rich.trip) {
                // A trip group only grows by joining; names are filled in as they arrive; settings come from its admin.
                groups.put((known ?: ChatGroup(gid, rich.groupName.ifBlank { "Trip" }, mapOf(e.fromNodeId to ""), now, trip = true))
                    .merge(e.fromNodeId, rich.groupName, rich.members - myId, rich.meta))
            } else if (rich.members.isNotEmpty()) {
                groups.put(ChatGroup(gid, rich.groupName.ifBlank { known?.name ?: "Group" }, rich.members - myId, known?.createdAt ?: now))
            } else if (known == null) {
                groups.put(ChatGroup(gid, rich.groupName.ifBlank { "Group" }, mapOf(e.fromNodeId to ""), now, trip = rich.trip, owner = rich.meta?.owner.orEmpty()))
            }
        }
        if (gid != null && groups != null) groups.get(gid)?.takeIf { it.trip }?.let { g ->
            var changed = g
            // A newer itinerary, from whoever plans this group.
            rich.itinerary?.let { plan -> if (g.canPlan(e.fromNodeId) && rich.itineraryAt > g.itineraryAt) changed = changed.copy(itinerary = plan, itineraryAt = rich.itineraryAt) }
            rich.buddies?.let { b -> if (g.canPlan(e.fromNodeId) && rich.buddiesAt > g.buddiesAt) changed = changed.copy(buddies = b, buddiesAt = rich.buddiesAt) }
            // An announcement from the admin or a captain: kept, so "Got it" can be tapped and counted.
            if (rich.announce && g.canManage(e.fromNodeId)) {
                val id = rich.groupMsgId ?: e.messageId
                if (changed.announcements.none { it.id == id })
                    changed = changed.copy(announcements = (changed.announcements + Announcement(id, rich.text.removePrefix("📣 ").trim(), e.sentAt, e.fromNodeId)).takeLast(Announcement.KEEP))
            }
            // A member reached a checkpoint (the time is theirs, but never in the future).
            rich.checkIn?.let { stop ->
                val at = rich.checkInAt.takeIf { it in 1..(now + 5 * 60_000) } ?: now
                changed = changed.copy(checkins = changed.checkins + (stop to (changed.checkins[stop].orEmpty() + (e.fromNodeId to at))))
            }
            if (changed != g) groups.put(changed)
        }
        if (rich.react != null) {
            val target = rich.reactTo?.let { dao.get(it) } ?: return
            // Only someone in that chat can react to its messages.
            if (target.peer != e.fromNodeId && target.peer != gid) return
            dao.update(target.copy(reactions = Rich.withReaction(target.reactions, e.fromNodeId, rich.react)))
            return
        }
        val peer = gid ?: e.fromNodeId
        val senderName = rich.members[e.fromNodeId].orEmpty()
        if (rich.leave) {
            dao.insert(MessageEntity(id = e.messageId, peer = peer, fromMe = false, text = "🚪 ${senderName.ifBlank { "Someone" }} left the group", createdAt = e.sentAt,
                status = MessageStatus.READ, readAt = now, readReceiptSent = true, sender = e.fromNodeId, senderName = senderName))
            return
        }
        val open = openConversation == peer
        if (!open) onIncoming(peer, if (gid != null) "${senderName.ifBlank { "Someone" }}: ${rich.text}" else rich.text)
        record(AuditKind.MESSAGE, e.fromNodeId, "Message ${e.messageId} received from {name}" + (if (gid != null) " in group ${rich.groupName}" else "") + ": \"${rich.text.take(80)}\"")
        val readNow = open && mesh.sendReceipt(e.fromNodeId, e.messageId, read = true)
        dao.insert(MessageEntity(
            id = if (gid != null) rich.groupMsgId ?: e.messageId else e.messageId, peer = peer, fromMe = false, text = rich.text, createdAt = e.sentAt,
            status = if (open) MessageStatus.READ else MessageStatus.RECEIVED, readAt = if (open) now else null, readReceiptSent = readNow,
            history = event(now, if (e.viaInternet) "Received over the internet, through the BlueMob relay. End-to-end encrypted" else "Received over ${mesh.linkName(e.fromNodeId)}"),
            sender = if (gid != null) e.fromNodeId else "", senderName = senderName,
            replyTo = rich.replyId?.let { Rich.replyField(it, rich.replyName, rich.replyText) } ?: "",
            parent = if (gid != null) e.messageId else "",
            actions = if (gid != null && rich.rollCall) "✅ I'm OK|trip-ok:$gid\n🆘 I need help|sos"
                else if (gid != null && rich.announce) "👍 Got it|ack:$gid:${rich.groupMsgId ?: e.messageId}" else "",
        ))
    }

    /** A member's receipt for their copy of our group message: the group message shows the best of them. */
    private suspend fun updateGroupStatus(parentId: String, now: Long) {
        val visible = dao.get(parentId) ?: return
        val copies = dao.copiesOf(parentId)
        if (copies.isEmpty()) return
        val read = copies.count { it.status == MessageStatus.READ }
        val delivered = copies.count { it.status == MessageStatus.DELIVERED || it.status == MessageStatus.READ }
        val status = when { read == copies.size -> MessageStatus.READ; delivered > 0 -> MessageStatus.DELIVERED; else -> MessageStatus.SENT }
        val note = "Delivered to $delivered of ${copies.size}, read by $read"
        if (status != visible.status || !visible.history.endsWith("$note\n")) dao.update(visible.copy(status = status,
            deliveredAt = visible.deliveredAt ?: if (delivered > 0) now else null, readAt = if (status == MessageStatus.READ) now else visible.readAt,
            history = visible.history + event(now, note)))
    }

    private suspend fun receipt(e: MeshEvent.Receipt) {
        val m = dao.get(e.messageId) ?: return
        if (!m.fromMe || m.peer != e.fromNodeId) return
        val now = System.currentTimeMillis()
        if (m.deliveredAt == null) onDelivered(m.copy(deliveredAt = now), e.hops, e.viaInternet)
        val link = m.deliveredVia ?: Reach.via(e.hops, e.viaInternet, mesh.linkName(e.fromNodeId))
        val delivered = m.copy(
            deliveredAt = m.deliveredAt ?: now, deliveredVia = link,
            // The path that delivered first is marked; the other one is no longer needed.
            directState = if (m.deliveredAt == null && e.viaInternet) (if (m.directState == PathState.DELIVERED) m.directState else PathState.CANCELLED) else if (m.deliveredAt == null) PathState.DELIVERED else m.directState,
            internetState = if (m.deliveredAt == null && e.viaInternet) PathState.DELIVERED
                else if (m.internetState == PathState.WAITING || m.internetState == PathState.TRYING) PathState.CANCELLED else m.internetState,
        )
        when {
            e.read && m.status != MessageStatus.READ -> {
                record(AuditKind.RECEIPT, m.peer, "Read receipt for ${m.id} from {name} over $link")
                dao.update(delivered.copy(status = MessageStatus.READ, readAt = now,
                    history = delivered.history + (if (m.deliveredAt == null) event(now, "Delivered over $link") else "") + event(now, "Read receipt came back over $link")))
            }
            !e.read && (m.status == MessageStatus.SENT || m.status == MessageStatus.PENDING) -> {
                record(AuditKind.RECEIPT, m.peer, "Delivery receipt for ${m.id} from {name} over $link")
                dao.update(delivered.copy(status = MessageStatus.DELIVERED,
                    history = delivered.history + event(now, "Delivered over $link. Delivery receipt came back")))
            }
        }
        if (m.hidden && m.parent.isNotBlank()) updateGroupStatus(m.parent, now)
    }

    private suspend fun talkToSky(text: String, now: Long) {
        dao.insert(MessageEntity(newId(), SkyBot.NODE_ID, true, text, now, MessageStatus.LOCAL))
        delay(350)
        _typing.update { it + SkyBot.NODE_ID }
        val started = System.currentTimeMillis()
        // The answer as it's being written: shown as one message that grows.
        val streamLock = Mutex()
        var shown: MessageEntity? = null
        var finished = false
        var lastShown = 0L
        val status = { if (openConversation == SkyBot.NODE_ID) MessageStatus.READ else MessageStatus.RECEIVED }
        val answer = runCatching {
            sky(text) { partial ->
                val t = System.currentTimeMillis()
                if (partial.isNotBlank() && t - lastShown >= 250) {
                    lastShown = t
                    scope.launch {
                        streamLock.withLock {
                            if (finished) return@withLock
                            val m = shown
                            if (m == null) {
                                val fresh = MessageEntity(newId(), SkyBot.NODE_ID, false, partial, System.currentTimeMillis(), status())
                                dao.insert(fresh); shown = fresh
                                _typing.update { it - SkyBot.NODE_ID }
                            } else dao.update(m.copy(text = partial).also { shown = it })
                        }
                    }
                }
            }
        }.getOrElse { SkyAnswer("Sorry, something went wrong. Please ask again.") }
        val actions = answer.actions.joinToString("\n") { "${it.label}|${it.target}" }
        streamLock.withLock {
            finished = true
            val m = shown
            if (m != null) {
                dao.update(m.copy(text = answer.text, actions = actions, status = if (openConversation == SkyBot.NODE_ID) MessageStatus.READ else m.status))
            } else {
                delay((SkyBot.typingDelayMs(answer.text) - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                dao.insert(MessageEntity(newId(), SkyBot.NODE_ID, false, answer.text, System.currentTimeMillis(), status(), actions = actions))
            }
        }
        _typing.update { it - SkyBot.NODE_ID }
    }

    companion object {
        const val KEY_NOTE = "Asking phones nearby for their key, so it can be encrypted"
        fun newId(): String = "m-" + UUID.randomUUID().toString().replace("-", "").take(20)
        fun event(at: Long, text: String) = "$at|$text\n"

        /** Parses [MessageEntity.history] into (time, text) pairs. */
        fun historyOf(m: MessageEntity): List<Pair<Long, String>> = m.history.lines().filter { it.isNotBlank() }.mapNotNull {
            val i = it.indexOf('|')
            if (i < 0) null else it.substring(0, i).toLongOrNull()?.let { t -> t to it.substring(i + 1) }
        }
    }
}

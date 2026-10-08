package com.bluemob.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.bluemob.app.BlueMobApp
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.mesh.LinkQuality
import com.bluemob.app.mesh.PeerState
import com.bluemob.app.settings.SignalMode
import com.bluemob.app.settings.Spot
import com.bluemob.app.audit.AuditVerification
import com.bluemob.app.data.AuditEntry
import com.bluemob.app.trail.PositionEstimate
import com.bluemob.app.util.Geo
import com.bluemob.app.util.shortId
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

enum class Presence { ONLINE, IN_RANGE, OFFLINE }

data class SecurityStatus(val databaseEncrypted: Boolean, val plainSettingsFiles: List<String>)

/** One person, as the screens show them. */
data class Person(
    val nodeId: String,
    val name: String,
    val avatar: String?,
    val presence: Presence,
    val lastSeen: Long,
    val quality: LinkQuality?,
    val distanceM: Double?,
    val bearingDeg: Double?,
    val location: GeoPoint?,
    /** Another person nearby has the same name: show the short ID to tell them apart. */
    val sharesName: Boolean,
    val sos: Boolean,
    /** Set while they're in lost mode: their latest position estimate. */
    val lost: PositionEstimate? = null,
    /** 0 to 5 stars from other people's ratings (4.0 for someone new). */
    val stars: Double = com.bluemob.app.trust.Trust.START,
    val ratingCount: Int = 0,
    /** Not nearby but reachable live, e.g. "Online on the internet" or "Reachable through Asha": calls work. */
    val reach: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val blueMob = app as BlueMobApp
    private val identity = blueMob.identity
    private val mesh = blueMob.mesh
    private val repo = blueMob.messages
    private val sosManager = blueMob.sos
    private val settings = blueMob.settings
    private val trail = blueMob.trail
    private val lostMode = blueMob.lost

    val nodeId = identity.nodeId
    val name = identity.displayName
    val avatar = identity.avatar
    val shareLocation = identity.shareLocation
    val onboardingDone = identity.onboardingDone
    val running = mesh.running
    val log = mesh.log
    val myLocation = blueMob.location.location
    val online = blueMob.connectivity.online
    val conversations: StateFlow<Map<String, List<MessageEntity>>> = repo.conversations
    val typing = repo.typing
    val meshEvents = mesh.events
    val mySos = sosManager.mine
    val sosAlert = sosManager.alert
    val signalDefault = settings.signalDefault
    val spots = settings.spots
    val bookmarks = settings.bookmarks
    val signals = blueMob.signals
    val compassAvailable = blueMob.heading.available
    val compassAccuracy = blueMob.heading.accuracy
    val altitude = blueMob.location.altitude
    val sosContacts = settings.sosContacts
    val radios = blueMob.radios.state
    val otherVersions = mesh.otherVersions
    val trailOn = trail.enabled
    val trailPoints = trail.points
    val estimate = trail.estimate
    val lostOn = lostMode.on
    val lostShared = lostMode.lastShared
    val stepCounterAvailable get() = trail.stepCounterAvailable
    fun hasStepPermission() = trail.hasStepPermission()

    /** The audit trail, newest first, with the result of checking every hash, signature and the checkpoint. */
    val audit: StateFlow<Pair<List<AuditEntry>, AuditVerification>> = blueMob.audit.entries
        .map { list -> list.asReversed() to blueMob.audit.verify(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<AuditEntry>() to AuditVerification(0))
    val witnesses = blueMob.witness.witnesses
    val auditPublicKey: String get() = blueMob.identity.keys.publicB64

    /** What's encrypted on this phone, checked live, for the security screen. */
    fun securityStatus(): SecurityStatus = SecurityStatus(
        databaseEncrypted = !com.bluemob.app.data.DbKey.isPlain(getApplication<android.app.Application>().getDatabasePath(com.bluemob.app.data.BlueMobDatabase.NAME)),
        plainSettingsFiles = com.bluemob.app.crypto.SecurePrefs.plainFilesLeft(getApplication()),
    )
    fun headings(): Flow<Float> = blueMob.heading.headings()

    /** Ticks every 30 s so "last seen 5 min ago" stays fresh. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val people: StateFlow<List<Person>> = combine(
        combine(mesh.peers, blueMob.live.presence) { a, b -> a to b }, blueMob.contacts.contacts, myLocation, clock, combine(sosManager.received, lostMode.received, blueMob.trust.scores) { a, b, c -> Triple(a, b, c) },
    ) { (peers, online), contacts, me, now, (sos, lost, scores) ->
        val neighbors = mesh.connectedNodes()
        val names = contacts.values.groupingBy { it.name }.eachCount()
        contacts.values.map { c ->
            val link = peers.values.filter { it.nodeId == c.nodeId }.maxByOrNull { it.state.ordinal }
            val presence = when (link?.state) {
                PeerState.CONNECTED -> Presence.ONLINE
                PeerState.CONNECTING, PeerState.DISCOVERED -> Presence.IN_RANGE
                null -> Presence.OFFLINE
            }
            val lostPos = lost[c.nodeId]?.pos
            val theirs = lostPos?.let { GeoPoint(it.lat, it.lon, it.uncertaintyM.toFloat(), it.at) } ?: c.location ?: sos[c.nodeId]?.let { s -> if (s.lat != null && s.lon != null) GeoPoint(s.lat, s.lon, 0f, s.at) else null }
            val name = link?.name ?: c.name
            Person(
                nodeId = c.nodeId, name = name, avatar = c.avatar, presence = presence,
                lastSeen = if (presence == Presence.OFFLINE) c.lastSeen else now,
                quality = link?.quality,
                distanceM = if (me != null && theirs != null) Geo.distanceM(me, theirs) else null,
                bearingDeg = if (me != null && theirs != null) Geo.bearingDeg(me, theirs) else null,
                location = theirs,
                sharesName = (names[c.name] ?: 0) > 1,
                sos = sos.containsKey(c.nodeId),
                lost = lostPos,
                stars = scores[c.nodeId]?.stars ?: com.bluemob.app.trust.Trust.START,
                ratingCount = scores[c.nodeId]?.ratings ?: 0,
                reach = if (presence != Presence.OFFLINE) null else mesh.routes.nextHop(c.nodeId, neighbors)?.let { (hop, hops) ->
                    "Reachable through ${contacts[hop]?.name ?: "a friend"}" + if (hops > 2) " ($hops hops)" else ""
                } ?: when {
                    c.nodeId in online.online -> "Online on the internet"
                    c.nodeId in online.via -> "Online through ${contacts[online.via[c.nodeId]]?.name ?: "a friend"}'s internet"
                    else -> null
                },
            )
        }.sortedWith(compareBy<Person> { it.presence.ordinal }.thenByDescending { it.lastSeen })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setName(value: String) = identity.setDisplayName(value)
    fun setAvatar(value: String) = identity.setAvatar(value)
    fun setShareLocation(value: Boolean) = identity.setShareLocation(value)
    fun finishOnboarding() = identity.setOnboardingDone(true)
    /** "Replay the intro" from You: shows it once, then back to the app (nothing is reset). */
    val replayingIntro = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun replayIntro() { replayingIntro.value = true }
    fun endIntroReplay() {
        replayingIntro.value = false
        // Someone who's signed in has seen the intro: don't show it again by itself.
        if (blueMob.profile.profile.value.verified) identity.setOnboardingDone(true)
    }

    /** What to ask before the mesh starts: a radio is off and the user hasn't decided whether BlueMob may turn it on. */
    enum class MeshAsk { BLUETOOTH, WIFI }
    val meshAsk = kotlinx.coroutines.flow.MutableStateFlow<MeshAsk?>(null)
    /** Waiting for the user to switch a radio on in Android's prompt, then the mesh starts. */
    private var pendingUntil = 0L
    private var pendingWifi = false
    val autoMesh = settings.meshAtStart
    val bluetoothPolicy = settings.bluetoothPolicy
    val wifiPolicy = settings.wifiPolicy
    fun setMeshAtStart(on: Boolean) = settings.setMeshAtStart(on)
    fun setBluetoothPolicy(p: com.bluemob.app.settings.RadioPolicy) = settings.setBluetoothPolicy(p)
    fun setWifiPolicy(p: com.bluemob.app.settings.RadioPolicy) = settings.setWifiPolicy(p)

    /** Starts the mesh, first asking about any radio that's off and that the user hasn't allowed BlueMob to turn on. */
    fun startMesh() {
        val r = blueMob.radios.state.value
        val bt = settings.bluetoothPolicy.value
        val wifi = settings.wifiPolicy.value
        when {
            !r.bluetooth && bt != com.bluemob.app.settings.RadioPolicy.ALLOW -> meshAsk.value = MeshAsk.BLUETOOTH
            !r.wifi && wifi == com.bluemob.app.settings.RadioPolicy.ASK -> meshAsk.value = MeshAsk.WIFI
            else -> { meshAsk.value = null; mesh.start(useWifi = r.wifi || wifi == com.bluemob.app.settings.RadioPolicy.ALLOW) }
        }
    }

    /**
     * The answer to [meshAsk]. [turnOn]: the user wants that radio on (Android shows its own prompt; the mesh starts once
     * it's on). [remember]: don't ask again. Returns true if the screen should open Android's switch for that radio.
     */
    fun answerMeshAsk(turnOn: Boolean, remember: Boolean): Boolean {
        val ask = meshAsk.value ?: return false
        meshAsk.value = null
        val p = com.bluemob.app.settings.RadioPolicy.ALLOW
        val r = blueMob.radios.state.value
        return when (ask) {
            MeshAsk.BLUETOOTH -> when {
                !turnOn -> false
                remember -> { settings.setBluetoothPolicy(p); startMesh(); false }
                else -> { pendingUntil = System.currentTimeMillis() + 90_000; true }
            }
            MeshAsk.WIFI -> {
                if (remember) settings.setWifiPolicy(if (turnOn) p else com.bluemob.app.settings.RadioPolicy.NEVER)
                if (turnOn && !remember) { pendingUntil = System.currentTimeMillis() + 90_000; pendingWifi = true; true }
                else { mesh.start(useWifi = turnOn || r.wifi); false }
            }
        }
    }

    init {
        // The user switched the radio on in Android's prompt: carry on starting the mesh.
        viewModelScope.launch {
            blueMob.radios.state.collect { r ->
                if (pendingUntil > System.currentTimeMillis() && !mesh.running.value) {
                    if (r.bluetooth && (!pendingWifi || r.wifi)) { pendingUntil = 0; pendingWifi = false; startMesh() }
                }
            }
        }
    }
    fun stopMesh() = mesh.stop()
    fun ping(nodeId: String) = mesh.ping(nodeId)

    fun send(nodeId: String, text: String) = repo.send(nodeId, text)
    fun reply(nodeId: String, text: String, to: MessageEntity) = repo.send(nodeId, text, to)
    fun react(m: MessageEntity, emoji: String) = repo.react(m, emoji)

    // ---- groups, pinned chats, missed-call badge ----
    val groups = blueMob.groups.groups
    fun createGroup(name: String, members: Map<String, String>) = repo.createGroup(name, members)
    fun leaveGroup(id: String) { repo.leaveGroup(id); settings.setPinned(id, false) }
    /** Adds people to a group: everyone (them included) learns the new member list with the message. */
    fun addToGroup(id: String, people: Map<String, String>) {
        val g = blueMob.groups.get(id) ?: return
        blueMob.groups.put(g.copy(members = g.members + people))
        repo.send(id, "➕ ${identity.displayName.value} added " + people.values.joinToString(", "))
    }
    // ---- Nearby: batteries and check-ins ----
    val batteries = blueMob.peerStatus.batteries
    val checkIn = blueMob.peerStatus.check
    val checkRequests = blueMob.peerStatus.requests
    fun checkOnEveryone(): Int = blueMob.peerStatus.checkOnEveryone().also { if (it == 0) toast("No one is connected nearby right now.") }
    fun answerCheck(r: com.bluemob.app.nearby.CheckRequest, ok: Boolean) {
        blueMob.peerStatus.answer(r, if (ok) com.bluemob.app.nearby.CheckIn.OK else com.bluemob.app.nearby.CheckIn.HELP)
        // "I need help" also opens their chat, so they can say what's wrong.
        if (!ok) { send(r.from, "🆘 I need help"); pendingRoute.value = "chat:" + r.from }
    }
    fun clearCheck() = blueMob.peerStatus.clearCheck()

    // ---- You: theme, emergency card, badges ----
    val theme = settings.theme
    fun setTheme(t: String) = settings.setTheme(t)
    val emergencyCard = settings.emergencyCard
    fun setEmergencyCard(on: Boolean) = settings.setEmergencyCard(on)
    // Lazy: it uses flows declared further down this class.
    private val badgeInputs by lazy { combine(
        combine(com.bluemob.app.guide.GuidePacks.read, com.bluemob.app.guide.GuidePacks.quizzesPassed, com.bluemob.app.guide.GuidePacks.readDays) { r, q, d -> Triple(r.size, q.size, com.bluemob.app.guide.Streak.of(d)) },
        activity, settings.sosContacts, blueMob.groups.groups,
        combine(blueMob.contacts.contacts, blueMob.profile.profile, trail.allTrips) { c, p, t -> Triple(c.count { it.value.lastSeen > 0 }, p.bloodGroup != null, t.size) },
    ) { g, act, contacts, groups, other ->
        com.bluemob.app.ui.profile.BadgeStats(
            guidesRead = g.first, quizzes = g.second, streak = g.third,
            rescuesJoined = act.count { it.type == com.bluemob.app.activity.ActivityType.HELPED },
            sosContactsAccepted = contacts.count { it.verified }, messagesSent = act.count { it.type == com.bluemob.app.activity.ActivityType.MSG_SENT },
            groups = groups.size, peopleMet = other.first, hasBloodGroup = other.second, tripsRecorded = other.third,
        )
    } }
    val badges: StateFlow<List<com.bluemob.app.ui.profile.Badge>> by lazy { badgeInputs.map { com.bluemob.app.ui.profile.Badges.of(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.bluemob.app.ui.profile.Badges.of(com.bluemob.app.ui.profile.BadgeStats())) }

    val pinnedChats = settings.pinnedChats
    fun setPinned(id: String, on: Boolean) = settings.setPinned(id, on)
    val callsSeenAt = settings.callsSeenAt
    fun markCallsSeen() = settings.markCallsSeen()
    private var chatOnScreen: String? = null
    private var visible = true

    /** BlueMob on screen or not: messages only count as read while the chat is actually visible. */
    fun setVisible(on: Boolean) {
        visible = on
        com.bluemob.app.activity.Usage.visible(on)
        repo.openConversation = if (on) chatOnScreen else null
    }

    fun openChat(nodeId: String?) {
        chatOnScreen = nodeId
        repo.openConversation = if (visible) nodeId else null
        // Check whether they're online over the internet right away, and keep checking while the chat is open.
        blueMob.watchPresence = { listOfNotNull(nodeId, blueMob.calls.call.value?.peer) }
        if (nodeId != null && blueMob.live.connected.value) blueMob.live.askPresence(listOf(nodeId))
    }
    fun message(id: String): MessageEntity? = repo.messages.value.firstOrNull { it.id == id }
    fun unreadCount(all: Map<String, List<MessageEntity>>) = all.values.sumOf { list -> list.count { !it.fromMe && it.status == MessageStatus.RECEIVED } }

    fun forgetPeople() = blueMob.contacts.forgetAll()
    /** Sends a problem report. Null when sent, otherwise why not. */
    /** A picture for a problem report: scaled to at most 1280 px and compressed, so a report stays small. */
    fun reportPhoto(uri: android.net.Uri, done: (com.bluemob.app.ui.profile.ReportPhoto?) -> Unit) = viewModelScope.launch {
        val p = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val app = getApplication<android.app.Application>()
                // Read the size first, then decode at about 1280 px (works on every Android version).
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
                val raw = app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
                    ?: error("unreadable")
                val scale = minOf(1f, 1280f / maxOf(raw.width, raw.height))
                val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
                var q = 75
                var out: ByteArray
                do { out = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, it) }.toByteArray(); q -= 15 } while (out.size > 800 * 1024 && q > 20)
                com.bluemob.app.ui.profile.ReportPhoto(bmp, out)
            }.getOrNull()
        }
        done(p)
    }

    suspend fun reportProblem(text: String, details: Boolean, log: List<com.bluemob.app.mesh.LogLine>,
        category: String = "", sub: String = "", photos: List<ByteArray> = emptyList()): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val app = getApplication<android.app.Application>()
        val device = com.bluemob.app.util.CrashLog.deviceLine(app)
        val extra = if (!details) "" else buildString {
            com.bluemob.app.util.CrashLog.lastNonFatal(app)?.let { append("Last background error:\n").append(it.take(6000)).append("\n\n") }
            com.bluemob.app.util.CrashLog.read(app)?.let { append("Last crash:\n").append(it.take(6000)).append("\n\n") }
            append("Mesh log:\n")
            log.take(80).forEach { append(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(it.timeMillis))).append("  ").append(it.text).append('\n') }
        }
        com.bluemob.app.util.ProblemReport.send(settings.bridgeUrl.value, text, com.bluemob.app.BuildConfig.VERSION_NAME, if (details) device else "", extra,
            category.substringAfter(' '), sub, photos)
    }

    fun deleteChat(peer: String) = viewModelScope.launch {
        repo.deleteChat(peer).forEach { runCatching { java.io.File(it).delete() } }
        blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "A chat was deleted from this phone")
    }

    fun clearMessages() { repo.clearAll(); blueMob.files.deleteAll(); blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "All messages deleted from this phone") }

    fun sendSos(note: String): Int = sosManager.send(note)
    fun previewSosAlert() = sosManager.preview("Ravi")
    suspend fun addSosContact(name: String, phone: String, nodeId: String?): String? = blueMob.sosCircle.add(name, phone, nodeId)
    fun removeSosContact(id: String) = blueMob.sosCircle.remove(id)
    fun askSosContactAgain(c: com.bluemob.app.settings.SosContact) = blueMob.sosCircle.ask(c)
    val sosRequests = blueMob.sosCircle.requests
    fun answerSosRequest(r: com.bluemob.app.sos.SosCircle.Request, yes: Boolean) = blueMob.sosCircle.answer(r, yes)

    /** The SOS text that goes to people nearby and to SOS contacts by SMS. */
    fun sosMessage(note: String): String {
        val pos = mySos.value?.pos ?: trail.snapshot()
        val n = note.trim().trimEnd('.')
        return "SOS from ${name.value} (BlueMob ${shortId(nodeId)}). I need help." + (if (n.isNotEmpty()) " $n." else "") +
            " " + (pos?.describe() ?: "Position unknown.") + (batteryPct()?.let { " Battery $it%." } ?: "") + " Sent with BlueMob."
    }

    fun setTrail(on: Boolean) = trail.setEnabled(on)
    val trips = trail.allTrips
    val backups = blueMob.backups
    val currentTrip = trail.currentTrip
    fun startNewTrip(name: String) = trail.startNewTrip(name)
    suspend fun pointsOf(id: String) = trail.pointsOf(id)
    fun renameTrip(id: String, name: String) = viewModelScope.launch { trail.renameTrip(id, name) }
    fun deleteTrip(id: String) = viewModelScope.launch { trail.deleteTrip(id) }
    /** The trip as a GPX file in the temporary share folder. */
    suspend fun tripGpxFile(id: String): java.io.File? {
        val t = trips.value.firstOrNull { it.id == id } ?: return null
        val gpx = com.bluemob.app.ui.compass.tripGpx(t, trail.pointsOf(id))
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            java.io.File(getApplication<android.app.Application>().cacheDir, "open").apply { mkdirs() }
                .resolve(com.bluemob.app.files.Attachment.safeName(t.name) + ".gpx").apply { writeText(gpx) }
        }
    }
    fun clearTrail() = trail.clear()
    fun onStepPermission() = trail.onStepPermission()
    fun setLost(on: Boolean) = if (on) lostMode.start() else lostMode.stop()
    fun ring(nodeId: String) { if (tooOld(nodeId, blueMob.contacts.contacts.value[nodeId]?.name ?: "They", "ring", "ringing")) return; if (!lostMode.ring(nodeId)) toast("No one is in range to pass this on. Get closer, or wait for the mesh to reconnect.") else toast("Ringing… ask everyone to be quiet and listen.") }
    fun setBaseCamp() = savePlace("Base camp", baseCamp = true)
    fun refreshRadios() {
        blueMob.radios.refresh()
        // Back from Android's Wi-Fi panel without turning it on: start over Bluetooth only, as they chose not to.
        if (pendingWifi && !mesh.running.value) {
            pendingWifi = false; pendingUntil = 0
            if (!blueMob.radios.state.value.wifi) mesh.start(useWifi = false)
        }
    }

    /** Adds someone by ID so their chat can open. Uses the name they gave, or a short form of the ID. */
    fun startChatById(id: String, name: String) {
        blueMob.contacts.addById(id, name.ifBlank { "BM " + com.bluemob.app.util.formatId(id).take(9) })
    }
    val carrying: Int get() = mesh.router.carrying

    val trustScores = blueMob.trust.scores
    val trustAboutMe = blueMob.trust.aboutMe
    fun scoreFor(id: String) = blueMob.trust.scoreFor(id)
    fun rate(subject: String, kind: com.bluemob.app.trust.RatingKind, ctx: String, remark: String) = blueMob.trust.rate(subject, kind, ctx, remark)
    /** Ratings this phone gave, so screens can show "You appreciated them". */
    fun myRatingsOf(subject: String) = blueMob.trust.ratings.value.filter { it.rater == nodeId && it.subject == subject }
    fun lastRatedAt(subject: String) = blueMob.trust.lastRatedAt(subject)
    fun rateCategories(subject: String, stars: Map<com.bluemob.app.trust.RatingCategory, Int>, remark: String) = blueMob.trust.rateCategories(subject, stars, remark)
    /** Only people we've actually met, chatted with or shared a rescue with can be rated. */
    fun canRate(subject: String): Boolean = subject != nodeId && (
        (blueMob.contacts.contacts.value[subject]?.lastSeen ?: 0L) > 0 ||
            repo.messages.value.any { it.peer == subject } ||
            blueMob.rescue.rooms.value.any { r -> r.victimId == subject || r.helpers.any { it.nodeId == subject } })

    /** Messages, calls and SOS, for the activity dashboard. */
    val activity: StateFlow<List<com.bluemob.app.activity.ActivityEvent>> =
        combine(repo.messages, blueMob.callLog.observe(), blueMob.audit.entries) { m, c, a -> com.bluemob.app.activity.Activity.events(m, c, a) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bridgeStatus = blueMob.bridge.status
    val bridgeUrl = settings.bridgeUrl
    val builtInRelay: String get() = settings.defaultBridgeUrl
    fun setBridgeUrl(url: String) { settings.setBridgeUrl(url); blueMob.bridge.reconfigure(); blueMob.live.reconnect() }
    /** Signed in to the relay for internet calls. */
    val liveConnected = blueMob.live.connected

    fun lastError(): String? = com.bluemob.app.util.CrashLog.lastNonFatal(getApplication())

    val background = settings.background
    fun setBackground(on: Boolean) = settings.setBackground(on)
    val techDetails = settings.techDetails
    fun setTechDetails(on: Boolean) = settings.setTechDetails(on)

    /** A screen to open, e.g. from a notification. The UI opens it and clears it. */
    val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val rescues = blueMob.rescue.rooms
    val rescueNotices = blueMob.rescue.notices
    fun joinRescue(id: String) = blueMob.rescue.join(id)
    /** "I'm coming" on an SOS: join its rescue group, and if it's one of our SOS contacts, tell them in their chat too. */
    fun comingToSos(id: String) = viewModelScope.launch {
        // Straight from the notification, the group may still be being set up: give it a moment.
        kotlinx.coroutines.withTimeoutOrNull(5_000) { blueMob.rescue.rooms.first { rooms -> rooms.any { it.id == id } } }
        joinRescue(id)
        val s = sosManager.received.value.values.firstOrNull { it.id == id } ?: return@launch
        val here = trail.snapshot()
        val away = if (here != null && s.lat != null && s.lon != null) {
            val d = com.bluemob.app.util.Geo.distanceM(com.bluemob.app.contacts.GeoPoint(here.lat, here.lon, 0f, 0), com.bluemob.app.contacts.GeoPoint(s.lat, s.lon, 0f, 0))
            com.bluemob.app.util.Geo.formatDistance(d) + " away"
        } else null
        blueMob.sosCircle.coming(s.fromNodeId, away)
        blueMob.rescue.note(id, "✓ ${s.name} has been told you're coming" + (away?.let { " ($it)" } ?: "") + ". " +
            if (s.pos != null) "Their position is on the compass: tap Navigate." else "Their position isn't known yet: ask them where they are, or listen for their whistle.")
    }
    /** Text someone who isn't on BlueMob yet, from your own phone's SMS app. */
    fun inviteText(name: String) = "Hi $name, I've added you as my SOS contact on BlueMob, the safety app that works even without signal. " +
        "Please install BlueMob and sign up with this number: you'll then get my alert if I'm ever in trouble."
    fun sendRescue(id: String, text: String) = blueMob.rescue.send(id, text)
    fun arrivedRescue(id: String) = blueMob.rescue.arrived(id)
    fun leaveRescue(id: String) = blueMob.rescue.leave(id)
    fun cancelSos() = sosManager.cancel()
    fun dismissSosAlert() = sosManager.dismissAlert()
    fun setSignalDefault(mode: SignalMode) = settings.setSignalDefault(mode)
    fun batteryPct(): Int? = sosManager.batteryPct()

    fun toggleBookmark(articleId: String) = settings.toggleBookmark(articleId)
    fun saveSpot(name: String) = savePlace(name, baseCamp = false)

    private var placing: kotlinx.coroutines.Job? = null

    /**
     * Saves where we are as a spot or the base camp. GPS may need a minute for its first fix (longer indoors or
     * in airplane mode), so this keeps GPS on and waits for a good fix, telling the user what's happening.
     */
    private fun savePlace(name: String, baseCamp: Boolean) {
        if (placing?.isActive == true) return toast("Still getting your position…")
        val tracker = blueMob.location
        if (!tracker.hasPermission()) return toast("Allow location first, then tap again")
        if (!blueMob.radios.state.value.location) return toast("Location (GPS) is off. Turn it on, then tap again")
        placing = viewModelScope.launch {
            // Fast GPS while we work out where you are.
            tracker.boost(true)
            try {
                val usable = { p: GeoPoint? -> p != null && System.currentTimeMillis() - p.time < FRESH_FIX_MS && p.accuracyM in 0f..USABLE_ACCURACY_M }
                var best: GeoPoint? = myLocation.value?.takeIf(usable) ?: tracker.lastKnown()?.takeIf(usable)
                if (best == null) {
                    toast("Getting your position… Stand in the open, away from walls.")
                    kotlinx.coroutines.withTimeoutOrNull(FIX_WAIT_MS) {
                        myLocation.first { p -> if (p != null && (best == null || p.accuracyM < best!!.accuracyM)) best = p; usable(p) }
                    }
                }
                val here = best ?: tracker.lastKnown() ?: trail.snapshot()?.let { GeoPoint(it.lat, it.lon, it.uncertaintyM.toFloat(), it.at) }
                    ?: return@launch toast("Couldn't get a GPS fix. Go outside under open sky and try again.")
                val id = if (baseCamp) Spot.BASE_CAMP_ID else "spot-" + UUID.randomUUID().toString().take(8)
                fun save(p: GeoPoint) {
                    if (baseCamp) settings.setBaseCamp(p.lat, p.lon)
                    else { settings.removeSpot(id); settings.addSpot(Spot(id, name, p.lat, p.lon, System.currentTimeMillis())) }
                }
                save(here)
                val ageMin = (System.currentTimeMillis() - here.time) / 60_000
                val how = if (ageMin >= 2) "your last known position ($ageMin min old)" else "±${here.accuracyM.toInt()} m"
                toast(if (baseCamp) "⛺ Base camp saved, $how" else "📍 $name saved, $how")
                if (baseCamp) blueMob.audit.add(com.bluemob.app.audit.AuditKind.POSITION, "Base camp set at ${Geo.formatLatLon(here.lat, here.lon)} (±${here.accuracyM.toInt()} m)")
                // Saved straight away; keep listening a little longer and move it if GPS gets a clearly better fix.
                var saved = here
                kotlinx.coroutines.withTimeoutOrNull(REFINE_MS) {
                    myLocation.first { p ->
                        if (p != null && p.accuracyM > 0 && p.accuracyM < saved.accuracyM * 0.6f && System.currentTimeMillis() - p.time < 30_000) {
                            saved = p; save(p)
                        }
                        saved.accuracyM <= GOOD_ACCURACY_M / 2
                    }
                }
                if (saved !== here) toast((if (baseCamp) "⛺ Base camp" else "📍 $name") + " made more accurate: ±${saved.accuracyM.toInt()} m")
            } finally {
                tracker.boost(false)
            }
        }
    }

    private fun toast(text: String) = android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_LONG).show()

    fun removeSpot(id: String) = settings.removeSpot(id)
    fun holdLocation() = blueMob.location.hold()
    fun releaseLocation() = blueMob.location.release(keepForSharing = shareLocation.value)
    fun hasLocationPermission() = blueMob.location.hasPermission()

    fun isBot(nodeId: String) = nodeId == SkyBot.NODE_ID

    val profile = blueMob.profile.profile
    /** Sign-up finished: the number is verified, the details saved, and the app opens. */
    fun completeSignup(r: com.bluemob.app.ui.account.SignupResult) {
        identity.setDisplayName(r.name); identity.setAvatar(r.avatar)
        blueMob.profile.setVerifiedPhone(r.phone)
        blueMob.profile.setDetails(r.age, r.bloodGroup)
        blueMob.profile.setEmail(r.email)
        syncEmail()
        identity.setOnboardingDone(true)
        blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "Signed up: mobile number verified (${r.phone.take(5)}…)")
    }
    /** "🩸 B+ · age 34" from someone's SOS, if they shared it. */
    fun medicalFor(nodeId: String): String? = sosManager.received.value[nodeId]?.let { s ->
        listOfNotNull(s.bloodGroup?.let { "🩸 Blood group $it" }, s.age?.let { "age $it" }).joinToString(" · ").ifBlank { null }
    }
    fun setDetails(age: Int?, blood: String?) = blueMob.profile.setDetails(age, blood)
    /** Optional recovery email: saved on the phone, and on the relay as soon as we're online. */
    fun setEmail(email: String?) { blueMob.profile.setEmail(email); syncEmail() }
    private fun syncEmail() = viewModelScope.launch { blueMob.syncEmail() }
    fun clearPin() { lock.clearPin(); blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "PIN lock turned off") }

    private val lock = blueMob.lock
    val pinSet = lock.pinSet
    val locked = lock.locked
    val biometric = lock.biometric
    val lockAfterMs = lock.lockAfterMs
    val recoverySaved = lock.recoverySaved
    fun setPin(pin: String) { val first = !lock.hasPin; lock.setPin(pin); blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, if (first) "Account PIN created" else "Account PIN changed") }
    fun checkPin(pin: String) = lock.check(pin).also { if (it is com.bluemob.app.account.PinResult.Wait) blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "Several wrong PINs: login paused") }
    fun unlockedByBiometric() = lock.unlockedByBiometric()
    fun setBiometric(on: Boolean) = lock.setBiometric(on)
    fun setLockAfter(ms: Long) = lock.setLockAfter(ms)
    fun lockNow() = lock.lockNow()
    fun recoveryCode(): String = identity.recoveryCode()
    fun setRecoverySaved() { lock.setRecoverySaved(); blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "Recovery code saved") }
    /** Restores an ID from a recovery code. Returns an error to show, or null when the app should restart. */
    fun restore(code: String): String? {
        val id = identity.restore(code) ?: return "That code isn't right. Check each group of 4, especially 0/O and 1/I."
        blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "BlueMob ID restored from a recovery code: BM ${com.bluemob.app.util.formatId(id)}")
        return null
    }

    val matches = blueMob.matches.all
    /** Tells the user when the other phone's BlueMob is too old for a feature. True if it is. */
    private fun tooOld(nodeId: String, name: String, cap: String, what: String): Boolean {
        val theirs = mesh.featureGap(nodeId, cap) ?: return false
        toast("$name has $theirs, which can't do $what yet. Ask them to update BlueMob. Messages and SOS still work between you.")
        return true
    }

    fun challenge(nodeId: String, name: String, game: String): String? =
        if (tooOld(nodeId, name, if (game == com.bluemob.app.games.Match.TTT || game == com.bluemob.app.games.Match.C4) "game" else "games2",
                if (game == com.bluemob.app.games.Match.TTT || game == com.bluemob.app.games.Match.C4) "games" else "this game")) null else blueMob.matches.invite(nodeId, name, game)?.id ?: null.also { toast("$name isn't in range right now. Games need them nearby.") }
    fun acceptGame(id: String) = blueMob.matches.accept(id)
    fun declineGame(id: String) = blueMob.matches.decline(id)
    fun playGame(id: String, spot: Int) = blueMob.matches.play(id, spot)
    val gameReactions = blueMob.matches.reactions
    fun reactInGame(id: String, text: String) = blueMob.matches.react(id, text)
    fun gameAgain(id: String) = blueMob.matches.again(id)
    fun leaveGame(id: String) = blueMob.matches.leave(id)

    // ---- Photos, documents, voice notes ----
    private val files = blueMob.files
    private val voiceNotes = blueMob.voiceNotes
    val fileProgress = files.progress
    val voicePlaying = voiceNotes.playing
    val recordingMs = voiceNotes.recordingMs

    fun sendFile(peer: String, uri: android.net.Uri) = viewModelScope.launch {
        toast("Preparing…")
        val (res, error) = files.prepare(uri)
        if (res == null) toast(error ?: "Couldn't send that file") else repo.sendAttachment(peer, res.first, res.second.path)
    }
    suspend fun thumbnail(m: MessageEntity, maxPx: Int = 480) = files.thumbnail(m, maxPx)
    suspend fun openCopy(m: MessageEntity) = files.openCopy(m)
    /** Starts recording a voice note. False if the microphone isn't allowed or is busy. */
    fun startVoiceNote(): Boolean = (blueMob.calls.hasMic() && voiceNotes.start()).also { recordingStarted = it }
    private var recordingStarted = false
    fun stopVoiceNote(peer: String, send: Boolean) {
        val started = recordingStarted.also { recordingStarted = false }
        val clip = voiceNotes.stop() ?: run { if (send && started) toast("Too short. Hold the mic while you talk, then let go to send"); return }
        if (!send) { clip.first.delete(); return }
        viewModelScope.launch { val (att, file) = files.prepareVoiceNote(clip.first, clip.second); repo.sendAttachment(peer, att, file.path) }
    }
    fun playVoice(m: MessageEntity) {
        val att = com.bluemob.app.files.Attachment.fromJson(m.att) ?: return
        if (voicePlaying.value?.first == att.fid) { voiceNotes.stopPlaying(); return }
        viewModelScope.launch { files.openCopy(m)?.let { voiceNotes.play(att.fid, it) } ?: toast("This voice note hasn't arrived yet") }
    }

    private val calls = blueMob.calls
    val call = calls.call
    val remoteFrame = calls.remoteFrame
    val localFrame = calls.localFrame
    fun startCall(nodeId: String, name: String, video: Boolean) { if (tooOld(nodeId, name, "call", "calls")) return; calls.start(nodeId, name, video)?.let(::toast) }
    fun acceptCall() = calls.accept()
    fun hangUp() = calls.hangUp()
    fun toggleMute() = calls.toggleMute()
    fun togglePtt() = calls.togglePtt()
    fun talk(down: Boolean) = calls.talk(down)
    val callLog = blueMob.callLog.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun clearCallLog() = viewModelScope.launch { blueMob.callLog.clear() }
    fun deleteCall(id: String) = viewModelScope.launch { blueMob.callLog.delete(id) }
    fun toggleSpeaker() = calls.toggleSpeaker()
    fun toggleCamera() = calls.toggleCamera()
    fun canUseCamera() = calls.hasCamera()
    fun wantsFrame() = calls.wantsFrame()
    fun onCameraFrame(frame: android.graphics.Bitmap) = calls.onCameraFrame(frame)

    private companion object {
        const val FRESH_FIX_MS = 2 * 60_000L
        const val GOOD_ACCURACY_M = 30f
        /** Good enough to save at once; it's refined afterwards. */
        const val USABLE_ACCURACY_M = 100f
        const val FIX_WAIT_MS = 60_000L
        const val REFINE_MS = 60_000L
    }
}

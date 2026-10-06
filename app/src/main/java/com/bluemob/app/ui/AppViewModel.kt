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
    val sosContacts = settings.sosContacts
    val radios = blueMob.radios.state
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
        mesh.peers, blueMob.contacts.contacts, myLocation, clock, combine(sosManager.received, lostMode.received, blueMob.trust.scores) { a, b, c -> Triple(a, b, c) },
    ) { peers, contacts, me, now, (sos, lost, scores) ->
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
            )
        }.sortedWith(compareBy<Person> { it.presence.ordinal }.thenByDescending { it.lastSeen })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setName(value: String) = identity.setDisplayName(value)
    fun setAvatar(value: String) = identity.setAvatar(value)
    fun setShareLocation(value: Boolean) = identity.setShareLocation(value)
    fun finishOnboarding() = identity.setOnboardingDone(true)
    fun replayIntro() = identity.setOnboardingDone(false)

    fun startMesh() = mesh.start()
    fun stopMesh() = mesh.stop()
    fun ping(nodeId: String) = mesh.ping(nodeId)

    fun send(nodeId: String, text: String) = repo.send(nodeId, text)
    fun openChat(nodeId: String?) { repo.openConversation = nodeId }
    fun message(id: String): MessageEntity? = repo.messages.value.firstOrNull { it.id == id }
    fun unreadCount(all: Map<String, List<MessageEntity>>) = all.values.sumOf { list -> list.count { !it.fromMe && it.status == MessageStatus.RECEIVED } }

    fun forgetPeople() = blueMob.contacts.forgetAll()
    fun clearMessages() = repo.clearAll()

    fun sendSos(note: String): Int = sosManager.send(note)
    fun previewSosAlert() = sosManager.preview("Ravi")
    fun addSosContact(name: String, phone: String) = settings.addSosContact(name, phone)
    fun removeSosContact(id: String) = settings.removeSosContact(id)

    /** The SOS text that goes to people nearby and to SOS contacts by SMS. */
    fun sosMessage(note: String): String {
        val pos = mySos.value?.pos ?: trail.snapshot()
        val n = note.trim().trimEnd('.')
        return "SOS from ${name.value} (BlueMob ${shortId(nodeId)}). I need help." + (if (n.isNotEmpty()) " $n." else "") +
            " " + (pos?.describe() ?: "Position unknown.") + (batteryPct()?.let { " Battery $it%." } ?: "") + " Sent with BlueMob."
    }

    fun setTrail(on: Boolean) = trail.setEnabled(on)
    fun clearTrail() = trail.clear()
    fun onStepPermission() = trail.onStepPermission()
    fun setLost(on: Boolean) = if (on) lostMode.start() else lostMode.stop()
    fun ring(nodeId: String) { if (!lostMode.ring(nodeId)) toast("No one is in range to pass this on. Get closer, or wait for the mesh to reconnect.") else toast("Ringing… ask everyone to be quiet and listen.") }
    fun setBaseCamp() = savePlace("Base camp", baseCamp = true)
    fun refreshRadios() = blueMob.radios.refresh()

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

    val bridgeStatus = blueMob.bridge.status
    val bridgeUrl = settings.bridgeUrl
    val builtInRelay: String get() = settings.defaultBridgeUrl
    fun setBridgeUrl(url: String) { settings.setBridgeUrl(url); blueMob.bridge.reconfigure() }

    fun lastError(): String? = com.bluemob.app.util.CrashLog.lastNonFatal(getApplication())

    val background = settings.background
    fun setBackground(on: Boolean) = settings.setBackground(on)

    /** A screen to open, e.g. from a notification. The UI opens it and clears it. */
    val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val rescues = blueMob.rescue.rooms
    val rescueNotices = blueMob.rescue.notices
    fun joinRescue(id: String) = blueMob.rescue.join(id)
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
            tracker.hold()
            try {
                val good = { p: GeoPoint? -> p != null && System.currentTimeMillis() - p.time < FRESH_FIX_MS && p.accuracyM in 0f..GOOD_ACCURACY_M }
                var best: GeoPoint? = myLocation.value?.takeIf(good) ?: tracker.lastKnown()?.takeIf(good)
                if (best == null) {
                    toast("Getting your position… Stand in the open, away from walls. This can take a minute.")
                    kotlinx.coroutines.withTimeoutOrNull(FIX_WAIT_MS) {
                        myLocation.first { p ->
                            if (p != null && (best == null || p.accuracyM < best!!.accuracyM)) best = p
                            good(p)
                        }
                    }
                }
                val here = best ?: tracker.lastKnown() ?: trail.snapshot()?.let { GeoPoint(it.lat, it.lon, it.uncertaintyM.toFloat(), it.at) }
                    ?: return@launch toast("Couldn't get a GPS fix. Go outside under open sky and try again.")
                val ageMin = (System.currentTimeMillis() - here.time) / 60_000
                val how = if (ageMin >= 2) "your last known position ($ageMin min old)" else "±${here.accuracyM.toInt()} m"
                if (baseCamp) {
                    settings.setBaseCamp(here.lat, here.lon)
                    blueMob.audit.add(com.bluemob.app.audit.AuditKind.POSITION, "Base camp set at ${Geo.formatLatLon(here.lat, here.lon)}")
                    toast("⛺ Base camp saved, $how")
                } else {
                    settings.addSpot(Spot("spot-" + UUID.randomUUID().toString().take(8), name, here.lat, here.lon, System.currentTimeMillis()))
                    toast("📍 $name saved, $how")
                }
            } finally {
                tracker.release(keepForSharing = shareLocation.value)
            }
        }
    }

    private fun toast(text: String) = android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_LONG).show()

    fun removeSpot(id: String) = settings.removeSpot(id)
    fun holdLocation() = blueMob.location.hold()
    fun releaseLocation() = blueMob.location.release(keepForSharing = shareLocation.value)
    fun hasLocationPermission() = blueMob.location.hasPermission()

    fun isBot(nodeId: String) = nodeId == SkyBot.NODE_ID

    val matches = blueMob.matches.all
    fun challenge(nodeId: String, name: String, game: String): String? =
        blueMob.matches.invite(nodeId, name, game)?.id ?: null.also { toast("$name isn't in range right now. Games need them nearby.") }
    fun acceptGame(id: String) = blueMob.matches.accept(id)
    fun declineGame(id: String) = blueMob.matches.decline(id)
    fun playGame(id: String, spot: Int) = blueMob.matches.play(id, spot)
    fun gameAgain(id: String) = blueMob.matches.again(id)
    fun leaveGame(id: String) = blueMob.matches.leave(id)

    private val calls = blueMob.calls
    val call = calls.call
    val remoteFrame = calls.remoteFrame
    val localFrame = calls.localFrame
    fun startCall(nodeId: String, name: String, video: Boolean) { calls.start(nodeId, name, video)?.let(::toast) }
    fun acceptCall() = calls.accept()
    fun hangUp() = calls.hangUp()
    fun toggleMute() = calls.toggleMute()
    fun toggleSpeaker() = calls.toggleSpeaker()
    fun toggleCamera() = calls.toggleCamera()
    fun canUseCamera() = calls.hasCamera()
    fun wantsFrame() = calls.wantsFrame()
    fun onCameraFrame(frame: android.graphics.Bitmap) = calls.onCameraFrame(frame)

    private companion object {
        const val FRESH_FIX_MS = 2 * 60_000L
        const val GOOD_ACCURACY_M = 60f
        const val FIX_WAIT_MS = 90_000L
    }
}

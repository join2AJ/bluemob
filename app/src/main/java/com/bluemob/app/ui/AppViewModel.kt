package com.bluemob.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bluemob.app.BlueMobApp
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.mesh.LinkQuality
import com.bluemob.app.mesh.PeerState
import com.bluemob.app.settings.SignalMode
import com.bluemob.app.settings.Spot
import com.bluemob.app.util.Geo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

enum class Presence { ONLINE, IN_RANGE, OFFLINE }

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
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val blueMob = app as BlueMobApp
    private val identity = blueMob.identity
    private val mesh = blueMob.mesh
    private val repo = blueMob.messages
    private val sosManager = blueMob.sos
    private val settings = blueMob.settings

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
    fun headings(): Flow<Float> = blueMob.heading.headings()

    /** Ticks every 30 s so "last seen 5 min ago" stays fresh. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val people: StateFlow<List<Person>> = combine(
        mesh.peers, blueMob.contacts.contacts, myLocation, clock, sosManager.received,
    ) { peers, contacts, me, now, sos ->
        val names = contacts.values.groupingBy { it.name }.eachCount()
        contacts.values.map { c ->
            val link = peers.values.filter { it.nodeId == c.nodeId }.maxByOrNull { it.state.ordinal }
            val presence = when (link?.state) {
                PeerState.CONNECTED -> Presence.ONLINE
                PeerState.CONNECTING, PeerState.DISCOVERED -> Presence.IN_RANGE
                null -> Presence.OFFLINE
            }
            val theirs = c.location ?: sos[c.nodeId]?.let { s -> if (s.lat != null && s.lon != null) GeoPoint(s.lat, s.lon, 0f, s.at) else null }
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
    fun cancelSos() = sosManager.cancel()
    fun dismissSosAlert() = sosManager.dismissAlert()
    fun setSignalDefault(mode: SignalMode) = settings.setSignalDefault(mode)
    fun batteryPct(): Int? = sosManager.batteryPct()

    fun toggleBookmark(articleId: String) = settings.toggleBookmark(articleId)
    fun saveSpot(name: String): Boolean {
        val here = myLocation.value ?: blueMob.location.lastKnown() ?: return false
        settings.addSpot(Spot("spot-" + UUID.randomUUID().toString().take(8), name, here.lat, here.lon, System.currentTimeMillis()))
        return true
    }
    fun removeSpot(id: String) = settings.removeSpot(id)
    fun holdLocation() = blueMob.location.hold()
    fun releaseLocation() = blueMob.location.release(keepForSharing = shareLocation.value)
    fun hasLocationPermission() = blueMob.location.hasPermission()

    fun isBot(nodeId: String) = nodeId == SkyBot.NODE_ID
}

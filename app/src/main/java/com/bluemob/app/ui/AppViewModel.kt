package com.bluemob.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bluemob.app.BlueMobApp
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.chat.Conversation
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.mesh.LinkQuality
import com.bluemob.app.mesh.PeerState
import com.bluemob.app.util.Geo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

enum class Presence { ONLINE, IN_RANGE, OFFLINE }

/** One person, as the dashboard and chat list show them. */
data class Person(
    val nodeId: String,
    val name: String,
    val avatar: String?,
    val presence: Presence,
    val lastSeen: Long,
    val endpointId: String?,
    val quality: LinkQuality?,
    val distanceM: Double?,
    val bearingDeg: Double?,
    val theirLocationAgeMs: Long?,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val blueMob = app as BlueMobApp
    private val identity = blueMob.identity
    private val mesh = blueMob.mesh
    private val chats = blueMob.chats

    val nodeId = identity.nodeId
    val name = identity.displayName
    val avatar = identity.avatar
    val shareLocation = identity.shareLocation
    val onboardingDone = identity.onboardingDone
    val running = mesh.running
    val log = mesh.log
    val myLocation = blueMob.location.location
    val online = blueMob.connectivity.online
    val conversations: StateFlow<Map<String, Conversation>> = chats.conversations

    /** Ticks every 30 s so "last seen 5 min ago" stays fresh. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val people: StateFlow<List<Person>> = combine(
        mesh.peers, blueMob.contacts.contacts, myLocation, clock,
    ) { peers, contacts, me, now ->
        contacts.values.map { c ->
            val link = peers.values.filter { it.nodeId == c.nodeId }.maxByOrNull { it.state.ordinal }
            val presence = when (link?.state) {
                PeerState.CONNECTED -> Presence.ONLINE
                PeerState.CONNECTING, PeerState.DISCOVERED -> Presence.IN_RANGE
                null -> Presence.OFFLINE
            }
            val theirs: GeoPoint? = c.location
            Person(
                nodeId = c.nodeId,
                name = link?.name ?: c.name,
                avatar = c.avatar,
                presence = presence,
                lastSeen = if (presence == Presence.OFFLINE) c.lastSeen else now,
                endpointId = link?.endpointId,
                quality = link?.quality,
                distanceM = if (me != null && theirs != null) Geo.distanceM(me, theirs) else null,
                bearingDeg = if (me != null && theirs != null) Geo.bearingDeg(me, theirs) else null,
                theirLocationAgeMs = theirs?.let { now - it.time },
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
    fun connect(endpointId: String) = mesh.connect(endpointId)
    fun ping(nodeId: String) = mesh.ping(nodeId)
    val meshEvents = mesh.events

    fun send(nodeId: String, text: String) = chats.send(nodeId, text)
    fun openChat(nodeId: String?) {
        chats.openConversation = nodeId
        if (nodeId != null) chats.markRead(nodeId)
    }

    fun forgetPeople() = blueMob.contacts.forgetAll()

    fun isBot(nodeId: String) = nodeId == SkyBot.NODE_ID
}

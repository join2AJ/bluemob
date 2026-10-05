package com.bluemob.app

import android.app.Application
import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.bot.SkyFacts
import com.bluemob.app.chat.MessageRepository
import com.bluemob.app.compass.HeadingSensor
import com.bluemob.app.contacts.ContactsStore
import com.bluemob.app.data.BlueMobDatabase
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.identity.Identity
import com.bluemob.app.location.LocationTracker
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import com.bluemob.app.settings.AppSettings
import com.bluemob.app.sos.SignalController
import com.bluemob.app.rescue.RescueManager
import com.bluemob.app.sos.SosManager
import com.bluemob.app.system.Radios
import com.bluemob.app.trail.LostMode
import com.bluemob.app.trail.TrailRecorder
import com.bluemob.app.util.Connectivity
import com.bluemob.app.util.formatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Holds app-wide singletons so the mesh survives screen rotation and activity restarts. */
class BlueMobApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var identity: Identity private set
    lateinit var contacts: ContactsStore private set
    lateinit var mesh: NearbyMeshTransport private set
    lateinit var location: LocationTracker private set
    lateinit var messages: MessageRepository private set
    lateinit var connectivity: Connectivity private set
    lateinit var settings: AppSettings private set
    lateinit var signals: SignalController private set
    lateinit var sos: SosManager private set
    lateinit var heading: HeadingSensor private set
    lateinit var audit: AuditLog private set
    lateinit var trail: TrailRecorder private set
    lateinit var lost: LostMode private set
    lateinit var radios: Radios private set
    lateinit var rescue: RescueManager private set

    override fun onCreate() {
        super.onCreate()
        identity = Identity(this)
        contacts = ContactsStore(this)
        mesh = NearbyMeshTransport(this, identity, contacts)
        location = LocationTracker(this)
        connectivity = Connectivity(this)
        settings = AppSettings(this)
        signals = SignalController(this)
        heading = HeadingSensor(this)
        radios = Radios(this)
        val db = BlueMobDatabase.create(this)
        audit = AuditLog(db.audit(), appScope)
        audit.add(AuditKind.APP, "BlueMob started · ID BM ${formatId(identity.nodeId)}")
        trail = TrailRecorder(this, location, heading, db.trail(), settings, audit, appScope) { identity.shareLocation.value }
        lost = LostMode(mesh, identity, trail, audit, appScope)
        sos = SosManager(this, mesh, identity, trail, signals, audit, appScope)
        rescue = RescueManager(mesh, identity, db.rescue(), trail, location, sos, audit, appScope)
        messages = MessageRepository(db.messages(), mesh, appScope, sky = { text -> SkyBot.reply(text, skyFacts()) }) { kind, peer, text ->
            audit.add(kind, text.replace("{name}", contacts.contacts.value[peer]?.name ?: "someone"))
        }
        appScope.launch {
            mesh.events.collect { e ->
                if (e is MeshEvent.PeerConnected) audit.add(AuditKind.MESH, "Connected to ${contacts.contacts.value[e.nodeId]?.name ?: "someone"} over ${mesh.linkName(e.nodeId)}")
            }
        }

        // Share our GPS position with connected phones only while the user allows it.
        appScope.launch {
            identity.shareLocation.collect { share -> if (share) location.start() else location.stop() }
        }
        appScope.launch {
            combine(identity.shareLocation, location.location) { share, loc -> if (share) loc else null }
                .collect { mesh.updateMyLocation(it) }
        }
        appScope.launch {
            combine(identity.displayName, identity.avatar) { _, _ -> }.drop(1).collect { mesh.broadcastProfile() }
        }
    }

    /** What Sky can see on this phone right now. */
    private fun skyFacts(): SkyFacts {
        val all = messages.messages.value
        val people = contacts.contacts.value
        return SkyFacts(
            name = identity.displayName.value,
            bluemobId = "BM · " + formatId(identity.nodeId),
            meshOn = mesh.running.value,
            nearby = mesh.connectedNodes().map { id -> (people[id]?.name ?: "Someone") + ": " + mesh.linkName(id) + " link" },
            thisPhoneOnline = connectivity.online.value,
            batteryPct = sos.batteryPct(),
            waitingMessages = all.count { it.fromMe && (it.status == MessageStatus.PENDING || it.status == MessageStatus.SENT) },
            unread = all.count { !it.fromMe && it.status == MessageStatus.RECEIVED && it.peer != SkyBot.NODE_ID },
            sosActive = sos.mine.value != null,
        )
    }
}

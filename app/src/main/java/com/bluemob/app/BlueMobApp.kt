package com.bluemob.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.bluemob.app.service.MeshService
import com.bluemob.app.service.Notifier
import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.audit.AuditWitness
import com.bluemob.app.bridge.InternetBridge
import com.bluemob.app.trust.TrustManager
import com.bluemob.app.crypto.SecurePrefs
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
import com.bluemob.app.mesh.RoomRelayStore
import com.bluemob.app.contacts.Contact
import com.bluemob.app.crypto.KeyBook
import com.bluemob.app.settings.AppSettings
import com.bluemob.app.sos.SignalController
import com.bluemob.app.rescue.RescueManager
import com.bluemob.app.sos.SosManager
import com.bluemob.app.system.Radios
import com.bluemob.app.trail.LostMode
import com.bluemob.app.trail.TrailRecorder
import com.bluemob.app.util.Connectivity
import com.bluemob.app.util.CrashLog
import com.bluemob.app.util.formatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Holds app-wide singletons so the mesh survives screen rotation and activity restarts. */
class BlueMobApp : Application() {
    /** App-wide work. An error in one task is recorded instead of closing the whole app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        android.util.Log.e("BlueMob", "Background task failed", e)
        CrashLog.saveNonFatal(this, e)
    })

    lateinit var identity: Identity private set
    lateinit var contacts: ContactsStore private set
    lateinit var database: BlueMobDatabase private set
    lateinit var backups: com.bluemob.app.backup.BackupManager private set
    lateinit var mesh: NearbyMeshTransport private set
    lateinit var location: LocationTracker private set
    lateinit var messages: MessageRepository private set
    lateinit var connectivity: Connectivity private set
    lateinit var settings: AppSettings private set
    lateinit var signals: SignalController private set
    lateinit var sos: SosManager private set
    lateinit var groups: com.bluemob.app.chat.GroupStore private set
    lateinit var sosCircle: com.bluemob.app.sos.SosCircle private set
    lateinit var peerStatus: com.bluemob.app.nearby.PeerStatus private set
    lateinit var heading: HeadingSensor private set
    lateinit var audit: AuditLog private set
    lateinit var trail: TrailRecorder private set
    lateinit var lost: LostMode private set
    lateinit var radios: Radios private set
    lateinit var rescue: RescueManager private set
    lateinit var keyBook: KeyBook private set
    lateinit var notifier: Notifier private set
    lateinit var witness: AuditWitness private set
    lateinit var bridge: InternetBridge private set
    lateinit var trust: TrustManager private set
    lateinit var matches: com.bluemob.app.games.Matches private set
    lateinit var calls: com.bluemob.app.call.CallManager private set
    lateinit var lock: com.bluemob.app.account.AppLock private set
    lateinit var profile: com.bluemob.app.account.ProfileStore private set
    lateinit var files: com.bluemob.app.files.FileShare private set
    lateinit var voiceNotes: com.bluemob.app.files.VoiceNotes private set
    lateinit var callLog: com.bluemob.app.data.CallLogDao private set
    lateinit var live: com.bluemob.app.bridge.LiveLink private set
    private var ratingPackets: List<org.json.JSONObject> = emptyList()
    private var auditEntries: List<com.bluemob.app.data.AuditEntry> = emptyList()
    private var auditHead: com.bluemob.app.data.AuditEntry? = null

    /** True while any BlueMob screen is visible. Notifications are only shown when it isn't. */
    @Volatile var inForeground = false
        private set

    /** People whose online status matters right now (the open chat or call), checked first. */
    @Volatile var watchPresence: () -> List<String> = { listOfNotNull(calls.call.value?.peer) }

    /** Set if BlueMob couldn't start; the activity shows it instead of closing. */
    var startupError: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        CrashLog.forgetOtherVersions(this) // an update starts fresh instead of showing the old version's crash
        // The last launch died while starting (for example inside native code): show what happened instead of
        // dying again. "Try again" on that screen clears this and starts normally.
        // One unexplained stop (no error recorded: the phone closed BlueMob, or it was updated mid-start) gets a quiet
        // second try; only a second stop in a row shows the error screen.
        CrashLog.unfinishedStep(this)?.takeIf { CrashLog.read(this) == null && CrashLog.firstRetry(this) }?.let { step ->
            CrashLog.saveNonFatal(this, IllegalStateException("BlueMob stopped while starting last time, during: $step (retried)"))
            CrashLog.started(this, keepRetry = true)
        }
        CrashLog.unfinishedStep(this)?.let { step ->
            startupError = IllegalStateException("BlueMob stopped while starting last time, during: $step")
            if (CrashLog.read(this) == null) CrashLog.save(this, startupError!!, "previous launch")
            return
        }
        try {
            start()
        } catch (t: Throwable) {
            startupError = t
            CrashLog.save(this, t, "startup")
        }
    }

    private fun start() {
        CrashLog.step(this, "setting up notifications")
        notifier = Notifier(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(a: Activity) { started++; inForeground = true }
            override fun onActivityStopped(a: Activity) { started--; inForeground = started > 0 }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
        CrashLog.step(this, "unlocking your identity (Android Keystore)")
        identity = Identity(this)
        lock = com.bluemob.app.account.AppLock(this)
        com.bluemob.app.guide.GuidePacks.init(SecurePrefs.open(this, "guides"))
        com.bluemob.app.bot.SkyMemory.init(SecurePrefs.open(this, "sky"))
        profile = com.bluemob.app.account.ProfileStore(SecurePrefs.open(this, "profile"))
        CrashLog.step(this, "unlocking encrypted contacts")
        contacts = ContactsStore(this)
        // Robolectric (the app-startup test) can't load SQLCipher's native library; real phones always encrypt.
        CrashLog.step(this, "opening the encrypted database (SQLCipher)")
        val db = BlueMobDatabase.create(this, encrypted = android.os.Build.FINGERPRINT != "robolectric")
        // Open it now, inside this protected start-up, so any problem (key, native library, upgrade) is caught here.
        db.openHelper.writableDatabase
        database = db
        CrashLog.step(this, "starting the mesh")
        keyBook = loadKeyBook()
        mesh = NearbyMeshTransport(this, identity, contacts, keyBook, RoomRelayStore(db.relay(), appScope))
        location = LocationTracker(this)
        connectivity = Connectivity(this)
        settings = AppSettings(this)
        signals = SignalController(this)
        heading = HeadingSensor(this)
        radios = Radios(this)
        // Nearby switches Bluetooth on when the mesh starts: only allowed if it's already on, or the user said so.
        mesh.radiosAllowed = { radios.state.value.bluetooth || settings.bluetoothPolicy.value == com.bluemob.app.settings.RadioPolicy.ALLOW }
        CrashLog.step(this, "starting the audit trail, SOS and relay")
        audit = AuditLog(db.audit(), appScope, identity.keys, SecurePrefs.open(this, "audit"))
        witness = AuditWitness(identity.keys, SecurePrefs.open(this, "witness")) { contacts.contacts.value[it]?.name ?: "someone" }
        // Every phone we meet gets a signed note of our newest audit entry, and hands back the one it kept.
        mesh.extraTypes = mesh.extraTypes + setOf(AuditWitness.TYPE_HEAD, AuditWitness.TYPE_ECHO)
        mesh.onConnectedPackets = { node -> listOfNotNull(witness.headNote(auditHead), witness.echoFor(node)) }
        appScope.launch { audit.entries.collect { auditEntries = it; auditHead = it.lastOrNull() } }
        appScope.launch {
            mesh.events.collect { e ->
                if (e !is MeshEvent.Extra) return@collect
                when (e.type) {
                    AuditWitness.TYPE_HEAD -> witness.onHead(e.fromNodeId, e.json)
                    AuditWitness.TYPE_ECHO -> witness.onEcho(e.fromNodeId, e.json, auditEntries)
                }
            }
        }
        audit.add(AuditKind.APP, "BlueMob started · ID BM ${formatId(identity.nodeId)}")
        identity.previousNodeId?.let { audit.add(AuditKind.APP, "BlueMob ID changed from BM ${formatId(it)} to a key-based ID that can't be copied") }
        trail = TrailRecorder(this, location, heading, db.trail(), db.trips(), settings, audit, appScope) { identity.shareLocation.value }
        sos = SosManager(this, mesh, identity, trail, signals, audit, appScope) { profile.profile.value.let { it.age to it.bloodGroup } }
        lost = LostMode(mesh, identity, trail, audit, appScope, location, signals, sosActive = { sos.mine.value != null }, say = ::say)
        matches = com.bluemob.app.games.Matches(mesh, appScope) { m ->
            if (!inForeground) notifier.note("${m.opponentName} wants to play", "${com.bluemob.app.games.Match.title(m.game)} over the mesh. Tap to answer.", "games")
        }
        calls = com.bluemob.app.call.CallManager(this, mesh, audit, appScope, onIncoming = { c ->
            if (!inForeground) notifier.note("${c.name} is calling", "${if (c.video) "Video" else "Voice"} call from someone nearby. Tap to answer.", "call", id = 7_007)
        }, relaySet = { settings.bridgeUrl.value.isNotBlank() },
            cipherFor = { peer, cid -> keyBook.key(peer)?.let { com.bluemob.app.mesh.CallCipher(com.bluemob.app.crypto.Crypto.sharedKey(identity.keys.keyPair.private, it, identity.nodeId, peer), cid, identity.nodeId) } },
            keepAlive = { active, video -> com.bluemob.app.service.CallService.update(this, active, video) },
            log = { entry ->
            db.calls().insert(entry)
            if (entry.outcome == "MISSED" && !inForeground) notifier.note("Missed call from ${entry.name}", "${if (entry.video) "Video" else "Voice"} call. Tap to call back.", "calls", id = 7_008)
        })
        bridge = InternetBridge(settings, identity, keyBook, mesh, connectivity, audit, appScope)
        // Live link to the relay for internet calls: stays signed in while there's internet and a relay is set.
        live = com.bluemob.app.bridge.LiveLink({ settings.bridgeUrl.value }, identity.keys, appScope, { connectivity.online.value })
        mesh.live = live
        live.onPoke = { bridge.syncNow() }
        // Firebase wake-ups (when configured): register this phone's token with the relay.
        if (com.bluemob.app.push.Push.init(this)) com.bluemob.app.push.Push.token { live.setPushToken(it) }
        // The ringing notification goes once the call is answered, declined or over.
        appScope.launch { calls.call.collect { c -> if (c?.phase != com.bluemob.app.call.CallPhase.INCOMING) notifier.cancel(com.bluemob.app.service.Notifier.INCOMING_CALL_ID) } }
        bridge.client.liveSend = { to, packet -> live.sendText(to, packet) }
        live.start()
        // Who's online over the internet, for chats and calls: the people we talk to, every 15 seconds.
        appScope.launch {
            while (true) {
                if (live.connected.value) live.askPresence((watchPresence() + contacts.contacts.value.values.sortedByDescending { it.lastSeen }.map { it.nodeId }).distinct().take(50))
                kotlinx.coroutines.delay(15_000)
            }
        }
        trust = TrustManager(db.ratings(), identity, mesh, bridge.client, contacts, audit, appScope)
        // Ratings travel as people meet: each phone gives the ones it holds to every phone it connects to.
        appScope.launch { trust.ratings.collect { ratingPackets = trust.packetsToShare() } }
        mesh.onConnectedPackets = { node -> listOfNotNull(witness.headNote(auditHead), witness.echoFor(node)) + ratingPackets }
        appScope.launch {
            trust.aboutMe.collect { r ->
                if (!inForeground) notifier.rating("${r.raterName}: ${r.kind.label}" + if (r.remark.isNotBlank()) " · “${r.remark}”" else "")
            }
        }
        bridge.client.ratingSubjects = { mesh.connectedNodes() + sos.received.value.keys + contacts.contacts.value.keys.take(20) }
        rescue = RescueManager(mesh, identity, db.rescue(), trail, location, sos, audit, appScope)
        files = com.bluemob.app.files.FileShare(this, mesh, db.messages(), audit, appScope)
        // Files for people who aren't nearby go through the relay, encrypted as they are.
        files.net = com.bluemob.app.bridge.RelayFiles({ settings.bridgeUrl.value.takeIf { it.isNotBlank() } }, identity.keys)
        files.netUp = { connectivity.online.value && settings.bridgeUrl.value.isNotBlank() }
        // Each time the live link (re)connects, anything waiting goes: messages, delivery and read receipts, files.
        appScope.launch { live.connected.collect { if (it) { mesh.router.onInternetUp(); files.pushAllOnline() } } }
        voiceNotes = com.bluemob.app.files.VoiceNotes(this, appScope)
        callLog = db.calls()
        backups = com.bluemob.app.backup.BackupManager(this)
        groups = com.bluemob.app.chat.GroupStore(SecurePrefs.open(this, "groups"))
        messages = MessageRepository(db.messages(), mesh.router, appScope, onAttachment = { files.onMessage(it) }, sky = { text -> SkyBot.reply(text, skyFacts()) },
            record = { kind, peer, text -> audit.add(kind, text.replace("{name}", contacts.contacts.value[peer]?.name ?: "someone")) },
            onIncoming = { peer, text -> if (!inForeground) notifier.message(peer, groups.get(peer)?.let { "👥 " + it.name } ?: contacts.contacts.value[peer]?.name ?: "Someone", text) },
            groups = groups, me = { identity.nodeId to identity.displayName.value },
        )
        // SOS contacts: alerted in BlueMob (online, nearby, or when they next connect) when we send an SOS.
        sosCircle = com.bluemob.app.sos.SosCircle(settings, mesh, identity.keys, { identity.displayName.value },
            { profile.profile.value.takeIf { it.verified }?.phone }, { settings.bridgeUrl.value.takeIf { it.isNotBlank() } },
            sendChat = { peer, text -> messages.send(peer, text) }, sos = sos, prefs = SecurePrefs.open(this, "sos_circle"), scope = appScope,
            onRequest = { r -> notifier.note("${r.name} wants you as their SOS contact", "Open BlueMob to accept: you'd get an alert if they ever send an SOS.", null) })
        peerStatus = com.bluemob.app.nearby.PeerStatus(mesh, appScope,
            battery = { sos.batteryPct()?.let { it to (getSystemService(android.os.BatteryManager::class.java)?.isCharging == true) } },
            onRequest = { r -> if (!inForeground) notifier.note("${r.name} is checking on everyone", "Open BlueMob and tap I'm OK, or I need help.", null) })
        sos.onSent = { sosCircle.alert(it) }
        sos.onSafe = { sosCircle.safe(it) }
        appScope.launch { live.connected.collect { if (it) { sosCircle.registerNumber(); syncEmail(); sosCircle.recheck() } } }
        // SOS contacts who hadn't joined BlueMob yet: look again every few hours and ask them as soon as they have.
        appScope.launch { while (true) { kotlinx.coroutines.delay(3 * 3_600_000L); if (live.connected.value) sosCircle.recheck() } }
        appScope.launch { sos.alert.collect { a -> if (a != null && !inForeground && a.id != SosManager.PREVIEW_ID) notifier.sos(a) } }
        // "I'm safe": take the alarm notification down too.
        appScope.launch { sos.incoming.collect { if (it.cancelled) notifier.sosEnded(it) } }
        appScope.launch { rescue.notices.collect { n -> if (!inForeground) notifier.rescue(n.room, n.text) } }
        // Start-up itself is done. Android also starts BlueMob with no screen (background service, scheduled backups,
        // wake-ups); the screen marks its own step when it opens (MainActivity), so a background start that's later
        // closed normally is never mistaken for a crash. The retry marker stays until a screen really opens.
        CrashLog.started(this, keepRetry = true)
        // The mesh keeps running in the background (with its notification) whenever it's on.
        appScope.launch {
            combine(mesh.running, settings.background) { on, bg -> on && bg }.collect { keep ->
                if (keep) MeshService.start(this@BlueMobApp) else MeshService.stop(this@BlueMobApp)
            }
        }
        // Bluetooth or Wi-Fi switched off and on: Nearby doesn't recover by itself, so restart the mesh.
        appScope.launch {
            var before = radios.state.value
            radios.state.collect { now ->
                val back = (now.bluetooth && !before.bluetooth) || (now.wifi && !before.wifi) || (!now.airplane && before.airplane)
                val gone = !now.bluetooth && before.bluetooth
                if (back || gone) mesh.onRadiosChanged(now.bluetooth)
                before = now
            }
        }
        // People who message us by our ID, without having met: add them so they show up in Chats.
        appScope.launch {
            mesh.router.events.collect { e ->
                if (e is MeshEvent.MessageReceived && e.hops > 1) {
                    val name = e.name ?: ("BM " + formatId(e.fromNodeId).take(9))
                    contacts.upsert(e.fromNodeId) { c -> c?.let { if (it.lastSeen == 0L) it.copy(name = name) else it } ?: Contact(e.fromNodeId, name, null, 0) }
                }
            }
        }
        appScope.launch {
            mesh.events.collect { e ->
                if (e is MeshEvent.PeerConnected) audit.add(AuditKind.MESH, "Connected to ${contacts.contacts.value[e.nodeId]?.name ?: "someone"} over ${mesh.linkName(e.nodeId)}")
            }
        }

        // Share our GPS position with connected phones only while the user allows it.
        appScope.launch {
            identity.shareLocation.collect { share -> location.setSharing(share) }
        }
        appScope.launch {
            combine(identity.shareLocation, location.location) { share, loc -> if (share) loc else null }
                .collect { mesh.updateMyLocation(it) }
        }
        appScope.launch {
            combine(identity.displayName, identity.avatar) { _, _ -> }.drop(1).collect { mesh.broadcastProfile() }
        }
    }

    /** A short note for the user: a toast while BlueMob is open, a notification while it's in the background. */
    fun say(text: String) {
        if (inForeground) android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show()
        else notifier.note("BlueMob", text)
    }

    /** Public keys we've learned, saved so we can message people later without asking the mesh again. */
    private fun loadKeyBook(): KeyBook {
        val prefs = com.bluemob.app.crypto.SecurePrefs.open(this, "keys")
        val initial = runCatching { org.json.JSONObject(prefs.getString("book", "{}")!!).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrDefault(emptyMap())
        var latest = initial
        val save = com.bluemob.app.util.Debounced(2_000) { prefs.edit().putString("book", org.json.JSONObject(latest).toString()).apply() }
        return KeyBook(initial) { latest = it; save() }
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

    /** The optional recovery email, sent to the relay once (and again whenever it changes). */
    suspend fun syncEmail() = com.bluemob.app.account.RecoveryEmail.sync(profile, settings.bridgeUrl.value.takeIf { it.isNotBlank() }, identity.keys)
}

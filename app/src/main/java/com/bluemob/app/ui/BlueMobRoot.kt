package com.bluemob.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.guide.GuideContent
import com.bluemob.app.ui.chat.ChatScreen
import com.bluemob.app.ui.chat.NewChatScreen
import com.bluemob.app.ui.chat.ChatsScreen
import com.bluemob.app.ui.chat.MessageInfoScreen
import com.bluemob.app.ui.compass.CompassScreen
import com.bluemob.app.ui.components.SosPill
import com.bluemob.app.ui.dashboard.NearbyScreen
import com.bluemob.app.ui.dashboard.NearbyState
import com.bluemob.app.ui.guide.ArticleScreen
import com.bluemob.app.ui.guide.GuideScreen
import com.bluemob.app.ui.onboarding.OnboardingScreen
import com.bluemob.app.ui.profile.ProfileScreen
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.sos.SosManager
import com.bluemob.app.ui.compass.TrailActions
import com.bluemob.app.ui.compass.TrailUi
import com.bluemob.app.ui.games.ConnectFourScreen
import com.bluemob.app.ui.games.GamesScreen
import com.bluemob.app.ui.games.MatchScreen
import com.bluemob.app.ui.games.TicTacToeScreen
import com.bluemob.app.ui.profile.AuditScreen
import com.bluemob.app.ui.profile.PersonScreen
import com.bluemob.app.rescue.RescueNotice
import com.bluemob.app.ui.rescue.RescueActions
import com.bluemob.app.ui.rescue.RescueScreen
import com.bluemob.app.ui.sos.SosAlert
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.bluemob.app.ui.sos.SosContactsScreen
import com.bluemob.app.ui.sos.SosHubState
import com.bluemob.app.ui.system.ConnectionsScreen
import com.bluemob.app.ui.system.BridgeScreen
import com.bluemob.app.ui.sos.SosHubScreen
import com.bluemob.app.ui.sos.SosSignalScreen
import com.bluemob.app.ui.theme.Extra

private enum class Tab(val label: String, val icon: ImageVector) {
    NEARBY("Nearby", Icons.Outlined.Radar),
    CHATS("Chats", Icons.AutoMirrored.Outlined.Chat),
    COMPASS("Compass", Icons.Outlined.Explore),
    GUIDE("Guide", Icons.AutoMirrored.Outlined.MenuBook),
    YOU("You", Icons.Outlined.Person),
}

data class SystemStatus(val permissionsGranted: Boolean, val locationServicesOff: Boolean, val keepsRunning: Boolean, val locationPermission: Boolean)

/** What the activity does for the UI: permission prompts and Android settings screens. */
class SystemActions(
    val requestMeshPermissions: () -> Unit,
    val openLocationSettings: () -> Unit,
    val enableLocationSharing: () -> Unit,
    val requestLocation: () -> Unit,
    val openBatterySaver: () -> Unit,
    val askKeepRunning: () -> Unit,
    val switchRadio: (com.bluemob.app.system.Radio, Boolean) -> Unit = { _, _ -> },
    val textSos: (List<String>, String) -> Unit = { _, _ -> },
    val requestSteps: () -> Unit = {},
    val shareId: () -> Unit = {},
    /** Asks for the microphone (and camera for video), then runs the callback whatever the answer. */
    val requestCallPermissions: (Boolean, () -> Unit) -> Unit = { _, then -> then() },
    val canUseBiometric: () -> Boolean = { false },
    /** Shows the phone's fingerprint / face prompt and calls back on success. */
    val biometricUnlock: (() -> Unit) -> Unit = {},
    val restartApp: () -> Unit = {},
    val leaveApp: () -> Unit = {},
    /** Shares a file (e.g. a trip as GPX) through Android's share sheet. */
    val shareFile: (java.io.File, String) -> Unit = { _, _ -> },
    val backup: com.bluemob.app.ui.system.BackupActions = com.bluemob.app.ui.system.BackupActions({}, { _, _ -> }, {}, {}),
    /** Opens the phone's picker: "photo", "video" or "doc". */
    val pickFile: (String, (android.net.Uri) -> Unit) -> Unit = { _, _ -> },
    /** Opens a (decrypted, temporary) file in another app. */
    val openFile: (java.io.File, String) -> Unit = { _, _ -> },
)

@Composable
fun BlueMobRoot(vm: AppViewModel, system: SystemStatus, actions: SystemActions) {
    val onboarded by vm.onboardingDone.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val profile by vm.profile.collectAsStateWithLifecycle()
        var restoring by rememberSaveable { mutableStateOf(false) }
        var introSeen by rememberSaveable { mutableStateOf(false) }
        // People who used BlueMob before sign-up existed verify their number once, with their name filled in.
        val upgrading = rememberSaveable { onboarded && !profile.verified }
        val stage = when {
            restoring && !profile.verified -> "restore"
            !onboarded && !introSeen -> "intro"
            !profile.verified -> "signup"
            else -> "app"
        }
        AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { s ->
            when (s) {
                "restore" -> {
                    BackHandler { restoring = false }
                    com.bluemob.app.ui.account.RestoreScreen(onBack = { restoring = false }, onRestore = { code ->
                        vm.restore(code).also { if (it == null) actions.restartApp() }
                    })
                }
                "intro" -> OnboardingScreen(initialName = name, initialAvatar = avatar, onFinish = { _, _ -> introSeen = true }, onRestore = { restoring = true })
                "signup" -> com.bluemob.app.ui.account.SignupFlow(
                    upgrading = upgrading, initialName = if (upgrading) name else "", initialAvatar = avatar,
                    bluemobId = com.bluemob.app.util.formatId(vm.nodeId),
                    onRestore = if (upgrading) null else ({ restoring = true }),
                    onDone = { r ->
                        vm.completeSignup(r)
                        if (!system.permissionsGranted) actions.requestMeshPermissions() else vm.startMesh()
                    },
                )
                else -> MainShell(vm, system, actions)
            }
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel, system: SystemStatus, actions: SystemActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.NEARBY) }
    var compassTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val stack = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) { mutableStateListOf<String>() }
    val top = stack.lastOrNull()
    fun push(route: String) { stack.add(route) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun goTab(t: Tab) { stack.clear(); tab = t }

    val name by vm.name.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val typing by vm.typing.collectAsStateWithLifecycle()
    val sharing by vm.shareLocation.collectAsStateWithLifecycle()
    val myFix by vm.myLocation.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val mySos by vm.mySos.collectAsStateWithLifecycle()
    val alert by vm.sosAlert.collectAsStateWithLifecycle()
    val signalDefault by vm.signalDefault.collectAsStateWithLifecycle()
    val spots by vm.spots.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val headings = remember { vm.headings() }
    val sosContacts by vm.sosContacts.collectAsStateWithLifecycle()
    val radios by vm.radios.collectAsStateWithLifecycle()
    val trailOn by vm.trailOn.collectAsStateWithLifecycle()
    val trailPoints by vm.trailPoints.collectAsStateWithLifecycle()
    val estimate by vm.estimate.collectAsStateWithLifecycle()
    val lostOn by vm.lostOn.collectAsStateWithLifecycle()
    var sosReached by rememberSaveable { mutableStateOf(-1) }
    val rescues by vm.rescues.collectAsStateWithLifecycle()
    val trustScores by vm.trustScores.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<RescueNotice?>(null) }
    LaunchedEffect(Unit) { vm.rescueNotices.collect { n -> notice = n } }
    val pendingRoute by vm.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) { pendingRoute?.let { stack.clear(); if (it == "calls") goTab(Tab.CHATS) else push(it); vm.pendingRoute.value = null } }
    LaunchedEffect(notice) { if (notice != null) { delay(6_000); notice = null } }
    val hereFix = myFix ?: estimate?.let { GeoPoint(it.lat, it.lon, it.uncertaintyM.toFloat(), it.at) }
    val askSteps = { if (vm.stepCounterAvailable && !vm.hasStepPermission()) actions.requestSteps() }

    LaunchedEffect(top) { vm.openChat(top?.takeIf { it.startsWith("chat:") }?.removePrefix("chat:")) }
    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.NEARBY) { if (stack.isNotEmpty()) pop() else tab = Tab.NEARBY }

    val fileProgress by vm.fileProgress.collectAsStateWithLifecycle()
    val voicePlaying by vm.voicePlaying.collectAsStateWithLifecycle()
    val recordingMs by vm.recordingMs.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun chatFiles(peer: String) = com.bluemob.app.ui.chat.ChatFiles(
        progress = fileProgress, playing = voicePlaying, recordingMs = recordingMs,
        thumbnail = { vm.thumbnail(it) },
        onOpen = { m ->
            val att = com.bluemob.app.files.Attachment.fromJson(m.att)
            if (att?.kind == com.bluemob.app.files.AttKind.IMAGE) push("photo:" + m.id)
            else scope.launch { vm.openCopy(m)?.let { actions.openFile(it, att?.mime ?: "*/*") } }
        },
        onPlay = vm::playVoice,
        onAttach = { kind -> actions.pickFile(kind) { uri -> vm.sendFile(peer, uri) } },
        onRecordStart = { if (!vm.startVoiceNote()) actions.requestCallPermissions(false) {} },
        onRecordStop = { send -> vm.stopVoiceNote(peer, send) },
        onMedia = { push("media:$peer") },
    )
    val toggleLocation: (Boolean) -> Unit = { on -> if (on) actions.enableLocationSharing() else vm.setShareLocation(false) }
    /** Buttons under Sky's replies. */
    val onSkyAction: (String) -> Unit = { target ->
        when {
            target == "sos" -> push("sos")
            target == "sos_signal" -> push("signal")
            target == "power" -> actions.openBatterySaver()
            target == "games" -> push("games")
            target.startsWith("guide:") -> push("article:" + target.removePrefix("guide:"))
            target.startsWith("tab:") -> goTab(when (target.removePrefix("tab:")) {
                "nearby" -> Tab.NEARBY; "chats" -> Tab.CHATS; "compass" -> Tab.COMPASS; "guide" -> Tab.GUIDE; else -> Tab.YOU
            })
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(top, transitionSpec = {
            if (targetState != null && (initialState == null || stack.size > 0)) slideInHorizontally { it / 3 } + fadeIn() togetherWith fadeOut()
            else fadeIn() togetherWith fadeOut()
        }, label = "screen") { route ->
            when {
                route == null -> Tabs(tab, vm.unreadCount(conversations), onTab = { tab = it }, onSos = { push("sos") }) { padding ->
                    when (tab) {
                        Tab.NEARBY -> NearbyScreen(
                            NearbyState(name, running, people, system.permissionsGranted, system.locationServicesOff, sharing, myFix != null, online, radios,
                                vm.otherVersions.collectAsStateWithLifecycle().value.values.map { it.name }),
                            padding, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                            onRequestPermissions = actions.requestMeshPermissions, onOpenLocationSettings = actions.openLocationSettings,
                            onShareLocation = { toggleLocation(true) }, onOpenChat = { push("chat:$it") },
                            onTalkToSky = { push("chat:" + SkyBot.NODE_ID) }, onOpenGuide = { tab = Tab.GUIDE }, onOpenBattery = { tab = Tab.YOU },
                            onConnections = { push("connections") }, onFixRadio = { actions.switchRadio(it, true) }, onGames = { push("games") },
                            onFindLost = { compassTarget = it; goTab(Tab.COMPASS) },
                        )
                        Tab.CHATS -> ChatsScreen(people, conversations, typing, padding, rescues, onOpenRescue = { push("rescue:$it") }, onNewChat = { push("newchat") },
                            calls = vm.callLog.collectAsStateWithLifecycle().value,
                            onCallBack = { id, n, video -> actions.requestCallPermissions(video) { vm.startCall(id, n, video) } },
                            onClearCalls = { vm.clearCallLog() }, onDeleteCall = { vm.deleteCall(it) },
                            filesFor = { chatFiles(it) }) { push("chat:$it") }
                        Tab.COMPASS -> CompassScreen(
                            people, spots, hereFix, headings, vm.compassAvailable, system.locationPermission, compassTarget, padding,
                            onHoldLocation = vm::holdLocation, onReleaseLocation = vm::releaseLocation, onRequestLocation = actions.requestLocation,
                            onSaveSpot = { if (!system.locationPermission) actions.requestLocation() else vm.saveSpot("Spot ${spots.count { !it.isBaseCamp } + 1}") }, onRemoveSpot = vm::removeSpot,
                            trail = TrailUi(trailOn, trailPoints, estimate, lostOn, vm.hasStepPermission() && vm.stepCounterAvailable, vm.stepCounterAvailable),
                            trailActions = TrailActions(
                                onTrail = { on -> if (on) { if (!system.locationPermission) actions.requestLocation(); askSteps() }; vm.setTrail(on) },
                                onLost = { on -> if (on) { if (!system.locationPermission) actions.requestLocation(); askSteps() }; vm.setLost(on) },
                                onBaseCamp = { if (!system.locationPermission) actions.requestLocation() else vm.setBaseCamp() }, onClear = vm::clearTrail, onAllowSteps = actions.requestSteps,
                                onTrips = { push("trips") },
                            ),
                            onRing = vm::ring,
                        )
                        Tab.GUIDE -> GuideScreen(bookmarks, padding, onOpen = { push("article:$it") }, onSos = { push("sos") }, onMore = { push("guide-packs") })
                        Tab.YOU -> ProfileScreen(
                            name, avatar, vm.nodeId, running, sharing, system.keepsRunning, signalDefault, log, padding,
                            onName = vm::setName, onAvatar = vm::setAvatar, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                            onToggleLocation = toggleLocation, onBatterySaver = actions.openBatterySaver, onKeepRunning = actions.askKeepRunning,
                            onSos = { push("sos") }, onReplayIntro = vm::replayIntro, onForgetPeople = vm::forgetPeople, onClearMessages = vm::clearMessages,
                            onBridge = { push("bridge") }, onMyRating = { push("person:" + vm.nodeId) },
                            lastError = remember { vm.lastError() },
                            myStars = (trustScores[vm.nodeId] ?: vm.scoreFor(vm.nodeId)).stars, myRatingCount = trustScores[vm.nodeId]?.ratings ?: 0,
                            onConnections = { push("connections") }, onSosContacts = { push("sos-contacts") }, onAudit = { push("audit") },
                            onGames = { push("games") }, onAccount = { push("account") }, onAutoStart = { push("autostart") }, onBackup = { push("backup") }, sosContactCount = sosContacts.size,
                            background = vm.background.collectAsStateWithLifecycle().value, onBackground = vm::setBackground,
                        )
                    }
                }
                route.startsWith("chat:") -> {
                    val id = route.removePrefix("chat:")
                    ChatScreen(
                        nodeId = id, person = people.firstOrNull { it.nodeId == id }, messages = conversations[id].orEmpty(), typing = id in typing,
                        meshEvents = vm.meshEvents, myName = name, myId = vm.nodeId, onBack = ::pop, onSend = { vm.send(id, it) },
                        onPing = { vm.ping(id) }, onInfo = { push("info:$it") }, onPerson = { if (id != SkyBot.NODE_ID) push("person:$id") }, onAction = onSkyAction,
                        files = chatFiles(id),
                        onCall = { video ->
                            val who = people.firstOrNull { it.nodeId == id }?.name ?: "them"
                            actions.requestCallPermissions(video) { vm.startCall(id, who, video) }
                        },
                    )
                }
                route.startsWith("media:") -> {
                    val id = route.removePrefix("media:")
                    com.bluemob.app.ui.chat.MediaScreen(people.firstOrNull { it.nodeId == id }?.name ?: "them", conversations[id].orEmpty(), chatFiles(id), ::pop)
                }
                route.startsWith("photo:") -> {
                    val m = vm.message(route.removePrefix("photo:"))
                    if (m == null) LaunchedEffect(Unit) { pop() } else PhotoViewer(m, vm, ::pop)
                }
                route.startsWith("info:") -> {
                    val m = vm.message(route.removePrefix("info:"))
                    if (m == null) LaunchedEffect(Unit) { pop() }
                    else MessageInfoScreen(m, name, vm.nodeId, people.firstOrNull { it.nodeId == m.peer }?.name ?: "them", ::pop)
                }
                route.startsWith("article:") -> {
                    val a = GuideContent.byId(route.removePrefix("article:"))
                    if (a == null) LaunchedEffect(Unit) { pop() }
                    else ArticleScreen(a, a.id in bookmarks, ::pop, onToggleSaved = { vm.toggleBookmark(a.id) }, onSos = { push("sos") }, onOpen = { push("article:$it") })
                }
                route == "sos" -> SosHubScreen(
                    SosHubState(mySos, people.count { it.presence == Presence.ONLINE }, signalDefault, sosContacts, sosReached),
                    message = vm::sosMessage, onBack = ::pop,
                    onSend = { sosReached = vm.sendSos(it) }, onSafe = { vm.cancelSos(); sosReached = -1 }, onSignal = { push("signal") },
                    onDefaultSignal = vm::setSignalDefault, onHowToHelp = { push("article:help-sos") }, onContacts = { push("sos-contacts") },
                    onText = actions.textSos, onPreviewAlert = vm::previewSosAlert,
                    rescue = rescues.firstOrNull { it.mine && it.id == mySos?.id }, onOpenRescue = { mySos?.let { push("rescue:" + it.id) } },
                )
                route == "newchat" -> NewChatScreen(vm.nodeId, people, ::pop,
                    onStart = { id, n -> vm.startChatById(id, n); pop(); push("chat:$id") },
                    onOpen = { pop(); push("chat:$it") }, onShareId = actions.shareId)
                route.startsWith("rescue:") -> {
                    val room = rescues.firstOrNull { it.id == route.removePrefix("rescue:") }
                    if (room == null) LaunchedEffect(Unit) { delay(1_500); pop() }
                    else RescueScreen(room, vm.nodeId, hereFix, headings, medical = vm.medicalFor(room.victimId), rated = remember(trustScores, room.id) {
                        (room.helpers.map { it.nodeId } + room.victimId).flatMap { s -> vm.myRatingsOf(s).filter { it.ctx == room.id }.map { "$s|${it.kind.code}" } }.toSet()
                    }, actions = RescueActions(
                        onBack = ::pop, onJoin = { vm.joinRescue(room.id) }, onSend = { vm.sendRescue(room.id, it) },
                        onArrived = { vm.arrivedRescue(room.id) }, onLeave = { vm.leaveRescue(room.id) },
                        onNavigate = { compassTarget = room.victimId; goTab(Tab.COMPASS) }, onGuide = { push("article:$it") },
                        onSignal = { push("signal") }, onSafe = { vm.cancelSos(); sosReached = -1 },
                        onRate = { who, kind -> vm.rate(who, kind, room.id, "") },
                    ))
                }
                route == "sos-contacts" -> SosContactsScreen(sosContacts, ::pop, onAdd = vm::addSosContact, onRemove = vm::removeSosContact)
                route == "connections" -> ConnectionsScreen(radios, online, ::pop, onSwitch = actions.switchRadio)
                route == "audit" -> {
                    val audit by vm.audit.collectAsStateWithLifecycle()
                    val witnesses by vm.witnesses.collectAsStateWithLifecycle()
                    AuditScreen(audit.first, audit.second, ::pop, witnesses, vm.auditPublicKey, remember(audit.first.size) { vm.securityStatus() })
                }
                route.startsWith("person:") -> {
                    val id = route.removePrefix("person:")
                    val scores by vm.trustScores.collectAsStateWithLifecycle()
                    val p = people.firstOrNull { it.nodeId == id }
                    val isMe = id == vm.nodeId
                    PersonScreen(id, if (isMe) name else p?.name ?: "Someone", if (isMe) avatar else p?.avatar, scores[id] ?: vm.scoreFor(id), isMe,
                        remember(scores) { vm.myRatingsOf(id) }, rescues.firstOrNull { it.victimId == id }?.id, ::pop,
                        onMessage = { pop(); push("chat:$id") }, onRate = { kind, ctx, remark -> vm.rate(id, kind, ctx, remark) })
                }
                route == "bridge" -> {
                    val st by vm.bridgeStatus.collectAsStateWithLifecycle()
                    val url by vm.bridgeUrl.collectAsStateWithLifecycle()
                    BridgeScreen(st, url, ::pop, onSave = vm::setBridgeUrl, builtIn = vm.builtInRelay, liveConnected = vm.liveConnected.collectAsStateWithLifecycle().value)
                }
                route == "games" -> {
                    val all by vm.matches.collectAsStateWithLifecycle()
                    GamesScreen(::pop, onPlay = { push("game:$it") },
                        // Nearby now, or anyone you've met when this phone is on the internet relay.
                        people = vm.liveConnected.collectAsStateWithLifecycle().value.let { live -> people.filter { it.presence == Presence.ONLINE || (live && it.lastSeen > 0) } },
                        matches = all.values.sortedByDescending { it.updatedAt },
                        onChallenge = { p, game -> vm.challenge(p.nodeId, p.name, game)?.let { push("match:$it") } },
                        onOpenMatch = { push("match:$it") }, onAccept = vm::acceptGame, onDecline = vm::declineGame)
                }
                route.startsWith("match:") -> {
                    val all by vm.matches.collectAsStateWithLifecycle()
                    val m = all[route.removePrefix("match:")]
                    if (m == null) LaunchedEffect(Unit) { pop() }
                    else MatchScreen(m, ::pop, onPlay = { vm.playGame(m.id, it) }, onAgain = { vm.gameAgain(m.id) }, onLeave = { vm.leaveGame(m.id) })
                }
                route.startsWith("game:") -> com.bluemob.app.ui.games.ComputerGameScreen(route.removePrefix("game:"), ::pop)
                route == "trips" -> com.bluemob.app.ui.compass.TripsScreen(vm.trips.collectAsStateWithLifecycle().value, vm.currentTrip.collectAsStateWithLifecycle().value,
                    ::pop, onOpen = { push("trip:$it") }, onNewTrip = { if (!system.locationPermission) actions.requestLocation(); vm.startNewTrip(it) })
                route.startsWith("trip:") -> {
                    val id = route.removePrefix("trip:")
                    val trip = vm.trips.collectAsStateWithLifecycle().value.firstOrNull { it.id == id }
                    val current = vm.currentTrip.collectAsStateWithLifecycle().value
                    val live by vm.trailPoints.collectAsStateWithLifecycle()
                    val stored = com.bluemob.app.ui.chat.rememberLoaded(id, trip?.points) { vm.pointsOf(id) }
                    if (trip == null) LaunchedEffect(Unit) { pop() }
                    else com.bluemob.app.ui.compass.TripScreen(trip, if (id == current) live else stored.orEmpty(), spots, id == current, ::pop,
                        onRename = { vm.renameTrip(id, it) }, onDelete = { vm.deleteTrip(id) },
                        onShareGpx = { scope.launch { vm.tripGpxFile(id)?.let { actions.shareFile(it, "application/gpx+xml") } } })
                }
                route == "backup" -> com.bluemob.app.ui.system.BackupScreen(vm.backups, vm.backups.status.collectAsStateWithLifecycle().value,
                    vm.backups.every.collectAsStateWithLifecycle().value, vm.backups.folder.collectAsStateWithLifecycle().value, actions.backup, ::pop)
                route == "guide-packs" -> com.bluemob.app.ui.guide.GuidePacksScreen(vm.bridgeUrl.collectAsStateWithLifecycle().value, online, ::pop)
                route == "autostart" -> com.bluemob.app.ui.system.AutoStartScreen(
                    vm.autoMesh.collectAsStateWithLifecycle().value, vm.bluetoothPolicy.collectAsStateWithLifecycle().value,
                    vm.wifiPolicy.collectAsStateWithLifecycle().value, vm.background.collectAsStateWithLifecycle().value, sharing,
                    ::pop, vm::setMeshAtStart, vm::setBluetoothPolicy, vm::setWifiPolicy, vm::setBackground, toggleLocation,
                )
                route == "account" -> com.bluemob.app.ui.account.AccountScreen(
                    shortId = com.bluemob.app.util.formatId(vm.nodeId),
                    profile = vm.profile.collectAsStateWithLifecycle().value, hasPin = vm.pinSet.collectAsStateWithLifecycle().value,
                    onDetails = vm::setDetails, onTurnOffPin = vm::clearPin,
                    biometric = vm.biometric.collectAsStateWithLifecycle().value, canUseBiometric = remember { actions.canUseBiometric() },
                    lockAfterMs = vm.lockAfterMs.collectAsStateWithLifecycle().value, recoverySaved = vm.recoverySaved.collectAsStateWithLifecycle().value,
                    onBack = ::pop, onBiometric = { on -> if (on) actions.biometricUnlock { vm.setBiometric(true) } else vm.setBiometric(false) },
                    onLockAfter = vm::setLockAfter, onLockNow = vm::lockNow, checkPin = vm::checkPin, onNewPin = vm::setPin,
                    recoveryCode = vm::recoveryCode, onRecoverySaved = vm::setRecoverySaved,
                )
                route == "signal" -> SosSignalScreen(signalDefault, signalDefault, vm.signals, onDefault = vm::setSignalDefault, onStop = ::pop)
            }
        }

        alert?.let { sos ->
            val preview = sos.id == SosManager.PREVIEW_ID
            SosAlert(
                sos, people.firstOrNull { it.nodeId == sos.fromNodeId }, hereFix?.lat, hereFix?.lon,
                onComing = { vm.dismissSosAlert(); if (!preview) { vm.joinRescue(sos.id); push("rescue:" + sos.id) } },
                onWay = { if (!preview) compassTarget = sos.fromNodeId; vm.dismissSosAlert(); if (!preview) goTab(Tab.COMPASS) },
                onHowTo = { vm.dismissSosAlert(); push("article:help-sos") },
                onClose = vm::dismissSosAlert,
                coming = rescues.firstOrNull { it.id == sos.id }?.coming?.map { it.name }.orEmpty(),
                trust = if (preview) null else (trustScores[sos.fromNodeId] ?: vm.scoreFor(sos.fromNodeId)),
                onProfile = { vm.dismissSosAlert(); push("person:" + sos.fromNodeId) },
            )
        }

        val meshAsk by vm.meshAsk.collectAsStateWithLifecycle()
        meshAsk?.let { ask ->
            com.bluemob.app.ui.system.MeshAskDialog(ask, onAnswer = { on, remember ->
                if (vm.answerMeshAsk(on, remember)) actions.switchRadio(
                    if (ask == AppViewModel.MeshAsk.BLUETOOTH) com.bluemob.app.system.Radio.BLUETOOTH else com.bluemob.app.system.Radio.WIFI, true)
            }, onDismiss = { vm.meshAsk.value = null })
        }
        // "Start the mesh when BlueMob opens", only if the user chose it.
        LaunchedEffect(Unit) { if (vm.autoMesh.value && system.permissionsGranted && !running) vm.startMesh() }

        val locked by vm.locked.collectAsStateWithLifecycle()
        val biometricOn by vm.biometric.collectAsStateWithLifecycle()
        if (locked) {
            BackHandler { actions.leaveApp() }
            com.bluemob.app.ui.account.LockScreen(
                name, avatar, com.bluemob.app.util.formatId(vm.nodeId).take(9), biometricOn,
                onPin = vm::checkPin, onBiometric = { actions.biometricUnlock { vm.unlockedByBiometric() } },
                onSos = { vm.sendSos("") }, sosActive = mySos != null,
            )
        }

        val matchesNow by vm.matches.collectAsStateWithLifecycle()
        matchesNow.values.firstOrNull { it.state == com.bluemob.app.games.MatchState.INVITED }?.takeIf { top != "games" && !locked }?.let { m ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { Text("${m.opponentName} wants to play") },
                text = { Text("${com.bluemob.app.games.Match.title(m.game)} over the mesh, phone to phone.") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.acceptGame(m.id); push("match:" + m.id) }) { Text("Play") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.declineGame(m.id) }) { Text("Not now") } },
            )
        }

        val call by vm.call.collectAsStateWithLifecycle()
        call?.let { c ->
            val remote by vm.remoteFrame.collectAsStateWithLifecycle()
            val local by vm.localFrame.collectAsStateWithLifecycle()
            BackHandler { }
            com.bluemob.app.ui.call.CallScreen(
                c, remote, local, canUseCamera = vm.canUseCamera(),
                onAccept = { actions.requestCallPermissions(c.video) { vm.acceptCall() } },
                onDecline = vm::hangUp, onMute = vm::toggleMute, onSpeaker = vm::toggleSpeaker, onCamera = vm::toggleCamera,
                wantsFrame = vm::wantsFrame, onFrame = vm::onCameraFrame,
                onPtt = vm::togglePtt, onTalk = vm::talk, avatar = people.firstOrNull { it.nodeId == c.peer }?.avatar,
                onQuickReply = { text -> vm.hangUp(); vm.send(c.peer, text) },
            )
        }

        notice?.takeIf { top != "rescue:" + it.room && !locked }?.let { n ->
            Surface(
                onClick = { notice = null; push("rescue:" + n.room) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🆘", fontSize = 22.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Rescue group", style = MaterialTheme.typography.labelSmall, color = Extra.rose)
                        Text(n.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Main tabs: a solid top bar with the SOS button, content, and a floating tab bar. */
@Composable
private fun Tabs(tab: Tab, unread: Int, onTab: (Tab) -> Unit, onSos: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val barHeight = 52.dp
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        content(PaddingValues(top = topInset + barHeight))
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().height(barHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("BlueMob", style = MaterialTheme.typography.titleMedium, color = Extra.ink3)
            Spacer(Modifier.weight(1f))
            SosPill(onSos)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 12.dp)
                .fillMaxWidth().height(64.dp).shadow(14.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface).border(1.dp, Extra.line, RoundedCornerShape(50)).padding(6.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                val bg by animateColorAsState(if (selected) Extra.pineTint else Color.Transparent, label = "tabbg")
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(50)).background(bg).clickable { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Box {
                        Icon(t.icon, t.label, tint = if (selected) MaterialTheme.colorScheme.primary else Extra.ink3, modifier = Modifier.size(22.dp))
                        if (t == Tab.CHATS && unread > 0) Box(Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(9.dp).background(Extra.rose, CircleShape))
                    }
                    Text(t.label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing),
                        color = if (selected) MaterialTheme.colorScheme.primary else Extra.ink3, maxLines = 1)
                }
            }
        }
    }
}

private fun <T> List<T>.toMutableStateList() = mutableStateListOf<T>().also { it.addAll(this) }


/** A photo from a chat, full screen. It's decrypted in memory only. */
@Composable
private fun PhotoViewer(m: com.bluemob.app.data.MessageEntity, vm: AppViewModel, onBack: () -> Unit) {
    val bmp = com.bluemob.app.ui.chat.rememberLoaded(m.id) { vm.thumbnail(m, 2048) }
    val att = remember(m.att) { com.bluemob.app.files.Attachment.fromJson(m.att) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
        .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan } }) {
        bmp?.let {
            androidx.compose.foundation.Image(it.asImageBitmap(), att?.name, Modifier.fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        } ?: Text("Opening…", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) }
            Text(att?.name ?: "Photo", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

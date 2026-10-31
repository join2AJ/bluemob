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
import com.bluemob.app.i18n.Text
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
import androidx.compose.ui.graphics.luminance
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
import com.bluemob.app.ui.chat.ChatRoutes
import com.bluemob.app.ui.compass.TripRoutes
import com.bluemob.app.ui.games.GameRoutes
import com.bluemob.app.ui.guide.GuideRoutes
import com.bluemob.app.ui.nav.Nav
import com.bluemob.app.ui.account.AccountViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bluemob.app.ui.nav.Route
import com.bluemob.app.ui.nav.Shell
import com.bluemob.app.ui.nav.Tab
import com.bluemob.app.ui.profile.YouRoutes
import com.bluemob.app.ui.sos.SosRoutes

data class SystemStatus(val permissionsGranted: Boolean, val locationServicesOff: Boolean, val keepsRunning: Boolean, val locationPermission: Boolean,
    /** The phone's own Battery Saver is on. */
    val batterySaver: Boolean = false)

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
    /** Shares text through Android's share sheet (WhatsApp, SMS, email…). */
    val shareText: (String) -> Unit = {},
    /** Asks for the microphone (and camera for video), then runs the callback whatever the answer. */
    val requestCallPermissions: (Boolean, () -> Unit) -> Unit = { _, then -> then() },
    val canUseBiometric: () -> Boolean = { false },
    /** Asks for the camera only, then runs the callback whatever the answer. */
    val requestCamera: (() -> Unit) -> Unit = { it() },
    /** Sends and checks sign-up codes: real SMS when Firebase is set up, otherwise the test code. */
    val phoneVerifier: () -> com.bluemob.app.account.PhoneVerifier = { com.bluemob.app.account.TestVerifier },
    /** Shows the phone's fingerprint / face prompt and calls back on success. */
    val biometricUnlock: (() -> Unit) -> Unit = {},
    val restartApp: () -> Unit = {},
    val leaveApp: () -> Unit = {},
    /** Shares a file (e.g. a trip as GPX) through Android's share sheet. */
    val shareFile: (java.io.File, String) -> Unit = { _, _ -> },
    /** Shares several files at once (e.g. a trip's picture, CSV and GPX). */
    val shareFiles: (List<java.io.File>) -> Unit = {},
    val backup: com.bluemob.app.ui.system.BackupActions = com.bluemob.app.ui.system.BackupActions({}, { _, _ -> }, {}, {}),
    /** Opens the phone's picker: "photo", "video" or "doc". */
    val pickFile: (String, (android.net.Uri) -> Unit) -> Unit = { _, _ -> },
    /** Opens a (decrypted, temporary) file in another app. */
    val openFile: (java.io.File, String) -> Unit = { _, _ -> },
)

@Composable
fun BlueMobRoot(vm: AppViewModel, system: SystemStatus, actions: SystemActions) {
    val account: AccountViewModel = viewModel()
    val onboarded by vm.onboardingDone.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val profile by account.profile.collectAsStateWithLifecycle()
        var restoring by rememberSaveable { mutableStateOf(false) }
        var introSeen by rememberSaveable { mutableStateOf(false) }
        // People who used BlueMob before sign-up existed verify their number once, with their name filled in.
        val upgrading = rememberSaveable { onboarded && !profile.verified }
        val replaying by vm.replayingIntro.collectAsStateWithLifecycle()
        val stage = when {
            restoring && !profile.verified -> "restore"
            replaying -> "intro"
            // Signed-in people never land on the intro again (only "Replay the intro" shows it).
            !onboarded && !introSeen && !profile.verified -> "intro"
            !profile.verified -> "signup"
            else -> "app"
        }
        AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { s ->
            when (s) {
                "restore" -> {
                    BackHandler { restoring = false }
                    com.bluemob.app.ui.account.RestoreScreen(onBack = { restoring = false }, onRestore = { code ->
                        account.restore(code).also { if (it == null) actions.restartApp() }
                    })
                }
                "intro" -> OnboardingScreen(initialName = name, initialAvatar = avatar, signedIn = profile.verified,
                    onFinish = { _, _ -> introSeen = true; vm.endIntroReplay() }, onRestore = { restoring = true })
                "signup" -> com.bluemob.app.ui.account.SignupFlow(
                    upgrading = upgrading, initialName = if (upgrading) name else "", initialAvatar = avatar,
                    bluemobId = com.bluemob.app.util.formatId(vm.nodeId),
                    onRestore = if (upgrading) null else ({ restoring = true }),
                    verifier = remember { actions.phoneVerifier() },
                    privacyUrl = vm.privacyUrl(),
                    onDone = { r ->
                        account.completeSignup(r)
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
    val account: AccountViewModel = viewModel()
    var tab by rememberSaveable { mutableStateOf(Tab.NEARBY) }
    var compassTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val stack = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) { mutableStateListOf<String>() }
    val nav = remember(stack) { Nav(stack) { tab = it } }
    val top = stack.lastOrNull()?.let(Route::parse)
    // Time spent in each part of the app, for the activity dashboard (counted on this phone only).
    val inCallNow = vm.call.collectAsStateWithLifecycle().value.let { it != null && it.phase != com.bluemob.app.call.CallPhase.ENDED }
    LaunchedEffect(top, tab, inCallNow) { com.bluemob.app.activity.Usage.enter(com.bluemob.app.activity.Usage.areaOf(top?.key, tab.name, inCallNow)) }
    fun push(route: Route) = nav.push(route)
    fun goTab(t: Tab) = nav.goTab(t)

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
    /** Switches on what helps an SOS reach people: asks for permission first where Android needs it. */
    val turnOnForSos: (String) -> Unit = { k ->
        when (k) {
            "location" -> if (!system.locationPermission) actions.requestLocation() else actions.switchRadio(com.bluemob.app.system.Radio.LOCATION, true)
            "bluetooth" -> if (!system.permissionsGranted) actions.requestMeshPermissions() else actions.switchRadio(com.bluemob.app.system.Radio.BLUETOOTH, true)
            "mesh" -> if (!system.permissionsGranted) actions.requestMeshPermissions() else vm.startMesh()
            "internet" -> actions.switchRadio(com.bluemob.app.system.Radio.INTERNET, true)
        }
    }
    val trailOn by vm.trailOn.collectAsStateWithLifecycle()
    val trailPoints by vm.trailPoints.collectAsStateWithLifecycle()
    val estimate by vm.estimate.collectAsStateWithLifecycle()
    val lostOn by vm.lostOn.collectAsStateWithLifecycle()
    val sosReached = rememberSaveable { mutableStateOf(-1) }
    val rescues by vm.rescues.collectAsStateWithLifecycle()
    val trustScores by vm.trustScores.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<RescueNotice?>(null) }
    LaunchedEffect(Unit) { vm.rescueNotices.collect { n -> notice = n } }
    val pendingRoute by vm.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let {
            nav.clear()
            when (val r = Route.parse(it)) {
                Route.Calls -> goTab(Tab.CHATS)
                // "I'm coming" straight from the SOS alarm notification.
                is Route.SosComing -> { vm.dismissSosAlert(); vm.comingToSos(r.id); push(Route.Rescue(r.id)) }
                null, is Route.Action -> {}
                else -> push(r)
            }
            vm.pendingRoute.value = null
        }
    }
    LaunchedEffect(notice) { if (notice != null) { delay(6_000); notice = null } }
    val hereFix = myFix ?: estimate?.let { GeoPoint(it.lat, it.lon, it.uncertaintyM.toFloat(), it.at) }
    val askSteps = { if (vm.stepCounterAvailable && !vm.hasStepPermission()) actions.requestSteps() }

    LaunchedEffect(top) { vm.openChat((top as? Route.Chat)?.id) }
    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.NEARBY) { if (stack.isNotEmpty()) nav.pop() else tab = Tab.NEARBY }

    val fileProgress by vm.fileProgress.collectAsStateWithLifecycle()
    val voicePlaying by vm.voicePlaying.collectAsStateWithLifecycle()
    val recordingMs by vm.recordingMs.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun chatFiles(peer: String) = com.bluemob.app.ui.chat.ChatFiles(
        progress = fileProgress, playing = voicePlaying, recordingMs = recordingMs,
        thumbnail = { vm.thumbnail(it) },
        onOpen = { m ->
            val att = com.bluemob.app.files.Attachment.fromJson(m.att)
            if (att?.kind == com.bluemob.app.files.AttKind.IMAGE) push(Route.Photo(m.id))
            else scope.launch { vm.openCopy(m)?.let { actions.openFile(it, att?.mime ?: "*/*") } }
        },
        onPlay = vm::playVoice,
        onAttach = { kind -> actions.pickFile(kind) { uri -> vm.sendFile(peer, uri) } },
        onRecordStart = { if (!vm.startVoiceNote()) actions.requestCallPermissions(false) {} },
        onRecordStop = { send -> vm.stopVoiceNote(peer, send) },
        onMedia = { push(Route.Media(peer)) },
    )
    val toggleLocation: (Boolean) -> Unit = { on -> if (on) actions.enableLocationSharing() else vm.setShareLocation(false) }
    /** "Teach Sky the answer": the question being taught, while the dialog is open. */
    var teaching by rememberSaveable { mutableStateOf<String?>(null) }
    teaching?.let { q ->
        var answer by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { teaching = null },
            title = { Text("Teach Sky") },
            text = {
                Column {
                    Text("When someone asks “$q”, Sky should answer:", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.OutlinedTextField(answer, { answer = it.take(1_000) }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text("Kept only on this phone.", style = MaterialTheme.typography.bodySmall, color = com.bluemob.app.ui.theme.Extra.ink3, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { com.bluemob.app.bot.SkyMemory.teach(q, answer); teaching = null; vm.send(SkyBot.NODE_ID, q) },
                enabled = answer.isNotBlank()) { Text("Teach") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { teaching = null }) { Text("Cancel") } },
        )
    }
    /** Buttons under Sky's replies. */
    val onSkyAction: (String) -> Unit = { target ->
        when {
            target.startsWith("teach:") -> teaching = target.removePrefix("teach:")
            target == "sos" -> push(Route.Sos)
            target == "sos_signal" -> push(Route.Signal)
            target == "power" -> actions.openBatterySaver()
            target == "games" -> if (com.bluemob.app.Features.GAMES) push(Route.Games)
            target.startsWith("trip-ok:") -> vm.send(target.removePrefix("trip-ok:"), "✅ I'm OK")
            target.startsWith("ack:") -> target.removePrefix("ack:").split(":", limit = 2).takeIf { it.size == 2 }?.let { (g, a) -> vm.ackAnnouncement(g, a) }
            target.startsWith("guide:") -> push(Route.Article(target.removePrefix("guide:")))
            target.startsWith("tab:") -> goTab(when (target.removePrefix("tab:")) {
                "nearby" -> Tab.NEARBY; "chats" -> Tab.CHATS; "compass" -> Tab.COMPASS; "guide" -> Tab.GUIDE; else -> Tab.YOU
            })
        }
    }

    val shell = Shell(vm, system, actions, nav, scope, hereFix, headings, sosReached, ::chatFiles, onSkyAction, turnOnForSos, toggleLocation,
        showOnCompass = { compassTarget = it; goTab(Tab.COMPASS) })

    // Each tab keeps its scroll position (and other saved state) while you're on a screen opened from it.
    val tabStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(top, transitionSpec = {
            if (targetState != null && (initialState == null || stack.size > 0)) slideInHorizontally { it / 3 } + fadeIn() togetherWith fadeOut()
            else fadeIn() togetherWith fadeOut()
        }, label = "screen") { route ->
            when {
                route == null -> Tabs(tab, vm.unreadCount(conversations), onTab = { tab = it }, onSos = { push(Route.Sos) }) { padding ->
                    tabStates.SaveableStateProvider("tab-" + tab.name) { when (tab) {
                        Tab.NEARBY -> NearbyScreen(
                            NearbyState(name, running, people, system.permissionsGranted, system.locationServicesOff, sharing, myFix != null, online, radios,
                                vm.otherVersions.collectAsStateWithLifecycle().value.values.map { it.name },
                                batteries = vm.batteries.collectAsStateWithLifecycle().value, check = vm.checkIn.collectAsStateWithLifecycle().value),
                            padding, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                            background = vm.background.collectAsStateWithLifecycle().value,
                            onRequestPermissions = actions.requestMeshPermissions, onOpenLocationSettings = actions.openLocationSettings,
                            onShareLocation = { toggleLocation(true) }, onOpenChat = { push(Route.Chat(it)) },
                            onTalkToSky = { push(Route.Chat(SkyBot.NODE_ID)) }, onOpenGuide = { tab = Tab.GUIDE }, onOpenBattery = { push(Route.Power("battery")) },
                            power = system.batterySaver to system.keepsRunning,
                            onConnections = { push(Route.Connections) }, onFixRadio = { actions.switchRadio(it, true) }, onGames = { push(Route.Games) },
                            onFindLost = { compassTarget = it; goTab(Tab.COMPASS) },
                            onCheckEveryone = { vm.checkOnEveryone() }, onClearCheck = vm::clearCheck,
                            trips = run {
                                val rolls by vm.rollCalls.collectAsStateWithLifecycle()
                                vm.groups.collectAsStateWithLifecycle().value.values.filter { it.trip }.sortedByDescending { it.createdAt }.map { it to rolls[it.id] }
                            },
                            onTrip = { push(Route.Group(it)) }, onNewTrip = { push(Route.NewTrip) },
                            tripAds = vm.tripsNearby.collectAsStateWithLifecycle().value.values.sortedByDescending { it.seenAt },
                            joinNotes = vm.joinNotes.collectAsStateWithLifecycle().value, onJoinAd = vm::joinNearby,
                            myId = vm.nodeId, onTripRollCall = { vm.rollCall(it) }, onTripChat = { push(Route.Chat(it)) }, onSos = { push(Route.Sos) },
                        )
                        Tab.CHATS -> ChatsScreen(people, conversations, typing, padding, rescues, onOpenRescue = { if (it.isEmpty()) push(Route.Rescues) else push(Route.Rescue(it)) }, onNewChat = { push(Route.NewChat) }, onNewTrip = { push(Route.NewTrip) }, onJoinTrip = { push(Route.JoinTrip) },
                            calls = vm.callLog.collectAsStateWithLifecycle().value,
                            onCallBack = { id, n, video -> actions.requestCallPermissions(video) { vm.startCall(id, n, video) } },
                            onClearCalls = { vm.clearCallLog() }, onDeleteCall = { vm.deleteCall(it) }, onDeleteChat = { vm.deleteChat(it) },
                            filesFor = { chatFiles(it) },
                            groups = vm.groups.collectAsStateWithLifecycle().value.values.toList(), pinned = vm.pinnedChats.collectAsStateWithLifecycle().value,
                            onPin = vm::setPinned, onNewGroup = { push(Route.NewGroup) }, onLeaveGroup = vm::leaveGroup,
                            callsSeenAt = vm.callsSeenAt.collectAsStateWithLifecycle().value, onCallsSeen = vm::markCallsSeen) { push(Route.Chat(it)) }
                        Tab.COMPASS -> CompassScreen(
                            people, spots, hereFix, headings, vm.compassAvailable, system.locationPermission, compassTarget, padding,
                            altitude = vm.altitude.collectAsStateWithLifecycle().value, compassAccuracy = vm.compassAccuracy.collectAsStateWithLifecycle().value,
                            onShareLocation = { g -> actions.shareText(com.bluemob.app.util.Geo.shareText(g)) },
                            onHoldLocation = vm::holdLocation, onReleaseLocation = vm::releaseLocation, onRequestLocation = actions.requestLocation,
                            onSaveSpot = { n -> if (!system.locationPermission) actions.requestLocation() else vm.saveSpot(n) }, onRemoveSpot = vm::removeSpot,
                            trail = TrailUi(trailOn, trailPoints, estimate, lostOn, vm.hasStepPermission() && vm.stepCounterAvailable, vm.stepCounterAvailable),
                            trailActions = TrailActions(
                                onTrail = { on -> if (on) { if (!system.locationPermission) actions.requestLocation(); askSteps() }; vm.setTrail(on) },
                                onLost = { on -> if (on) { if (!system.locationPermission) actions.requestLocation(); askSteps() }; vm.setLost(on) },
                                onBaseCamp = { if (!system.locationPermission) actions.requestLocation() else vm.setBaseCamp() }, onClear = vm::clearTrail, onAllowSteps = actions.requestSteps,
                                onTrips = { push(Route.Trips) },
                            ),
                            onRing = vm::ring,
                        )
                        Tab.GUIDE -> GuideScreen(bookmarks, padding, onOpen = { push(Route.Article(it)) }, onSos = { push(Route.Sos) }, onMore = { push(Route.GuidePacks) },
                            onAskSky = { push(Route.Chat(SkyBot.NODE_ID)) })
                        Tab.YOU -> ProfileScreen(
                            name, avatar, vm.nodeId, running, sharing, system.keepsRunning, signalDefault, log, padding,
                            onName = vm::setName, onAvatar = vm::setAvatar, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                            onToggleLocation = toggleLocation, onBatterySaver = actions.openBatterySaver, onKeepRunning = actions.askKeepRunning,
                            onSos = { push(Route.Sos) }, onReplayIntro = vm::replayIntro, onForgetPeople = vm::forgetPeople, onClearMessages = vm::clearMessages,
                            onBridge = { push(Route.Bridge) }, onMyRating = { push(Route.Person(vm.nodeId)) }, onActivity = { push(Route.Activity) },
                            game = vm.game.collectAsStateWithLifecycle().value,
                            onBadges = { push(Route.Badges) },
                            theme = vm.theme.collectAsStateWithLifecycle().value, onTheme = vm::setTheme,
                            language = vm.language.collectAsStateWithLifecycle().value, onLanguage = vm::setLanguage,
                            emergencyCard = vm.emergencyCard.collectAsStateWithLifecycle().value, onEmergencyCard = vm::setEmergencyCard,
                            emergencyCardText = vm::emergencyCardText,
                            powerSos = vm.powerSos.collectAsStateWithLifecycle().value, onPowerSos = vm::setPowerSos,
                            checkInDue = vm.checkInDue.collectAsStateWithLifecycle().value, onCheckIn = { push(Route.CheckIn) },
                            fallDetect = vm.fallDetect.collectAsStateWithLifecycle().value, onFallDetect = if (vm.fallAvailable) vm::setFallDetect else null,
                            remoteSignal = vm.allowRemoteSignal.collectAsStateWithLifecycle().value, onRemoteSignal = vm::setAllowRemoteSignal,
                            helperKinds = vm.helperKinds.collectAsStateWithLifecycle().value, onHelperKind = vm::setHelperKind,
                            onEmergencyCardOpen = { push(Route.EmergencyCard) }, onHelperSignalsOpen = { push(Route.HelperSignals) },
                            dndUntil = vm.dndUntil.collectAsStateWithLifecycle().value, onDndUntil = vm::setDndUntil,
                            fullSilence = vm.fullSilence.collectAsStateWithLifecycle().value, onFullSilence = vm::setFullSilence,
                            dndSosCalls = vm.dndAllowSosContacts.collectAsStateWithLifecycle().value, onDndSosCalls = vm::setDndAllowSosContacts,
                            newsUnread = vm.news.items.collectAsStateWithLifecycle().value.count { it.at > vm.news.seenAt.collectAsStateWithLifecycle().value },
                            onNews = { push(Route.News) },
                            activitySummary = vm.activity.collectAsStateWithLifecycle().value.let { ev ->
                                val week = System.currentTimeMillis() - 7 * 86_400_000L
                                val recent = ev.filter { it.at >= week }
                                val msgs = recent.count { it.type == com.bluemob.app.activity.ActivityType.MSG_SENT || it.type == com.bluemob.app.activity.ActivityType.MSG_RECEIVED }
                                val calls = recent.count { it.type.name.startsWith("CALL") }
                                "This week: $msgs message${if (msgs == 1) "" else "s"} · $calls call${if (calls == 1) "" else "s"}"
                            },
                            lastError = remember { vm.lastError() },
                            myStars = (trustScores[vm.nodeId] ?: vm.scoreFor(vm.nodeId)).stars, myRatingCount = trustScores[vm.nodeId]?.let { maxOf(it.raters, it.ratings) } ?: 0,
                            onConnections = { push(Route.Connections) }, onSosContacts = { push(Route.SosContacts) }, onAudit = { push(Route.Audit) },
                            onGames = { push(Route.Games) }, onAccount = { push(Route.Account) }, onAutoStart = { push(Route.AutoStart) }, onBackup = { push(Route.Backup) }, sosContactCount = sosContacts.size,
                            background = vm.background.collectAsStateWithLifecycle().value, onBackground = vm::setBackground,
                            onStorage = { push(Route.Storage) }, onDiagnostics = { push(Route.Diagnostics) },
                        )
                    } }
                }
                route is Route.ChatArea -> ChatRoutes(route, shell)
                route is Route.GuideArea -> GuideRoutes(route, shell)
                route is Route.SosArea -> SosRoutes(route, shell)
                route is Route.GamesArea -> GameRoutes(route, shell)
                route is Route.TripsArea -> TripRoutes(route, shell)
                route is Route.YouArea -> YouRoutes(route, shell)
                // Actions never stay on the stack; an unknown key (from an older version) is dropped.
                else -> LaunchedEffect(route) { nav.pop() }
            }
        }

        // People who signed up before the consent step: asked once to agree to the privacy policy.
        var askConsent by remember { mutableStateOf(account.needsConsent()) }
        if (askConsent) {
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            val policy = vm.privacyUrl()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { Text("Our privacy policy") },
                text = { Text("BlueMob keeps your details encrypted on your phone. Your health details are shared only inside an SOS, and you can download or delete your data any time (You → Account). Please read and agree to keep using BlueMob.") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { account.recordConsent(false); askConsent = false }) { Text("I agree") } },
                dismissButton = { if (policy.isNotBlank()) androidx.compose.material3.TextButton(onClick = { runCatching { uri.openUri(policy) } }) { Text("Read it") } },
            )
        }

        // Reachable when closed needs Android to let BlueMob run in the background: ask once, plainly.
        var askKeep by remember { mutableStateOf(vm.background.value && !system.keepsRunning && !vm.askedKeepRunning) }
        if (askKeep) androidx.compose.material3.AlertDialog(
            onDismissRequest = { askKeep = false; vm.askedKeepRunning = true },
            title = { Text("Get calls and messages when BlueMob is closed") },
            text = { Text("So people can call and message you even when BlueMob isn't open, Android needs to let it keep running in the background. " +
                "It uses little battery: it just stays signed in. You can change this in You → Connections & power.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { askKeep = false; vm.askedKeepRunning = true; actions.askKeepRunning() }) { Text("Allow") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askKeep = false; vm.askedKeepRunning = true }) { Text("Not now") } },
        )

        // A helper asked this phone to light up its screen: full brightness, flashing, so it can be seen from far.
        val flashUntil by vm.screenFlash.collectAsStateWithLifecycle()
        if (flashUntil > 0L) com.bluemob.app.ui.sos.HelperFlash(flashUntil, onStop = vm::stopSignalling)
        vm.checkRequests.collectAsStateWithLifecycle().value.firstOrNull()?.let { r ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { Text("🙋 ${r.name} is checking on everyone") },
                text = { Text("Are you OK?") },
                confirmButton = { androidx.compose.material3.Button(onClick = { vm.answerCheck(r, true) }) { Text("I'm OK") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.answerCheck(r, false) }) { Text("I need help", color = com.bluemob.app.ui.theme.Extra.rose) } },
            )
        }
        vm.sosRequests.collectAsStateWithLifecycle().value.firstOrNull()?.let { r ->
            com.bluemob.app.ui.sos.SosContactRequest(r.name) { yes -> vm.answerSosRequest(r, yes) }
        }
        alert?.let { sos ->
            val preview = sos.id == SosManager.PREVIEW_ID
            SosAlert(
                sos, people.firstOrNull { it.nodeId == sos.fromNodeId }, hereFix?.lat, hereFix?.lon,
                onComing = { vm.dismissSosAlert(); if (!preview) { vm.comingToSos(sos.id); push(Route.Rescue(sos.id)) } },
                onWay = { if (!preview) compassTarget = sos.fromNodeId; vm.dismissSosAlert(); if (!preview) goTab(Tab.COMPASS) },
                onHowTo = { vm.dismissSosAlert(); push(Route.Article("help-sos")) },
                onClose = vm::dismissSosAlert,
                coming = rescues.firstOrNull { it.id == sos.id }?.coming?.map { it.name }.orEmpty(),
                trust = if (preview) null else (trustScores[sos.fromNodeId] ?: vm.scoreFor(sos.fromNodeId)),
                onProfile = { vm.dismissSosAlert(); push(Route.Person(sos.fromNodeId)) },
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

        val locked by account.locked.collectAsStateWithLifecycle()
        val biometricOn by account.biometric.collectAsStateWithLifecycle()
        if (locked) {
            BackHandler { actions.leaveApp() }
            com.bluemob.app.ui.account.LockScreen(
                name, avatar, com.bluemob.app.util.formatId(vm.nodeId).take(9), biometricOn,
                onPin = account::checkPin, onBiometric = { actions.biometricUnlock { account.unlockedByBiometric() } },
                onSos = {
                    val reached = vm.sendSos("")
                    when {
                        !system.permissionsGranted -> actions.requestMeshPermissions()
                        !system.locationPermission -> actions.requestLocation()
                        !radios.bluetooth -> actions.switchRadio(com.bluemob.app.system.Radio.BLUETOOTH, true)
                        !radios.location -> actions.switchRadio(com.bluemob.app.system.Radio.LOCATION, true)
                    }
                    reached
                }, sosActive = mySos != null,
            )
        }

        val matchesNow by vm.matches.collectAsStateWithLifecycle()
        matchesNow.values.firstOrNull { it.state == com.bluemob.app.games.MatchState.INVITED }?.takeIf { com.bluemob.app.Features.GAMES && top != Route.Games && !locked }?.let { m ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { Text("${m.opponentName} wants to play") },
                text = { Text("${com.bluemob.app.games.Match.title(m.game)} over the mesh, phone to phone.") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.acceptGame(m.id); push(Route.Match(m.id)) }) { Text("Play") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.declineGame(m.id) }) { Text("Not now") } },
            )
        }

        // A new medal: confetti, unless something more important is on screen.
        val medal by vm.newMedal.collectAsStateWithLifecycle()
        medal?.takeIf { com.bluemob.app.Features.BADGES && !locked && alert == null && top !is Route.SosArea }?.let { (t, tier) ->
            com.bluemob.app.ui.profile.MedalCelebration(t, tier) { vm.medalSeen(t, tier) }
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
                levels = vm::callLevels,
            )
        }

        notice?.takeIf { top != Route.Rescue(it.room) && !locked }?.let { n ->
            Surface(
                onClick = { notice = null; push(Route.Rescue(n.room)) }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
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

/** Main tabs: a top bar with the SOS button, content, and a tab bar docked to the bottom. */
@Composable
internal fun Tabs(tab: Tab, unread: Int, onTab: (Tab) -> Unit, onSos: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val barHeight = 52.dp
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content(PaddingValues(top = topInset + barHeight))
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().height(barHeight).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandTitle()
                Spacer(Modifier.weight(1f))
                SosPill(onSos)
            }
        }
        // Docked to the bottom edge, in the brand gradient, with the selected tab in a white pill.
        Row(
            Modifier.fillMaxWidth().shadow(12.dp).background(com.bluemob.app.ui.theme.Gradients.brand())
                .navigationBarsPadding().height(66.dp).padding(horizontal = 6.dp, vertical = 7.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "tabbg")
                val fg = if (selected) MaterialTheme.colorScheme.primary else Color.White
                Column(
                    Modifier.weight(1f).fillMaxSize().padding(horizontal = 3.dp).clip(RoundedCornerShape(22.dp)).background(bg)
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Box {
                        Icon(t.icon, null, tint = fg, modifier = Modifier.size(23.dp))
                        if (t == Tab.CHATS && unread > 0) Box(Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(10.dp)
                            .background(Color(0xFFFF5A5F), CircleShape).border(1.5.dp, Color.White, CircleShape))
                    }
                    Text(t.label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing),
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                        color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** "BlueMob" in the brand colours: Blue in sky, Mob in pine. */
@Composable
private fun BrandTitle() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = if (dark) Color(0xFF7DB7EA) else Color(0xFF1E7BD8))); append("Blue"); pop()
            pushStyle(androidx.compose.ui.text.SpanStyle(color = if (dark) Color(0xFF3FC897) else Color(0xFF0E9F6E))); append("Mob"); pop()
        },
        style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
    )
}

private fun <T> List<T>.toMutableStateList() = mutableStateListOf<T>().also { it.addAll(this) }


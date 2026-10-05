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
import com.bluemob.app.ui.games.TicTacToeScreen
import com.bluemob.app.ui.profile.AuditScreen
import com.bluemob.app.rescue.RescueNotice
import com.bluemob.app.ui.rescue.RescueActions
import com.bluemob.app.ui.rescue.RescueScreen
import com.bluemob.app.ui.sos.SosAlert
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.bluemob.app.ui.sos.SosContactsScreen
import com.bluemob.app.ui.sos.SosHubState
import com.bluemob.app.ui.system.ConnectionsScreen
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
)

@Composable
fun BlueMobRoot(vm: AppViewModel, system: SystemStatus, actions: SystemActions) {
    val onboarded by vm.onboardingDone.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(onboarded, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { done ->
            if (!done) {
                OnboardingScreen(initialName = name, initialAvatar = avatar, onFinish = { n, a ->
                    vm.setName(n); vm.setAvatar(a); vm.finishOnboarding()
                    if (!system.permissionsGranted) actions.requestMeshPermissions() else vm.startMesh()
                })
            } else {
                MainShell(vm, system, actions)
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
    var notice by remember { mutableStateOf<RescueNotice?>(null) }
    LaunchedEffect(Unit) { vm.rescueNotices.collect { n -> notice = n } }
    LaunchedEffect(notice) { if (notice != null) { delay(6_000); notice = null } }
    val hereFix = myFix ?: estimate?.let { GeoPoint(it.lat, it.lon, it.uncertaintyM.toFloat(), it.at) }
    val askSteps = { if (vm.stepCounterAvailable && !vm.hasStepPermission()) actions.requestSteps() }

    LaunchedEffect(top) { vm.openChat(top?.takeIf { it.startsWith("chat:") }?.removePrefix("chat:")) }
    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.NEARBY) { if (stack.isNotEmpty()) pop() else tab = Tab.NEARBY }

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
                            NearbyState(name, running, people, system.permissionsGranted, system.locationServicesOff, sharing, myFix != null, online, radios),
                            padding, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                            onRequestPermissions = actions.requestMeshPermissions, onOpenLocationSettings = actions.openLocationSettings,
                            onShareLocation = { toggleLocation(true) }, onOpenChat = { push("chat:$it") },
                            onTalkToSky = { push("chat:" + SkyBot.NODE_ID) }, onOpenGuide = { tab = Tab.GUIDE }, onOpenBattery = { tab = Tab.YOU },
                            onConnections = { push("connections") }, onFixRadio = { actions.switchRadio(it, true) }, onGames = { push("games") },
                            onFindLost = { compassTarget = it; goTab(Tab.COMPASS) },
                        )
                        Tab.CHATS -> ChatsScreen(people, conversations, typing, padding, rescues, onOpenRescue = { push("rescue:$it") }) { push("chat:$it") }
                        Tab.COMPASS -> CompassScreen(
                            people, spots, hereFix, headings, vm.compassAvailable, system.locationPermission, compassTarget, padding,
                            onHoldLocation = vm::holdLocation, onReleaseLocation = vm::releaseLocation, onRequestLocation = actions.requestLocation,
                            onSaveSpot = { vm.saveSpot("Spot ${spots.count { !it.isBaseCamp } + 1}") }, onRemoveSpot = vm::removeSpot,
                            trail = TrailUi(trailOn, trailPoints, estimate, lostOn, vm.hasStepPermission() && vm.stepCounterAvailable, vm.stepCounterAvailable),
                            trailActions = TrailActions(
                                onTrail = { on -> if (on) { if (!system.locationPermission) actions.requestLocation(); askSteps() }; vm.setTrail(on) },
                                onLost = { on -> if (on) { if (!system.locationPermission) actions.requestLocation(); askSteps() }; vm.setLost(on) },
                                onBaseCamp = { vm.setBaseCamp() }, onClear = vm::clearTrail, onAllowSteps = actions.requestSteps,
                            ),
                        )
                        Tab.GUIDE -> GuideScreen(bookmarks, padding, onOpen = { push("article:$it") }, onSos = { push("sos") })
                        Tab.YOU -> ProfileScreen(
                            name, avatar, vm.nodeId, running, sharing, system.keepsRunning, signalDefault, log, padding,
                            onName = vm::setName, onAvatar = vm::setAvatar, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                            onToggleLocation = toggleLocation, onBatterySaver = actions.openBatterySaver, onKeepRunning = actions.askKeepRunning,
                            onSos = { push("sos") }, onReplayIntro = vm::replayIntro, onForgetPeople = vm::forgetPeople, onClearMessages = vm::clearMessages,
                            onConnections = { push("connections") }, onSosContacts = { push("sos-contacts") }, onAudit = { push("audit") },
                            onGames = { push("games") }, sosContactCount = sosContacts.size,
                        )
                    }
                }
                route.startsWith("chat:") -> {
                    val id = route.removePrefix("chat:")
                    ChatScreen(
                        nodeId = id, person = people.firstOrNull { it.nodeId == id }, messages = conversations[id].orEmpty(), typing = id in typing,
                        meshEvents = vm.meshEvents, myName = name, myId = vm.nodeId, onBack = ::pop, onSend = { vm.send(id, it) },
                        onPing = { vm.ping(id) }, onInfo = { push("info:$it") }, onPerson = {}, onAction = onSkyAction,
                    )
                }
                route.startsWith("info:") -> {
                    val m = vm.message(route.removePrefix("info:"))
                    if (m == null) LaunchedEffect(Unit) { pop() }
                    else MessageInfoScreen(m, name, vm.nodeId, people.firstOrNull { it.nodeId == m.peer }?.name ?: "them", ::pop)
                }
                route.startsWith("article:") -> {
                    val a = GuideContent.byId(route.removePrefix("article:"))
                    if (a == null) LaunchedEffect(Unit) { pop() }
                    else ArticleScreen(a, a.id in bookmarks, ::pop, onToggleSaved = { vm.toggleBookmark(a.id) }, onSos = { push("sos") })
                }
                route == "sos" -> SosHubScreen(
                    SosHubState(mySos, people.count { it.presence == Presence.ONLINE }, signalDefault, sosContacts, sosReached),
                    message = vm::sosMessage, onBack = ::pop,
                    onSend = { sosReached = vm.sendSos(it) }, onSafe = { vm.cancelSos(); sosReached = -1 }, onSignal = { push("signal") },
                    onDefaultSignal = vm::setSignalDefault, onHowToHelp = { push("article:help-sos") }, onContacts = { push("sos-contacts") },
                    onText = actions.textSos, onPreviewAlert = vm::previewSosAlert,
                    rescue = rescues.firstOrNull { it.mine && it.id == mySos?.id }, onOpenRescue = { mySos?.let { push("rescue:" + it.id) } },
                )
                route.startsWith("rescue:") -> {
                    val room = rescues.firstOrNull { it.id == route.removePrefix("rescue:") }
                    if (room == null) LaunchedEffect(Unit) { delay(1_500); pop() }
                    else RescueScreen(room, vm.nodeId, hereFix, headings, RescueActions(
                        onBack = ::pop, onJoin = { vm.joinRescue(room.id) }, onSend = { vm.sendRescue(room.id, it) },
                        onArrived = { vm.arrivedRescue(room.id) }, onLeave = { vm.leaveRescue(room.id) },
                        onNavigate = { compassTarget = room.victimId; goTab(Tab.COMPASS) }, onGuide = { push("article:$it") },
                        onSignal = { push("signal") }, onSafe = { vm.cancelSos(); sosReached = -1 },
                    ))
                }
                route == "sos-contacts" -> SosContactsScreen(sosContacts, ::pop, onAdd = vm::addSosContact, onRemove = vm::removeSosContact)
                route == "connections" -> ConnectionsScreen(radios, online, ::pop, onSwitch = actions.switchRadio)
                route == "audit" -> {
                    val audit by vm.audit.collectAsStateWithLifecycle()
                    AuditScreen(audit.first, audit.second, ::pop)
                }
                route == "games" -> GamesScreen(::pop) { push("game:$it") }
                route == "game:ttt" -> TicTacToeScreen(::pop)
                route == "game:c4" -> ConnectFourScreen(::pop)
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
            )
        }

        notice?.takeIf { top != "rescue:" + it.room }?.let { n ->
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


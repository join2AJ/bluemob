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
import com.bluemob.app.ui.sos.SosAlert
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

    LaunchedEffect(top) { vm.openChat(top?.takeIf { it.startsWith("chat:") }?.removePrefix("chat:")) }
    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.NEARBY) { if (stack.isNotEmpty()) pop() else tab = Tab.NEARBY }

    val toggleLocation: (Boolean) -> Unit = { on -> if (on) actions.enableLocationSharing() else vm.setShareLocation(false) }
    /** Buttons under Sky's replies. */
    val onSkyAction: (String) -> Unit = { target ->
        when {
            target == "sos" -> push("sos")
            target == "sos_signal" -> push("signal")
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
                            NearbyState(name, running, people, system.permissionsGranted, system.locationServicesOff, sharing, myFix != null, online),
                            padding, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                            onRequestPermissions = actions.requestMeshPermissions, onOpenLocationSettings = actions.openLocationSettings,
                            onShareLocation = { toggleLocation(true) }, onOpenChat = { push("chat:$it") },
                            onTalkToSky = { push("chat:" + SkyBot.NODE_ID) }, onOpenGuide = { tab = Tab.GUIDE }, onOpenBattery = { tab = Tab.YOU },
                        )
                        Tab.CHATS -> ChatsScreen(people, conversations, typing, padding) { push("chat:$it") }
                        Tab.COMPASS -> CompassScreen(
                            people, spots, myFix, headings, vm.compassAvailable, system.locationPermission, compassTarget, padding,
                            onHoldLocation = vm::holdLocation, onReleaseLocation = vm::releaseLocation, onRequestLocation = actions.requestLocation,
                            onSaveSpot = { vm.saveSpot("Spot ${spots.size + 1}") }, onRemoveSpot = vm::removeSpot,
                        )
                        Tab.GUIDE -> GuideScreen(bookmarks, padding, onOpen = { push("article:$it") }, onSos = { push("sos") })
                        Tab.YOU -> ProfileScreen(
                            name, avatar, vm.nodeId, running, sharing, system.keepsRunning, signalDefault, log, padding,
                            onName = vm::setName, onAvatar = vm::setAvatar, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                            onToggleLocation = toggleLocation, onBatterySaver = actions.openBatterySaver, onKeepRunning = actions.askKeepRunning,
                            onSos = { push("sos") }, onReplayIntro = vm::replayIntro, onForgetPeople = vm::forgetPeople, onClearMessages = vm::clearMessages,
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
                    mySos, people.count { it.presence == Presence.ONLINE }, signalDefault, ::pop,
                    onSend = vm::sendSos, onSafe = vm::cancelSos, onSignal = { push("signal") },
                    onDefaultSignal = vm::setSignalDefault, onHowToHelp = { push("article:help-sos") },
                )
                route == "signal" -> SosSignalScreen(signalDefault, signalDefault, vm.signals, onDefault = vm::setSignalDefault, onStop = ::pop)
            }
        }

        alert?.let { sos ->
            SosAlert(
                sos, people.firstOrNull { it.nodeId == sos.fromNodeId }, myFix?.lat, myFix?.lon,
                onComing = { vm.send(sos.fromNodeId, "I'm coming! Stay where you are 🙏"); vm.dismissSosAlert() },
                onWay = { compassTarget = sos.fromNodeId; vm.dismissSosAlert(); goTab(Tab.COMPASS) },
                onHowTo = { vm.dismissSosAlert(); push("article:help-sos") },
                onClose = vm::dismissSosAlert,
            )
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


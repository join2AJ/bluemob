package com.bluemob.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.ui.chat.ChatScreen
import com.bluemob.app.ui.chat.ChatsScreen
import com.bluemob.app.ui.dashboard.DashboardScreen
import com.bluemob.app.ui.dashboard.DashboardState
import com.bluemob.app.ui.onboarding.OnboardingScreen
import com.bluemob.app.ui.profile.ProfileScreen

private enum class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector) {
    RADAR("Radar", Icons.Filled.Radar, Icons.Outlined.Radar),
    CHATS("Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    YOU("You", Icons.Filled.Person, Icons.Outlined.Person),
}

data class SystemStatus(val permissionsGranted: Boolean, val locationServicesOff: Boolean)

@Composable
fun BlueMobRoot(
    vm: AppViewModel,
    system: SystemStatus,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onEnableLocationSharing: () -> Unit,
) {
    val onboarded by vm.onboardingDone.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(onboarded, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { done ->
            if (!done) {
                OnboardingScreen(
                    initialName = name,
                    initialAvatar = avatar,
                    onFinish = { n, a ->
                        vm.setName(n)
                        vm.setAvatar(a)
                        vm.finishOnboarding()
                        if (!system.permissionsGranted) onRequestPermissions() else vm.startMesh()
                    },
                )
            } else {
                MainShell(vm, system, onRequestPermissions, onOpenLocationSettings, onEnableLocationSharing)
            }
        }
    }
}

@Composable
private fun MainShell(
    vm: AppViewModel,
    system: SystemStatus,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onEnableLocationSharing: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.RADAR) }
    var openChat by rememberSaveable { mutableStateOf<String?>(null) }

    val name by vm.name.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val sharing by vm.shareLocation.collectAsStateWithLifecycle()
    val myFix by vm.myLocation.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()

    LaunchedEffect(openChat) { vm.openChat(openChat) }
    BackHandler(enabled = openChat != null || tab != Tab.RADAR) {
        if (openChat != null) openChat = null else tab = Tab.RADAR
    }

    val toggleLocation: (Boolean) -> Unit = { on -> if (on) onEnableLocationSharing() else vm.setShareLocation(false) }

    AnimatedContent(
        openChat,
        transitionSpec = {
            if (targetState != null) slideInHorizontally { it } togetherWith fadeOut()
            else fadeIn() togetherWith slideOutHorizontally { it }
        },
        label = "chat",
    ) { chatId ->
        if (chatId != null) {
            ChatScreen(
                nodeId = chatId,
                person = people.firstOrNull { it.nodeId == chatId },
                conversation = conversations[chatId],
                meshEvents = vm.meshEvents,
                onBack = { openChat = null },
                onSend = { vm.send(chatId, it) },
                onPing = { vm.ping(chatId) },
            )
            return@AnimatedContent
        }
        val unread = conversations.values.sumOf { it.unread }
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                BadgedBox(badge = { if (t == Tab.CHATS && unread > 0) Badge { Text("$unread") } }) {
                                    Icon(if (tab == t) t.selected else t.unselected, contentDescription = t.label)
                                }
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { current ->
                when (current) {
                    Tab.RADAR -> DashboardScreen(
                        state = DashboardState(
                            name = name, avatar = avatar, nodeId = vm.nodeId, running = running, people = people,
                            permissionsGranted = system.permissionsGranted,
                            locationServicesOff = system.locationServicesOff,
                            sharingLocation = sharing, hasMyFix = myFix != null, online = online,
                        ),
                        contentPadding = padding,
                        onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                        onRequestPermissions = onRequestPermissions,
                        onOpenLocationSettings = onOpenLocationSettings,
                        onShareLocation = { toggleLocation(true) },
                        onOpenChat = { openChat = it },
                        onTalkToSky = { openChat = SkyBot.NODE_ID },
                    )
                    Tab.CHATS -> ChatsScreen(
                        people = people,
                        conversations = conversations,
                        contentPadding = padding,
                        onOpen = { openChat = it },
                    )
                    Tab.YOU -> ProfileScreen(
                        name = name, avatar = avatar, nodeId = vm.nodeId, running = running,
                        sharingLocation = sharing, log = log, contentPadding = padding,
                        onName = vm::setName,
                        onAvatar = vm::setAvatar,
                        onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
                        onToggleLocation = toggleLocation,
                        onReplayIntro = vm::replayIntro,
                        onForgetPeople = vm::forgetPeople,
                    )
                }
            }
        }
    }
}

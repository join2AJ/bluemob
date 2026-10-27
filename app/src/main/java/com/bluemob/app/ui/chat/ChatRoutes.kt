package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bluemob.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.files.Attachment
import com.bluemob.app.ui.AppViewModel
import com.bluemob.app.ui.nav.Route
import com.bluemob.app.ui.nav.Shell

/** Chats, groups, files and Sky. */
@Composable
fun ChatRoutes(route: Route.ChatArea, s: Shell) {
    val vm = s.vm
    val people by vm.people.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    when (route) {
        is Route.Chat -> {
            val id = route.id
            val name by vm.name.collectAsStateWithLifecycle()
            val typing by vm.typing.collectAsStateWithLifecycle()
            ChatScreen(
                nodeId = id, person = people.firstOrNull { it.nodeId == id }, messages = conversations[id].orEmpty(), typing = id in typing,
                meshEvents = vm.meshEvents, myName = name, myId = vm.nodeId, onBack = s::pop, onSend = { vm.send(id, it) },
                onPing = { vm.ping(id) }, onInfo = { s.push(Route.Info(it)) }, onPerson = { if (id != SkyBot.NODE_ID) s.push(Route.Person(id)) }, onAction = s.onSkyAction,
                files = s.chatFiles(id), techDetails = vm.techDetails.collectAsStateWithLifecycle().value,
                group = vm.groups.collectAsStateWithLifecycle().value[id],
                onReply = { t, m -> vm.reply(id, t, m) }, onReact = vm::react, onGroupInfo = { s.push(Route.Group(id)) },
                verified = vm.isVerified(id, vm.verified.collectAsStateWithLifecycle().value),
                aiReady = vm.aiStatus.collectAsStateWithLifecycle().value.ready, onAiSetup = if (id == SkyBot.NODE_ID) ({ s.push(Route.SkyAi) }) else null,
                onCall = { video ->
                    val who = people.firstOrNull { it.nodeId == id }?.name ?: "them"
                    s.actions.requestCallPermissions(video) { vm.startCall(id, who, video) }
                },
            )
        }
        Route.NewGroup -> NewGroupScreen(people.filter { it.nodeId != SkyBot.NODE_ID }, s::pop) { n, members ->
            vm.createGroup(n, members)?.let { s.nav.replace(Route.Chat(it)) }
        }
        is Route.Group -> {
            val g = vm.groups.collectAsStateWithLifecycle().value[route.id]
            if (g == null) LaunchedEffect(Unit) { s.pop() }
            else if (g.trip) {
                val all by vm.groups.collectAsStateWithLifecycle()
                val now by produceState(System.currentTimeMillis()) { while (true) { kotlinx.coroutines.delay(30_000); value = System.currentTimeMillis() } }
                TripGroupScreen(g, vm.nodeId, vm.rollCalls.collectAsStateWithLifecycle().value[g.id], people,
                    remember(g.id, g.expiresAt, g.name) { vm.tripInvite(g.id) },
                    TripActions(onBack = s::pop, onRollCall = { vm.rollCall(g.id) }, onOpenChat = { s.push(Route.Chat(g.id)) }, onPerson = { s.push(Route.Person(it)) },
                        onFieldTest = { s.push(Route.FieldTest(g.id)) }, onLeave = { vm.leaveGroup(g.id); s.nav.clear() },
                        onShare = { code -> s.actions.shareText("Join my BlueMob trip group. In BlueMob: Groups → Trip group → Join, then paste this code:\n\n$code") },
                        onAutoMin = { vm.setAutoRollCall(g.id, it) }, onEnd = { vm.setTripEnd(g.id, it) }, onKeepOpen = { vm.keepTripOpen(g.id) },
                        onClose = { vm.closeTrip(g.id); all.values.filter { it.parent == g.id }.forEach { t -> vm.closeTrip(t.id) } },
                        onNewTeam = { s.push(Route.NewTeam(g.id)) }, onOpenGroup = { s.push(Route.Group(it)) }, onAdd = { vm.addToTrip(g.id, it) }),
                    teams = all.values.filter { it.parent == g.id }.sortedBy { it.createdAt }, main = all[g.parent], now = now)
            }
            else GroupInfoScreen(g, people, s::pop, onAdd = { vm.addToGroup(g.id, it) },
                onLeave = { vm.leaveGroup(g.id); s.nav.clear() }, onPerson = { s.push(Route.Person(it)) })
        }
        is Route.Media -> MediaScreen(people.firstOrNull { it.nodeId == route.peer }?.name ?: "them", conversations[route.peer].orEmpty(), s.chatFiles(route.peer), s::pop)
        is Route.Photo -> {
            val m = vm.message(route.messageId)
            if (m == null) LaunchedEffect(Unit) { s.pop() } else PhotoViewer(m, vm, s::pop)
        }
        is Route.Info -> {
            val m = vm.message(route.messageId)
            val name by vm.name.collectAsStateWithLifecycle()
            if (m == null) LaunchedEffect(Unit) { s.pop() }
            else MessageInfoScreen(m, name, vm.nodeId, people.firstOrNull { it.nodeId == m.peer }?.name ?: "them", s::pop)
        }
        Route.NewChat -> NewChatScreen(vm.nodeId, people, s::pop,
            onStart = { id, n -> vm.startChatById(id, n); s.nav.replace(Route.Chat(id)) },
            onOpen = { s.nav.replace(Route.Chat(it)) }, onShareId = s.actions.shareId)
        Route.SkyAi -> SkyAiScreen(vm, s::pop)
        Route.NewTrip -> {
            val mine = vm.groups.collectAsStateWithLifecycle().value.values.filter { it.trip && !it.isTeam && it.owner == vm.nodeId }
            val next = (mine.mapNotNull { Regex("""^Group (\d+)$""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
            NewTripScreen(s::pop, onCreate = { n, end -> vm.createTrip(n, end)?.let { s.nav.replace(Route.Group(it)) } }, onJoin = { s.nav.replace(Route.JoinTrip) }, nextNumber = next)
        }
        is Route.NewTeam -> {
            val main = vm.groups.collectAsStateWithLifecycle().value[route.main]
            val teams = vm.groups.collectAsStateWithLifecycle().value.values.count { it.parent == route.main }
            if (main == null) LaunchedEffect(Unit) { s.pop() }
            else NewTeamScreen(main, vm.nodeId, vm.name.collectAsStateWithLifecycle().value, people, teams, s::pop) { n, captain, members ->
                vm.createTeam(main.id, n, captain, members)?.let { s.nav.replace(Route.Group(it)) }
            }
        }
        Route.JoinTrip -> JoinTripScreen(s::pop, onScanned = { code ->
            vm.joinTrip(code).fold({ s.nav.replace(Route.Group(it)); null }, { it.message })
        }, askCamera = s.actions.requestCamera)
    }
}

/** A photo from a chat, full screen. It's decrypted in memory only. */
@Composable
private fun PhotoViewer(m: MessageEntity, vm: AppViewModel, onBack: () -> Unit) {
    val bmp = rememberLoaded(m.id) { vm.thumbnail(m, 2048) }
    val att = remember(m.att) { Attachment.fromJson(m.att) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset = if (scale == 1f) Offset.Zero else offset + pan } }) {
        bmp?.let {
            androidx.compose.foundation.Image(it.asImageBitmap(), att?.name, Modifier.fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
        } ?: Text("Opening…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text(att?.name ?: "Photo", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

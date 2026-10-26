package com.bluemob.app.ui.sos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.nav.Route
import com.bluemob.app.ui.nav.Shell
import com.bluemob.app.ui.rescue.RescueActions
import com.bluemob.app.ui.rescue.RescueScreen
import com.bluemob.app.ui.rescue.RescuesScreen
import kotlinx.coroutines.delay

/** SOS, signals, SOS contacts and rescue groups. */
@Composable
fun SosRoutes(route: Route.SosArea, s: Shell) {
    val vm = s.vm
    val mySos by vm.mySos.collectAsStateWithLifecycle()
    val rescues by vm.rescues.collectAsStateWithLifecycle()
    val signalDefault by vm.signalDefault.collectAsStateWithLifecycle()
    val sosContacts by vm.sosContacts.collectAsStateWithLifecycle()
    val radios by vm.radios.collectAsStateWithLifecycle()
    var sosReached by s.sosReached
    when (route) {
        Route.Sos -> {
            val people by vm.people.collectAsStateWithLifecycle()
            SosHubScreen(
                SosHubState(mySos, people.count { it.presence == Presence.ONLINE }, signalDefault, sosContacts, sosReached),
                message = vm::sosMessage, onBack = s::pop,
                onSend = { sosReached = vm.sendSos(it); s.askForSosPieces(radios) },
                onSafe = { vm.cancelSos(); sosReached = -1 }, onSignal = { s.push(Route.Signal) },
                onDefaultSignal = vm::setSignalDefault, onHowToHelp = { s.push(Route.Article("help-sos")) }, onContacts = { s.push(Route.SosContacts) },
                onText = s.actions.textSos, onPreviewAlert = vm::previewSosAlert,
                rescue = rescues.firstOrNull { it.mine && it.id == mySos?.id }, onOpenRescue = { mySos?.let { s.push(Route.Rescue(it.id)) } },
            )
        }
        Route.Signal -> SosSignalScreen(signalDefault, signalDefault, vm.signals, onDefault = vm::setSignalDefault, onStop = s::pop)
        Route.SosContacts -> {
            val people by vm.people.collectAsStateWithLifecycle()
            SosContactsScreen(sosContacts, s::pop, onAdd = { n, p, id -> vm.addSosContact(n, p, id) }, onRemove = vm::removeSosContact,
                onInvite = { c -> s.actions.textSos(listOf(c.phone), vm.inviteText(c.name)) },
                people = people.filter { it.nodeId != SkyBot.NODE_ID }, onAskAgain = vm::askSosContactAgain)
        }
        Route.CheckIn -> CheckInScreen(vm.checkInDue.collectAsStateWithLifecycle().value, vm.checkInNote.collectAsStateWithLifecycle().value,
            sosContacts.size, vm.background.collectAsStateWithLifecycle().value, s::pop,
            onStart = vm::startCheckIn, onExtend = vm::extendCheckIn, onDone = vm::checkedIn)
        Route.Rescues -> RescuesScreen(rescues, s::pop, onOpen = { s.push(Route.Rescue(it)) }, mySosId = mySos?.id)
        is Route.Rescue -> {
            val trustScores by vm.trustScores.collectAsStateWithLifecycle()
            val running by vm.running.collectAsStateWithLifecycle()
            val online by vm.online.collectAsStateWithLifecycle()
            val room = rescues.firstOrNull { it.id == route.id }
            // A group heard about a moment ago may still be on its way: wait a little before giving up.
            if (room == null) LaunchedEffect(Unit) { delay(1_500); s.pop() }
            else RescueScreen(room, vm.nodeId, s.hereFix, s.headings, medical = vm.medicalFor(room.victimId), rated = remember(trustScores, room.id) {
                (room.helpers.map { it.nodeId } + room.victimId).flatMap { p -> vm.myRatingsOf(p).filter { it.ctx == room.id }.map { "$p|${it.kind.code}" } }.toSet()
            }, actions = RescueActions(
                onBack = s::pop, onJoin = { vm.joinRescue(room.id) }, onSend = { vm.sendRescue(room.id, it) },
                onArrived = { vm.arrivedRescue(room.id) }, onLeave = { vm.leaveRescue(room.id) },
                onNavigate = { s.showOnCompass(room.victimId) }, onGuide = { s.push(Route.Article(it)) },
                onSignal = { s.push(Route.Signal) }, onSafe = { vm.cancelSos(); sosReached = -1 },
                readiness = if (!room.mine) emptyMap() else mapOf(
                    "location" to (s.system.locationPermission && radios.location), "bluetooth" to radios.bluetooth,
                    "mesh" to running, "internet" to online),
                onTurnOn = s.turnOnForSos,
                onSignalThem = { what -> vm.ring(room.victimId, what) },
                onRate = { who, kind -> vm.rate(who, kind, room.id, "") },
            ))
        }
    }
}

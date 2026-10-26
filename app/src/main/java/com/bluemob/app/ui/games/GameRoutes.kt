package com.bluemob.app.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.nav.Route
import com.bluemob.app.ui.nav.Shell

/** Games with people nearby (or over the internet relay) and against the phone. */
@Composable
fun GameRoutes(route: Route.GamesArea, s: Shell) {
    val vm = s.vm
    val all by vm.matches.collectAsStateWithLifecycle()
    when (route) {
        Route.Games -> {
            val people by vm.people.collectAsStateWithLifecycle()
            GamesScreen(s::pop, onPlay = { s.push(Route.Game(it)) },
                // Nearby now, or anyone you've met when this phone is on the internet relay.
                people = vm.liveConnected.collectAsStateWithLifecycle().value.let { live -> people.filter { it.presence == Presence.ONLINE || (live && it.lastSeen > 0) } },
                matches = all.values.sortedByDescending { it.updatedAt },
                onChallenge = { p, game -> vm.challenge(p.nodeId, p.name, game)?.let { s.push(Route.Match(it)) } },
                onOpenMatch = { s.push(Route.Match(it)) }, onAccept = vm::acceptGame, onDecline = vm::declineGame)
        }
        is Route.Match -> {
            val m = all[route.id]
            if (m == null) LaunchedEffect(Unit) { s.pop() }
            else MatchScreen(m, s::pop, onPlay = { vm.playGame(m.id, it) }, onAgain = { vm.gameAgain(m.id) }, onLeave = { vm.leaveGame(m.id) },
                reaction = vm.gameReactions.collectAsStateWithLifecycle().value[m.id], onReact = { vm.reactInGame(m.id, it) })
        }
        is Route.Game -> ComputerGameScreen(route.id, s::pop)
    }
}

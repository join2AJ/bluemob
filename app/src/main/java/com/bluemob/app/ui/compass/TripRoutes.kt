package com.bluemob.app.ui.compass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bluemob.app.trail.TravelMode
import com.bluemob.app.ui.chat.rememberLoaded
import com.bluemob.app.ui.nav.Route
import com.bluemob.app.ui.nav.Shell
import kotlinx.coroutines.launch

/** Recorded trips: the list, and one trip with its map, speeds and exports. */
@Composable
fun TripRoutes(route: Route.TripsArea, s: Shell) {
    val vm: TripsViewModel = viewModel()
    val trail = s.vm
    val trips by vm.trips.collectAsStateWithLifecycle()
    val current by vm.currentTrip.collectAsStateWithLifecycle()
    when (route) {
        Route.Trips -> TripsScreen(trips, current, s::pop, onOpen = { s.push(Route.Trip(it)) },
            onNewTrip = { if (!s.system.locationPermission) s.actions.requestLocation(); vm.startNewTrip(it) })
        is Route.Trip -> {
            val id = route.id
            val trip = trips.firstOrNull { it.id == id }
            val live by trail.trailPoints.collectAsStateWithLifecycle()
            val spots by trail.spots.collectAsStateWithLifecycle()
            val stored = rememberLoaded(id, trip?.points) { vm.pointsOf(id) }
            if (trip == null) LaunchedEffect(Unit) { s.pop() }
            else TripScreen(trip, if (id == current) live else stored.orEmpty(), spots, id == current, s::pop,
                onRename = { vm.renameTrip(id, it) }, onDelete = { vm.deleteTrip(id) },
                onShareGpx = { s.scope.launch { vm.tripGpxFile(id)?.let { s.actions.shareFile(it, "application/gpx+xml") } } },
                onExport = { s.scope.launch { vm.exportTrip(id).takeIf { it.isNotEmpty() }?.let { s.actions.shareFiles(it) } } },
                mode = vm.tripModes.collectAsStateWithLifecycle().value[id]?.let { m -> runCatching { TravelMode.valueOf(m) }.getOrNull() },
                onMode = { vm.setTripMode(id, it) })
        }
    }
}

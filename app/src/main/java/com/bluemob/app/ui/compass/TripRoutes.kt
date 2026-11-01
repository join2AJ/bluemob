package com.bluemob.app.ui.compass

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        Route.DemoTrip -> com.bluemob.app.ui.chat.DemoTripScreen(s::pop) { trail.say(it) }
        Route.Peaks -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            var camera by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
            androidx.compose.runtime.DisposableEffect(Unit) { trail.holdLocation(); onDispose { trail.releaseLocation() } }
            val here = trail.myLocation.collectAsStateWithLifecycle().value
            PeakFinderScreen(here, trail.altitude.collectAsStateWithLifecycle().value, trail.savedPeaks.collectAsStateWithLifecycle().value,
                androidx.compose.runtime.remember { trail.cameraPose() }, camera,
                onAskCamera = { s.actions.requestCamera { camera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED } },
                onAskLocation = s.actions.requestLocation,
                onDownload = { here?.let { trail.downloadPeaks(it.lat, it.lon) } ?: "Waiting for your position" }, onBack = s::pop)
        }
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

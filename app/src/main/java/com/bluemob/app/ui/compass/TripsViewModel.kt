package com.bluemob.app.ui.compass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bluemob.app.BlueMobApp
import kotlinx.coroutines.launch

/** Recorded trips: list, points, rename, delete, exports and the user's choice of vehicle. */
class TripsViewModel(app: Application) : AndroidViewModel(app) {
    private val blueMob = app as BlueMobApp
    private val trail = blueMob.trail
    private val settings = blueMob.settings

    val trips = trail.allTrips
    val currentTrip = trail.currentTrip
    fun startNewTrip(name: String) = trail.startNewTrip(name)
    suspend fun pointsOf(id: String) = trail.pointsOf(id)
    fun renameTrip(id: String, name: String) = viewModelScope.launch { trail.renameTrip(id, name) }
    fun deleteTrip(id: String) = viewModelScope.launch { trail.deleteTrip(id) }
    /** The trip as a GPX file in the temporary share folder. */
    suspend fun tripGpxFile(id: String): java.io.File? {
        val t = trips.value.firstOrNull { it.id == id } ?: return null
        val gpx = com.bluemob.app.ui.compass.tripGpx(t, trail.pointsOf(id))
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            java.io.File(getApplication<android.app.Application>().cacheDir, "open").apply { mkdirs() }
                .resolve(com.bluemob.app.files.Attachment.safeName(t.name) + ".gpx").apply { writeText(gpx) }
        }
    }
    /** Picture (PNG), every point with timestamps (CSV), and GPX: everything about a trip, ready to share. */
    suspend fun exportTrip(id: String): List<java.io.File> {
        val t = trips.value.firstOrNull { it.id == id } ?: return emptyList()
        val points = trail.pointsOf(id)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(getApplication<android.app.Application>().cacheDir, "open").apply { mkdirs() }
            val base = com.bluemob.app.files.Attachment.safeName(t.name)
            val png = dir.resolve("$base.png").apply {
                outputStream().use { com.bluemob.app.trail.TripImage.render(t, points).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            val csv = dir.resolve("$base.csv").apply { writeText(com.bluemob.app.ui.compass.tripCsv(points)) }
            val gpx = dir.resolve("$base.gpx").apply { writeText(com.bluemob.app.ui.compass.tripGpx(t, points)) }
            listOf(png, csv, gpx)
        }
    }
    val tripModes = settings.tripModes
    fun setTripMode(tripId: String, mode: com.bluemob.app.trail.TravelMode?) = settings.setTripMode(tripId, mode?.name)
}

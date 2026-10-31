package com.bluemob.app.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/** Web map tiles (256 px squares, "slippy map" numbering), for the satellite picture of a position. */
object Tiles {
    const val SIZE = 256

    /** The position in world pixels at [zoom]: (x, y). */
    fun pixel(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val n = (1 shl zoom).toDouble() * SIZE
        val la = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val x = (lon + 180.0) / 360.0 * n
        val y = (1.0 - ln(tan(la) + 1.0 / cos(la)) / PI) / 2.0 * n
        return x to y
    }

    /** Metres on the ground per pixel at [lat], [zoom]. */
    fun metresPerPixel(lat: Double, zoom: Int): Double = 156_543.033_92 * cos(Math.toRadians(lat)) / (1 shl zoom)

    /** A closer zoom for a precise position, a wider one for a rough one. */
    fun zoomFor(accuracyM: Double?): Int = when {
        accuracyM == null || accuracyM <= 60 -> 17
        accuracyM <= 250 -> 16
        accuracyM <= 1_000 -> 14
        else -> 12
    }

    /** Esri World Imagery (free satellite pictures; they must be credited on the map). */
    fun url(z: Int, x: Int, y: Int) = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
    const val CREDIT = "Imagery © Esri, Maxar, Earthstar Geographics"

    /** Google Maps with a pin at the position (the app if installed, else the browser). */
    fun googleMaps(lat: Double, lon: Double) = "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f".format(java.util.Locale.US, lat, lon)

    /** Google Maps centred on the position, in satellite view. */
    fun googleSatellite(lat: Double, lon: Double) =
        "https://www.google.com/maps/@?api=1&map_action=map&center=%.6f,%.6f&zoom=18&basemap=satellite".format(java.util.Locale.US, lat, lon)
}

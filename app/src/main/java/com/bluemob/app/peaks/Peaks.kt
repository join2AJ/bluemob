package com.bluemob.app.peaks

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A named summit: where it is and how high (metres). */
data class Peak(val name: String, val lat: Double, val lon: Double, val eleM: Int, val id: String = "$lat,$lon")

/** A peak as seen from here: which way, how far, and how high above the horizon. */
data class SeenPeak(val peak: Peak, val bearingDeg: Double, val distM: Double, val elevDeg: Double)

/**
 * Peak finder maths and data. Directions are true bearings; the height above the horizon allows for the Earth's
 * curve and the usual bending of light near the ground. Without a terrain model we can't tell when a nearer ridge hides
 * a peak, so it says "may be hidden behind nearer hills".
 */
object Peaks {
    private const val R = 6_371_000.0
    /** Atmospheric refraction coefficient (light bends a little over the curve). */
    private const val K = 0.13

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * R * atan2(sqrt(a), sqrt(1 - a))
    }

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2); val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Degrees above (or below) a level horizon, for a peak [distM] away and [rise] metres higher than us. */
    fun elevationDeg(rise: Double, distM: Double): Double {
        if (distM < 1) return 90.0
        val drop = distM * distM / (2 * R) * (1 - K)
        return Math.toDegrees(atan((rise - drop) / distM))
    }

    /** Peaks within [maxDistM] of us, nearest first, with where to look for each. */
    fun around(all: Collection<Peak>, lat: Double, lon: Double, myEleM: Double, maxDistM: Double): List<SeenPeak> =
        all.mapNotNull { p ->
            val d = distanceM(lat, lon, p.lat, p.lon)
            if (d > maxDistM || d < 30) null else SeenPeak(p, bearingDeg(lat, lon, p.lat, p.lon), d, elevationDeg(p.eleM - myEleM, d))
        }.filter { it.elevDeg > -2.0 }.sortedBy { it.distM }

    /** Signed difference a − b in degrees, in −180..180. */
    fun angleDiff(a: Double, b: Double): Double = ((a - b + 540) % 360) - 180

    /** Well-known Indian and Himalayan peaks, for when nothing has been downloaded. Positions are approximate. */
    val BUILT_IN = listOf(
        Peak("Mount Everest", 27.9881, 86.9250, 8849), Peak("K2", 35.8808, 76.5155, 8611), Peak("Kangchenjunga", 27.7025, 88.1475, 8586),
        Peak("Lhotse", 27.9617, 86.9333, 8516), Peak("Makalu", 27.8897, 87.0889, 8485), Peak("Cho Oyu", 28.0942, 86.6608, 8188),
        Peak("Dhaulagiri", 28.6983, 83.4875, 8167), Peak("Manaslu", 28.5497, 84.5597, 8163), Peak("Nanga Parbat", 35.2375, 74.5892, 8126),
        Peak("Annapurna", 28.5961, 83.8203, 8091), Peak("Nanda Devi", 30.3757, 79.9707, 7816), Peak("Kamet", 30.9200, 79.5917, 7756),
        Peak("Saltoro Kangri", 35.3992, 76.8483, 7742), Peak("Abi Gamin", 30.9325, 79.6025, 7355), Peak("Chaukhamba", 30.7497, 79.2877, 7138),
        Peak("Nun", 33.9800, 76.0200, 7135), Peak("Trishul", 30.3125, 79.7764, 7120), Peak("Kun", 34.0247, 76.0425, 7077),
        Peak("Dunagiri", 30.5161, 79.8361, 7066), Peak("Kedarnath", 30.7960, 79.0660, 6940), Peak("Panchachuli", 30.2142, 80.4278, 6904),
        Peak("Thalay Sagar", 30.8590, 79.0700, 6904), Peak("Nanda Kot", 30.2753, 80.0675, 6861), Peak("Neelkanth", 30.7333, 79.4000, 6596),
        Peak("Shivling", 30.8800, 79.0631, 6543), Peak("Bandarpunch", 31.0094, 78.5528, 6316), Peak("Indrasan", 32.2167, 77.4000, 6221),
        Peak("Stok Kangri", 33.9847, 77.4569, 6153), Peak("Kinner Kailash", 31.5100, 78.4700, 6050), Peak("Deo Tibba", 32.2075, 77.3800, 6001),
        Peak("Hanuman Tibba", 32.3333, 77.1000, 5982), Peak("Friendship Peak", 32.4069, 77.1414, 5289), Peak("Kedarkantha", 31.0215, 78.1718, 3800),
        Peak("Saramati", 25.7417, 95.0333, 3841), Peak("Sandakphu", 27.1060, 88.0000, 3636), Peak("Anamudi", 10.1689, 77.0614, 2695),
        Peak("Doddabetta", 11.4022, 76.7350, 2637), Peak("Guru Shikhar", 24.6514, 72.7764, 1722), Peak("Kalsubai", 19.6011, 73.7106, 1646),
    )

    // ---- downloaded peaks (OpenStreetMap), kept on the phone ----

    /** The question for OpenStreetMap's Overpass service: named peaks within [radiusM] of a point. */
    fun overpassQuery(lat: Double, lon: Double, radiusM: Int) =
        "[out:json][timeout:60];node[\"natural\"=\"peak\"][\"name\"](around:$radiusM,${"%.2f".format(java.util.Locale.US, lat)},${"%.2f".format(java.util.Locale.US, lon)});out body;"

    /** Peaks from an Overpass answer; ones without a height are skipped (we can't place them above the horizon). */
    fun parseOverpass(json: String): List<Peak> = runCatching {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        (0 until els.length()).mapNotNull { i ->
            val e = els.optJSONObject(i) ?: return@mapNotNull null
            val tags = e.optJSONObject("tags") ?: return@mapNotNull null
            val name = tags.optString("name:en").ifBlank { tags.optString("name") }.trim().take(60)
            val ele = parseEle(tags.optString("ele")) ?: return@mapNotNull null
            if (name.isBlank()) null else Peak(name, e.optDouble("lat"), e.optDouble("lon"), ele, "osm:" + e.optLong("id"))
        }
    }.getOrDefault(emptyList())

    /** "4210", "4210 m", "4,210", "13812 ft" → metres. */
    fun parseEle(s: String): Int? {
        val t = s.trim().lowercase().replace(",", "")
        val n = Regex("-?\\d+(\\.\\d+)?").find(t)?.value?.toDoubleOrNull() ?: return null
        val m = if ("ft" in t || "'" in t) n * 0.3048 else n
        return m.toInt().takeIf { it in 1..9_000 }
    }

    fun save(file: File, peaks: List<Peak>, lat: Double, lon: Double, at: Long) {
        val a = JSONArray(); peaks.forEach { a.put(JSONArray().put(it.name).put(it.lat).put(it.lon).put(it.eleM).put(it.id)) }
        file.writeText(JSONObject().put("at", at).put("lat", lat).put("lon", lon).put("peaks", a).toString())
    }

    data class Saved(val peaks: List<Peak>, val lat: Double, val lon: Double, val at: Long)

    fun load(file: File): Saved? = runCatching {
        val o = JSONObject(file.readText())
        val a = o.getJSONArray("peaks")
        Saved((0 until a.length()).map { i -> a.getJSONArray(i).let { p -> Peak(p.getString(0), p.getDouble(1), p.getDouble(2), p.getInt(3), p.optString(4)) } },
            o.optDouble("lat"), o.optDouble("lon"), o.optLong("at"))
    }.getOrNull()

    /** Downloads named peaks within [radiusM] (needs internet). Null if it failed. */
    fun download(lat: Double, lon: Double, radiusM: Int = 150_000): List<Peak>? = runCatching {
        val c = java.net.URL("https://overpass-api.de/api/interpreter").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20_000; c.readTimeout = 90_000
        c.setRequestProperty("User-Agent", "BlueMob (Android) peak finder")
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        try {
            c.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(overpassQuery(lat, lon, radiusM), "UTF-8")).toByteArray()) }
            if (c.responseCode != 200) null else parseOverpass(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }.getOrNull()
}

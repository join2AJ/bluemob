package com.bluemob.app.peaks

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One downloaded area: the named peaks within 150 km of a point, kept for offline. */
data class PeakArea(val name: String, val lat: Double, val lon: Double, val at: Long, val peaks: List<Peak>)

/** A search hit online: a peak, or a place (town, village) to download the peaks around. */
data class FoundPlace(val name: String, val lat: Double, val lon: Double, val eleM: Int?, val isPeak: Boolean, val area: String = "")

/**
 * Every peak kept on this phone: several downloaded areas, plus peaks the user added themselves
 * (also sent to the BlueMob team as a request, so everyone gets them later).
 */
data class PeakLibrary(val areas: List<PeakArea> = emptyList(), val mine: List<Peak> = emptyList()) {
    val all: List<Peak> get() = (mine + areas.flatMap { it.peaks }).distinctBy { it.id }
    val isEmpty: Boolean get() = areas.isEmpty() && mine.isEmpty()

    /** Adds or replaces an area: one within 30 km of another counts as the same place. Keeps the newest [MAX_AREAS]. */
    fun withArea(a: PeakArea): PeakLibrary =
        copy(areas = (listOf(a) + areas.filter { Peaks.distanceM(it.lat, it.lon, a.lat, a.lon) > 30_000 }).take(MAX_AREAS))
    fun withoutArea(name: String, at: Long) = copy(areas = areas.filterNot { it.name == name && it.at == at })
    fun withMine(p: Peak) = copy(mine = (listOf(p) + mine.filterNot { it.id == p.id }).take(200))
    fun withoutMine(id: String) = copy(mine = mine.filterNot { it.id == id })

    /** For the peak finder: every peak, and when the newest area was downloaded. Null when nothing is kept. */
    fun asSaved(): Peaks.Saved? = if (isEmpty) null else {
        val newest = areas.maxByOrNull { it.at }
        Peaks.Saved(all, newest?.lat ?: mine.first().lat, newest?.lon ?: mine.first().lon, newest?.at ?: 0L)
    }

    /** Offline search: kept and famous peaks whose name contains [q]. */
    fun searchOffline(q: String): List<Peak> {
        val t = q.trim().lowercase(); if (t.length < 2) return emptyList()
        return (all + Peaks.BUILT_IN).distinctBy { it.name.lowercase() to (it.lat * 20).toInt() }
            .filter { t in it.name.lowercase() }.sortedBy { it.name.lowercase().indexOf(t) }.take(20)
    }

    companion object {
        const val MAX_AREAS = 12
        const val MINE_PREFIX = "mine:"

        fun encode(l: PeakLibrary): String {
            fun peaks(ps: List<Peak>) = JSONArray().apply { ps.forEach { put(JSONArray().put(it.name).put(it.lat).put(it.lon).put(it.eleM).put(it.id)) } }
            return JSONObject()
                .put("areas", JSONArray().apply { l.areas.forEach { a -> put(JSONObject().put("name", a.name).put("lat", a.lat).put("lon", a.lon).put("at", a.at).put("peaks", peaks(a.peaks))) } })
                .put("mine", peaks(l.mine)).toString()
        }

        fun decode(s: String): PeakLibrary? = runCatching {
            fun peaks(a: JSONArray) = (0 until a.length()).map { i -> a.getJSONArray(i).let { p -> Peak(p.getString(0), p.getDouble(1), p.getDouble(2), p.getInt(3), p.optString(4)) } }
            val o = JSONObject(s)
            val areas = o.optJSONArray("areas") ?: JSONArray()
            PeakLibrary((0 until areas.length()).map { i -> areas.getJSONObject(i).let { a ->
                PeakArea(a.getString("name"), a.getDouble("lat"), a.getDouble("lon"), a.getLong("at"), peaks(a.getJSONArray("peaks"))) } },
                peaks(o.optJSONArray("mine") ?: JSONArray()))
        }.getOrNull()

        /** The library file, or the single area older versions kept in peaks.json. */
        fun load(file: File, old: File): PeakLibrary =
            (if (file.exists()) decode(file.readText()) else null)
                ?: Peaks.load(old)?.let { s -> PeakLibrary(listOf(PeakArea("Around you", s.lat, s.lon, s.at, s.peaks))) }
                ?: PeakLibrary()

        // ---- online search ----

        /** India, Nepal, Bhutan, Pakistan and Tibet: where BlueMob's users trek. */
        private const val BBOX = "5.0,60.0,37.5,98.5"

        /** The search words, without anything Overpass would read as a pattern or a quote. */
        fun cleanQuery(q: String) = q.trim().take(40).replace(Regex("[\\\\\"^$.|?*+()\\[\\]{}]"), "").replace(Regex("\\s+"), " ")

        /** Overpass question: peaks and places whose name contains [q] (case-insensitive). */
        fun searchQuery(q: String): String {
            val safe = cleanQuery(q)
            return "[out:json][timeout:25];(" +
                "node[\"natural\"=\"peak\"][\"name\"~\"$safe\",i]($BBOX);" +
                "node[\"natural\"=\"peak\"][\"name:en\"~\"$safe\",i]($BBOX);" +
                "node[\"place\"~\"^(city|town|village|hamlet|locality)$\"][\"name\"~\"$safe\",i]($BBOX););out body 40;"
        }

        fun parseSearch(json: String): List<FoundPlace> = runCatching {
            val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
            (0 until els.length()).mapNotNull { i ->
                val e = els.optJSONObject(i) ?: return@mapNotNull null
                val tags = e.optJSONObject("tags") ?: return@mapNotNull null
                val name = tags.optString("name:en").ifBlank { tags.optString("name") }.trim().take(60)
                if (name.isBlank()) return@mapNotNull null
                val peak = tags.optString("natural") == "peak"
                FoundPlace(name, e.optDouble("lat"), e.optDouble("lon"), Peaks.parseEle(tags.optString("ele")), peak,
                    tags.optString("is_in:state").ifBlank { tags.optString("place") })
            }.distinctBy { it.name.lowercase() to (it.lat * 50).toInt() to (it.lon * 50).toInt() }
                .sortedWith(compareByDescending<FoundPlace> { it.isPeak }.thenByDescending { it.eleM ?: 0 })
        }.getOrDefault(emptyList())

        /** OpenStreetMap's place search (Nominatim): fast, finds peaks, towns, valleys and states. */
        fun nominatimUrl(q: String) = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=15&extratags=1&countrycodes=in,np,bt,pk&q=" +
            java.net.URLEncoder.encode(q.trim().take(60), "UTF-8")

        fun parseNominatim(json: String): List<FoundPlace> = runCatching {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val display = o.optString("display_name")
                val name = o.optString("name").ifBlank { display.substringBefore(",") }.trim().take(60)
                val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                val peak = o.optString("category") == "natural" && o.optString("type") in setOf("peak", "volcano", "hill")
                val ele = o.optJSONObject("extratags")?.optString("ele")?.let { Peaks.parseEle(it) }
                FoundPlace(name, lat, lon, ele, peak, display.substringAfter(", ", "").split(", ").takeLast(2).joinToString(", "))
            }.distinctBy { it.name.lowercase() to (it.lat * 50).toInt() }
        }.getOrDefault(emptyList())

        private fun get(url: String): String? {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 12_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "BlueMob/1 (Android; peak finder)")
            c.setRequestProperty("Accept-Language", "en")
            return try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
        }

        /** Online search: the place search first; peaks by name from Overpass if it finds nothing. Null when offline. */
        fun search(q: String): List<FoundPlace>? = runCatching {
            val fast = get(nominatimUrl(q))?.let(::parseNominatim)
            if (!fast.isNullOrEmpty()) return@runCatching fast
            val c = java.net.URL("https://overpass-api.de/api/interpreter").openConnection() as java.net.HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 12_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "BlueMob/1 (Android; peak finder)")
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val slow = try {
                c.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(searchQuery(q), "UTF-8")).toByteArray()) }
                if (c.responseCode != 200) null else parseSearch(c.inputStream.bufferedReader().readText())
            } catch (e: java.io.IOException) { null } finally { c.disconnect() }
            slow ?: fast
        }.getOrNull()

        // ---- "add a missing peak" ----

        /** "30.8612" or "30° 51' 40\" N" → decimal degrees. Null if it can't be read. */
        fun parseCoord(s: String): Double? {
            val t = s.trim().uppercase()
            t.toDoubleOrNull()?.let { return it }
            val n = Regex("\\d+(\\.\\d+)?").findAll(t).map { it.value.toDouble() }.toList()
            if (n.isEmpty()) return null
            val v = n[0] + (n.getOrNull(1) ?: 0.0) / 60 + (n.getOrNull(2) ?: 0.0) / 3600
            return if ('S' in t || 'W' in t || t.startsWith("-")) -v else v
        }

        /** What's wrong with a missing-peak request, or null when it can be sent. */
        fun problem(name: String, lat: Double?, lon: Double?, ele: Int?): String? = when {
            name.trim().length < 2 -> "Give the peak's name."
            lat == null || lat !in -90.0..90.0 -> "Latitude should be between -90 and 90, like 30.8612."
            lon == null || lon !in -180.0..180.0 -> "Longitude should be between -180 and 180, like 79.0634."
            ele == null -> "Give the height in metres. A rough number is fine."
            ele !in 1..9_000 -> "Height should be in metres, between 1 and 9,000."
            else -> null
        }

        fun requestText(name: String, lat: Double, lon: Double, ele: Int, note: String) =
            "Please add this peak:\nName: ${name.trim()}\nLatitude: ${"%.5f".format(java.util.Locale.US, lat)}\nLongitude: ${"%.5f".format(java.util.Locale.US, lon)}\n" +
                "Height: $ele m\n" + note.trim().let { if (it.isEmpty()) "" else "Note: $it" }
    }
}

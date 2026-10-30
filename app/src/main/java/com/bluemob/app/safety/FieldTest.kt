package com.bluemob.app.safety

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * A field test: before trusting BlueMob on a trek or at a mela, a group runs it for a few hours with everyone's
 * phones in pockets, and each phone measures what really happened:
 *
 * - **delivery:** every few minutes a roll call goes to the trip group; each member's receipt says whether their
 *   phone was reached, how fast, and through how many other phones;
 * - **battery:** the level every 5 minutes (time on the charger is left out);
 * - **stopped by the phone:** BlueMob notes it's alive every minute; a gap means the phone paused or closed it
 *   (battery saver, "app killer"), which is the biggest real-world risk;
 * - **people around:** how many phones were connected.
 *
 * Everything is a line of JSON in one file on the phone, so it survives BlueMob being closed and can be shared as is.
 */
class FieldTest(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    /** The running test, or null. */
    data class Session(val startedAt: Long, val tripId: String?, val everyMin: Int)

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** Roll calls this test sent (their message IDs), so their receipts are counted. */
    private val probes = HashSet<String>().apply { events().filter { it.kind == "probe" }.forEach { add(it.j.optString("id")) } }

    private val _changed = MutableStateFlow(0)
    /** Goes up each time something is written (the screen redraws its report). */
    val changed: StateFlow<Int> = _changed.asStateFlow()

    /** Starts a new test (the last one's results are replaced). [device] is "maker model · Android version". */
    @Synchronized
    fun start(tripId: String?, everyMin: Int, device: String) {
        file.delete()
        probes.clear()
        val s = Session(now(), tripId, everyMin.coerceIn(2, 60))
        write(JSONObject().put("k", "start").put("trip", tripId ?: "").put("every", s.everyMin).put("device", device))
        _session.value = s
    }

    @Synchronized
    fun stop() {
        if (_session.value == null) return
        write(JSONObject().put("k", "stop"))
        _session.value = null
    }

    /** BlueMob is alive; [peers] phones are connected right now; the mesh is [meshOn]. */
    fun beat(peers: Int, meshOn: Boolean) { if (_session.value != null) write(JSONObject().put("k", "beat").put("peers", peers).put("mesh", meshOn)) }

    fun battery(pct: Int, charging: Boolean) { if (_session.value != null) write(JSONObject().put("k", "bat").put("pct", pct).put("chg", charging)) }

    /** A roll call went to [members] phones. */
    @Synchronized
    fun probe(messageId: String, members: Int) {
        if (_session.value == null) return
        probes += messageId
        write(JSONObject().put("k", "probe").put("id", messageId).put("n", members))
    }

    /** A member's phone answered roll call [parent]: [hops] (0 = over the internet) after [ms]. */
    @Synchronized
    fun delivered(parent: String, member: String, hops: Int, viaInternet: Boolean, ms: Long) {
        if (_session.value == null || parent !in probes) return
        write(JSONObject().put("k", "got").put("id", parent).put("to", member).put("hops", if (viaInternet) 0 else hops).put("ms", ms))
    }

    /** A finished test's results are erased 30 days after it stopped (storage limitation). */
    @Synchronized
    fun purgeIfOld(now: Long) {
        if (_session.value != null) return
        val stop = events().lastOrNull { it.kind == "stop" }?.at ?: return
        if (now - stop >= KEEP_MS) { file.delete(); probes.clear(); _changed.value++ }
    }

    fun events(): List<Event> = runCatching {
        file.readLines().mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull()?.let { Event(it.optLong("t"), it.optString("k"), it) } }
    }.getOrDefault(emptyList())

    fun report(): Report = Report.of(events(), now())

    /** The raw results, to share. */
    fun export(): String = runCatching { file.readText() }.getOrDefault("")

    @Synchronized
    private fun write(j: JSONObject) {
        runCatching { file.appendText(j.put("t", now()).toString() + "\n") }
        _changed.value++
    }

    private fun load(): Session? {
        val ev = events()
        val start = ev.lastOrNull { it.kind == "start" } ?: return null
        if (ev.any { it.kind == "stop" && it.at >= start.at }) return null
        return Session(start.at, start.j.optString("trip").ifBlank { null }, start.j.optInt("every", 10))
    }

    data class Event(val at: Long, val kind: String, val j: JSONObject)

    companion object {
        const val KEEP_MS = 30 * 24 * 3_600_000L
    }

    /** What a test found. */
    data class Report(
        val device: String,
        val minutes: Long,
        val sent: Int,
        val delivered: Int,
        val medianMs: Long?,
        val slowestMs: Long?,
        /** Answers by path: "Direct", "1 phone", "2 phones", "3+ phones", "Internet". */
        val paths: Map<String, Int>,
        /** Battery used per hour, off the charger. */
        val batteryPerHour: Double?,
        val batteryFrom: Int?,
        val batteryTo: Int?,
        /** Times BlueMob was paused or stopped by the phone, and for how long in all. */
        val stops: Int,
        val stoppedMinutes: Long,
        val maxPeers: Int,
        val avgPeers: Double,
    ) {
        val deliveryPct: Int? get() = if (sent == 0) null else (delivered * 100 / sent)

        /** Plain text to paste in a chat or an email. */
        fun summary(): String = buildString {
            appendLine("BlueMob field test · $device")
            appendLine("Ran for ${minutes / 60} h ${minutes % 60} min")
            appendLine("Delivery: ${deliveryPct?.let { "$it% ($delivered of $sent)" } ?: "no roll calls yet"}")
            medianMs?.let { appendLine("Time to reach: ${secs(it)} typical, ${secs(slowestMs ?: it)} slowest") }
            if (paths.isNotEmpty()) appendLine("Paths: " + paths.entries.joinToString(", ") { "${it.key} ${it.value}" })
            batteryPerHour?.let { appendLine("Battery: ${"%.1f".format(it)}% per hour ($batteryFrom% → $batteryTo%)") }
            appendLine("Stopped by the phone: $stops time${if (stops == 1) "" else "s"}${if (stops > 0) ", $stoppedMinutes min in all" else ""}")
            append("Phones connected: up to $maxPeers, ${"%.1f".format(avgPeers)} on average")
        }

        companion object {
            /** A gap longer than this between "alive" notes means the phone paused or stopped BlueMob. */
            const val GAP_MS = 3 * 60_000L

            fun secs(ms: Long) = if (ms < 60_000) "${(ms + 500) / 1000} s" else "${ms / 60_000} min ${(ms % 60_000) / 1000} s"

            fun of(all: List<Event>, now: Long): Report {
                val startAt = all.lastOrNull { it.kind == "start" }?.at
                val ev = if (startAt == null) all else all.filter { it.at >= startAt }
                val end = ev.lastOrNull { it.kind == "stop" }?.at ?: now
                val got = ev.filter { it.kind == "got" }.distinctBy { it.j.optString("id") + "/" + it.j.optString("to") }
                val ms = got.map { it.j.optLong("ms") }.sorted()
                val paths = got.groupingBy {
                    when (val h = it.j.optInt("hops")) { 0 -> "Internet"; 1 -> "Direct"; 2 -> "1 phone"; 3 -> "2 phones"; else -> if (h > 3) "3+ phones" else "Direct" }
                }.eachCount()
                // Battery: add up the drops between readings off the charger.
                val bats = ev.filter { it.kind == "bat" }
                var used = 0; var offMs = 0L
                bats.zipWithNext().forEach { (a, b) ->
                    if (!a.j.optBoolean("chg") && !b.j.optBoolean("chg")) {
                        used += (a.j.optInt("pct") - b.j.optInt("pct")).coerceAtLeast(0); offMs += b.at - a.at
                    }
                }
                // Stops: gaps between "alive" notes (and from the last one to now, while the test runs).
                val beats = ev.filter { it.kind == "beat" || it.kind == "start" }.map { it.at } + listOfNotNull(if (ev.none { it.kind == "stop" }) null else end)
                val gaps = beats.sorted().zipWithNext().map { (a, b) -> b - a }.filter { it > GAP_MS }
                val peers = ev.filter { it.kind == "beat" }.map { it.j.optInt("peers") }
                return Report(
                    device = ev.firstOrNull { it.kind == "start" }?.j?.optString("device").orEmpty(),
                    minutes = if (startAt == null) 0 else (end - startAt) / 60_000,
                    sent = ev.filter { it.kind == "probe" }.sumOf { it.j.optInt("n") },
                    delivered = got.size,
                    medianMs = ms.getOrNull(ms.size / 2), slowestMs = ms.lastOrNull(),
                    paths = paths,
                    batteryPerHour = if (offMs >= 10 * 60_000L) used * 3_600_000.0 / offMs else null,
                    batteryFrom = bats.firstOrNull()?.j?.optInt("pct"), batteryTo = bats.lastOrNull()?.j?.optInt("pct"),
                    stops = gaps.size, stoppedMinutes = gaps.sumOf { it } / 60_000,
                    maxPeers = peers.maxOrNull() ?: 0, avgPeers = if (peers.isEmpty()) 0.0 else peers.average(),
                )
            }
        }
    }
}

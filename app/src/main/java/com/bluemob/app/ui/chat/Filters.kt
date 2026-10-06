package com.bluemob.app.ui.chat

import com.bluemob.app.data.CallLogEntry
import com.bluemob.app.files.AttKind
import com.bluemob.app.files.Attachment
import java.util.Calendar

/** Which calls to show in the call history. */
enum class CallKind(val label: String) {
    ALL("All"), MISSED("Missed"), RECEIVED("Received"), DIALLED("Dialled"), VIDEO("Video"), VOICE("Voice");

    fun matches(c: CallLogEntry): Boolean = when (this) {
        ALL -> true
        MISSED -> !c.outgoing && c.outcome == "MISSED"
        RECEIVED -> !c.outgoing && c.outcome == "ANSWERED"
        DIALLED -> c.outgoing
        VIDEO -> c.video
        VOICE -> !c.video
    }
}

/** How far back to look. */
enum class Period(val label: String) {
    ANY("Any time"), TODAY("Today"), WEEK("Last 7 days"), MONTH("Last 30 days");

    fun matches(at: Long, now: Long): Boolean = when (this) {
        ANY -> true
        TODAY -> sameDay(at, now)
        WEEK -> now - at < 7 * DAY
        MONTH -> now - at < 30 * DAY
    }
}

object CallFilter {
    fun apply(calls: List<CallLogEntry>, kind: CallKind, period: Period, query: String, now: Long = System.currentTimeMillis()): List<CallLogEntry> {
        val q = query.trim()
        return calls.filter { kind.matches(it) && period.matches(it.startedAt, now) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
            .sortedByDescending { it.startedAt }
    }

    /** Heading for the group a call falls in: "Today", "Yesterday", "This week", "This month" or "Earlier". */
    fun group(at: Long, now: Long = System.currentTimeMillis()): String = when {
        sameDay(at, now) -> "Today"
        sameDay(at, now - DAY) -> "Yesterday"
        now - at < 7 * DAY -> "This week"
        now - at < 30 * DAY -> "This month"
        else -> "Earlier"
    }

    /** Total talk time, e.g. "1 h 4 min", for the summary line. */
    fun talkTime(calls: List<CallLogEntry>): String {
        val s = calls.filter { it.outcome == "ANSWERED" }.sumOf { it.durationS }
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60} min"
            else -> "${s / 3600} h ${s % 3600 / 60} min"
        }
    }
}

/** Ordering for shared files. */
enum class FileSort(val label: String) { LATEST("Latest"), OLDEST("Oldest"), LARGEST("Largest"), SMALLEST("Smallest"), NAME("Name") }

/** A shared file in a chat, with who it's with. [ref] is the message it came in (any type, so this stays testable). */
data class SharedFile<T>(val ref: T, val peer: String, val fromMe: Boolean, val at: Long, val att: Attachment) {
    val ext: String get() = FileFilter.extension(att)
}

object FileFilter {
    /** "pdf", "jpg", "m4a"…: what the user knows the file as. Voice notes are "voice". */
    fun extension(a: Attachment): String =
        if (a.kind == AttKind.AUDIO && a.durationMs > 0) "voice"
        else a.name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { a.mime.substringAfter('/').take(5).ifEmpty { "file" } }

    /** Extensions present, most common first, with counts: for the filter chips. */
    fun extensions(files: List<SharedFile<*>>): List<Pair<String, Int>> =
        files.groupingBy { it.ext }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }

    fun <T> apply(files: List<SharedFile<T>>, kind: AttKind?, ext: String?, sort: FileSort, query: String = ""): List<SharedFile<T>> {
        val q = query.trim()
        val shown = files.filter { (kind == null || it.att.kind == kind) && (ext == null || it.ext == ext) && (q.isEmpty() || it.att.name.contains(q, ignoreCase = true)) }
        return when (sort) {
            FileSort.LATEST -> shown.sortedByDescending { it.at }
            FileSort.OLDEST -> shown.sortedBy { it.at }
            FileSort.LARGEST -> shown.sortedByDescending { it.att.size }
            FileSort.SMALLEST -> shown.sortedBy { it.att.size }
            FileSort.NAME -> shown.sortedBy { it.att.name.lowercase() }
        }
    }
}

private const val DAY = 86_400_000L

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

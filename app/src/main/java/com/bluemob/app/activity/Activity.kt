package com.bluemob.app.activity

import com.bluemob.app.data.AuditEntry
import com.bluemob.app.data.CallLogEntry
import com.bluemob.app.data.MessageEntity
import java.util.Calendar

/** What happened, for the activity dashboard. */
enum class ActivityType(val label: String) {
    MSG_SENT("Sent"), MSG_RECEIVED("Received"),
    CALL_DIALLED("Dialled"), CALL_RECEIVED("Received"), CALL_MISSED("Missed"),
    SOS_SENT("SOS sent"), SOS_RECEIVED("SOS received"), HELPED("Rescues joined"),
}

data class ActivityEvent(val at: Long, val type: ActivityType, val seconds: Long = 0, val peer: String? = null)

/** The dashboard's date filter. */
enum class ActivityRange(val label: String, val days: Int?) { WEEK("7 days", 7), MONTH("30 days", 30), QUARTER("90 days", 90), ALL("All", null) }

/** One bar in a chart: a day or a week, with a count per type. */
data class ActivityBin(val start: Long, val counts: Map<ActivityType, Int>)

/** Everything counted over a range, plus the bars for the charts. */
data class ActivitySummary(
    val counts: Map<ActivityType, Int>,
    val talkSeconds: Long,
    val people: Int,
    val bins: List<ActivityBin>,
    /** Days per bar: 1 for the short ranges, 7 for longer ones. */
    val binDays: Int,
) {
    fun count(t: ActivityType) = counts[t] ?: 0
}

object Activity {
    private const val DAY = 86_400_000L

    /** Turns messages, calls and the audit trail into events. Sky chats aren't counted as messages. */
    fun events(messages: List<MessageEntity>, calls: List<CallLogEntry>, audit: List<AuditEntry>): List<ActivityEvent> = buildList {
        messages.filter { it.peer != "sky" }.forEach { add(ActivityEvent(it.createdAt, if (it.fromMe) ActivityType.MSG_SENT else ActivityType.MSG_RECEIVED, peer = it.peer)) }
        calls.forEach {
            val t = when {
                it.outgoing -> ActivityType.CALL_DIALLED
                it.outcome == "ANSWERED" -> ActivityType.CALL_RECEIVED
                else -> ActivityType.CALL_MISSED
            }
            add(ActivityEvent(it.startedAt, t, if (it.outcome == "ANSWERED") it.durationS else 0, it.peer))
        }
        audit.filter { it.kind == "SOS" }.forEach {
            val t = when {
                it.text.startsWith("SOS sent") -> ActivityType.SOS_SENT
                it.text.startsWith("SOS received") -> ActivityType.SOS_RECEIVED
                it.text.startsWith("Joined the rescue") -> ActivityType.HELPED
                else -> null
            }
            if (t != null) add(ActivityEvent(it.time, t))
        }
    }

    /** Midnight (local time) of the day [at] falls in. */
    fun dayStart(at: Long): Long = Calendar.getInstance().apply {
        timeInMillis = at; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun summarize(events: List<ActivityEvent>, range: ActivityRange, now: Long): ActivitySummary {
        val today = dayStart(now)
        val days = range.days ?: (((today - dayStart(events.minOfOrNull { it.at } ?: now)) / DAY).toInt() + 1).coerceIn(7, 730)
        val from = today - (days - 1) * DAY
        val inRange = events.filter { it.at >= from && it.at <= now + DAY }
        val binDays = if (days <= 31) 1 else 7
        val binCount = (days + binDays - 1) / binDays
        // The newest bar always ends today; the first one may start before [from] for whole weeks.
        val firstStart = today - (binCount * binDays - 1) * DAY
        val bins = (0 until binCount).map { i ->
            val s = firstStart + i * binDays * DAY
            val e = s + binDays * DAY
            ActivityBin(s, inRange.filter { it.at in s until e }.groupingBy { it.type }.eachCount())
        }
        return ActivitySummary(
            counts = inRange.groupingBy { it.type }.eachCount(),
            talkSeconds = inRange.sumOf { it.seconds },
            people = inRange.mapNotNull { it.peer }.toSet().size,
            bins = bins,
            binDays = binDays,
        )
    }
}

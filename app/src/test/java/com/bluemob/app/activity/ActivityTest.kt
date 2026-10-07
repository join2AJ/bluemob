package com.bluemob.app.activity

import com.bluemob.app.data.AuditEntry
import com.bluemob.app.data.CallLogEntry
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityTest {
    private val day = 86_400_000L
    private val now = Activity.dayStart(1_760_000_000_000L) + 12 * 3_600_000L

    private fun msg(id: String, peer: String, fromMe: Boolean, at: Long) = MessageEntity(id, peer, fromMe, "hi", at, MessageStatus.SENT)
    private fun call(id: String, outgoing: Boolean, outcome: String, at: Long, s: Long = 0) = CallLogEntry(id, "p1", "P", false, outgoing, outcome, at, s)
    private fun audit(text: String, at: Long) = AuditEntry(at, at, "SOS", text, "", "")

    @Test fun countsByTypeAndSkipsSky() {
        val ev = Activity.events(
            listOf(msg("a", "p1", true, now), msg("b", "p2", false, now), msg("c", "sky", true, now)),
            listOf(call("1", true, "ANSWERED", now, 90), call("2", false, "ANSWERED", now, 30), call("3", false, "MISSED", now)),
            listOf(audit("SOS sent to 3 phones nearby", now), audit("Joined the rescue for Ravi: \"I'm coming\"", now), audit("\"I'm safe\": SOS ended", now)),
        )
        val s = Activity.summarize(ev, ActivityRange.WEEK, now)
        assertEquals(1, s.count(ActivityType.MSG_SENT))
        assertEquals(1, s.count(ActivityType.MSG_RECEIVED))
        assertEquals(1, s.count(ActivityType.CALL_DIALLED))
        assertEquals(1, s.count(ActivityType.CALL_RECEIVED))
        assertEquals(1, s.count(ActivityType.CALL_MISSED))
        assertEquals(1, s.count(ActivityType.SOS_SENT))
        assertEquals(1, s.count(ActivityType.HELPED))
        assertEquals(120L, s.talkSeconds)
        assertEquals(2, s.people)
    }

    @Test fun rangeFiltersAndBins() {
        val ev = listOf(ActivityEvent(now, ActivityType.MSG_SENT), ActivityEvent(now - 10 * day, ActivityType.MSG_SENT), ActivityEvent(now - 60 * day, ActivityType.MSG_SENT))
        val week = Activity.summarize(ev, ActivityRange.WEEK, now)
        assertEquals(1, week.count(ActivityType.MSG_SENT))
        assertEquals(7, week.bins.size)
        assertEquals(1, week.bins.last().counts[ActivityType.MSG_SENT])
        assertEquals(2, Activity.summarize(ev, ActivityRange.MONTH, now).count(ActivityType.MSG_SENT))
        val quarter = Activity.summarize(ev, ActivityRange.QUARTER, now)
        assertEquals(3, quarter.count(ActivityType.MSG_SENT))
        assertEquals(7, quarter.binDays)
        assertEquals(3, Activity.summarize(ev, ActivityRange.ALL, now).count(ActivityType.MSG_SENT))
    }
}

package com.bluemob.app.ui

import com.bluemob.app.data.CallLogEntry
import com.bluemob.app.files.AttKind
import com.bluemob.app.files.Attachment
import com.bluemob.app.ui.chat.CallFilter
import com.bluemob.app.ui.chat.CallKind
import com.bluemob.app.ui.chat.FileFilter
import com.bluemob.app.ui.chat.FileSort
import com.bluemob.app.ui.chat.Period
import com.bluemob.app.ui.chat.SharedFile
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltersTest {
    private val now = java.util.Calendar.getInstance().apply { set(2026, 9, 6, 18, 0) }.timeInMillis
    private val hour = 3_600_000L
    private val day = 24 * hour
    private fun call(id: String, outgoing: Boolean, outcome: String, ago: Long, video: Boolean = false, name: String = "Asha", secs: Long = 0) =
        CallLogEntry(id, "p", name, video, outgoing, outcome, now - ago, secs)

    private val calls = listOf(
        call("1", false, "MISSED", hour),
        call("2", false, "ANSWERED", 2 * hour, secs = 125),
        call("3", true, "NO_ANSWER", day + hour, video = true, name = "Ravi"),
        call("4", true, "ANSWERED", 10 * day, secs = 3_700),
    )

    @Test fun callKinds() {
        assertEquals(listOf("1"), CallFilter.apply(calls, CallKind.MISSED, Period.ANY, "", now).map { it.id })
        assertEquals(listOf("2"), CallFilter.apply(calls, CallKind.RECEIVED, Period.ANY, "", now).map { it.id })
        assertEquals(listOf("3", "4"), CallFilter.apply(calls, CallKind.DIALLED, Period.ANY, "", now).map { it.id })
        assertEquals(listOf("3"), CallFilter.apply(calls, CallKind.VIDEO, Period.ANY, "", now).map { it.id })
    }

    @Test fun byTimeAndName() {
        assertEquals(listOf("1", "2"), CallFilter.apply(calls, CallKind.ALL, Period.TODAY, "", now).map { it.id })
        assertEquals(listOf("1", "2", "3"), CallFilter.apply(calls, CallKind.ALL, Period.WEEK, "", now).map { it.id })
        assertEquals(listOf("3"), CallFilter.apply(calls, CallKind.ALL, Period.ANY, "rav", now).map { it.id })
        assertEquals("Today", CallFilter.group(now - hour, now))
        assertEquals("Yesterday", CallFilter.group(now - day, now))
        assertEquals("Earlier", CallFilter.group(now - 40 * day, now))
        assertEquals("1 h 3 min", CallFilter.talkTime(calls))
    }

    private fun file(name: String, kind: AttKind, size: Long, at: Long, dur: Long = 0) =
        SharedFile(name, "p", false, at, Attachment("f-$name", name, "x/y", size, "k", kind, dur))

    @Test fun filesByExtensionAndOrder() {
        val files = listOf(
            file("map.pdf", AttKind.DOC, 2_000, 1), file("notes.PDF", AttKind.DOC, 500, 3), file("tent.jpg", AttKind.IMAGE, 9_000, 2),
            file("Voice note.m4a", AttKind.AUDIO, 100, 4, dur = 3_000),
        )
        assertEquals(listOf("pdf" to 2, "jpg" to 1, "voice" to 1), FileFilter.extensions(files))
        assertEquals(listOf("notes.PDF", "map.pdf"), FileFilter.apply(files, null, "pdf", FileSort.LATEST).map { it.ref })
        assertEquals(listOf("tent.jpg", "map.pdf", "notes.PDF", "Voice note.m4a"), FileFilter.apply(files, null, null, FileSort.LARGEST).map { it.ref })
        assertEquals(listOf("map.pdf"), FileFilter.apply(files, AttKind.DOC, null, FileSort.OLDEST, "map").map { it.ref })
        assertEquals(listOf("map.pdf", "notes.PDF", "tent.jpg", "Voice note.m4a"), FileFilter.apply(files, null, null, FileSort.NAME).map { it.ref })
    }
}

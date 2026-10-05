package com.bluemob.app.rescue

import com.bluemob.app.data.RescueMessage
import com.bluemob.app.trail.PositionEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RescueRoomTest {
    /** Positions are stored as "lat,lon" here, so the test doesn't need JSON. */
    private val decode: (String?) -> PositionEstimate? = { s ->
        s?.split(",")?.let { (a, b) -> PositionEstimate(a.toDouble(), b.toDouble(), true, a.toDouble(), b.toDouble(), 0, 5f, null, null, null, 5.0, 0) }
    }
    private var t = 0L
    private fun m(kind: String, from: String, name: String, text: String = "", pos: String? = null, room: String = "sos1") =
        RescueMessage("id${t}", room, from, name, kind, text, ++t, pos)

    private val open = m(RescueRoom.OPEN, "ravi", "Ravi", "Twisted ankle", "30.0837,78.2663")

    @Test fun helpersJoinMoveArriveAndLeave() {
        val rows = listOf(
            open,
            m(RescueRoom.JOIN, "asha", "Asha", "I'm coming", "30.0871,78.2680"),
            m(RescueRoom.JOIN, "me", "Arjun", "I'm coming", "30.0900,78.2700"),
            m(RescueRoom.TEXT, "ravi", "Ravi", "I can hear you!"),
            m(RescueRoom.POS, "asha", "Asha", pos = "30.0850,78.2670"),
            m(RescueRoom.ARRIVED, "asha", "Asha", "I'm here"),
            m(RescueRoom.JOIN, "meera", "Meera", "I'm coming"),
            m(RescueRoom.LEAVE, "meera", "Meera"),
        )
        val r = RescueRoom.build(rows.shuffled(), "me", decode).single()
        assertEquals("Ravi", r.victimName)
        assertEquals("Twisted ankle", r.note)
        assertEquals(listOf("asha", "me", "meera"), r.helpers.map { it.nodeId })
        assertEquals(listOf(HelperStatus.ARRIVED, HelperStatus.COMING, HelperStatus.LEFT), r.helpers.map { it.status })
        assertEquals(30.0850, r.helpers[0].pos!!.lat, 1e-9) // latest position wins
        assertEquals(2, r.coming.size)
        assertEquals(HelperStatus.COMING, r.myStatus)
        assertTrue(r.iAmIn)
        assertFalse(r.mine)
        assertEquals(6, r.chat.size) // 3 joins, text, arrived, left. POS updates don't clutter the chat; OPEN isn't a chat line
        assertEquals(r.chat.sortedBy { it.at }, r.chat)
    }

    @Test fun victimSeesTheirOwnRoomAndLatestPosition() {
        val rows = listOf(open, m(RescueRoom.POS, "ravi", "Ravi", pos = "30.0840,78.2660"))
        val r = RescueRoom.build(rows, "ravi", decode).single()
        assertTrue(r.mine && r.iAmIn)
        assertNull(r.myStatus)
        assertEquals(30.0840, r.victimPos!!.lat, 1e-9)
    }

    @Test fun messagesWaitUntilTheSosArrives() {
        val early = listOf(m(RescueRoom.JOIN, "asha", "Asha", "I'm coming", room = "sos2"))
        assertTrue(RescueRoom.build(early, "me", decode).isEmpty())
        val later = early + m(RescueRoom.OPEN, "tara", "Tara", room = "sos2")
        assertEquals(1, RescueRoom.build(later, "me", decode).single().helpers.size)
    }

    @Test fun endedRoomIsEndedAndOnlookerIsNotIn() {
        val r = RescueRoom.build(listOf(open, m(RescueRoom.ENDED, "ravi", "Ravi", "Ravi is safe now")), "me", decode).single()
        assertTrue(r.ended)
        assertFalse(r.iAmIn)
    }

    @Test fun walkingTimeIsRoundedUpToAMinute() {
        assertEquals(1, RescueRoom.walkMinutes(20.0))
        assertEquals(6, RescueRoom.walkMinutes(420.0))
    }
}

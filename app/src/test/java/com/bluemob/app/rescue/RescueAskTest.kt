package com.bluemob.app.rescue

import org.junit.Assert.assertEquals
import org.junit.Test

class RescueAskTest {
    private val h = 3_600_000L
    private val now = 100 * 24 * h
    private fun room(mine: Boolean, ago: Long, ended: Boolean = false) =
        RescueRoom("r", "v", "Ravi", "", null, now - ago, null, ended, emptyList(), emptyList(), mine, null, now - ago)

    @Test fun ownSosAsksAfterTwelveHours() {
        assertEquals(RescueAsk.Ask.NONE, RescueAsk.ask(room(true, 11 * h), now, null))
        assertEquals(RescueAsk.Ask.STILL_NEED_HELP, RescueAsk.ask(room(true, 21 * 24 * h), now, null))
    }

    @Test fun helpersAskAfterADay() {
        assertEquals(RescueAsk.Ask.NONE, RescueAsk.ask(room(false, 20 * h), now, null))
        assertEquals(RescueAsk.Ask.IS_IT_OVER, RescueAsk.ask(room(false, 25 * h), now, null))
    }

    @Test fun snoozeAndEndedStayQuiet() {
        assertEquals(RescueAsk.Ask.NONE, RescueAsk.ask(room(true, 30 * h), now, now + h))
        assertEquals(RescueAsk.Ask.STILL_NEED_HELP, RescueAsk.ask(room(true, 30 * h), now, now - h))
        assertEquals(RescueAsk.Ask.NONE, RescueAsk.ask(room(false, 30 * h, ended = true), now, null))
    }

    @Test fun openForReadsWell() {
        assertEquals("21 days", RescueAsk.openFor(room(true, 21 * 24 * h), now))
        assertEquals("14 hours", RescueAsk.openFor(room(true, 14 * h), now))
    }
}

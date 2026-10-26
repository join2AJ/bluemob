package com.bluemob.app.sos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressCounterTest {
    @Test fun fiveQuickPressesTrigger() {
        val c = PressCounter()
        listOf(0L, 500, 1_000, 1_500).forEach { assertFalse(c.press(it)) }
        assertTrue(c.press(2_000))
        // Counting starts again afterwards.
        assertFalse(c.press(2_300))
    }

    @Test fun slowPressesDont() {
        val c = PressCounter()
        listOf(0L, 1_500, 3_000, 4_500, 6_000, 7_500).forEach { assertFalse(c.press(it)) }
    }

    @Test fun anOldPressFallsOutOfTheWindow() {
        val c = PressCounter()
        assertFalse(c.press(0)); listOf(5_000L, 5_400, 5_800, 6_200).forEach { assertFalse(c.press(it)) }
        assertTrue(c.press(6_600))
    }
}

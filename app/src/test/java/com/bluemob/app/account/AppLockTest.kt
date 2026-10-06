package com.bluemob.app.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class PinPolicyTest {
    @Test fun simplePinsAreRefused() {
        listOf("000000", "111111", "123456", "654321", "234567").forEach { assertTrue(it, PinPolicy.tooSimple(it)) }
        listOf("482913", "112358", "907153").forEach { assertFalse(it, PinPolicy.tooSimple(it)) }
    }

    @Test fun waitsGrowAfterFiveWrongTriesAndAreCapped() {
        assertEquals(0, PinPolicy.waitMs(4))
        assertEquals(30_000, PinPolicy.waitMs(5))
        assertEquals(60_000, PinPolicy.waitMs(6))
        assertEquals(15 * 60_000L, PinPolicy.waitMs(30))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockTest {
    private var now = 1_000_000L
    private fun lock() = AppLock(RuntimeEnvironment.getApplication().getSharedPreferences("lock-test-" + now, 0)) { now }

    @Test fun pinUnlocksAndWrongPinsLeadToAWait() {
        val l = lock()
        assertFalse(l.hasPin)
        l.setPin("482913")
        assertTrue(l.hasPin)
        l.lockNow()
        assertTrue(l.locked.value)
        repeat(4) { assertTrue(l.check("000000") is PinResult.Wrong) }
        val fifth = l.check("000000")
        assertTrue(fifth is PinResult.Wait)
        // Even the right PIN waits until the pause is over.
        assertTrue(l.check("482913") is PinResult.Wait)
        now += 31_000
        assertEquals(PinResult.Ok, l.check("482913"))
        assertFalse(l.locked.value)
    }

    @Test fun locksAgainOnlyAfterTheChosenTimeAway() {
        val l = lock()
        l.setPin("482913")
        l.setLockAfter(60_000)
        l.onBackground(); now += 30_000; l.onForeground()
        assertFalse(l.locked.value)
        l.onBackground(); now += 61_000; l.onForeground()
        assertTrue(l.locked.value)
    }

    @Test fun noPinMeansNoLock() {
        val l = lock()
        l.onBackground(); now += 10 * 60_000; l.onForeground()
        assertFalse(l.locked.value)
        l.lockNow()
        assertFalse(l.locked.value)
    }

    @Test fun biometricOnlyUnlocksWhenTurnedOn() {
        val l = lock()
        l.setPin("482913"); l.lockNow()
        l.unlockedByBiometric()
        assertTrue(l.locked.value)
        l.setBiometric(true)
        l.unlockedByBiometric()
        assertFalse(l.locked.value)
    }
}

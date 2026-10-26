package com.bluemob.app.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallLogicTest {
    private val g = 9.81f

    /** Feeds [m] every 20 ms from [from] for [ms]; true if any reading confirmed a fall. */
    private fun FallLogic.feed(from: Long, ms: Long, m: (Long) -> Float): Boolean =
        (from until from + ms step 20).fold(false) { hit, t -> sample(t, m(t)) || hit }

    @Test fun fallImpactThenStillIsAFall() {
        val f = FallLogic()
        assertFalse(f.feed(0, 1_000) { g })              // walking about
        assertFalse(f.feed(1_000, 300) { 1f })           // free fall
        assertFalse(f.feed(1_300, 60) { 35f })           // impact
        assertTrue(f.feed(1_360, 12_000) { g + 0.3f })   // lying still
    }

    @Test fun gettingUpAfterwardsIsNot() {
        val f = FallLogic()
        f.feed(0, 300) { 1f }; f.feed(300, 60) { 35f }
        assertFalse(f.feed(360, 12_000) { t -> if (t in 4_000..6_000) 15f else g })
    }

    @Test fun aJoltWithoutFreeFallIsNot() {
        val f = FallLogic()
        assertFalse(f.feed(0, 100) { 40f })
        assertFalse(f.feed(100, 12_000) { g })
    }

    @Test fun freeFallWithNoImpactIsNot() {
        val f = FallLogic()
        f.feed(0, 300) { 1f }
        assertFalse(f.feed(300, 12_000) { g })
    }
}

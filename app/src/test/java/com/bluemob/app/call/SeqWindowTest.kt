package com.bluemob.app.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeqWindowTest {
    private fun accept(last: Int, seq: Int) = VoiceLink.SeqWindow.accept(last, seq)

    @Test fun firstFrameOfACallIsAlwaysPlayed() {
        // The 0.7 bug: a new call starts again at 0, but the old call's last number was kept, so every frame
        // looked "late" and the voice never came through. Each call now starts with no last number.
        assertTrue(accept(-1, 0))
        assertTrue(accept(-1, 1234))
    }

    @Test fun inOrderAndSmallGapsPlay() {
        assertTrue(accept(10, 11))
        assertTrue(accept(10, 15))
    }

    @Test fun lateAndRepeatedFramesAreSkipped() {
        assertFalse(accept(10, 10))
        assertFalse(accept(10, 9))
        assertFalse(accept(2000, 0))
    }

    @Test fun wrapsAround() {
        assertTrue(accept(65535, 0))
        assertTrue(accept(65530, 3))
        assertFalse(accept(3, 65530))
    }
}

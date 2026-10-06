package com.bluemob.app.util

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CrashLogTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before fun clean() = CrashLog.clear(context)

    @Test fun readsTheVersionFromAReport() {
        assertEquals("0.11.1", CrashLog.reportVersion("BlueMob 0.11.1 · Android 16 (API 36) · motorola edge 50 pro · arm64-v8a\n2026-10-06"))
        assertNull(CrashLog.reportVersion("something else"))
    }

    @Test fun anUpdateForgetsTheOldVersionsCrash() {
        File(context.noBackupFilesDir, "last_crash.txt").writeText("BlueMob 0.11.1 · Android 16\nWhile: showing the first screen\n\nSecurityException")
        File(context.noBackupFilesDir, "starting.txt").writeText("showing the first screen") // written by 0.11.1, no version line
        assertTrue(CrashLog.forgetOtherVersions(context, current = "0.11.3"))
        assertNull(CrashLog.read(context))
        assertNull(CrashLog.unfinishedStep(context))
    }

    @Test fun keepsThisVersionsCrash() {
        CrashLog.save(context, RuntimeException("boom"), "test")
        CrashLog.step(context, "starting the mesh")
        val current = CrashLog.reportVersion(CrashLog.read(context)!!)
        assertFalse(CrashLog.forgetOtherVersions(context, current))
        assertNotNull(CrashLog.read(context))
        assertEquals("starting the mesh", CrashLog.unfinishedStep(context))
    }
}

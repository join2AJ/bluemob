package com.bluemob.app.startup

import android.os.Looper
import com.bluemob.app.BlueMobApp
import com.bluemob.app.MainActivity
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Starts the real app on a simulated phone (Robolectric): Application.onCreate, MainActivity and the first screen.
 * Everything runs except SQLCipher's native library and the hardware Keystore, which a simulator can't load
 * (the database opens unencrypted here, and keys use the app-private fallback).
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34])
class AppStartupTest {
    @Test fun appStartsAndShowsItsFirstScreen() {
        val app = RuntimeEnvironment.getApplication() as BlueMobApp
        app.startupError?.let { throw AssertionError("BlueMob failed to start", it) }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(app.startupError)
        activity.finish()
    }
}

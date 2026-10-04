package com.bluemob.app.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.resources.NightMode
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.chat.ChatMessage
import com.bluemob.app.chat.Conversation
import com.bluemob.app.chat.MessageStatus
import com.bluemob.app.mesh.LinkQuality
import com.bluemob.app.mesh.LogLine
import com.bluemob.app.ui.chat.ChatScreen
import com.bluemob.app.ui.chat.ChatsScreen
import com.bluemob.app.ui.dashboard.DashboardScreen
import com.bluemob.app.ui.dashboard.DashboardState
import com.bluemob.app.ui.onboarding.OnboardingScreen
import com.bluemob.app.ui.profile.ProfileScreen
import com.bluemob.app.ui.theme.BlueMobTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test

/**
 * Renders every main screen to a PNG on the JVM (no phone needed), so the design can be
 * reviewed in pull requests. Run `./gradlew recordPaparazziDebug`; images land in
 * app/src/test/snapshots/images.
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private val now = System.currentTimeMillis()

    private val people = listOf(
        Person("a1", "Asha", "🦋", Presence.ONLINE, now, "e1", LinkQuality.HIGH, 42.0, 60.0, 5_000),
        Person("b2", "Ravi", "🐬", Presence.ONLINE, now, "e2", LinkQuality.LOW, 380.0, 200.0, 5_000),
        Person("c3", "Meera", "🌵", Presence.IN_RANGE, now, "e3", null, null, null, null),
        Person("d4", "Kabir", "🐺", Presence.OFFLINE, now - 2 * 3_600_000, null, null, 1_800.0, 300.0, 7_200_000),
    )

    private val dashboard = DashboardState(
        name = "Arjun", avatar = "🦅", nodeId = "me", running = true, people = people,
        permissionsGranted = true, locationServicesOff = false, sharingLocation = true, hasMyFix = true, online = false,
    )

    private val skyChat = Conversation(
        SkyBot.NODE_ID,
        listOf(
            ChatMessage("1", false, SkyBot.greeting[0], now - 60_000),
            ChatMessage("2", true, "Hi 👋", now - 50_000, MessageStatus.DELIVERED),
            ChatMessage("3", false, "Hi! 😊 Great to hear from you. This is exactly how it feels when a friend nearby messages you, no towers needed.", now - 40_000),
            ChatMessage("4", true, "Tell me a joke", now - 20_000, MessageStatus.DELIVERED),
        ),
        typing = true,
    )

    private fun shot(dark: Boolean = false, tall: Boolean = false, content: @Composable () -> Unit) {
        var config = DeviceConfig.PIXEL_6
        if (dark) config = config.copy(nightMode = NightMode.NIGHT)
        if (tall) config = config.copy(screenHeight = config.screenHeight * 2)
        if (dark || tall) paparazzi.unsafeUpdateConfig(config)
        paparazzi.snapshot {
            BlueMobTheme { Surface(color = MaterialTheme.colorScheme.background, content = content) }
        }
    }

    @Test fun onboarding() = shot { OnboardingScreen("Arjun", "🦅") { _, _ -> } }

    @Test fun dashboard() = shot(tall = true) { DashboardScreen(dashboard, PaddingValues(), {}, {}, {}, {}, {}, {}) }

    @Test fun dashboardDark() = shot(dark = true) { DashboardScreen(dashboard, PaddingValues(), {}, {}, {}, {}, {}, {}) }

    @Test fun dashboardEmpty() = shot {
        DashboardScreen(
            dashboard.copy(people = emptyList(), running = false, sharingLocation = false, hasMyFix = false),
            PaddingValues(), {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun chats() = shot { ChatsScreen(people, mapOf(SkyBot.NODE_ID to skyChat.copy(unread = 1)), PaddingValues()) {} }

    @Test fun skyChat() = shot {
        ChatScreen(SkyBot.NODE_ID, null, skyChat, MutableSharedFlow(), {}, {}, { true })
    }

    @Test fun profile() = shot {
        ProfileScreen(
            "Arjun", "🦅", "3f9a1c2b7d4e8a01", running = true, sharingLocation = false,
            log = listOf(LogLine(now, "Connected to Asha")), contentPadding = PaddingValues(),
            onName = {}, onAvatar = {}, onToggleMesh = {}, onToggleLocation = {}, onReplayIntro = {}, onForgetPeople = {},
        )
    }
}

package com.bluemob.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.chat.MessageRepository
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.PathState
import com.bluemob.app.guide.GuideContent
import com.bluemob.app.mesh.LinkQuality
import com.bluemob.app.mesh.LogLine
import com.bluemob.app.mesh.SosSignal
import com.bluemob.app.settings.SignalMode
import com.bluemob.app.settings.Spot
import com.bluemob.app.ui.chat.ChatScreen
import com.bluemob.app.ui.chat.ChatsScreen
import com.bluemob.app.ui.chat.MessageInfoScreen
import com.bluemob.app.ui.compass.CompassScreen
import com.bluemob.app.ui.dashboard.NearbyScreen
import com.bluemob.app.ui.dashboard.NearbyState
import com.bluemob.app.ui.guide.ArticleScreen
import com.bluemob.app.ui.guide.GuideScreen
import com.bluemob.app.ui.onboarding.OnboardingScreen
import com.bluemob.app.ui.profile.ProfileScreen
import com.bluemob.app.ui.sos.SosAlert
import com.bluemob.app.ui.sos.SosHubScreen
import com.bluemob.app.ui.theme.BlueMobTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

/**
 * Renders the main screens to PNGs on the JVM (no phone needed), so the design can be reviewed.
 * Record with `./gradlew recordPaparazziDebug`; images land in app/src/test/snapshots/images.
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private val now = System.currentTimeMillis()
    private val me = GeoPoint(30.0869, 78.2676, 5f, now)
    private val pad = PaddingValues(top = 52.dp)

    private val people = listOf(
        Person("a1c2", "Asha", "🦋", Presence.ONLINE, now, LinkQuality.HIGH, 42.0, 60.0, GeoPoint(30.0871, 78.2680, 5f, now), true, false),
        Person("b7e4", "Ravi", "🐬", Presence.ONLINE, now, LinkQuality.LOW, 380.0, 200.0, GeoPoint(30.0837, 78.2663, 5f, now), false, true),
        Person("c3d9", "Meera", "🌵", Presence.IN_RANGE, now, null, null, null, null, false, false),
        Person("e4b0", "Asha", "🌙", Presence.OFFLINE, now - 2 * 3_600_000, null, null, null, null, true, false),
    )

    private fun msg(id: String, mine: Boolean, text: String, ago: Long, status: MessageStatus, peer: String = "a1c2") = MessageEntity(
        id = id, peer = peer, fromMe = mine, text = text, createdAt = now - ago, status = status,
        directState = if (status == MessageStatus.PENDING) PathState.WAITING else PathState.DELIVERED, internetState = PathState.UNAVAILABLE,
        attempts = if (mine) 1 else 0, deliveredAt = if (status == MessageStatus.DELIVERED || status == MessageStatus.READ) now - ago + 900 else null,
        deliveredVia = "Wi-Fi", readAt = if (status == MessageStatus.READ) now - ago + 4000 else null,
        history = MessageRepository.event(now - ago, "Written on your phone") + MessageRepository.event(now - ago, "Not in range. Waiting on your phone until they are") +
            MessageRepository.event(now - ago + 600, "Sent over Wi-Fi") + MessageRepository.event(now - ago + 900, "Delivered over Wi-Fi. Delivery receipt came back") +
            MessageRepository.event(now - ago + 4000, "Read receipt came back over Wi-Fi"),
    )

    private val chat = listOf(
        msg("1", false, "Saw you pop up on my radar 👋", 600_000, MessageStatus.READ),
        msg("2", false, "Meet at the stream?", 590_000, MessageStatus.READ),
        msg("3", true, "On my way!", 500_000, MessageStatus.READ),
        msg("4", true, "Bringing the water filter", 490_000, MessageStatus.DELIVERED),
        msg("5", true, "Did you see Meera?", 60_000, MessageStatus.PENDING),
    )

    private val sky = listOf(
        MessageEntity("s1", SkyBot.NODE_ID, false, SkyBot.greeting[0], now - 70_000, MessageStatus.READ),
        MessageEntity("s2", SkyBot.NODE_ID, true, "How do I make water safe?", now - 60_000, MessageStatus.LOCAL),
        MessageEntity("s3", SkyBot.NODE_ID, false, "From your survival guide: Making water safe\nBoiling is the most reliable method.\n\n1. If water is cloudy, let it settle, then pour it through a cloth.\n2. Bring it to a rolling boil for 1 minute (3 minutes above 2,000 m).",
            now - 50_000, MessageStatus.READ, actions = "Open full guide|guide:purify"),
    )

    private fun shot(dark: Boolean = false, tall: Boolean = false, content: @Composable () -> Unit) {
        var config = DeviceConfig.PIXEL_6
        if (dark) config = config.copy(nightMode = NightMode.NIGHT)
        if (tall) config = config.copy(screenHeight = config.screenHeight * 2)
        if (dark || tall) paparazzi.unsafeUpdateConfig(config)
        paparazzi.snapshot {
            BlueMobTheme(dark = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    private val nearby = NearbyState("Arjun", true, people, true, false, true, true, false)

    @Test fun onboarding() = shot { OnboardingScreen("Arjun", "🦅") { _, _ -> } }
    @Test fun nearby() = shot(tall = true) { NearbyScreen(nearby, pad, {}, {}, {}, {}, {}, {}, {}, {}) }
    @Test fun nearbyDark() = shot(dark = true) { NearbyScreen(nearby, pad, {}, {}, {}, {}, {}, {}, {}, {}) }
    @Test fun chats() = shot { ChatsScreen(people, mapOf("a1c2" to chat, SkyBot.NODE_ID to sky), emptySet(), pad) {} }
    @Test fun chat() = shot {
        ChatScreen("a1c2", people[0], chat, false, MutableSharedFlow(), "Arjun", "3f9a1c2b7d4e8a01", {}, {}, { true }, {}, {}, {})
    }
    @Test fun skyChat() = shot {
        ChatScreen(SkyBot.NODE_ID, null, sky, false, MutableSharedFlow(), "Arjun", "3f9a1c2b7d4e8a01", {}, {}, { true }, {}, {}, {})
    }
    @Test fun messageInfo() = shot(tall = true) { MessageInfoScreen(chat[2], "Arjun", "3f9a1c2b7d4e8a01", "Asha") {} }
    @Test fun compass() = shot {
        CompassScreen(people, listOf(Spot("s", "Base camp", 30.0830, 78.2640, now)), me, flowOf(20f), true, true, "s", pad, {}, {}, {}, {}, {})
    }
    @Test fun guide() = shot { GuideScreen(setOf("burns"), pad, {}, {}) }
    @Test fun article() = shot { ArticleScreen(GuideContent.byId("burns")!!, true, {}, {}, {}) }
    @Test fun sosHub() = shot { SosHubScreen(null, 2, SignalMode.SCREEN, {}, { 2 }, {}, {}, {}, {}) }
    @Test fun sosAlert() = shot {
        SosAlert(SosSignal("x", "b7e4", "Ravi", "Twisted my ankle near the stream. Can't walk", 30.0837, 78.2663, 21, now, 1), people[1], me.lat, me.lon, {}, {}, {}, {})
    }
    @Test fun profile() = shot(tall = true) {
        ProfileScreen("Arjun", "🦅", "3f9a1c2b7d4e8a01", true, false, false, SignalMode.ALL, listOf(LogLine(now, "Connected to Asha")), pad,
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

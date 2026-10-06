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
import com.bluemob.app.ui.chat.NewChatScreen
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
import com.bluemob.app.audit.AuditChain
import com.bluemob.app.data.AuditEntry
import com.bluemob.app.data.TrailPoint
import com.bluemob.app.settings.SosContact
import com.bluemob.app.system.RadioState
import com.bluemob.app.trail.PositionEstimate
import com.bluemob.app.ui.compass.TrailUi
import com.bluemob.app.data.RescueMessage
import com.bluemob.app.rescue.Helper
import com.bluemob.app.rescue.HelperStatus
import com.bluemob.app.rescue.RescueRoom
import com.bluemob.app.ui.rescue.RescueActions
import com.bluemob.app.ui.rescue.RescueScreen
import com.bluemob.app.ui.games.GamesScreen
import com.bluemob.app.ui.games.TicTacToeScreen
import com.bluemob.app.ui.profile.AuditScreen
import com.bluemob.app.ui.profile.PersonScreen
import com.bluemob.app.ui.sos.SosContactsScreen
import com.bluemob.app.ui.sos.SosHubState
import com.bluemob.app.ui.system.ConnectionsScreen
import com.bluemob.app.util.Geo
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

    @Test fun onboarding() = shot { OnboardingScreen("Arjun", "🦅", onFinish = { _, _ -> }) }
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
    private val sosMsg: (String) -> String = { note ->
        "SOS from Arjun (BlueMob #3F9A). I need help." + (if (note.isNotBlank()) " $note." else "") + " " + noGps.describe(now) + " Battery 48%. Sent with BlueMob."
    }
    private val contacts = listOf(SosContact("1", "Papa", "+91 98100 12345"), SosContact("2", "Didi", "+91 98200 67890"))
    private val noGps = run {
        val (lat, lon) = Geo.offset(me.lat, me.lon, 215.0, 330.0)
        PositionEstimate(lat, lon, false, me.lat, me.lon, now - 18 * 60_000, 8f, 340.0, 215.0, 230f, 61.0, now)
    }
    @Composable private fun hub(state: SosHubState) = SosHubScreen(state, sosMsg, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, still = true)

    @Test fun sosHub() = shot(tall = true) { hub(SosHubState(null, 2, SignalMode.SCREEN, contacts)) }
    @Test fun sosHubDark() = shot(dark = true) { hub(SosHubState(null, 2, SignalMode.SCREEN, contacts)) }
    @Test fun sosActive() = shot(tall = true) {
        hub(SosHubState(SosSignal("s", "me", "Arjun", "Twisted ankle", noGps.lat, noGps.lon, 48, now - 60_000, 0, pos = noGps), 2, SignalMode.ALL, contacts, reached = 2))
    }
    @Test fun sosContacts() = shot { SosContactsScreen(contacts, {}, { _, _ -> }, {}) }
    @Test fun sosAlert() = shot(tall = true) {
        SosAlert(SosSignal("x", "b7e4", "Ravi", "Twisted my ankle near the stream. Can't walk", noGps.lat, noGps.lon, 21, now, 2, pos = noGps), people[1], me.lat, me.lon, {}, {}, {}, {})
    }
    @Test fun connections() = shot(tall = true) {
        ConnectionsScreen(RadioState(bluetooth = true, wifi = false, location = true, airplane = true), online = false, {}, { _, _ -> })
    }
    @Test fun nearbyBanner() = shot(tall = true) {
        val lostAsha = people.mapIndexed { i, p -> if (i == 0) p.copy(lost = noGps) else p }
        NearbyScreen(nearby.copy(people = lostAsha, radios = RadioState(bluetooth = false, wifi = true, location = true, airplane = false)), pad, {}, {}, {}, {}, {}, {}, {}, {})
    }
    private val trailPts = run {
        val pts = mutableListOf<TrailPoint>()
        var lat = 30.0830; var lon = 78.2640; var bearing = 30.0
        repeat(40) { i ->
            val est = i >= 28
            pts += TrailPoint(i.toLong(), now - (40 - i) * 60_000L, lat, lon, if (est) 30f else 6f, est)
            val next = Geo.offset(lat, lon, bearing, 25.0); lat = next.first; lon = next.second
            bearing += if (i > 14) 7.0 else 1.0
        }
        pts
    }
    @Test fun compassTrail() = shot(tall = true) {
        val last = trailPts.last()
        val est = PositionEstimate(last.lat, last.lon, false, trailPts[27].lat, trailPts[27].lon, now - 13 * 60_000, 6f, 310.0, 140.0, 150f, 56.0, now)
        CompassScreen(people, listOf(Spot(Spot.BASE_CAMP_ID, "Base camp", 30.0830, 78.2640, now)), GeoPoint(last.lat, last.lon, 56f, now), flowOf(150f), true, true,
            Spot.BASE_CAMP_ID, pad, {}, {}, {}, {}, {}, trail = TrailUi(true, trailPts, est, lost = true))
    }
    @Test fun compassTrailOff() = shot(tall = true) {
        CompassScreen(people, emptyList(), me, flowOf(20f), true, true, null, pad, {}, {}, {}, {}, {})
    }
    @Test fun audit() = shot(tall = true) {
        var prev: AuditEntry? = null
        val entries = listOf(
            "APP" to "BlueMob started · ID BM 3F9A 1C2B 7D4E 8A01", "MESH" to "Connected to Asha over Wi-Fi",
            "MESSAGE" to "Message m-1f2e written to Asha: \"On my way!\"", "RECEIPT" to "Delivery receipt for m-1f2e from Asha over Wi-Fi",
            "POSITION" to "Base camp set at 30.0830 N, 78.2640 E", "SOS" to "SOS sent to 2 phones nearby: \"Twisted ankle\". " + noGps.describe(now),
        ).mapIndexed { i, (k, t) -> AuditChain.next(prev, now - (6 - i) * 60_000L, k, t).also { prev = it } }
        AuditScreen(entries.asReversed(), com.bluemob.app.audit.AuditVerification(entries.size), {},
            listOf(com.bluemob.app.audit.Witness("a1c2", "Asha", 5, true, now - 600_000), com.bluemob.app.audit.Witness("b7e4", "Ravi", 4, true, now - 3_600_000)),
            "MFkw…", SecurityStatus(true, emptyList()))
    }
    private fun rescueRoom(mine: Boolean): RescueRoom {
        var t = now - 9 * 60_000L
        val victim = if (mine) "me" else "b7e4"
        val vName = if (mine) "Arjun" else "Ravi"
        fun row(kind: String, from: String, name: String, text: String = "") = RescueMessage("r${t}", "sos1", from, name, kind, text, t.also { t += 60_000 })
        val rows = listOf(
            row(RescueRoom.JOIN, "a1c2", "Asha", "I'm coming · 420 m away, about 6 min"),
            row(RescueRoom.JOIN, if (mine) "c3d9" else "me", if (mine) "Meera" else "Arjun", "I'm coming · 380 m away, about 5 min"),
            row(RescueRoom.TEXT, victim, vName, "Thank you! I'm under the big pine by the stream"),
            row(RescueRoom.TEXT, "a1c2", "Asha", "Can you hear my whistle?"),
            row(RescueRoom.TEXT, victim, vName, "I can hear you!"),
        )
        val ashaPos = PositionEstimate(30.0858, 78.2650, true, 30.0858, 78.2650, now - 60_000, 6f, null, null, null, 6.0, now - 60_000)
        val otherPos = PositionEstimate(30.0870, 78.2676, true, 30.0870, 78.2676, now - 30_000, 6f, null, null, null, 6.0, now - 30_000)
        val helpers = listOf(
            Helper("a1c2", "Asha", HelperStatus.COMING, ashaPos, now - 60_000, rows[0].at),
            Helper(if (mine) "c3d9" else "me", if (mine) "Meera" else "Arjun", HelperStatus.COMING, otherPos, now - 30_000, rows[1].at),
        )
        return RescueRoom("sos1", victim, vName, "Twisted my ankle near the stream. Can't walk", 21, now - 10 * 60_000, noGps, false, helpers, rows, mine,
            if (mine) null else HelperStatus.COMING)
    }
    @Test fun rescueHelper() = shot(tall = true) { RescueScreen(rescueRoom(false), "me", me, flowOf(200f), RescueActions()) }
    @Test fun rescueVictim() = shot(tall = true) { RescueScreen(rescueRoom(true), "me", me, flowOf(0f), RescueActions()) }
    @Test fun newChat() = shot(tall = true) {
        NewChatScreen("3f9a1c2b7d4e8a01", people + people[0].copy(nodeId = "9c1f00aa77b2e410", name = "Kabir", avatar = "🐺", presence = Presence.OFFLINE, lastSeen = 0, location = null),
            {}, { _, _ -> }, {}, {})
    }
    private val raviScore = com.bluemob.app.trust.Trust.score("b7e4", listOf(
        com.bluemob.app.trust.Rating("a1c2", "Asha", "b7e4", com.bluemob.app.trust.RatingKind.THANKS, "s0", "Brought water and stayed with me till dawn", now - 3 * 86_400_000L),
        com.bluemob.app.trust.Rating("c3d9", "Meera", "b7e4", com.bluemob.app.trust.RatingKind.GENUINE_SOS, "s1", "Really twisted his ankle, we carried him down", now - 86_400_000L),
        com.bluemob.app.trust.Rating("e4b0", "Asha", "b7e4", com.bluemob.app.trust.RatingKind.FAKE_SOS, "s2", "Nobody was there, they laughed about it", now - 3_600_000L),
    ), now)
    @Test fun person() = shot(tall = true) {
        PersonScreen("b7e4c3d900aa1122", "Ravi", "🐬", raviScore, false, emptyList(), "sos1", {}, {}, { _, _, _ -> })
    }
    @Test fun sosAlertWarning() = shot(tall = true) {
        SosAlert(SosSignal("x", "b7e4", "Ravi", "Snake bite near the stream", noGps.lat, noGps.lon, 21, now, 1, pos = noGps), people[1], me.lat, me.lon, {}, {}, {}, {},
            trust = raviScore)
    }
    @Test fun rescueEnded() = shot(tall = true) {
        RescueScreen(rescueRoom(false).copy(ended = true), "me", me, flowOf(200f), RescueActions(), rated = emptySet())
    }
    @Test fun bridge() = shot {
        com.bluemob.app.ui.system.BridgeScreen(com.bluemob.app.bridge.BridgeStatus(true, true, now - 40_000, null, 12, 9, 0), "https://relay.bluemob.example", {}, {})
    }
    @Test fun crashScreen() = shot(tall = true) {
        CrashScreen(true, "BlueMob 0.6.1 · Android 14 (API 34) · samsung SM-A546E · arm64-v8a\nWhile: opening the encrypted database (SQLCipher)\n\n" +
            "java.lang.IllegalStateException: example error\n\tat com.bluemob.app.data.BlueMobDatabase.create(Database.kt:212)", {}, {}, {}, {})
    }
    @Test fun games() = shot(tall = true) {
        GamesScreen({}, {}, people = people.take(2), matches = listOf(
            com.bluemob.app.games.Match("g-1", "c4", "b7e4", "Ravi", iInvited = false, state = com.bluemob.app.games.MatchState.INVITED),
            com.bluemob.app.games.Match("g-2", "ttt", "a1c2", "Asha", iInvited = true, state = com.bluemob.app.games.MatchState.PLAYING, myScore = 2, theirScore = 1),
        ))
    }
    @Test fun matchConnectFour() = shot {
        var b = com.bluemob.app.games.ConnectFour.empty()
        for ((who, col) in listOf(1 to 3, 2 to 3, 1 to 2, 2 to 4, 1 to 4)) b = com.bluemob.app.games.ConnectFour.drop(b, col, who)!!
        com.bluemob.app.ui.games.MatchScreen(com.bluemob.app.games.Match("g-1", "c4", "b7e4", "Ravi", iInvited = true,
            state = com.bluemob.app.games.MatchState.PLAYING, board = b, myScore = 1), {}, {}, {}, {})
    }
    @Test fun lockScreen() = shot(tall = true) {
        com.bluemob.app.ui.account.LockScreen("Arjun", "🦅", "5742 99A8", true, { com.bluemob.app.account.PinResult.Wrong(3) }, {}, { 2 }, false)
    }
    @Test fun signUpPin() = shot {
        com.bluemob.app.ui.account.AccountSetup(false, true, false, { "" }, {}, {}, {})
    }
    @Test fun recoveryCode() = shot(tall = true) {
        com.bluemob.app.ui.account.AccountSetup(true, false, true, { "7KQ2-M9XA-T4PB-0RCE-W3HD-NF6Y-JS8G" }, {}, {}, {})
    }
    @Test fun accountSettings() = shot(tall = true) {
        com.bluemob.app.ui.account.AccountScreen("5742 99A8", true, true, 60_000, false, {}, {}, {}, {}, { com.bluemob.app.account.PinResult.Ok }, {}, { "" }, {})
    }
    @Test fun restore() = shot { com.bluemob.app.ui.account.RestoreScreen({}) { null } }
    @Test fun incomingCall() = shot {
        com.bluemob.app.ui.call.CallScreen(com.bluemob.app.call.Call("c-1", "a1c2", "Asha", true, com.bluemob.app.call.CallPhase.INCOMING),
            null, null, false, {}, {}, {}, {}, {}, { false }, {})
    }
    @Test fun activeCall() = shot {
        com.bluemob.app.ui.call.CallScreen(com.bluemob.app.call.Call("c-1", "a1c2", "Asha", false, com.bluemob.app.call.CallPhase.ACTIVE, startedAt = now - 83_000, muted = true),
            null, null, false, {}, {}, {}, {}, {}, { false }, {})
    }
    @Test fun compassLostPerson() = shot(tall = true) {
        val pos = com.bluemob.app.trail.PositionEstimate(30.0837, 78.2663, false, 30.0840, 78.2660, now - 6 * 60_000, 12f, 120.0, 200.0, 190f, 45.0, now - 40_000)
        CompassScreen(listOf(people[1].copy(lost = pos, quality = LinkQuality.LOW)), emptyList(), me, flowOf(20f), true, true, "b7e4", pad, {}, {}, {}, {}, {})
    }
    @Test fun ticTacToe() = shot { TicTacToeScreen {} }
    @Test fun profile() = shot(tall = true) {
        ProfileScreen("Arjun", "🦅", "3f9a1c2b7d4e8a01", true, false, false, SignalMode.ALL, listOf(LogLine(now, "Connected to Asha")), pad,
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

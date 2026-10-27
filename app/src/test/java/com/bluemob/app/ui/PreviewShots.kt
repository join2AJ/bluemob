package com.bluemob.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.bluemob.app.activity.ActivityEvent
import com.bluemob.app.activity.ActivityType
import com.bluemob.app.chat.ChatGroup
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.RescueMessage
import com.bluemob.app.i18n.Text
import com.bluemob.app.mesh.LinkQuality
import com.bluemob.app.rescue.Helper
import com.bluemob.app.rescue.HelperStatus
import com.bluemob.app.rescue.RescueRoom
import com.bluemob.app.ui.chat.ChatsScreen
import com.bluemob.app.ui.chat.ChatsTab
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.dashboard.NearbyScreen
import com.bluemob.app.ui.dashboard.NearbyState
import com.bluemob.app.ui.nav.Tab
import com.bluemob.app.ui.profile.BadgeStats
import com.bluemob.app.ui.profile.Badges
import com.bluemob.app.ui.profile.BadgesScreen
import com.bluemob.app.ui.rescue.RescuesScreen
import com.bluemob.app.ui.theme.BlueMobTheme
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.settings.SignalMode
import org.junit.Rule
import org.junit.Test

/** Pictures of the 0.30 designs (and the proposed simple mode), in dark mode like the user's phone. */
class PreviewShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 0.1)

    private val now = System.currentTimeMillis()
    private val pad = PaddingValues(top = 52.dp)
    private val day = 86_400_000L
    private val people = listOf(
        Person("a1c2", "Asha", "🦋", Presence.ONLINE, now, LinkQuality.HIGH, 42.0, 60.0, GeoPoint(30.0871, 78.2680, 5f, now), false, false),
        Person("b7e4", "Ravi", "🐬", Presence.ONLINE, now, LinkQuality.LOW, 380.0, 200.0, null, false, false),
        Person("c3d9", "Meera", "🌵", Presence.IN_RANGE, now, null, null, null, null, false, false),
        Person("d5f1", "IV2201", "🦅", Presence.OFFLINE, now - 2 * 3_600_000, null, null, null, null, false, false),
    )
    private fun m(id: String, peer: String, mine: Boolean, text: String, ago: Long, st: MessageStatus = MessageStatus.READ) =
        MessageEntity(id, peer, mine, text, now - ago, st)
    private val convos = mapOf(
        "a1c2" to listOf(m("1", "a1c2", false, "Meet at the stream?", 900_000), m("2", "a1c2", true, "On my way!", 600_000)),
        "b7e4" to listOf(m("3", "b7e4", false, "Battery at 20%, heading back", 300_000, MessageStatus.RECEIVED)),
        "d5f1" to listOf(m("4", "d5f1", true, "Reached camp 2 ✅", 7_200_000)),
        "g1" to listOf(m("5", "g1", false, "Tea at 6?", 1_200_000, MessageStatus.RECEIVED).copy(senderName = "Asha")),
    )
    private val groups = listOf(ChatGroup("g1", "Kedarkantha trek", mapOf("a1c2" to "Asha", "b7e4" to "Ravi", "c3d9" to "Meera")),
        ChatGroup("g2", "Family", mapOf("x1" to "Papa", "x2" to "Maa")))

    private fun room(id: String, mine: Boolean, ago: Long, ended: Boolean, came: Int, note: String = "", victim: String = "IV2201"): RescueRoom {
        val start = now - ago
        val helpers = (0 until came).map { Helper("h$it", listOf("Asha", "Ravi", "Meera")[it % 3], HelperStatus.ARRIVED, null, start, start + 60_000) }
        val chat = listOf(RescueMessage("$id-1", id, "a1c2", "Asha", RescueRoom.TEXT, "Coming!", start + 120_000),
            RescueMessage("$id-2", id, "me", "Arjun", RescueRoom.ENDED, "safe", start + 14 * 60_000))
        return RescueRoom(id, if (mine) "me" else "d5f1", if (mine) "Arjun" else victim, note, 60, start, null, ended, helpers,
            if (ended) chat else chat.take(1), mine, if (mine) null else HelperStatus.ARRIVED)
    }
    private val rescues = listOf(
        room("r0", false, 20 * 60_000L, false, 2, "Twisted ankle near the stream", "Ravi"),
        room("r1", true, 2 * day, true, 0), room("r2", true, 3 * day, true, 1, "Lost the trail after the bridge"),
        room("r3", false, 4 * day, true, 1), room("r4", true, 20 * day, true, 2, "Low battery, fog"), room("r5", false, 21 * day, true, 1),
    )

    private fun shot(tall: Boolean = false, content: @Composable () -> Unit) {
        if (tall) paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT, screenHeight = DeviceConfig.PIXEL_6.screenHeight * 2))
        paparazzi.snapshot { BlueMobTheme(dark = true) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    }

    @Test fun tabBarDocked() = shot {
        Tabs(Tab.NEARBY, 2, {}, {}) { p -> NearbyScreen(NearbyState("Arjun", true, people, true, false, true, true, false), p, {}, {}, {}, {}, {}, {}, {}, {}) }
    }
    @Test fun chatsNew() = shot(tall = true) {
        ChatsScreen(people, convos, setOf("b7e4"), pad, rescues, groups = groups, pinned = setOf("a1c2")) {}
    }
    @Test fun groupsTab() = shot(tall = true) {
        ChatsScreen(people, convos, emptySet(), pad, rescues, groups = groups, startTab = ChatsTab.GROUPS) {}
    }
    @Test fun rescuesNew() = shot(tall = true) { RescuesScreen(rescues, {}, {}, mySosId = null) }
    @Test fun activityNew() = shot(tall = true) {
        val types = listOf(ActivityType.MSG_SENT, ActivityType.MSG_RECEIVED, ActivityType.MSG_SENT, ActivityType.CALL_DIALLED, ActivityType.CALL_RECEIVED,
            ActivityType.MSG_RECEIVED, ActivityType.CALL_MISSED, ActivityType.MSG_SENT)
        val ev = (0 until 160).map { i ->
            val t = types[i % types.size]
            ActivityEvent(now - (i % 13) * day / 2 - (i * 37 % 600) * 60_000L, t, if (t.name.startsWith("CALL")) 90L + i else 0, "p${i % 4}",
                via = if (t == ActivityType.MSG_SENT) listOf("nearby", "phones", "internet", "nearby")[i % 4] else null)
        }
        val usage = (0 until 7).associate { d ->
            com.bluemob.app.guide.Streak.dayKey(now - d * day) to mapOf(com.bluemob.app.activity.AppArea.CHATS to 1200L + d * 100, com.bluemob.app.activity.AppArea.NEARBY to 800L,
                com.bluemob.app.activity.AppArea.GUIDE to 400L, com.bluemob.app.activity.AppArea.COMPASS to 300L, com.bluemob.app.activity.AppArea.GAMES to 200L)
        }
        com.bluemob.app.ui.profile.ActivityScreen(ev, {}, now, mapOf("p0" to "Asha", "p1" to "Ravi", "p2" to "Meera"), usage, Triple(12, 4, 5))
    }
    @Test fun badgesGame() = shot(tall = true) {
        BadgesScreen(Badges.of(BadgeStats(guidesRead = 12, quizzes = 4, streak = 5, rescuesJoined = 2, sosContactsAccepted = 2, messagesSent = 180, groups = 2,
            peopleMet = 11, hasBloodGroup = true, tripsRecorded = 3, goodRatings = mapOf("c_helpful" to 6, "c_reliable" to 2), verifiedPeople = 1,
            longestTripKm = 14, readToday = true, messagedToday = true))) {}
    }
    @Test fun simpleModeMockup() = shot { SimpleHome() }

    /** The proposed simple mode: one home screen with the SOS, who's near, recent chats; three tabs. */
    @Composable
    private fun SimpleHome() {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).statusBarsPadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Blue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7DB7EA))
                    Text("Mob", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3FC897))
                    Spacer(Modifier.weight(1f))
                    Text("🌐 हिं", style = MaterialTheme.typography.labelLarge, color = Extra.ink2)
                }
                // Protection status, in one line.
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF12A97A).copy(alpha = 0.18f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", fontSize = 20.sp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("You're protected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("2 SOS contacts · phones nearby on · location on", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                }
                // The big button.
                Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(196.dp).clip(CircleShape).background(Color(0xFFE5484D).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(156.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFF6B6B), Color(0xFFD63A3F)))),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SOS", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text("Hold to ask for help", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    }
                }
                Text("PEOPLE NEAR YOU · 3", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    people.take(3).forEach { p ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(p.avatar, p.name, p.nodeId, 54.dp, p.presence)
                            Text(p.name, style = MaterialTheme.typography.bodySmall)
                            Text(p.distanceM?.let { "${it.toInt()} m" } ?: "near", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("🧭" to "Compass", "📖" to "Survival guide", "💬" to "2 new messages").forEach { (e, l) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Extra.line, RoundedCornerShape(16.dp))
                            .padding(12.dp)) {
                            Text(e, fontSize = 22.sp)
                            Text(l, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            // Three tabs instead of five.
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF1D5F94), Color(0xFF15745A)))).height(66.dp).padding(7.dp)) {
                listOf("🏠" to "Home", "💬" to "Chats", "🙂" to "You").forEachIndexed { i, (e, l) ->
                    Column(Modifier.weight(1f).fillMaxSize().padding(horizontal = 4.dp).clip(RoundedCornerShape(18.dp)).background(if (i == 0) Color.White else Color.Transparent),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(e, fontSize = 20.sp)
                        Text(l, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (i == 0) Color(0xFF15745A) else Color.White)
                    }
                }
            }
        }
    }

    @Test fun chatWallpaper() = shot {
        com.bluemob.app.ui.chat.ChatScreen("a1c2", people[0], convos["a1c2"]!! + listOf(m("6", "a1c2", false, "Reached the stream, waiting for you 🌊", 120_000)),
            false, kotlinx.coroutines.flow.MutableSharedFlow(), "Arjun", "3f9a1c2b7d4e8a01", {}, {}, { true }, {}, {}, {}, verified = true)
    }
    @Test fun callsCompact() = shot(tall = true) {
        val calls = (0 until 9).map { i -> com.bluemob.app.data.CallLogEntry("c$i", listOf("a1c2", "b7e4", "c3d9")[i % 3], listOf("Asha", "Ravi", "Meera")[i % 3],
            i % 4 == 0, i % 2 == 0, listOf("ANSWERED", "MISSED", "ANSWERED")[i % 3], now - i * 5 * 3_600_000L, if (i % 3 == 1) 0 else 120L + i * 30) }
        ChatsScreen(people, convos, emptySet(), pad, rescues, calls = calls, groups = groups, startTab = ChatsTab.CALLS) {}
    }
    @Test fun guideClean() = shot(tall = true) { com.bluemob.app.ui.guide.GuideScreen(setOf("burns"), pad, {}, {}) }
    @Test fun gameRules() = shot { com.bluemob.app.ui.games.RulesDialog(com.bluemob.app.games.Engine.of(com.bluemob.app.games.Match.C4)!!) {} }
    @Test fun gameBoard() = shot(tall = true) { com.bluemob.app.ui.games.ComputerGameScreen(com.bluemob.app.games.Match.C4) {} }
    @Test fun safetySettings() = shot(tall = true) {
        com.bluemob.app.ui.profile.ProfileScreen("Arjun", "🦅", "3f9a1c2b7d4e8a01", true, false, true, SignalMode.ALL, emptyList(), pad,
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, emergencyCard = true, remoteSignal = true, helperKinds = setOf("sound", "flash", "mic"), powerSos = true, fallDetect = true, onFallDetect = {})
    }

    @Test fun emergencyCardScreen() = shot { com.bluemob.app.ui.profile.EmergencyCardScreen(true, {}, { "🆘 In an emergency: Arjun\n🩸 Blood group B+ · age 34\n📞 Papa: +91 98100 12345" }, {}) }
    @Test fun helperSignalsScreen() = shot { com.bluemob.app.ui.profile.HelperSignalsScreen(true, {}, setOf("sound", "flash", "mic"), { _, _ -> }, {}) }

    private val trip = com.bluemob.app.chat.ChatGroup("g-trip", "Kedarnath trek", mapOf("a1c2" to "Asha", "b7e4" to "Ravi", "c3d9" to "Meera", "d5f1" to "Imran"), now, trip = true)
    private val roll = com.bluemob.app.chat.RollCall("m-1", now - 4 * 60_000, mapOf(
        "a1c2" to com.bluemob.app.chat.Reach.Direct(1_800), "b7e4" to com.bluemob.app.chat.Reach.Mesh(2, 14_000),
        "c3d9" to com.bluemob.app.chat.Reach.Internet(6_000), "d5f1" to com.bluemob.app.chat.Reach.Waiting))
    @Test fun nearbyTrip() = shot(tall = true) {
        NearbyScreen(NearbyState("Arjun", true, people, true, false, true, true, false), pad, {}, {}, {}, {}, {}, {}, {}, {}, trips = listOf(trip to roll))
    }
    @Test fun newTrip() = shot(tall = true) { com.bluemob.app.ui.chat.NewTripScreen({}, {}, {}) }
    @Test fun tripGroup() = shot(tall = true) {
        com.bluemob.app.ui.chat.TripGroupScreen(trip, "3f9a1c2b7d4e8a01", roll, people, null, {}, {}, {}, {}, {}, {}, now = now)
    }
    @Test fun fieldTestRunning() = shot(tall = true) {
        com.bluemob.app.ui.system.FieldTestScreen(com.bluemob.app.safety.FieldTest.Session(now - 150 * 60_000, "g-trip", 10),
            com.bluemob.app.safety.FieldTest.Report("Motorola edge 50 · Android 14", 150, 60, 54, 9_000, 190_000,
                mapOf("Direct" to 31, "1 phone" to 14, "2 phones" to 6, "Internet" to 3), 3.4, 92, 84, 1, 12, 6, 3.2),
            listOf(trip), "g-trip", { _, _ -> }, {}, {}, {}, {}, {})
    }
}

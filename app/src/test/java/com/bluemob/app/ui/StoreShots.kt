package com.bluemob.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bluemob.app.i18n.Dictionary
import com.bluemob.app.i18n.Lang
import com.bluemob.app.i18n.Text
import com.bluemob.app.settings.SignalMode
import com.bluemob.app.ui.chat.ChatScreen
import com.bluemob.app.ui.dashboard.NearbyScreen
import com.bluemob.app.ui.guide.ArticleScreen
import com.bluemob.app.ui.profile.BadgeStats
import com.bluemob.app.ui.profile.Badges
import com.bluemob.app.ui.profile.BadgesScreen
import com.bluemob.app.ui.rescue.RescueActions
import com.bluemob.app.ui.rescue.RescueScreen
import com.bluemob.app.ui.sos.CheckInScreen
import com.bluemob.app.ui.sos.SosHubState
import com.bluemob.app.ui.theme.BlueMobTheme
import com.bluemob.app.ui.theme.Gradients
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Google Play screenshots (1080 × 2160), in English and Hindi: a caption on the brand gradient over a real screen.
 * Record with `./gradlew :app:recordPaparazziDebug --tests '*StoreShots*'`.
 */
class StoreShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(screenWidth = 1080, screenHeight = 2160), maxPercentDifference = 0.1)

    private val f = ScreenshotTest()

    private fun store(hindi: Boolean, caption: String, screen: @Composable () -> Unit) {
        Lang.install("hi", Dictionary.parse(File("src/main/assets/i18n/hi.tsv").readText()))
        Lang.current.value = if (hindi) "hi" else "en"
        paparazzi.snapshot {
            BlueMobTheme(dark = false) {
                Column(Modifier.fillMaxSize().background(Gradients.brand()).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(caption, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 36.dp, bottom = 24.dp))
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))) {
                        Surface(color = MaterialTheme.colorScheme.background) { screen() }
                    }
                }
            }
        }
        Lang.current.value = "en"
    }

    private fun sos(hi: Boolean) = store(hi, "SOS that reaches people, even with no signal") { f.hub(SosHubState(null, 3, SignalMode.SCREEN, f.contacts)) }
    private fun nearby(hi: Boolean) = store(hi, "See who's around: no towers, no internet") {
        NearbyScreen(f.nearby, androidx.compose.foundation.layout.PaddingValues(top = 8.dp), {}, {}, {}, {}, {}, {}, {}, {})
    }
    private fun rescue(hi: Boolean) = store(hi, "Follow the compass to whoever needs help") { RescueScreen(f.rescueRoom(false), "me", f.me, flowOf(200f), RescueActions()) }
    private fun chat(hi: Boolean) = store(hi, "Messages and calls, phone to phone, end-to-end encrypted") {
        ChatScreen("a1c2", f.people[0], f.chat, false, MutableSharedFlow(), "Arjun", "3f9a1c2b7d4e8a01", {}, {}, { true }, {}, {}, {}, verified = true)
    }
    private fun guide(hi: Boolean) = store(hi, "A survival guide that works offline, and reads aloud") {
        ArticleScreen(com.bluemob.app.guide.GuideContent.byId("bleed")!!, true, {}, {}, {})
    }
    private fun checkIn(hi: Boolean) = store(hi, "Going alone? An SOS if you don't check in") {
        CheckInScreen(System.currentTimeMillis() + 2 * 3_600_000L + 14 * 60_000L, "Summit and back by the forest trail", 2, true, {}, { _, _ -> }, {}, {})
    }
    private fun badges(hi: Boolean) = store(hi, "Levels and badges for being prepared") {
        BadgesScreen(Badges.of(BadgeStats(guidesRead = 12, quizzes = 4, streak = 5, rescuesJoined = 2, sosContactsAccepted = 2, messagesSent = 180, groups = 2,
            peopleMet = 11, hasBloodGroup = true, tripsRecorded = 3, goodRatings = mapOf("c_helpful" to 6), verifiedPeople = 1, readToday = true, messagedToday = true))) {}
    }

    @Test fun en1Sos() = sos(false)
    @Test fun en2Nearby() = nearby(false)
    @Test fun en3Rescue() = rescue(false)
    @Test fun en4Chat() = chat(false)
    @Test fun en5Guide() = guide(false)
    @Test fun en6CheckIn() = checkIn(false)
    @Test fun en7Badges() = badges(false)
    @Test fun hi1Sos() = sos(true)
    @Test fun hi2Nearby() = nearby(true)
    @Test fun hi3Rescue() = rescue(true)
    @Test fun hi4Chat() = chat(true)
    @Test fun hi5Guide() = guide(true)
    @Test fun hi6CheckIn() = checkIn(true)
    @Test fun hi7Badges() = badges(true)
}

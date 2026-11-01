package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bluemob.app.chat.AmsCheck
import com.bluemob.app.chat.Announcement
import com.bluemob.app.chat.ChatGroup
import com.bluemob.app.chat.Reach
import com.bluemob.app.chat.RollCall
import com.bluemob.app.chat.Stop
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A pretend trip with pretend people, kept only on this screen: try roll call and the tabs without anyone else. */
object DemoTrip {
    const val ME = "demo000000000000"
    private val people = linkedMapOf(
        "demoasha00000001" to "Asha", "demoravi00000002" to "Ravi", "demomeera0000003" to "Meera", "demoimran0000004" to "Imran", "demokabir0000005" to "Kabir",
    )
    fun group(now: Long): ChatGroup {
        val h = 3_600_000L
        val stops = listOf(
            Stop("s1", now - 3 * h, "Leave Sankri", checkpoint = true),
            Stop("s2", now - h, "Juda ka Talab", checkpoint = true),
            Stop("s3", now + 2 * h, "Kedarkantha base camp", checkpoint = true),
        )
        return ChatGroup("g-demo", "Demo trek", people, now - 5 * h, trip = true, owner = ME, expiresAt = now + 2 * 24 * h,
            itinerary = stops, itineraryAt = now - 5 * h,
            checkins = mapOf("s1" to (people.keys + ME).associateWith { now - 3 * h }, "s2" to people.keys.take(3).associateWith { now - 50 * 60_000L }),
            buddies = mapOf("demoasha00000001" to "demoravi00000002", "demoravi00000002" to "demoasha00000001"),
            announcements = listOf(Announcement("a1", "Lunch at Juda ka Talab, then we go up together. Fill water there.", now - 2 * h, ME)),
            acks = mapOf("a1" to people.keys.take(4).associateWith { now - h }),
            amsDaily = true, amsChecks = mapOf("demomeera0000003" to AmsCheck(6, 2, false, now - 30 * 60_000L, 3_420), "demoasha00000001" to AmsCheck(1, 0, false, now - h, 3_400)),
        )
    }
    /** How each pretend phone answers a roll call, and after how long. */
    val ANSWERS = listOf(
        Triple("demoasha00000001", 700L, Reach.Direct(80)), Triple("demoravi00000002", 1_300L, Reach.Direct(140)),
        Triple("demomeera0000003", 2_200L, Reach.Mesh(1, 900)), Triple("demoimran0000004", 3_400L, Reach.Mesh(2, 2_100)),
    )
}

/** The demo trip: the real trip screen, with roll calls answered by pretend phones (Kabir is out of range). */
@Composable
fun DemoTripScreen(onBack: () -> Unit, onSay: (String) -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val g = remember { DemoTrip.group(now) }
    var roll by remember { mutableStateOf<RollCall?>(null) }
    val scope = rememberCoroutineScope()
    val notReal = { onSay("This is a demo. In a real trip group, this opens for the admin.") }
    TripGroupScreen(g, DemoTrip.ME, roll, emptyList(), "bluemob:t:demo-only-not-a-real-code",
        TripActions(onBack = onBack,
            onRollCall = {
                val start = System.currentTimeMillis()
                roll = RollCall("demo-$start", start, g.members.keys.associateWith { Reach.Waiting })
                scope.launch {
                    var waited = 0L
                    DemoTrip.ANSWERS.forEach { (id, at, reach) ->
                        delay(at - waited); waited = at
                        roll = roll?.let { it.copy(members = it.members + (id to reach)) }
                    }
                }
            },
            onOpenChat = notReal, onHeadcount = notReal, onAnnounce = { notReal() }, onItinerary = notReal, onBuddies = notReal, onPhotos = notReal,
            onReport = notReal, onAltitude = notReal, onShare = { notReal() }, onNewTeam = notReal, onFieldTest = notReal, onClose = notReal, onLeave = onBack),
        now = now, banner = {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(Palette.Gold.copy(alpha = 0.18f)).padding(14.dp)) {
                Text("Demo: pretend people, nothing is sent", style = MaterialTheme.typography.titleSmall)
                Text("Tap Roll call and watch each phone answer: straight to it, through other phones, or not reached (Kabir is out of range). Then look at Plan and Health.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        })
}

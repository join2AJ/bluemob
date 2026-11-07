package com.bluemob.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.IconBadge
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette

/** One thing to have ready before a trip: done or not, why it matters, and the button that fixes it. */
class ReadyItem(val icon: ImageVector, val title: String, val why: String, val done: Boolean, val fix: String? = null, val must: Boolean = true, val onFix: () -> Unit = {})

/** The checks, from what this phone knows. Pure, so it can be tested. */
object ReadyCheck {
    data class State(
        val meshOn: Boolean, val bluetooth: Boolean, val wifi: Boolean, val permissions: Boolean,
        val location: Boolean, val keepsRunning: Boolean, val batterySaver: Boolean, val batteryPct: Int?,
        val sosContacts: Int, val bloodGroup: Boolean, val emergencyCard: Boolean,
        val openTrip: Boolean, val fieldTestDone: Boolean, val baseCamp: Boolean, val peaksSaved: Boolean, val backByTimer: Boolean,
    )
}

@Composable
fun ReadyScreen(items: List<ReadyItem>, onBack: () -> Unit) {
    val must = items.filter { it.must }
    val done = must.count { it.done }
    SubScreen("Ready for the trip?", onBack) {
        item {
            val all = done == must.size
            val c = if (all) Palette.BrandGreen else Palette.Gold
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(c.copy(alpha = 0.14f)).padding(16.dp)) {
                Text(if (all) "Ready to go" else "$done of ${must.size} ready", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(if (all) "Your phone is set up to stay reachable out there. Have a good trip." else "Most problems in the mountains come from phone settings, not from the app. Fix these at home, with signal.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
                LinearProgressIndicator(progress = { if (must.isEmpty()) 1f else done / must.size.toFloat() }, color = c, trackColor = c.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        }
        listOf(true to "Must have", false to "Good to have").forEach { (m, label) ->
            val list = items.filter { it.must == m }
            if (list.isNotEmpty()) {
                item { GroupLabel(label) }
                item {
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
                        list.forEach { r -> ReadyRow(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyRow(r: ReadyItem) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(r.icon, if (r.done) Palette.BrandGreen else Extra.ink3, size = 36.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(r.title, style = MaterialTheme.typography.titleSmall)
            Text(r.why, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
        }
        if (r.done) Icon(Icons.Rounded.CheckCircle, "Done", tint = Palette.BrandGreen, modifier = Modifier.size(26.dp))
        else if (r.fix != null) TextButton(onClick = r.onFix) { Text(r.fix) }
        else Icon(Icons.Rounded.RadioButtonUnchecked, "Not yet", tint = Extra.ink3, modifier = Modifier.size(26.dp))
    }
}

/** The list for this phone. [go] opens a fix by name. */
fun readyItems(s: ReadyCheck.State, go: (String) -> Unit): List<ReadyItem> = listOf(
    ReadyItem(Icons.Rounded.Bluetooth, "Bluetooth, Wi-Fi and the mesh on", "So nearby phones can pass your messages and SOS",
        s.meshOn && s.bluetooth && s.wifi && s.permissions, "Fix") { go("connections") },
    ReadyItem(Icons.Rounded.LocationOn, "Location on", "Your SOS carries your position; the compass and peak finder need it", s.location, "Fix") { go("location") },
    ReadyItem(Icons.Rounded.Verified, "Allowed to run in the background", "Many phones stop apps to save battery: then no one can reach you",
        s.keepsRunning && !s.batterySaver, "Fix") { go("power") },
    ReadyItem(Icons.Rounded.BatteryChargingFull, "Battery charged", s.batteryPct?.let { "Now $it%. Carry a power bank; the mesh uses a little extra" } ?: "Carry a power bank",
        (s.batteryPct ?: 0) >= 80),
    ReadyItem(Icons.Rounded.Contacts, "SOS contacts", "Family or friends who get your SOS by text or over the internet", s.sosContacts > 0, "Add") { go("sos-contacts") },
    ReadyItem(Icons.Rounded.MedicalInformation, "Blood group in your profile", "Goes with your SOS, so helpers can tell medics", s.bloodGroup, "Add") { go("account") },
    ReadyItem(Icons.Rounded.Hiking, "Trip group made and joined", "Everyone scans one code; roll calls check every phone", s.openTrip, "Make one", must = false) { go("new-trip") },
    ReadyItem(Icons.Rounded.Science, "Field test done", "A few roll calls with the group before you set off: see what really gets through", s.fieldTestDone, "Test", must = false) { go("field-test") },
    ReadyItem(Icons.Rounded.Cabin, "Base camp saved", "Walk back to it with the compass; BlueMob warns you when it's time to turn back", s.baseCamp, "Compass", must = false) { go("compass") },
    ReadyItem(Icons.Rounded.Timer, "\"Back by\" timer", "Going alone? If you're not back in time, BlueMob sends your SOS", s.backByTimer, "Set", must = false) { go("check-in") },
    ReadyItem(Icons.Rounded.Landscape, "Peaks downloaded for the area", "The peak finder then names every peak around you, offline", s.peaksSaved, "Get", must = false) { go("peaks") },
    ReadyItem(Icons.Rounded.MedicalInformation, "Emergency card on the lock screen", "Whoever finds you sees your blood group and who to call", s.emergencyCard, "Turn on", must = false) { go("emergency-card") },
)

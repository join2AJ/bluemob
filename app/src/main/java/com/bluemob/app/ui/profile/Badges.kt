package com.bluemob.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra

/** One badge: earned once [have] reaches [need]. */
data class Badge(val emoji: String, val name: String, val how: String, val have: Int, val need: Int) {
    val earned get() = have >= need
}

/** What badges are worth: things that make you (and others) safer. */
data class BadgeStats(
    val guidesRead: Int = 0, val quizzes: Int = 0, val streak: Int = 0, val rescuesJoined: Int = 0,
    val sosContactsAccepted: Int = 0, val messagesSent: Int = 0, val groups: Int = 0, val peopleMet: Int = 0,
    val hasBloodGroup: Boolean = false, val tripsRecorded: Int = 0,
)

object Badges {
    fun of(s: BadgeStats): List<Badge> = listOf(
        Badge("🛟", "Covered", "Have an SOS contact who accepted", s.sosContactsAccepted, 1),
        Badge("🩸", "Ready for help", "Add your blood group, so helpers know", if (s.hasBloodGroup) 1 else 0, 1),
        Badge("📖", "Reader", "Read 5 guides", s.guidesRead, 5),
        Badge("📚", "Know-it-all", "Read 20 guides", s.guidesRead, 20),
        Badge("🧠", "Tested", "Pass 3 guide quizzes", s.quizzes, 3),
        Badge("🎓", "Expert", "Pass 10 guide quizzes", s.quizzes, 10),
        Badge("🔥", "Steady", "Read guides 3 days in a row", s.streak, 3),
        Badge("🤝", "Neighbour", "Meet 5 people with BlueMob", s.peopleMet, 5),
        Badge("👥", "Team", "Start or join a group", s.groups, 1),
        Badge("💬", "Connected", "Send 50 messages", s.messagesSent, 50),
        Badge("🥾", "Explorer", "Record a trip", s.tripsRecorded, 1),
        Badge("🏃", "Rescuer", "Go to help someone who sent an SOS", s.rescuesJoined, 1),
        Badge("🦸", "Guardian", "Help in 5 rescues", s.rescuesJoined, 5),
    )
}

@Composable
fun BadgesScreen(badges: List<Badge>, onBack: () -> Unit) {
    SubScreen("Badges", onBack) {
        item {
            Text("${badges.count { it.earned }} of ${badges.size} earned. Each one is something that makes you, or the people around you, safer.",
                style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
        }
        badges.chunked(3).forEach { row ->
            item {
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { b ->
                        Column(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).background(if (b.earned) Extra.pineTint else Extra.sand).padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(b.emoji, fontSize = 30.sp, modifier = Modifier.alpha(if (b.earned) 1f else 0.3f))
                            Text(b.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                            Text(b.how, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, textAlign = TextAlign.Center, minLines = 2)
                            if (!b.earned) Text("${b.have.coerceAtMost(b.need)} / ${b.need}", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f).height(1.dp)) }
                }
            }
        }
    }
}

package com.bluemob.app.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bluemob.app.trust.RatingCategory
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import kotlin.math.sqrt
import kotlin.random.Random

/** Medal levels for each badge. Each one earned is worth [xp]. */
enum class Tier(val label: String, val color: Color, val xp: Int) {
    BRONZE("Bronze", Color(0xFFCD7F32), 50),
    SILVER("Silver", Color(0xFF9AA7B4), 150),
    GOLD("Gold", Color(0xFFF2B70C), 400),
    DIAMOND("Diamond", Color(0xFF3FB8F0), 1000),
}

enum class TrackGroup(val title: String) {
    READY("Be ready"), LEARN("Learn"), CONNECT("Connect"), HELP("Help others"), EXPLORE("Explore"),
    STRENGTHS("What people say about you"), SECRET("Secret badges"),
}

/**
 * One badge with up to four medals: Bronze at [steps][0], Silver at [steps][1]… [have] is how far the user is.
 * [unit] completes "3 more guides".
 */
data class Track(
    val id: String, val emoji: String, val name: String, val unit: String, val have: Int, val steps: List<Int>,
    val group: TrackGroup, val secretHint: String? = null,
) {
    val passed: Int get() = steps.count { have >= it }
    val tier: Tier? get() = if (passed == 0) null else Tier.entries[passed - 1]
    val nextTier: Tier? get() = Tier.entries.getOrNull(passed)?.takeIf { passed < steps.size }
    val next: Int? get() = steps.getOrNull(passed)
    val maxed: Boolean get() = passed == steps.size
    /** 0..1 towards the next medal. */
    val progress: Float get() {
        val n = next ?: return 1f
        val from = steps.getOrNull(passed - 1) ?: 0
        return ((have - from).toFloat() / (n - from)).coerceIn(0f, 1f)
    }
    val hidden: Boolean get() = secretHint != null && passed == 0
    fun how(): String = when {
        maxed -> "All medals won!"
        else -> "${next!! - have} more $unit for ${nextTier!!.label}"
    }
}

/** What badges are worth: things that make you (and others) safer. */
data class BadgeStats(
    val guidesRead: Int = 0, val quizzes: Int = 0, val streak: Int = 0, val rescuesJoined: Int = 0,
    val sosContactsAccepted: Int = 0, val messagesSent: Int = 0, val groups: Int = 0, val peopleMet: Int = 0,
    val hasBloodGroup: Boolean = false, val tripsRecorded: Int = 0,
    /** Ratings of 4 stars or more that others gave this user, by category code. */
    val goodRatings: Map<String, Int> = emptyMap(),
    val verifiedPeople: Int = 0, val nightMessages: Int = 0, val longestTripKm: Int = 0,
    /** Today's missions: read a guide, send a message, be near another BlueMob phone. */
    val readToday: Boolean = false, val messagedToday: Boolean = false, val metToday: Boolean = false,
)

data class Rank(val fromLevel: Int, val emoji: String, val title: String)

data class Mission(val emoji: String, val text: String, val done: Boolean)

/** Everything the badges screen shows: medals, XP, level and rank, and today's missions. */
data class GameProgress(val tracks: List<Track>, val xp: Int, val missions: List<Mission>) {
    val level: Int get() = Badges.levelFor(xp)
    val levelStart: Int get() = Badges.xpForLevel(level)
    val levelEnd: Int get() = Badges.xpForLevel(level + 1)
    val rank: Rank get() = Badges.RANKS.last { level >= it.fromLevel }
    val nextRank: Rank? get() = Badges.RANKS.firstOrNull { it.fromLevel > level }
    val medals: Int get() = tracks.sumOf { it.passed }
    val medalsTotal: Int get() = tracks.sumOf { it.steps.size }
    /** Each medal won, as "track:tier", to spot new ones. */
    val earnedKeys: Set<String> get() = tracks.flatMap { t -> (0 until t.passed).map { "${t.id}:$it" } }.toSet()
    /** The medals closest to being won: what to aim for next. */
    val upNext: List<Track> get() = tracks.filter { !it.maxed && !it.hidden }.sortedByDescending { it.progress }.take(3)
}

object Badges {
    val RANKS = listOf(
        Rank(1, "🌱", "Newcomer"), Rank(3, "🧭", "Scout"), Rank(5, "🥾", "Pathfinder"), Rank(8, "🏕️", "Ranger"),
        Rank(12, "🛡️", "Guardian"), Rank(17, "🦸", "Hero"), Rank(23, "🌟", "Legend"),
    )

    /** Level n starts at 50·n·(n−1) XP: 0, 100, 300, 600, 1000… each level a little longer than the last. */
    fun xpForLevel(level: Int): Int = 50 * level * (level - 1)
    fun levelFor(xp: Int): Int = ((1 + sqrt(1 + xp / 12.5)) / 2).toInt().coerceAtLeast(1).let { n ->
        var l = n
        while (xpForLevel(l + 1) <= xp) l++
        while (l > 1 && xpForLevel(l) > xp) l--
        l
    }

    fun of(s: BadgeStats): GameProgress {
        val tracks = listOf(
            Track("covered", "🛟", "Covered", "SOS contacts", s.sosContactsAccepted, listOf(1, 2, 3, 5), TrackGroup.READY),
            Track("blood", "🩸", "Ready for help", "step: add your blood group", if (s.hasBloodGroup) 1 else 0, listOf(1), TrackGroup.READY),
            Track("verified", "🔐", "Trusted circle", "people verified with a QR code", s.verifiedPeople, listOf(1, 3, 10, 25), TrackGroup.READY),
            Track("reader", "📖", "Reader", "guides", s.guidesRead, listOf(3, 10, 25, 50), TrackGroup.LEARN),
            Track("quiz", "🧠", "Quiz master", "quizzes passed", s.quizzes, listOf(1, 5, 15, 30), TrackGroup.LEARN),
            Track("streak", "🔥", "On fire", "days in a row", s.streak, listOf(3, 7, 14, 30), TrackGroup.LEARN),
            Track("neighbour", "🤝", "Neighbour", "people met", s.peopleMet, listOf(3, 10, 25, 60), TrackGroup.CONNECT),
            Track("chatter", "💬", "Connected", "messages", s.messagesSent, listOf(10, 50, 200, 1000), TrackGroup.CONNECT),
            Track("team", "👥", "Team player", "groups", s.groups, listOf(1, 3, 6, 10), TrackGroup.CONNECT),
            Track("rescuer", "🏃", "Rescuer", "rescues joined", s.rescuesJoined, listOf(1, 3, 10, 25), TrackGroup.HELP),
            Track("explorer", "🥾", "Explorer", "trips", s.tripsRecorded, listOf(1, 5, 15, 40), TrackGroup.EXPLORE),
            Track("distance", "🗺️", "Long haul", "km in one trip", s.longestTripKm, listOf(5, 20, 50, 100), TrackGroup.EXPLORE),
        ) + RatingCategory.entries.map { c ->
            Track("rated_" + c.code, c.emoji, c.label, "good ratings", s.goodRatings[c.code] ?: 0, listOf(1, 5, 15, 40), TrackGroup.STRENGTHS)
        } + listOf(
            Track("night", "🦉", "Night owl", "night-time messages", s.nightMessages, listOf(1, 10, 50, 100), TrackGroup.SECRET,
                secretHint = "Something happens when most people are asleep…"),
        )
        val xp = tracks.sumOf { t -> (0 until t.passed).sumOf { Tier.entries[it].xp } } +
            s.guidesRead * 10 + s.quizzes * 25 + minOf(s.messagesSent, 500) + s.peopleMet * 5 + s.rescuesJoined * 200 +
            s.tripsRecorded * 20 + s.sosContactsAccepted * 50 + (if (s.hasBloodGroup) 30 else 0) + s.streak * 15 + s.verifiedPeople * 40 +
            s.goodRatings.values.sum() * 30
        val missions = listOf(
            Mission("📖", "Read a guide", s.readToday),
            Mission("💬", "Send a message", s.messagedToday),
            Mission("📡", "Be near another BlueMob phone", s.metToday),
        )
        return GameProgress(tracks, xp, missions)
    }
}

/** The pill on the You tab: level, rank and medals. */
@Composable
fun LevelPill(p: GameProgress, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFFFB13B), Color(0xFFFF7A3D))))
        .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${p.rank.emoji} Level ${p.level} · ${p.rank.title}", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text("  🏅 ${p.medals}", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
fun BadgesScreen(p: GameProgress, onBack: () -> Unit) {
    var open by remember { mutableStateOf<Track?>(null) }
    open?.let { t -> TrackDialog(t) { open = null } }
    SubScreen("Badges & level", onBack) {
        item { LevelCard(p) }
        item { GroupLabel("Today's missions") }
        item { Missions(p.missions) }
        if (p.upNext.isNotEmpty()) {
            item { GroupLabel("Almost there") }
            p.upNext.forEach { t -> item { NextUp(t) { open = t } } }
        }
        TrackGroup.entries.forEach { g ->
            val inGroup = p.tracks.filter { it.group == g }
            if (inGroup.isEmpty()) return@forEach
            item { GroupLabel(g.title) }
            inGroup.chunked(3).forEach { row ->
                item {
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t -> MedalTile(t, Modifier.weight(1f)) { open = t } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f).height(1.dp)) }
                    }
                }
            }
            if (g == TrackGroup.STRENGTHS) item {
                Text("Won when people you helped or met rate you 4 stars or more.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun LevelCard(p: GameProgress) {
    val span = (p.levelEnd - p.levelStart).coerceAtLeast(1)
    val into = p.xp - p.levelStart
    val fill = remember { Animatable(0f) }
    LaunchedEffect(p.xp) { fill.animateTo(into.toFloat() / span, tween(900)) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF1C7FD6), Color(0xFF7B4DDB), Color(0xFFFF7A3D)))).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LEVEL", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Text("${p.level}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("${p.rank.emoji} ${p.rank.title}", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${p.xp} XP · 🏅 ${p.medals} of ${p.medalsTotal} medals", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(fill.value.coerceIn(0.02f, 1f)).height(12.dp).clip(RoundedCornerShape(50)).background(Color.White))
        }
        Text("${p.levelEnd - p.xp} XP to level ${p.level + 1}" + (p.nextRank?.let { " · ${it.emoji} ${it.title} at level ${it.fromLevel}" } ?: ""),
            style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Missions(missions: List<Mission>) {
    val done = missions.count { it.done }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Extra.sand).padding(14.dp)) {
        missions.forEach { m ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (m.done) MaterialTheme.colorScheme.primary else Extra.line), contentAlignment = Alignment.Center) {
                    Text(if (m.done) "✓" else m.emoji, color = if (m.done) Color.White else Color.Unspecified, fontSize = 13.sp)
                }
                Text(m.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp),
                    color = if (m.done) Extra.ink3 else MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(if (done == missions.size) "🎉 All done today! Come back tomorrow for new ones." else "$done of ${missions.size} done today",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun NextUp(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.large).background(Extra.sand).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Medal(t, 46.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleSmall)
            Text(t.how(), style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            ProgressBar(t.progress, t.nextTier?.color ?: Tier.GOLD.color, Modifier.padding(top = 6.dp))
        }
        Text("+${t.nextTier?.xp ?: 0} XP", style = MaterialTheme.typography.labelMedium, color = t.nextTier?.color ?: Extra.ink3,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MedalTile(t: Track, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(if (t.tier != null) Extra.pineTint else Extra.sand).clickable(onClick = onClick).padding(10.dp)
        .semantics(mergeDescendants = true) { contentDescription = "${t.name}: " + (t.tier?.label ?: "not won yet") + ". " + if (t.hidden) "Secret" else t.how() },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Medal(t, 54.dp)
        Text(if (t.hidden) "???" else t.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        Text(t.tier?.label ?: if (t.hidden) "Secret" else "Locked", style = MaterialTheme.typography.labelSmall, color = t.tier?.color ?: Extra.ink3, fontWeight = FontWeight.Bold)
        if (!t.maxed && !t.hidden) ProgressBar(t.progress, t.nextTier?.color ?: Tier.BRONZE.color, Modifier.padding(top = 6.dp))
    }
}

/** The badge's emoji in a ring the colour of its best medal, with a pip for each medal won. */
@Composable
private fun Medal(t: Track, size: androidx.compose.ui.unit.Dp) {
    val ring = t.tier?.color ?: Extra.line
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(CircleShape).background(if (t.tier != null) ring.copy(alpha = 0.18f) else Extra.sand2).border(3.dp, ring, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(if (t.hidden) "❓" else t.emoji, fontSize = (size.value * 0.45f).sp, modifier = Modifier.alpha(if (t.tier != null || t.hidden) 1f else 0.35f))
        }
        Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            t.steps.indices.forEach { i ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (i < t.passed) Tier.entries[i].color else Extra.line))
            }
        }
    }
}

@Composable
private fun ProgressBar(value: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Extra.line)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
private fun TrackDialog(t: Track, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        icon = { Medal(t, 72.dp) },
        title = { Text(if (t.hidden) "Secret badge" else t.name, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (t.hidden) Text(t.secretHint!!, textAlign = TextAlign.Center)
                else {
                    Text(t.how(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    t.steps.forEachIndexed { i, need ->
                        val tier = Tier.entries[i]
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < t.passed) tier.color else Extra.line))
                            Text("${tier.label}: $need ${t.unit}", Modifier.padding(start = 8.dp).weight(1f))
                            Text(if (i < t.passed) "✓" else "+${tier.xp} XP", color = if (i < t.passed) MaterialTheme.colorScheme.primary else Extra.ink3)
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("OK") } },
    )
}

/** A medal was just won: confetti, the medal popping in, and the XP. */
@Composable
fun MedalCelebration(t: Track, tierIndex: Int, onDone: () -> Unit) {
    val tier = Tier.entries[tierIndex]
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(0.2f) }
    val fall = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { fall.animateTo(1f, tween(2_600, easing = LinearEasing)) }
    val pieces = remember { List(70) { Confetto(Random.nextFloat(), Random.nextFloat() * 0.6f, Random.nextFloat() * 0.6f + 0.6f, CONFETTI.random(), Random.nextFloat() * 360f) } }
    Dialog(onDismissRequest = onDone) {
        Box(Modifier.fillMaxWidth().height(440.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NEW MEDAL!", style = MaterialTheme.typography.labelLarge, color = tier.color, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.padding(vertical = 16.dp).scale(pop.value).size(120.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(tier.color.copy(alpha = 0.45f), tier.color.copy(alpha = 0.1f)))).border(5.dp, tier.color, CircleShape),
                    contentAlignment = Alignment.Center) { Text(t.emoji, fontSize = 56.sp) }
                Text("${tier.label} ${t.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("+${tier.xp} XP", style = MaterialTheme.typography.titleLarge, color = tier.color, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
                Text(if (t.maxed) "You've won every medal for this badge. Legendary!" else t.how(),
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp).width(160.dp)) { Text("Awesome!") }
            }
            Canvas(Modifier.fillMaxSize()) {
                pieces.forEach { c ->
                    val y = (c.y0 + fall.value * c.speed) * size.height - size.height * 0.2f
                    if (y < size.height) rotate(c.spin + fall.value * 720f, pivot = Offset(c.x * size.width, y)) {
                        drawRect(c.color.copy(alpha = 1f - fall.value * 0.6f), Offset(c.x * size.width, y), Size(14f, 8f))
                    }
                }
            }
        }
    }
}

private data class Confetto(val x: Float, val y0: Float, val speed: Float, val color: Color, val spin: Float)
private val CONFETTI = listOf(Color(0xFFFF5A5F), Color(0xFFFFB13B), Color(0xFF3FC897), Color(0xFF1C7FD6), Color(0xFF7B4DDB), Color(0xFFF2B70C))


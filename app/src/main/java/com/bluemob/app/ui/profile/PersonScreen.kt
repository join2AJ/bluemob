package com.bluemob.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bluemob.app.trust.Rating
import com.bluemob.app.trust.RatingKind
import com.bluemob.app.trust.TrustManager
import com.bluemob.app.trust.TrustScore
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Gap
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.StarRow
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.TimeText
import com.bluemob.app.util.formatId

/**
 * Someone's profile: their stars out of 5, what the rating is made of, remarks from people who rated them,
 * and buttons to rate them yourself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonScreen(
    nodeId: String,
    name: String,
    avatar: String?,
    score: TrustScore,
    isMe: Boolean,
    myRatings: List<Rating>,
    /** Their most recent SOS, if we know one, so it can be rated real or fake. */
    sosId: String?,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onRate: (RatingKind, String, String) -> Unit,
    /** True when we've met, chatted or shared a rescue: only then can we rate them. */
    eligible: Boolean = true,
    lastRatedAt: Long? = null,
    onRateCategories: (Map<com.bluemob.app.trust.RatingCategory, Int>, String) -> String? = { _, _ -> null },
) {
    var picks by remember { mutableStateOf(mapOf<com.bluemob.app.trust.RatingCategory, Int>()) }
    var rateNote by remember { mutableStateOf<String?>(null) }
    var composing by rememberSaveable { mutableStateOf<String?>(null) }
    var remark by rememberSaveable { mutableStateOf("") }
    SubScreen(if (isMe) "Your rating" else name, onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(avatar, name, nodeId, 84.dp)
                Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                Text("BM " + formatId(nodeId), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = Extra.ink3)
                com.bluemob.app.ui.components.QuarterStars(score.stars, 30.dp, Modifier.padding(top = 12.dp))
                Text(if (score.raters == 0 && score.fakeSos == 0) "New · no ratings yet" else "%.2f out of 5 · %d %s rated".format(score.quarters, score.raters, if (score.raters == 1) "person" else "people"),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (score.thanks > 0) Tag("👏 ${score.thanks} thanked them", Extra.pineTint, MaterialTheme.colorScheme.primary)
                    if (score.genuineSos > 0) Tag("✅ ${score.genuineSos} confirmed a real SOS", Extra.pineTint, MaterialTheme.colorScheme.primary)
                    if (score.badLanguage > 0) Tag("🚩 ${score.badLanguage} language flag${if (score.badLanguage == 1) "" else "s"}", Extra.emberTint, Extra.ember)
                    if (score.fakeSos > 0) Tag("⚠ ${score.fakeSos} fake SOS report${if (score.fakeSos == 1) "" else "s"}", Extra.emberTint, Extra.rose)
                }
            }
        }
        // Each category, in quarter stars.
        item { GroupLabel("By category") }
        item {
            Group {
                com.bluemob.app.trust.RatingCategory.entries.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 14.dp), color = Extra.line)
                    val cs = score.categories[c]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.emoji, fontSize = 20.sp)
                        Text(c.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        com.bluemob.app.ui.components.QuarterStars(cs?.stars ?: com.bluemob.app.trust.Trust.START, 16.dp)
                        Text(if ((cs?.count ?: 0) == 0) " new" else " %.2f".format(com.bluemob.app.trust.Trust.quarter(cs!!.stars)), style = MaterialTheme.typography.labelMedium, color = Extra.ink2, modifier = Modifier.width(44.dp))
                    }
                }
            }
        }
        if (!isMe) {
            item { GroupLabel("Rate ${name.substringBefore(" ")}") }
            item {
                val waitUntil = lastRatedAt?.plus(com.bluemob.app.trust.Trust.RERATE_MS)?.takeIf { it > System.currentTimeMillis() }
                Group {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        when {
                            !eligible -> Text("You can rate people you've met nearby, chatted with, or shared a rescue with.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                            waitUntil != null -> Text("You rated ${name.substringBefore(" ")} ${TimeText.ago(lastRatedAt!!)}. You can rate again after " +
                                java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(waitUntil)) + ".", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                            else -> {
                                com.bluemob.app.trust.RatingCategory.entries.forEach { c ->
                                    Text("${c.emoji} ${c.label}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                                    Text(c.question, style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                                    com.bluemob.app.ui.components.StarPicker(picks[c] ?: 0) { v -> picks = picks + (c to v) }
                                }
                                Box(Modifier.padding(top = 8.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(12.dp)) {
                                    if (remark.isEmpty()) Text("A few words (optional), e.g. Brought water and stayed with me", color = Extra.ink3, style = MaterialTheme.typography.bodyLarge)
                                    BasicTextField(remark, { remark = it.take(TrustManager.MAX_REMARK) }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                                }
                                Button(onClick = { rateNote = onRateCategories(picks, remark) ?: "Thanks! Your rating is signed and shared."; if (rateNote?.startsWith("Thanks") == true) { picks = emptyMap(); remark = "" } },
                                    enabled = picks.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Send rating") }
                                Text("Rate only what you saw. You can rate each person once every 30 days.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        rateNote?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            }
        }
        if (!isMe && sosId != null) {
            item { GroupLabel("About their SOS") }
            item {
                Group {
                    // Thanking someone (or confirming their SOS) and calling their SOS fake contradict each other: only
                    // the side you haven't taken is offered.
                    val vouched = myRatings.any { it.kind == RatingKind.THANKS || it.kind == RatingKind.GENUINE_SOS }
                    val flagged = myRatings.any { it.kind == RatingKind.FAKE_SOS }
                    val options = buildList {
                        if (!flagged) add(RatingKind.GENUINE_SOS to sosId)
                        if (!vouched) add(RatingKind.FAKE_SOS to sosId)
                    }
                    options.forEachIndexed { i, (kind, ctx) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 14.dp), color = Extra.line)
                        val mine = myRatings.firstOrNull { it.kind == kind && it.ctx == ctx }
                        val key = kind.code + "|" + ctx
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(actionTitle(kind), style = MaterialTheme.typography.titleMedium)
                                    Text(mine?.let { "You did this ${TimeText.ago(it.at)}" + if (it.remark.isNotBlank()) ": \"${it.remark}\"" else "" } ?: actionHint(kind),
                                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                                }
                                TextButton(onClick = { composing = if (composing == key) null else key; remark = mine?.remark ?: "" }) {
                                    Text(if (mine == null) "Rate" else "Change", color = if (kind.positive) MaterialTheme.colorScheme.primary else Extra.rose)
                                }
                            }
                            if (composing == key) {
                                Box(Modifier.padding(top = 8.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(12.dp)) {
                                    if (remark.isEmpty()) Text(remarkHint(kind), color = Extra.ink3, style = MaterialTheme.typography.bodyLarge)
                                    BasicTextField(remark, { remark = it.take(TrustManager.MAX_REMARK) }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                                }
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onRate(kind, ctx, remark); composing = null },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (kind.positive) MaterialTheme.colorScheme.primary else Extra.rose, contentColor = Color.White)) {
                                        Text(if (kind.positive) "Send" else "Report")
                                    }
                                    OutlinedButton(onClick = { composing = null }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isMe) item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = onMessage) { Text("Message ${name.substringBefore(" ")}") }
            }
        }
        val remarks = score.remarks.distinctBy { it.rater to it.remark }
        item { GroupLabel(if (remarks.isEmpty()) "Remarks" else "Remarks (${remarks.size})") }
        item {
            Group {
                if (remarks.isEmpty()) Text(if (isMe) "No remarks yet. Help someone, and they can say so here." else "No remarks yet.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(14.dp))
                remarks.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 14.dp), color = Extra.line)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.raterName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(TimeText.ago(r.at), style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                        }
                        Text("${r.category.emoji} ${r.category.label} · ${r.stars}★", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("“${r.remark}”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = Extra.skyTint, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("How stars work", style = MaterialTheme.typography.titleSmall)
                    Text("People rate 5 things, 1 to 5 stars each: helpful, quick to respond, reliable, clear communication, respectful. " +
                        "Each starts at 4 stars, so one or two ratings can't swing it much; the overall is their average. Only people who met, " +
                        "chatted or shared a rescue can rate, once every 30 days. People met in person count fully, others half, and ratings older " +
                        "than 6 months count half. Each fake-SOS report takes ½ star off the overall. Every rating is signed by the phone that gave it, so it can't be faked or changed.",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { Gap(8.dp) }
    }
}

private fun actionTitle(k: RatingKind) = when (k) {
    RatingKind.THANKS -> "👏 Appreciate their help"
    RatingKind.GENUINE_SOS -> "✅ Their SOS was real"
    RatingKind.FAKE_SOS -> "⚠ Their SOS was fake or a prank"
    RatingKind.BAD_LANGUAGE -> "🚩 Report bad language"
}

private fun actionHint(k: RatingKind) = when (k) {
    RatingKind.THANKS -> "Adds to their stars. Say what they did, if you like"
    RatingKind.GENUINE_SOS -> "They really needed help"
    RatingKind.FAKE_SOS -> "Only if you're sure. It costs them 1½ stars"
    RatingKind.BAD_LANGUAGE -> "Abusive or offensive messages"
}

private fun remarkHint(k: RatingKind) = when (k) {
    RatingKind.THANKS -> "e.g. Brought water and stayed with me"
    RatingKind.GENUINE_SOS -> "e.g. Had a sprained ankle, we carried her down"
    RatingKind.FAKE_SOS -> "e.g. Nobody was there, they laughed about it"
    RatingKind.BAD_LANGUAGE -> "What happened (optional)"
}

package com.bluemob.app.ui.profile

import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
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
import com.bluemob.app.i18n.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
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
    blocked: Boolean = false,
    onBlock: (Boolean) -> Unit = {},
    /** Reports them to the BlueMob team: (reason, what happened, include their last messages). Null when sent. */
    onReport: suspend (String, String, Boolean) -> String? = { _, _, _ -> null },
    /** Verified in person with a QR code or the safety number. */
    verified: Boolean = false,
    onVerify: () -> Unit = {},
) {
    var reporting by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    if (reporting) ReportPersonDialog(name, onReport) { reporting = false }
    if (confirmBlock) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmBlock = false },
        title = { Text("Block ${name.substringBefore(" ")}?") },
        text = { Text("Their messages and calls won't reach you, and they won't be told. If they send an SOS, you'll still see it: safety comes first. You can unblock them here any time.") },
        confirmButton = { TextButton(onClick = { confirmBlock = false; onBlock(true) }) { Text("Block", color = Extra.rose) } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } },
    )
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
                if (!isMe) VerifyPill(verified, onVerify)
                // The score: one card with the big number, the stars and how many rated.
                Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val new = score.raters == 0 && score.fakeSos == 0
                    com.bluemob.app.i18n.RawText(if (new) "–" else "%.1f".format(score.quarters), style = MaterialTheme.typography.displayMedium,
                        color = com.bluemob.app.ui.theme.Palette.Gold)
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        com.bluemob.app.ui.components.QuarterStars(score.stars, 20.dp)
                        Text(if (new) "New · no ratings yet" else "out of 5 · ${score.raters} ${if (score.raters == 1) "person" else "people"} rated",
                            style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (score.thanks > 0) Tag("${score.thanks} thanked them", Extra.pineTint, MaterialTheme.colorScheme.primary)
                    if (score.genuineSos > 0) Tag("${score.genuineSos} confirmed a real SOS", Extra.pineTint, MaterialTheme.colorScheme.primary)
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
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 62.dp), color = Extra.line)
                    val cs = score.categories[c]
                    val rated = (cs?.count ?: 0) > 0
                    val value = cs?.stars ?: com.bluemob.app.trust.Trust.START
                    val (icon, tint) = categoryLook(c)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(18.dp), tint = tint)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                com.bluemob.app.i18n.RawText(if (rated) "%.1f".format(com.bluemob.app.trust.Trust.quarter(value)) else "–", style = MaterialTheme.typography.titleSmall,
                                    color = if (rated) MaterialTheme.colorScheme.onSurface else Extra.ink3)
                            }
                            // How full the category is, out of 5 (faint until someone has rated it).
                            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Extra.sand)) {
                                Box(Modifier.fillMaxWidth((value / 5f).toFloat().coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp))
                                    .background(if (rated) tint else tint.copy(alpha = 0.3f)))
                            }
                        }
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
                                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val (ci, ct) = categoryLook(c)
                                        androidx.compose.material3.Icon(ci, null, tint = ct, modifier = Modifier.size(18.dp))
                                        Text(c.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
                                    }
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
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (blocked) Text("🚫 Blocked: their messages and calls don't reach you", style = MaterialTheme.typography.bodySmall, color = Extra.rose,
                    modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!blocked) OutlinedButton(onClick = onMessage) { Text("Message") }
                    OutlinedButton(onClick = { if (blocked) onBlock(false) else confirmBlock = true }) { Text(if (blocked) "Unblock" else "Block") }
                    OutlinedButton(onClick = { reporting = true }) { Text("Report", color = Extra.rose) }
                }
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
                        Text("${r.category.label} · ${r.stars}★", style = MaterialTheme.typography.labelMedium, color = categoryLook(r.category).second)
                        Text("“${r.remark}”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        item {
            var how by rememberSaveable { mutableStateOf(false) }
            Surface(onClick = { how = !how }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(androidx.compose.material.icons.Icons.Outlined.Info, null, Modifier.size(18.dp), tint = Extra.ink3)
                        Text("How stars work", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Icon(androidx.compose.material.icons.Icons.Outlined.ExpandMore, null, Modifier.size(20.dp).rotate(if (how) 180f else 0f), tint = Extra.ink3)
                    }
                    if (how) Text("People rate 5 things, 1 to 5 stars each: helpful, quick to respond, reliable, clear communication, respectful. " +
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


/** Why someone is being reported, what happened, and whether to include the last messages they sent. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReportPersonDialog(name: String, onReport: suspend (String, String, Boolean) -> String?, onClose: () -> Unit) {
    val reasons = listOf("Spam", "Harassment or threats", "Inappropriate content", "Fake SOS", "Pretending to be someone", "Something else")
    var reason by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var withMessages by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (done) "Report sent" else "Report ${name.substringBefore(" ")}") },
        text = {
            if (done) Text("Thank you. The BlueMob team will look at it. You can also block them, so their messages and calls stop.")
            else Column {
                Text("What's wrong?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { r -> com.bluemob.app.ui.components.Chip(r, reason == r) { reason = r } }
                }
                androidx.compose.material3.OutlinedTextField(text, { text = it.take(1000) }, label = { Text("What happened? (optional)") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(withMessages, { withMessages = it })
                    Text("Include the last 10 messages they sent me", style = MaterialTheme.typography.bodySmall)
                }
                result?.let { Text(it, color = Extra.rose, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (done) TextButton(onClick = onClose) { Text("Done") }
            else TextButton(enabled = reason != null && !sending, onClick = {
                sending = true
                scope.launch { result = onReport(reason!!, text, withMessages); sending = false; if (result == null) done = true }
            }) { Text(if (sending) "Sending…" else "Send report", color = Extra.rose) }
        },
        dismissButton = { if (!done) TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** "✓ Verified" when checked in person, otherwise a button to do it. */
@Composable
fun VerifyPill(verified: Boolean, onVerify: () -> Unit) {
    val bg = if (verified) Extra.pineTint else Extra.skyTint
    val fg = if (verified) MaterialTheme.colorScheme.primary else Extra.sky
    Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onVerify).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (verified) "✓ Verified in person" else "🔒 Verify with a QR code", style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** Each rating category's icon and colour. */
private fun categoryLook(c: com.bluemob.app.trust.RatingCategory): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> = when (c) {
    com.bluemob.app.trust.RatingCategory.HELPFUL -> androidx.compose.material.icons.Icons.Rounded.VolunteerActivism to com.bluemob.app.ui.theme.Palette.BrandGreen
    com.bluemob.app.trust.RatingCategory.QUICK -> androidx.compose.material.icons.Icons.Rounded.Bolt to com.bluemob.app.ui.theme.Palette.Gold
    com.bluemob.app.trust.RatingCategory.RELIABLE -> androidx.compose.material.icons.Icons.Rounded.VerifiedUser to com.bluemob.app.ui.theme.Palette.BrandBlue
    com.bluemob.app.trust.RatingCategory.CLEAR -> androidx.compose.material.icons.Icons.Rounded.Forum to com.bluemob.app.ui.theme.Palette.Violet
    com.bluemob.app.trust.RatingCategory.RESPECTFUL -> androidx.compose.material.icons.Icons.Rounded.Favorite to com.bluemob.app.ui.theme.Palette.Sunset
}

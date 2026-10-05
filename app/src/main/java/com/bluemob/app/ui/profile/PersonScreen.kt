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
) {
    var composing by rememberSaveable { mutableStateOf<String?>(null) }
    var remark by rememberSaveable { mutableStateOf("") }
    SubScreen(if (isMe) "Your rating" else name, onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(avatar, name, nodeId, 84.dp)
                Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                Text("BM " + formatId(nodeId), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = Extra.ink3)
                StarRow(score.stars, 30.dp, Modifier.padding(top = 12.dp))
                Text(if (score.isNew) "New · no ratings yet" else "${score.label} out of 5 · ${score.ratings} rating${if (score.ratings == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (score.thanks > 0) Tag("👏 ${score.thanks} thanked them", Extra.pineTint, MaterialTheme.colorScheme.primary)
                    if (score.genuineSos > 0) Tag("✅ ${score.genuineSos} confirmed a real SOS", Extra.pineTint, MaterialTheme.colorScheme.primary)
                    if (score.badLanguage > 0) Tag("🚩 ${score.badLanguage} language flag${if (score.badLanguage == 1) "" else "s"}", Extra.emberTint, Extra.ember)
                    if (score.fakeSos > 0) Tag("⚠ ${score.fakeSos} fake SOS report${if (score.fakeSos == 1) "" else "s"}", Extra.emberTint, Extra.rose)
                }
            }
        }
        if (!isMe) {
            item { GroupLabel("Rate ${name.substringBefore(" ")}") }
            item {
                Group {
                    val options = buildList {
                        add(RatingKind.THANKS to "general")
                        if (sosId != null) { add(RatingKind.GENUINE_SOS to sosId); add(RatingKind.FAKE_SOS to sosId) }
                        add(RatingKind.BAD_LANGUAGE to "general")
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
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = onMessage) { Text("Message ${name.substringBefore(" ")}") }
                }
            }
        }
        item { GroupLabel(if (score.recent.isEmpty()) "Remarks" else "Remarks (${score.recent.size})") }
        item {
            Group {
                if (score.recent.isEmpty()) Text(if (isMe) "No one has rated you yet. Help someone, and they can thank you here." else "No ratings yet.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(14.dp))
                score.recent.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 14.dp), color = Extra.line)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.raterName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(TimeText.ago(r.at), style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                        }
                        Text(r.kind.label, style = MaterialTheme.typography.labelMedium, color = if (r.kind.positive) MaterialTheme.colorScheme.primary else Extra.rose)
                        if (r.remark.isNotBlank()) Text("“${r.remark}”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = Extra.skyTint, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("How stars work", style = MaterialTheme.typography.titleSmall)
                    Text("Everyone starts at 4 stars, and the most is 5. Being thanked for help adds ½, a confirmed real SOS adds ¼. " +
                        "A bad-language flag takes away ¾, a fake SOS report takes away 1½. One person counts at most twice for each kind, " +
                        "people you've never met count half, and old ratings fade. Every rating is signed by the phone that gave it, so it can't be faked or changed.",
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

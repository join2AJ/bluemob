package com.bluemob.app.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bluemob.app.BuildConfig
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra

/** "Build an app like this": what someone wants, turned into one message for email or WhatsApp. Pure, so it can be tested. */
object AppRequest {
    val KINDS = listOf("Education & learning", "Business", "Health & fitness", "Community & personal", "Utilities & productivity", "Travel & outdoors", "Other")
    val FEATURES = listOf("User login / accounts", "Works offline", "Payments (UPI / cards)", "Push notifications", "Chat / messaging", "Maps & location",
        "Camera / photo upload", "Audio / voice", "Video", "Admin dashboard", "Multiple languages (English / Hindi / ...)", "Reports & charts",
        "Booking / calendar", "Website too", "iPhone (iOS) too", "Play Store publishing")
    val BUDGETS = listOf("Under ₹10,000", "₹10,000 – 25,000", "₹25,000 – 50,000", "₹50,000 – 1 lakh", "₹1 – 3 lakh", "Above ₹3 lakh", "Not sure - advise me")
    val WHEN = listOf("As soon as possible", "Within 1 month", "1 – 3 months", "Flexible")
    val STAGE = listOf("Just an idea", "Have a design / sketch", "Have an existing app to improve")

    data class Form(val kind: String? = null, val features: Set<String> = emptySet(), val budget: String? = null, val time: String? = null,
                    val stage: String? = null, val about: String = "", val name: String = "", val contact: String = "")

    /** What's still missing before it can be sent; empty when ready. */
    fun missing(f: Form): List<String> = listOfNotNull(
        "category".takeIf { f.kind == null }, "budget".takeIf { f.budget == null },
        "how to reach you".takeIf { f.contact.isBlank() }, "a short description".takeIf { f.about.isBlank() },
    )

    fun text(f: Form, version: String): String = buildString {
        appendLine("App request (from BlueMob $version)")
        appendLine()
        appendLine("Type: ${f.kind ?: "-"}")
        appendLine("Features: ${f.features.sortedBy { FEATURES.indexOf(it) }.joinToString(", ").ifEmpty { "-" }}")
        appendLine("Budget: ${f.budget ?: "-"}")
        appendLine("When: ${f.time ?: "-"}")
        appendLine("Stage: ${f.stage ?: "-"}")
        appendLine()
        appendLine(f.about.trim())
        appendLine()
        append("Name: ${f.name.trim().ifEmpty { "-" }} · Contact: ${f.contact.trim()}")
    }
}

/** About BlueMob: why it exists, how to support it, and how to ask for an app of your own. */
@Composable
fun AboutScreen(onBack: () -> Unit, onShare: () -> Unit, startOpen: String? = null) {
    var open by rememberSaveable { mutableStateOf(startOpen) }
    val context = LocalContext.current
    val go = { i: Intent -> runCatching { context.startActivity(i) }; Unit }
    SubScreen("About us", onBack) {
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.bluemob.app.R.drawable.ic_launcher_foreground), "BlueMob",
                        Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(Color(0xFF1565C0)))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("BlueMob", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Text("Stay in touch where there's no signal · version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                }
                Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CellTower, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Works where there's no mobile tower. Phones connect straight to each other using Bluetooth and Wi-Fi: no SIM, no signal, no internet needed.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                }
                Text("Messages and SOS hop from phone to phone until they reach someone, and when any phone has internet, on to family far away. " +
                    "Trip groups with roll call, a compass back to camp, a peak finder, knots, first aid and a weather warning: all offline.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                Text("No ads · SOS is free, always · your data stays on your phone", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 10.dp))
            }
        }
        item {
            Card {
                Text("OUR STORY", style = MaterialTheme.typography.labelMedium, color = Extra.ink3)
                Text("Why I made BlueMob", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                listOf(
                    "I love the mountains: the quiet of a high trail, a group walking together, the first view of the snow peaks in the morning.",
                    "Up there the phone signal goes, and with it everything we rely on. A group gets split up, someone takes a wrong turn or twists an ankle, and there's no way to call. I wanted one app that keeps people in touch with no network at all, phone to phone, and gets an SOS out until it reaches someone who can help. I couldn't find it, so I built it.",
                    "BlueMob works offline, has no ads, and its SOS will always be free. I hope it brings you and your friends home safe from every trip.",
                ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            }
        }
        item {
            Fold(Icons.Rounded.Favorite, "Support us", "Donate or share - keep BlueMob free for every hiker",
                Brush.horizontalGradient(listOf(Color(0xFFC2416E), Color(0xFFE0619A))), open == "support", { open = if (open == "support") null else "support" }) {
                Text("No ads, no tracking, and the SOS is never charged. If BlueMob helps you, a small contribution pays for servers, offline maps, more languages and more phones to test on.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
                if (BuildConfig.SUPPORT_UPI.isNotBlank()) WhiteButton(Icons.Rounded.Favorite, "I want to donate") {
                    go(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=${Uri.encode(BuildConfig.SUPPORT_UPI)}&pn=BlueMob&tn=${Uri.encode("Support BlueMob")}&cu=INR")))
                }
                SoftButton(Icons.Rounded.Share, "Share with a friend", onShare)
            }
        }
        item {
            Fold(Icons.Rounded.Smartphone, "Build an app like this", "We will build your Android app for you",
                Brush.horizontalGradient(listOf(Color(0xFF34509A), Color(0xFF4A6FC4))), open == "build", { open = if (open == "build") null else "build" }) {
                Text("For a shop, school, clinic, trekking company, community or your own idea - choose the type of app, the features you want and your budget, and we will build it for you.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
                RequestForm(go)
            }
        }
        item { Text("Made in India with care for the open sky", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(16.dp)) { content() }
}

@Composable
private fun Fold(icon: ImageVector, title: String, sub: String, bg: Brush, isOpen: Boolean, onToggle: () -> Unit, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(bg)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.2f)), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Color.White) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
            }
            Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (isOpen) "Close" else "Open", tint = Color.White)
        }
        AnimatedVisibility(isOpen) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { body() }
        }
    }
}

@Composable
private fun WhiteButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B1B1F)), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(icon, null, Modifier.size(20.dp)); Text("  $label", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SoftButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(icon, null, Modifier.size(20.dp)); Text("  $label", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pick(title: String, options: List<String>, chosen: (String) -> Boolean, onPick: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(selected = chosen(o), onClick = { onPick(o) }, label = { Text(o) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Color.White, selectedLabelColor = Color(0xFF1B1B1F)))
        }
    }
}

@Composable
private fun RequestForm(go: (Intent) -> Unit) {
    var f by remember { mutableStateOf(AppRequest.Form()) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Color.Black.copy(alpha = 0.18f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pick("1  What kind of app?", AppRequest.KINDS, { f.kind == it }) { f = f.copy(kind = it) }
        Pick("2  Which features? (pick all that apply)", AppRequest.FEATURES, { it in f.features }) { f = f.copy(features = if (it in f.features) f.features - it else f.features + it) }
        Pick("3  Budget you are ready to spend", AppRequest.BUDGETS, { f.budget == it }) { f = f.copy(budget = it) }
        Pick("When do you need it?", AppRequest.WHEN, { f.time == it }) { f = f.copy(time = it) }
        Pick("Where are you now?", AppRequest.STAGE, { f.stage == it }) { f = f.copy(stage = it) }
        Text("4  Tell me about it", style = MaterialTheme.typography.titleSmall, color = Color.White)
        val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.5f), focusedPlaceholderColor = Color.White.copy(alpha = 0.6f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f))
        OutlinedTextField(f.about, { f = f.copy(about = it) }, Modifier.fillMaxWidth().height(120.dp), colors = fieldColors,
            placeholder = { Text("What should the app do? Who will use it? Any app you like as an example?") })
        OutlinedTextField(f.name, { f = f.copy(name = it) }, Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors, placeholder = { Text("Your name") })
        OutlinedTextField(f.contact, { f = f.copy(contact = it) }, Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors, placeholder = { Text("Phone / WhatsApp or email") })
        Text("Only sent when you press a button below, through your own email or WhatsApp. Nothing is stored or uploaded by this app.",
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        val missing = AppRequest.missing(f)
        if (missing.isNotEmpty()) Text("Still needed: " + missing.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        val text = AppRequest.text(f, BuildConfig.VERSION_NAME)
        val ready = missing.isEmpty()
        Button(onClick = {
            go(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + BuildConfig.DEV_EMAIL)).putExtra(Intent.EXTRA_SUBJECT, "App request: ${f.kind}").putExtra(Intent.EXTRA_TEXT, text))
        }, enabled = ready, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B1B1F)), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.Email, null, Modifier.size(20.dp)); Text("  Send by email", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = {
            go(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${BuildConfig.DEV_WHATSAPP.filter(Char::isDigit)}?text=" + Uri.encode(text))))
        }, enabled = ready, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Text("Send on WhatsApp", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Text(if (copied) "  Copied" else "  Copy request")
        }
    }
}

/** The pink card on the You tab that opens About us. */
@Composable
fun SupportCard(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(MaterialTheme.shapes.large)
        .background(Brush.horizontalGradient(listOf(Color(0xFFC2416E), Color(0xFFE0619A)))).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.size(42.dp).clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.2f)), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Favorite, null, tint = Color.White) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Support BlueMob · About us", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text("Donate or share · our story · build an app like this", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White)
    }
}

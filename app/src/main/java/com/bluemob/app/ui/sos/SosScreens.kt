package com.bluemob.app.ui.sos

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.bluemob.app.settings.SosContact
import com.bluemob.app.ui.components.Gap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.mesh.SosSignal
import com.bluemob.app.settings.SignalMode
import com.bluemob.app.sos.SignalController
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.Geo
import com.bluemob.app.util.cardinal
import com.bluemob.app.util.shortId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
private val REASONS = listOf("Injured", "Lost", "Medical", "Need water", "Stuck", "Cold", "Animal")

/** What the SOS screen needs to show. */
data class SosHubState(
    val mine: SosSignal?,
    val connected: Int,
    val defaultSignal: SignalMode,
    val contacts: List<SosContact>,
    val reached: Int = -1,
)

@Composable
fun SosHubScreen(
    state: SosHubState,
    /** The text that goes to people nearby and to SOS contacts, for a given note. */
    message: (String) -> String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onSafe: () -> Unit,
    onSignal: () -> Unit,
    onDefaultSignal: (SignalMode) -> Unit,
    onHowToHelp: () -> Unit,
    onContacts: () -> Unit,
    onText: (List<String>, String) -> Unit,
    onPreviewAlert: () -> Unit,
    still: Boolean = false,
    rescue: com.bluemob.app.rescue.RescueRoom? = null,
    onOpenRescue: () -> Unit = {},
) {
    var note by rememberSaveable { mutableStateOf("") }
    var armed by remember { mutableStateOf(false) }
    val mine = state.mine

    SubScreen("SOS", onBack) {
        if (mine == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SosButton(armed, onTap = { if (armed) { armed = false; onSend(note) } else armed = true }, onDisarm = { armed = false }, still = still)
                    Text(
                        "Goes to everyone nearby right away (${state.connected} connected now), and every phone that hears it passes it on." +
                            if (state.contacts.isNotEmpty()) " Then you can text your SOS contacts." else "",
                        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            item { GroupLabel("What's happening? (optional)") }
            item {
                Group {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(REASONS) { r -> Chip(r, r in note) { note = if (r in note) note.replace(r, "").trim(',', ' ') else if (note.isBlank()) r else "$note, $r" } }
                        }
                        Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(12.dp)) {
                            if (note.isEmpty()) Text("e.g. Twisted ankle near the stream, can't walk", color = Extra.ink3, style = MaterialTheme.typography.bodyLarge)
                            BasicTextField(note, { note = it.take(160) }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(Extra.rose), modifier = Modifier.fillMaxWidth())
                        }
                        Text("You can send without a note. Every second counts.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                }
            }
            item { GroupLabel("What will be sent") }
            item {
                Group { Text(message(note), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(14.dp)) }
            }
        } else {
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Tag("SOS ACTIVE · " + clock.format(Date(mine.at)), Extra.rose, Color.White, dot = Color.White)
                    Text("Help is being called", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
                }
            }
            item { Gap(14.dp) }
            item {
                val coming = rescue?.coming.orEmpty()
                androidx.compose.material3.Surface(onClick = onOpenRescue, shape = MaterialTheme.shapes.large, color = if (coming.isEmpty()) Extra.emberTint else Extra.pineTint,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(when (coming.size) { 0 -> "Waiting for someone to answer"; 1 -> "${coming[0].name} is coming"; else -> "${coming.size} people are coming" },
                                style = MaterialTheme.typography.titleMedium)
                            Text(if (coming.isEmpty()) "When people tap \"I'm coming\", you'll see them here and can chat with all of them."
                                else "See where they are and chat with the whole group", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                        }
                        Chevron()
                    }
                }
            }
            item {
                Group {
                    SettingRow(Icons.Outlined.Hub, MaterialTheme.colorScheme.primary,
                        if (state.reached >= 0) "Reached ${state.reached} phone${if (state.reached == 1) "" else "s"} nearby" else "Sent to everyone nearby",
                        "Re-sent to every phone that comes into range, and passed on by each one, until you tap \"I'm safe\"")
                    mine.pos?.let { pos ->
                        SettingRow(Icons.Outlined.MyLocation, Extra.sky, if (pos.gps) "Your GPS position is included" else "Your estimated position is included",
                            pos.describe(), divider = true)
                    } ?: SettingRow(Icons.Outlined.LocationOff, Extra.ink3, "No position included", "Turn on location so helpers can find you", divider = true)
                    if (state.contacts.isEmpty()) {
                        SettingRow(Icons.Outlined.Sms, Extra.ember, "No SOS contacts yet", "Add family or friends to text when you send an SOS", divider = true, onClick = onContacts) { Chevron() }
                    } else {
                        state.contacts.forEach { c ->
                            SettingRow(Icons.Outlined.Sms, Extra.ember, "${c.name} · ${c.phone}", "Opens your SMS app with the SOS written in. Needs mobile signal.", divider = true) {
                                TextButton(onClick = { onText(listOf(c.phone), message(mine.note)) }) { Text("Text") }
                            }
                        }
                    }
                }
            }
            if (state.contacts.size > 1) item {
                OutlinedButton(onClick = { onText(state.contacts.map { it.phone }, message(mine.note)) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Text all ${state.contacts.size} SOS contacts")
                }
            }
            item {
                Text(
                    (if (mine.note.isNotBlank()) "Your note: ${mine.note}\n\n" else "") +
                        "Texts go from your phone's own SMS app. Sending them through a nearby phone that has internet comes with the internet bridge.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
                )
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    Button(onClick = { onSafe(); note = "" }, modifier = Modifier.height(52.dp)) { Text("I'm safe now", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        item { GroupLabel("More") }
        item {
            Group {
                SettingRow(Icons.Outlined.FlashlightOn, Extra.rose, "SOS signal", "Screen, flashlight, sound or all · ··· ––– ···", onClick = onSignal) { Chevron() }
                SettingRow(Icons.Outlined.Bolt, Extra.ember, "Default signal", "Used when you open the SOS signal", divider = true)
                LazyRow(Modifier.padding(start = 62.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SignalMode.entries) { m -> Chip("${m.emoji} ${m.label}", m == state.defaultSignal) { onDefaultSignal(m) } }
                }
                SettingRow(Icons.Outlined.Contacts, Extra.sky, "SOS contacts",
                    if (state.contacts.isEmpty()) "None yet. Add a number" else state.contacts.joinToString(", ") { it.name }, divider = true, onClick = onContacts) { Chevron() }
                SettingRow(Icons.AutoMirrored.Outlined.MenuBook, MaterialTheme.colorScheme.primary, "If you receive an SOS", "How to help someone safely", divider = true, onClick = onHowToHelp) { Chevron() }
                SettingRow(Icons.Outlined.NotificationsActive, Extra.ink3, "Preview: receive an SOS", "See what happens when someone nearby asks for help", divider = true, onClick = onPreviewAlert) { Chevron() }
            }
        }
    }
}

@Composable
internal fun Chevron() = Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Extra.ink3)

/** Add and remove the people to text when you send an SOS. Numbers stay on this phone. */
@Composable
fun SosContactsScreen(contacts: List<SosContact>, onBack: () -> Unit, onAdd: (String, String) -> Unit, onRemove: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    SubScreen("SOS contacts", onBack) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("SOS contacts", style = MaterialTheme.typography.headlineMedium)
                Text("Family or friends to text when you send an SOS. They don't need BlueMob: they get a normal text with your note and position.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { Gap(12.dp) }
        item {
            Group {
                if (contacts.isEmpty()) Text("No one yet. Add someone below.", color = Extra.ink2, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                contacts.forEachIndexed { i, c ->
                    SettingRow(null, Color.Transparent, c.name, c.phone, divider = i > 0) {
                        TextButton(onClick = { onRemove(c.id) }) { Text("Remove", color = Extra.rose) }
                    }
                }
            }
        }
        item { GroupLabel("Add someone") }
        item {
            Group {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Name", name, "e.g. Papa", KeyboardType.Text) { name = it.take(30) }
                    Field("Phone number", phone, "+91 …", KeyboardType.Phone) { phone = it.filter { ch -> ch.isDigit() || ch in "+ -()" }.take(20) }
                    Button(onClick = { onAdd(name, phone); name = ""; phone = "" }, enabled = name.isNotBlank() && phone.count { it.isDigit() } >= 6,
                        modifier = Modifier.fillMaxWidth()) { Text("Add") }
                }
            }
        }
        item {
            Text("Numbers stay on your phone. They're only used when you send an SOS.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, type: KeyboardType, onChange: (String) -> Unit) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(12.dp)) {
            if (value.isEmpty()) Text(hint, color = Extra.ink3, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(value, onChange, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Full-screen ··· ––– ··· using the screen, the flashlight, a whistle tone, or all of them. */
@Composable
fun SosSignalScreen(start: SignalMode, defaultMode: SignalMode, signals: SignalController, onDefault: (SignalMode) -> Unit, onStop: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(start) }
    var lit by remember { mutableStateOf(false) }
    val screen = mode == SignalMode.SCREEN || mode == SignalMode.ALL
    val torch = mode == SignalMode.TORCH || mode == SignalMode.ALL
    val sound = mode == SignalMode.SOUND || mode == SignalMode.ALL

    // Keep the screen on while signalling; full brightness only when the screen itself is the signal (saves battery
    // with flashlight or sound).
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; signals.stop() }
    }
    DisposableEffect(screen) {
        val window = activity?.window
        val before = window?.attributes?.screenBrightness
        if (screen) window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { if (screen) window?.attributes = window?.attributes?.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }
    LaunchedEffect(mode) {
        while (true) {
            for ((on, ms) in SignalController.SOS_PATTERN) {
                lit = on
                if (torch) signals.torch(on)
                if (on && sound) signals.beep(ms)
                delay(ms.toLong())
            }
        }
    }
    LaunchedEffect(torch) { if (!torch) signals.torch(false) }

    val bg = if (lit && screen) Color.White else Color.Black
    val fg = if (lit && screen) Color.Black else Color.White
    Column(
        Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SOS SIGNAL · ${mode.label.uppercase()}", color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        Text("··· ––– ···", color = fg, fontSize = 34.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp), maxLines = 1)
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalMode.entries.forEach { m ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (m == mode) Extra.rose else Color.Gray.copy(alpha = 0.25f))
                        .clickable { mode = m }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(m.emoji, fontSize = 22.sp)
                    Text(m.label, color = if (m == mode) Color.White else fg, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        if (torch) {
            Text("🔦", fontSize = 48.sp, modifier = Modifier.alpha(if (lit) 1f else 0.35f))
            Text(if (signals.hasTorch) "Flashlight" else "This phone has no flashlight", color = fg, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(30.dp))
        Text(mode.tip + ". " + if (screen) "Point the screen at rescuers." else "Repeats until you stop it.", color = fg.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (mode != defaultMode) TextButton(onClick = { onDefault(mode) }) { Text("Make ${mode.label} my default", color = fg) }
        else Text("This is your default", color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(30.dp))
        Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Extra.rose, contentColor = Color.White),
            modifier = Modifier.height(56.dp).width(180.dp)) { Text("Stop", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun MedicalTag(label: String, value: String) {
    Column(Modifier.clip(MaterialTheme.shapes.medium).background(Extra.emberTint).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Extra.rose)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

/** Opens over everything when someone nearby sends an SOS. */
@Composable
fun SosAlert(
    sos: SosSignal, person: Person?, myLat: Double?, myLon: Double?, onComing: () -> Unit, onWay: () -> Unit, onHowTo: () -> Unit, onClose: () -> Unit,
    /** Names of people who already said they're coming. */
    coming: List<String> = emptyList(),
    /** The sender's standing, to help judge whether the SOS is genuine. */
    trust: com.bluemob.app.trust.TrustScore? = null,
    onProfile: () -> Unit = {},
) {
    val distance = if (myLat != null && myLon != null && sos.lat != null && sos.lon != null) {
        val me = com.bluemob.app.contacts.GeoPoint(myLat, myLon, 0f, 0)
        val them = com.bluemob.app.contacts.GeoPoint(sos.lat, sos.lon, 0f, 0)
        Geo.formatDistance(Geo.distanceM(me, them)) + " away to the " + cardinal(Geo.bearingDeg(me, them))
    } else null
    val preview = sos.id == com.bluemob.app.sos.SosManager.PREVIEW_ID
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(Extra.rose).statusBarsPadding().padding(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            com.bluemob.app.ui.components.PulsingDot(Color.White, 10.dp)
            Spacer(Modifier.width(10.dp))
            Text((if (preview) "PREVIEW · NOTHING WAS SENT" else "SOS RECEIVED · " + clock.format(Date(sos.at))), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(person?.avatar, sos.name, sos.fromNodeId, 84.dp)
            Text("${sos.name}${if (person?.sharesName == true) " " + shortId(sos.fromNodeId) else ""} needs help", style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Text(listOfNotNull(distance, sos.battery?.let { "their battery $it%" }, if (sos.hops > 1) "passed on by ${sos.hops - 1} phone${if (sos.hops > 2) "s" else ""}" else "direct").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            if (sos.bloodGroup != null || sos.age != null) {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sos.bloodGroup?.let { MedicalTag("🩸 Blood group", it) }
                    sos.age?.let { MedicalTag("Age", it.toString()) }
                }
            }
            if (sos.note.isNotBlank()) {
                Text("“${sos.note}”", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp).clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(16.dp))
            }
            sos.pos?.let { pos ->
                Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.skyTint).padding(14.dp)) {
                    Text(if (pos.gps) "WHERE THEY ARE · GPS" else "WHERE THEY ARE · ESTIMATE", style = MaterialTheme.typography.labelSmall, color = Extra.sky)
                    Text(pos.describe(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            trust?.let { t ->
                Row(Modifier.padding(top = 10.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onProfile).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.bluemob.app.ui.components.StarRow(t.stars, 16.dp)
                    Text(if (t.isNew) "  New on BlueMob" else "  ${t.label} · ${t.ratings} rating${if (t.ratings == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = Extra.ink2)
                }
                t.sosWarning?.let { w ->
                    Text("⚠ $w. Still go if you can: check from a safe distance.", style = MaterialTheme.typography.bodySmall, color = Extra.rose, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp).clip(MaterialTheme.shapes.small).background(Extra.emberTint).padding(10.dp))
                }
            }
            if (coming.isNotEmpty()) Text("Already coming: ${coming.joinToString(", ")}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
            Text(if (preview) "This is how an SOS from someone nearby looks. Real ones also sound a short alarm." else "Your phone is already passing it on to everyone it can reach.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 12.dp))
        }
        Column(Modifier.navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onComing, colors = ButtonDefaults.buttonColors(containerColor = Extra.rose, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("I'm coming", style = MaterialTheme.typography.titleMedium)
                    Text("Join the rescue group: directions and chat", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
            if (sos.lat != null) OutlinedButton(onClick = onWay, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Show me the way") }
            OutlinedButton(onClick = onHowTo, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("How to help") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}


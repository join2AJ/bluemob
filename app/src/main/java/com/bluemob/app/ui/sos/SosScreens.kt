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
import androidx.compose.material.icons.outlined.MenuBook
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

@Composable
fun SosHubScreen(
    mine: SosSignal?,
    connected: Int,
    defaultSignal: SignalMode,
    onBack: () -> Unit,
    onSend: (String) -> Int,
    onSafe: () -> Unit,
    onSignal: () -> Unit,
    onDefaultSignal: (SignalMode) -> Unit,
    onHowToHelp: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    var armed by remember { mutableStateOf(false) }
    var reached by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    SubScreen("SOS", onBack) {
        if (mine == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val t = rememberInfiniteTransition(label = "armed")
                    val glow by t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "g")
                    Box(
                        Modifier.size(184.dp).scale(if (armed) glow else 1f).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFFF0727A), Extra.rose, Color(0xFFA8323A))))
                            .clickable {
                                if (!armed) {
                                    armed = true
                                    scope.launch { delay(4000); armed = false }
                                } else {
                                    armed = false
                                    reached = onSend(note)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SOS", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.displaySmall)
                            Text(if (armed) "Tap again to send" else "Tap to send", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("Reaches everyone nearby right away ($connected connected now). Every phone that hears it passes it on.",
                        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp))
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
        } else {
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Tag("SOS active · " + clock.format(Date(mine.at)), Extra.rose, Color.White)
                    Text("Help is being called", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        (if (reached >= 0) "Reached $reached phone${if (reached == 1) "" else "s"} straight away. " else "") +
                            "It's re-sent to every phone that comes into range, and each one passes it on, until you tap \"I'm safe\"." +
                            if (mine.note.isNotBlank()) "\n\nYour note: ${mine.note}" else "",
                        style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = { onSafe(); reached = -1; note = "" }, modifier = Modifier.padding(top = 16.dp)) { Text("I'm safe now") }
                }
            }
        }
        item { GroupLabel("More") }
        item {
            Group {
                SettingRow(Icons.Outlined.FlashlightOn, Extra.rose, "SOS signal", "Screen, flashlight, sound or all · ··· ––– ···", onClick = onSignal)
                SettingRow(Icons.Outlined.Bolt, Extra.ember, "Default signal", "Used when you open the SOS signal", divider = true)
                LazyRow(Modifier.padding(start = 62.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SignalMode.entries) { m -> Chip("${m.emoji} ${m.label}", m == defaultSignal) { onDefaultSignal(m) } }
                }
                SettingRow(Icons.Outlined.MenuBook, MaterialTheme.colorScheme.primary, "If you receive an SOS", "How to help someone safely", divider = true, onClick = onHowToHelp)
            }
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

    // Keep the screen on and as bright as it goes while signalling.
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = activity?.window
        val before = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose {
            view.keepScreenOn = false
            window?.attributes = window?.attributes?.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            signals.stop()
        }
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

/** Opens over everything when someone nearby sends an SOS. */
@Composable
fun SosAlert(sos: SosSignal, person: Person?, myLat: Double?, myLon: Double?, onComing: () -> Unit, onWay: () -> Unit, onHowTo: () -> Unit, onClose: () -> Unit) {
    val distance = if (myLat != null && myLon != null && sos.lat != null && sos.lon != null) {
        val me = com.bluemob.app.contacts.GeoPoint(myLat, myLon, 0f, 0)
        val them = com.bluemob.app.contacts.GeoPoint(sos.lat, sos.lon, 0f, 0)
        Geo.formatDistance(Geo.distanceM(me, them)) + " away to the " + cardinal(Geo.bearingDeg(me, them))
    } else null
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(Extra.rose).statusBarsPadding().padding(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            com.bluemob.app.ui.components.PulsingDot(Color.White, 10.dp)
            Spacer(Modifier.width(10.dp))
            Text("SOS RECEIVED · " + clock.format(Date(sos.at)), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(person?.avatar, sos.name, sos.fromNodeId, 84.dp)
            Text("${sos.name}${if (person?.sharesName == true) " " + shortId(sos.fromNodeId) else ""} needs help", style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Text(listOfNotNull(distance, sos.battery?.let { "their battery $it%" }, if (sos.hops > 1) "passed on by ${sos.hops - 1} phone${if (sos.hops > 2) "s" else ""}" else "direct").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            if (sos.note.isNotBlank()) {
                Text("“${sos.note}”", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp).clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(16.dp))
            }
            Text("Your phone is already passing it on to everyone it can reach.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 12.dp))
        }
        Column(Modifier.navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onComing, colors = ButtonDefaults.buttonColors(containerColor = Extra.rose, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("I'm coming", style = MaterialTheme.typography.titleMedium) }
            if (sos.lat != null) OutlinedButton(onClick = onWay, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Show me the way") }
            OutlinedButton(onClick = onHowTo, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("How to help") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}


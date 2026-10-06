package com.bluemob.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.account.PinPolicy
import com.bluemob.app.account.PinResult
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Space
import kotlinx.coroutines.delay

/** Dots for the digits typed so far, and a number pad. */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: String?,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    extraKey: (@Composable () -> Unit)? = null,
    resetKey: Any = Unit,
) {
    var pin by remember(resetKey) { mutableStateOf("") }
    LaunchedEffect(error) { if (error != null) pin = "" }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp))
        Row(Modifier.padding(top = 22.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(length) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape)
                    .background(if (i < pin.length) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.5.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
        Text(error ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp).height(36.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("x", "0", "<"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { k ->
                        when (k) {
                            "x" -> Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { extraKey?.invoke() }
                            "<" -> Key("⌫", "Delete") { pin = pin.dropLast(1) }
                            else -> Key(k, k) {
                                if (pin.length < length) {
                                    pin += k
                                    if (pin.length == length) { val done = pin; onDone(done) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}

/** Choose a 6-digit PIN, then type it again. */
@Composable
fun CreatePin(onPin: (String) -> Unit, title: String = "Create a PIN") {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    PinPad(
        title = if (first == null) title else "Type it again",
        subtitle = if (first == null) "6 digits. You'll use it to open BlueMob on this phone. It never leaves your phone." else "To be sure it's right.",
        error = error, resetKey = round,
        onDone = { pin ->
            when {
                first == null && PinPolicy.tooSimple(pin) -> { error = "Too easy to guess. Avoid repeats and runs like 123456."; round++ }
                first == null -> { first = pin; error = null; round++ }
                pin == first -> onPin(pin)
                else -> { first = null; error = "The two PINs didn't match. Start again."; round++ }
            }
        },
    )
}

/** The recovery code, big and readable, with a copy button and a confirmation that it's written down. */
@Composable
fun RecoveryCodeCard(code: String, onSaved: (() -> Unit)?) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var wrote by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Group {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("YOUR RECOVERY CODE", style = MaterialTheme.typography.labelMedium, color = Extra.ink2, letterSpacing = 1.2.sp)
                Text(code.replace("-", " "), fontFamily = FontFamily.Monospace, fontSize = if (code.length > 40) 17.sp else 21.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, lineHeight = 30.sp, modifier = Modifier.padding(vertical = 12.dp))
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }) { Text(if (copied) "Copied ✓" else "Copy") }
            }
        }
        Text(
            "Write it on paper and keep it somewhere safe, like your wallet or with family. It's the only way to get your BlueMob ID back " +
                "on a new phone. Anyone with this code can use your ID, so never share it or send it in a message.",
            style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 14.dp),
        )
        if (onSaved != null) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { wrote = !wrote }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = wrote, onCheckedChange = { wrote = it })
                Text("I've written it down", style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = onSaved, enabled = wrote, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Done") }
        }
    }
}

/**
 * Sign-up, after choosing a name: create a PIN, choose fingerprint unlock, and save the recovery code.
 * Also shown once to people who used BlueMob before accounts existed.
 */
@Composable
fun AccountSetup(
    upgrading: Boolean,
    canUseBiometric: Boolean,
    /** The PIN is already set (the app closed during sign-up): go straight to the recovery code. */
    startAtCode: Boolean,
    recoveryCode: () -> String,
    onPin: (String) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(if (startAtCode) 2 else 0) }
    Box(Modifier.fillMaxSize().background(Gradients.dawn())) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg, vertical = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (upgrading) "Secure your account" else "Create your account", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("Step ${if (step == 2 && !canUseBiometric) 2 else step + 1} of ${if (canUseBiometric) 3 else 2}", style = MaterialTheme.typography.labelMedium, color = Extra.ink2, modifier = Modifier.padding(bottom = 20.dp))
            when (step) {
                0 -> {
                    if (upgrading) Text("BlueMob now has a login. Your ID, chats and contacts stay as they are.",
                        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))
                    CreatePin(onPin = { onPin(it); step = if (canUseBiometric) 1 else 2 })
                }
                1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👆", fontSize = 56.sp)
                    Text("Unlock with fingerprint or face?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    Text("Faster than your PIN. Your PIN still works, and is needed to see your recovery code.",
                        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { onBiometric(true); step = 2 }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Use fingerprint or face") }
                    TextButton(onClick = { onBiometric(false); step = 2 }) { Text("Just the PIN") }
                }
                else -> Column {
                    Text("Save your recovery code", style = MaterialTheme.typography.headlineSmall)
                    Text("If you lose this phone, this code brings your BlueMob ID to a new one, so people can still reach you.",
                        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                    val code = remember { recoveryCode() }
                    RecoveryCodeCard(code, onSaved = onDone)
                }
            }
        }
    }
}

/**
 * Log in: PIN or fingerprint. SOS works without logging in: hold the button for a second.
 * The mesh keeps running behind this screen, so messages and SOS from others still arrive.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LockScreen(
    name: String,
    avatar: String,
    shortId: String,
    biometric: Boolean,
    onPin: (String) -> PinResult,
    onBiometric: () -> Unit,
    onSos: () -> Int,
    sosActive: Boolean,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var waitLeft by remember { mutableStateOf(0L) }
    var sosNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (biometric) onBiometric() }
    LaunchedEffect(waitLeft) { if (waitLeft > 0) { delay(1_000); waitLeft -= 1; error = if (waitLeft > 0) "Too many wrong PINs. Try again in ${waitLeft}s." else null } }
    Box(Modifier.fillMaxSize().background(Gradients.dawn()).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(avatar, fontSize = 44.sp)
            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
            Text("BM $shortId", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(18.dp))
            PinPad(
                title = "Enter your PIN", subtitle = "BlueMob is still connecting to people nearby in the background.",
                error = error, length = 6,
                onDone = { pin ->
                    if (waitLeft > 0) return@PinPad
                    when (val r = onPin(pin)) {
                        PinResult.Ok -> error = null
                        is PinResult.Wrong -> error = if (r.triesBeforeWait > 1) "Wrong PIN. ${r.triesBeforeWait} tries left before a short wait." else "Wrong PIN. One more try before a short wait."
                        is PinResult.Wait -> { waitLeft = r.seconds; error = "Too many wrong PINs. Try again in ${r.seconds}s." }
                    }
                },
                extraKey = if (biometric) ({ TextButton(onClick = onBiometric) { Text("👆", fontSize = 26.sp) } }) else null,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFD94F55))
                    .combinedClickable(onClick = { sosNote = "Hold the button for a second to send SOS." }, onLongClick = {
                        val n = onSos()
                        sosNote = if (sosActive) "SOS is already on. Unlock to end it." else "SOS sent to $n phone${if (n == 1) "" else "s"} nearby. It keeps going out to everyone you meet."
                    })
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (sosActive) "SOS is on" else "Hold for emergency SOS", color = Color.White, style = MaterialTheme.typography.titleMedium) }
            sosNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)) }
        }
    }
}

/** On a new phone: type the recovery code to get your BlueMob ID back. */
@Composable
fun RestoreScreen(onBack: () -> Unit, onRestore: (String) -> String?) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Gradients.dawn())) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(Space.lg)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Restore your BlueMob ID", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text("Type the recovery code you wrote down when you created your account. Spaces and dashes don't matter.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(80); error = null }, label = { Text("Recovery code") },
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                isError = error != null, supportingText = { error?.let { Text(it) } }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { confirm = !confirm }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = confirm, onCheckedChange = { confirm = it })
                Text("I understand this replaces the ID on this phone. Use the code on one phone at a time.", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { error = onRestore(code) }, enabled = confirm && code.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Restore and restart BlueMob")
            }
            Text("Your chats stay on your old phone: they're encrypted there and never uploaded. Messages still waiting for you will arrive here.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** Account & login settings: PIN, fingerprint, when to lock, and the recovery code. */
@Composable
fun AccountScreen(
    shortId: String,
    biometric: Boolean,
    canUseBiometric: Boolean,
    lockAfterMs: Long,
    recoverySaved: Boolean,
    onBack: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onLockAfter: (Long) -> Unit,
    onLockNow: () -> Unit,
    checkPin: (String) -> PinResult,
    onNewPin: (String) -> Unit,
    recoveryCode: () -> String,
    onRecoverySaved: () -> Unit,
) {
    // What the PIN pad is unlocking: null, "code" (show recovery code) or "change" (change PIN).
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    var showCode by rememberSaveable { mutableStateOf(false) }
    var changing by rememberSaveable { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    SubScreen("Account & login", onBack) {
        when {
            asking != null -> item {
                PinPad("Enter your current PIN", if (asking == "code") "To show your recovery code." else "To change your PIN.", pinError,
                    resetKey = round, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    onDone = { pin ->
                        when (val r = checkPin(pin)) {
                            PinResult.Ok -> { if (asking == "code") showCode = true else changing = true; asking = null; pinError = null }
                            is PinResult.Wrong -> { pinError = "Wrong PIN. ${r.triesBeforeWait} tries left."; round++ }
                            is PinResult.Wait -> { pinError = "Too many wrong PINs. Try again in ${r.seconds}s."; round++ }
                        }
                    })
            }
            changing -> item {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    CreatePin(title = "Choose a new PIN", onPin = { onNewPin(it); changing = false; note = "PIN changed." })
                }
            }
            showCode -> item {
                Column(Modifier.padding(top = 12.dp)) {
                    RecoveryCodeCard(recoveryCode(), onSaved = { onRecoverySaved(); showCode = false })
                }
            }
            else -> {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("Your account is your BlueMob ID, BM $shortId. It lives on this phone, not on a server, so it works with no internet.",
                            style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                        note?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                item { GroupLabel("Login") }
                item {
                    Group {
                        Row(Modifier.fillMaxWidth().clickable { asking = "change"; round++ }.padding(16.dp)) {
                            Text("Change PIN", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("›", color = Extra.ink3)
                        }
                        if (canUseBiometric) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Unlock with fingerprint or face", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = biometric, onCheckedChange = onBiometric)
                        }
                        Row(Modifier.fillMaxWidth().clickable(onClick = onLockNow).padding(16.dp)) {
                            Text("Lock now", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
                item { GroupLabel("Ask for PIN after leaving BlueMob") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0L to "Right away", 60_000L to "1 min", 5 * 60_000L to "5 min", 30 * 60_000L to "30 min").forEach { (ms, label) ->
                            Chip(label, lockAfterMs == ms) { onLockAfter(ms) }
                        }
                    }
                }
                item { GroupLabel("Recovery") }
                item {
                    Group {
                        Row(Modifier.fillMaxWidth().clickable { asking = "code"; round++ }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Show recovery code", style = MaterialTheme.typography.bodyLarge)
                                Text(if (recoverySaved) "You saved it. Check it's still somewhere safe." else "Not saved yet. Do this now: it's the only way to move your ID to a new phone.",
                                    style = MaterialTheme.typography.bodySmall, color = if (recoverySaved) Extra.ink2 else MaterialTheme.colorScheme.error)
                            }
                            Text("›", color = Extra.ink3)
                        }
                    }
                }
                item {
                    Text("Forgot your PIN? Reinstall BlueMob and choose \"I already have a BlueMob ID\" with your recovery code. " +
                        "Without the code, a forgotten PIN can't be reset: that's what keeps your account safe if the phone is stolen.",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

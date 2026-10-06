package com.bluemob.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.account.BloodGroups
import com.bluemob.app.account.Otp
import com.bluemob.app.account.PhoneNumbers
import com.bluemob.app.identity.Identity
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.onboarding.AvatarPicker
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Space
import kotlinx.coroutines.delay

/** What sign-up collects. */
data class SignupResult(val phone: String, val name: String, val avatar: String, val age: Int?, val bloodGroup: String?)

/**
 * Sign up: mobile number → one-time code → name, age and blood group. People who used BlueMob before sign-up
 * existed ([upgrading]) go through the same steps once, with their name filled in.
 */
@Composable
fun SignupFlow(
    upgrading: Boolean,
    initialName: String,
    initialAvatar: String,
    bluemobId: String,
    onRestore: (() -> Unit)?,
    onDone: (SignupResult) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var cc by rememberSaveable { mutableStateOf("+91") }
    var number by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Gradients.dawn())) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg, vertical = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("← Back") }
                Spacer(Modifier.weight(1f))
                Text("Step ${step + 1} of 3", style = MaterialTheme.typography.labelMedium, color = Extra.ink2)
            }
            StepBar(step)
            Spacer(Modifier.height(20.dp))
            when (step) {
                0 -> PhoneStep(upgrading, cc, number, onCc = { cc = it }, onNumber = { number = it }, onRestore = onRestore) {
                    phone = PhoneNumbers.e164(cc, number) ?: return@PhoneStep
                    step = 1
                }
                1 -> OtpStep(phone) { step = 2 }
                else -> DetailsStep(upgrading, initialName, initialAvatar, bluemobId) { name, avatar, age, blood ->
                    onDone(SignupResult(phone, name, avatar, age, blood))
                }
            }
        }
    }
}

@Composable
private fun StepBar(step: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(if (i <= step) MaterialTheme.colorScheme.primary else Extra.line))
        }
    }
}

@Composable
private fun PhoneStep(upgrading: Boolean, cc: String, number: String, onCc: (String) -> Unit, onNumber: (String) -> Unit, onRestore: (() -> Unit)?, onNext: () -> Unit) {
    var tried by remember { mutableStateOf(false) }
    val problem = PhoneNumbers.problem(cc, number)
    Text(if (upgrading) "Verify your mobile number" else "Sign up with your mobile number", style = MaterialTheme.typography.headlineSmall)
    Text(
        if (upgrading) "BlueMob now links your ID to your number. Your ID, chats and contacts stay as they are."
        else "We'll send a 6-digit code to check it's yours. Your number stays private: people reach you by your BlueMob ID.",
        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
    )
    Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = cc, onValueChange = { v -> onCc("+" + PhoneNumbers.digits(v).take(3)) }, label = { Text("Code") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.width(92.dp),
        )
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = number, onValueChange = { onNumber(it.filter { c -> c.isDigit() || c == ' ' }.take(16)) },
            label = { Text("Mobile number") }, singleLine = true, isError = tried && problem != null,
            supportingText = { if (tried && problem != null) Text(problem) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
    }
    Button(onClick = { tried = true; if (problem == null) onNext() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Send code") }
    TestModeNote()
    if (onRestore != null) TextButton(onClick = onRestore, modifier = Modifier.padding(top = 8.dp)) { Text("I already have a BlueMob ID (recovery code)") }
}

@Composable
private fun TestModeNote() {
    Surface(shape = RoundedCornerShape(12.dp), color = Extra.sand2, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Test build: no SMS is sent. Use any mobile number, and the code ${Otp.TEST_CODE}.",
            style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun OtpStep(phone: String, onVerified: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(Otp.RESEND_AFTER_S) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(resendIn) { if (resendIn > 0) { delay(1_000); resendIn-- } }
    Text("Enter the code", style = MaterialTheme.typography.headlineSmall)
    Text("Sent by SMS to ${PhoneNumbers.pretty(phone)}", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
    BasicTextField(
        value = code,
        onValueChange = { v ->
            code = v.filter { it.isDigit() }.take(Otp.LENGTH); error = null
            if (code.length == Otp.LENGTH) { if (Otp.check(code)) onVerified() else error = "That code isn't right. Check the SMS and try again." }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(fontSize = 1.sp),
        modifier = Modifier.focusRequester(focus).fillMaxWidth(),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(Otp.LENGTH) { i ->
                    val c = code.getOrNull(i)?.toString() ?: ""
                    val active = i == code.length
                    Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(if (active) 2.dp else 1.dp, if (error != null) MaterialTheme.colorScheme.error else if (active) MaterialTheme.colorScheme.primary else Extra.line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(c, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (resendIn > 0) Text("Resend code in ${resendIn}s", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
        else TextButton(onClick = { resendIn = Otp.RESEND_AFTER_S; code = ""; error = null }) { Text("Resend code") }
    }
    TestModeNote()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(upgrading: Boolean, initialName: String, initialAvatar: String, bluemobId: String, onDone: (String, String, Int?, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var avatar by rememberSaveable { mutableStateOf(initialAvatar) }
    var age by rememberSaveable { mutableStateOf("") }
    var blood by rememberSaveable { mutableStateOf<String?>(null) }
    val ageNum = age.toIntOrNull()
    val ageOk = age.isEmpty() || ageNum in 1..120
    Text("About you", style = MaterialTheme.typography.headlineSmall)
    Text("People nearby see your name. Your age and blood group are shared only when you send an SOS, so helpers know.",
        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Gradients.horizon()), contentAlignment = Alignment.Center) { Text(avatar, fontSize = 32.sp) }
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(Identity.MAX_NAME_LENGTH) }, label = { Text("Your name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    AvatarPicker(selected = avatar, onSelect = { avatar = it })
    OutlinedTextField(
        value = age, onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Age") }, singleLine = true,
        isError = !ageOk, supportingText = { if (!ageOk) Text("Between 1 and 120") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Text("Blood group", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (BloodGroups.ALL + BloodGroups.UNKNOWN).forEach { g -> Chip(if (g == BloodGroups.UNKNOWN) g else "🩸 $g", blood == g) { blood = g } }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("YOUR BLUEMOB ID", style = MaterialTheme.typography.labelSmall, color = Extra.ink2, letterSpacing = 1.sp)
            Text("BM $bluemobId", fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Text("Linked to your number. Give it to anyone: they can message you with it, even with no signal.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
        }
    }
    Button(
        onClick = { onDone(name.trim(), avatar, ageNum, blood?.takeIf { it != BloodGroups.UNKNOWN }) },
        enabled = name.isNotBlank() && ageOk && blood != null,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
    ) { Text(if (upgrading) "Save and continue" else "Create my account") }
    if (blood == null) Text("Pick your blood group, or \"${BloodGroups.UNKNOWN}\".", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

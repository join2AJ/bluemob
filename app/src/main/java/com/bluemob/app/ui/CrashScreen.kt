package com.bluemob.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.ui.theme.Extra

/**
 * Shown instead of closing: when BlueMob couldn't start ([fatal]), or after it crashed last time.
 * The error can be copied or shared, so it can be fixed.
 */
@Composable
fun CrashScreen(fatal: Boolean, report: String, onShare: () -> Unit, onContinue: () -> Unit, onRetry: () -> Unit, onReset: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (fatal) "BlueMob couldn't start" else "BlueMob closed unexpectedly last time", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (fatal) "Something went wrong while opening your data. Nothing has been deleted. Please copy or share the error below and send it to the BlueMob team, then try again."
            else "Sorry about that. The error is below: please copy or share it with the BlueMob team so it can be fixed. You can carry on using BlueMob.",
            style = MaterialTheme.typography.bodyLarge, color = Extra.ink2,
        )
        Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(MaterialTheme.shapes.medium).background(Extra.sand)
                .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { clipboard.setText(AnnotatedString(report)); copied = true }) { Text(if (copied) "Copied ✓" else "Copy error") }
            OutlinedButton(onClick = onShare) { Text("Share error") }
        }
        if (fatal) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            if (!confirmReset) TextButton(onClick = { confirmReset = true }) { Text("Still not working? Reset BlueMob…", color = Extra.rose) }
            else {
                Text("Reset deletes everything BlueMob stored on this phone: messages, contacts, keys, trail and audit trail. You'll get a new BlueMob ID. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.rose)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = Extra.rose, contentColor = Color.White)) { Text("Delete everything and reset") }
                    OutlinedButton(onClick = { confirmReset = false }) { Text("Cancel") }
                }
            }
        } else {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue to BlueMob") }
        }
    }
}

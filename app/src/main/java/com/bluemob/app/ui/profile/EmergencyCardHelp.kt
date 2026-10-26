package com.bluemob.app.ui.profile

import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bluemob.app.i18n.Text
import com.bluemob.app.service.Notifier
import com.bluemob.app.ui.theme.Extra

/**
 * Under the emergency card switch: whether Android will actually show it on the lock screen, how to fix it, and a
 * second way that always works: Android's own emergency information, which every lock screen opens.
 */
@Composable
fun EmergencyCardHelp(cardText: () -> String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ok by remember { mutableStateOf(cardAllowed(context)) }
    val owner = LocalLifecycleOwner.current
    // Check again when coming back from Android's settings.
    LaunchedEffect(Unit) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { ok = cardAllowed(context) } }
    var note by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Extra.sand)) {
        // Is Android going to show it? One line, one button.
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) MaterialTheme.colorScheme.primary else Extra.rose))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (ok) "Allowed on the lock screen" else "Hidden by Android", style = MaterialTheme.typography.labelLarge,
                    color = if (ok) MaterialTheme.colorScheme.primary else Extra.rose)
                Text(if (ok) "Not showing? Set lock screen to show all notifications" else "Notifications for BlueMob are off",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
            TextButton(onClick = { openCardSettings(context) }) { Text(if (ok) "Settings" else "Fix") }
        }
        HorizontalDivider(color = Extra.line)
        // A second way that every lock screen has: Android's own emergency information.
        Row(Modifier.fillMaxWidth().clickable { note = addToAndroidEmergencyInfo(context, cardText()) }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("🩺", modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text("Also add to Android's emergency info", style = MaterialTheme.typography.labelLarge)
                Text("Opens from the Emergency button on any lock screen", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
            Text("›", color = Extra.ink3)
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) }
    }
}

/** Notifications on for BlueMob, and the card's channel not turned off or silenced below the lock screen. */
fun cardAllowed(context: Context): Boolean = runCatching { allowed(context) }.getOrDefault(true)

private fun allowed(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val ch = NotificationManagerCompat.from(context).getNotificationChannel(Notifier.CH_CARD) ?: return true
    return ch.importance >= android.app.NotificationManager.IMPORTANCE_DEFAULT &&
        ch.lockscreenVisibility != android.app.Notification.VISIBILITY_SECRET
}

private fun openCardSettings(context: Context) {
    val channel = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CH_CARD)
    val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(if (NotificationManagerCompat.from(context).areNotificationsEnabled()) channel else app) }
        .onFailure { runCatching { context.startActivity(app) } }
}

/**
 * Copies the card's text and opens Android's emergency information (Safety & emergency → Medical information), which
 * anyone can open from the lock screen's Emergency button. Phones name that screen differently, so a few are tried.
 */
private fun addToAndroidEmergencyInfo(context: Context, text: String): String {
    val clip = context.getSystemService(android.content.ClipboardManager::class.java)
    clip?.setPrimaryClip(android.content.ClipData.newPlainText("Emergency info", text))
    val tries = listOf(
        Intent("android.settings.EDIT_EMERGENCY_INFO"),
        Intent("android.telephony.action.EMERGENCY_ASSISTANCE"),
        Intent().setClassName("com.android.emergency", "com.android.emergency.edit.EditInfoActivity"),
        Intent().setClassName("com.google.android.apps.safetyhub", "com.google.android.apps.safetyhub.emergencyinfo.EmergencyInfoActivity"),
        Intent(Settings.ACTION_SETTINGS),
    )
    val opened = tries.firstOrNull { i -> runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) }
    return if (opened?.action == Settings.ACTION_SETTINGS) "Copied. In Settings, open Safety & emergency → Medical information, and paste it into the notes."
    else "Copied. Paste it into the medical notes, and add your emergency contacts there too."
}

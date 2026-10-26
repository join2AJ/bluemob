package com.bluemob.app.ui.profile

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (ok) "✓ Android allows the card. If it still doesn't show when locked: Settings → Notifications → Notifications on lock screen → Show all."
            else "⚠ Android is hiding BlueMob's notifications, so the card can't show. Tap Fix.",
            style = MaterialTheme.typography.bodySmall, color = if (ok) Extra.ink2 else Extra.rose)
        OutlinedButton(onClick = { openCardSettings(context) }, Modifier.fillMaxWidth()) { Text(if (ok) "Lock-screen settings" else "Fix") }
        OutlinedButton(onClick = { note = addToAndroidEmergencyInfo(context, cardText()) }, Modifier.fillMaxWidth()) { Text("Add to Android's emergency info") }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
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

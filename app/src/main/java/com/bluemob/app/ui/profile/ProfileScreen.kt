package com.bluemob.app.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.identity.Identity
import com.bluemob.app.mesh.LogLine
import com.bluemob.app.ui.onboarding.AvatarPicker
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    name: String,
    avatar: String,
    nodeId: String,
    running: Boolean,
    sharingLocation: Boolean,
    log: List<LogLine>,
    contentPadding: PaddingValues,
    onName: (String) -> Unit,
    onAvatar: (String) -> Unit,
    onToggleMesh: (Boolean) -> Unit,
    onToggleLocation: (Boolean) -> Unit,
    onReplayIntro: () -> Unit,
    onForgetPeople: () -> Unit,
) {
    var draft by remember(name) { mutableStateOf(name) }
    var showLog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.lg, end = Space.lg,
            top = contentPadding.calculateTopPadding() + Space.lg,
            bottom = contentPadding.calculateBottomPadding() + Space.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(104.dp).clip(CircleShape).background(Gradients.horizon()),
                    contentAlignment = Alignment.Center,
                ) { Text(avatar, fontSize = 52.sp) }
                Spacer(Modifier.size(Space.md))
                Text(name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "ID ${nodeId.chunked(4).joinToString(" ")}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Section("Your profile") {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it.take(Identity.MAX_NAME_LENGTH)
                            if (draft.isNotBlank()) onName(draft)
                        },
                        label = { Text("Name") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AvatarPicker(selected = avatar, onSelect = onAvatar)
                }
            }
        }
        item {
            Section("Connections") {
                ToggleRow(Icons.Filled.Wifi, "Mesh", "Find and be found by nearby phones", running, onToggleMesh)
                HorizontalDivider(Modifier.padding(horizontal = Space.lg))
                ToggleRow(
                    Icons.Filled.LocationOn, "Share my location",
                    "Connected people see how far you are. Uses GPS, no internet.",
                    sharingLocation, onToggleLocation,
                )
            }
        }
        item {
            Section("App") {
                ListItem(
                    headlineContent = { Text("Replay the intro") },
                    leadingContent = { Icon(Icons.Filled.Refresh, null) },
                    trailingContent = { TextButton(onClick = onReplayIntro) { Text("Show") } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
                HorizontalDivider(Modifier.padding(horizontal = Space.lg))
                ListItem(
                    headlineContent = { Text("Forget people I've met") },
                    supportingContent = { Text("Clears the list and last-seen history") },
                    leadingContent = { Icon(Icons.Outlined.DeleteOutline, null) },
                    trailingContent = { TextButton(onClick = onForgetPeople) { Text("Clear") } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
        item {
            Section("For testers") {
                ListItem(
                    headlineContent = { Text("Mesh activity log") },
                    supportingContent = { Text("Useful to screenshot if something doesn't connect") },
                    trailingContent = {
                        Icon(if (showLog) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    },
                    modifier = Modifier.clickable { showLog = !showLog },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
                AnimatedVisibility(showLog) {
                    Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                        if (log.isEmpty()) Text("Nothing yet", style = MaterialTheme.typography.bodySmall)
                        log.take(80).forEach { LogRow(it) }
                    }
                }
            }
        }
        item {
            Text(
                "BlueMob 0.2 · made for the open sky 🌍",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Space.xs))
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) { content() }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val logTime = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
private fun LogRow(line: LogLine) {
    Text(
        "${logTime.format(Date(line.timeMillis))}  ${line.text}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

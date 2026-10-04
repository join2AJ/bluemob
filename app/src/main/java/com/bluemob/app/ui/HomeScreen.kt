package com.bluemob.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.identity.Identity
import com.bluemob.app.mesh.LogLine
import com.bluemob.app.mesh.Peer
import com.bluemob.app.mesh.PeerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PermissionStatus(val granted: Boolean, val locationOff: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MeshViewModel,
    permissions: PermissionStatus,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val running by viewModel.running.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("BlueMob") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!permissions.granted) {
                item { PermissionCard(onRequestPermissions, onOpenAppSettings) }
            }
            if (permissions.locationOff) {
                item {
                    WarningCard(
                        "Turn on Location",
                        "Android 12 and older need the Location switch on to find nearby phones. " +
                            "No GPS signal or internet is used.",
                        "Open settings", onOpenLocationSettings,
                    )
                }
            }
            item {
                IdentityCard(
                    name = name,
                    nodeId = viewModel.nodeId,
                    editable = !running,
                    onNameChange = viewModel::setName,
                )
            }
            item {
                MeshSwitchCard(
                    running = running,
                    enabled = permissions.granted,
                    onToggle = { if (it) viewModel.start() else viewModel.stop() },
                )
            }
            item {
                Text("Nearby phones (${peers.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (peers.isEmpty()) {
                item {
                    Text(
                        if (running) "Searching… Open BlueMob on another phone nearby."
                        else "Turn on the mesh to look for nearby phones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(peers.values.sortedBy { it.name }, key = { it.endpointId }) { peer ->
                PeerRow(
                    peer = peer,
                    onConnect = { viewModel.connect(peer.endpointId) },
                    onPing = { viewModel.ping(peer.endpointId) },
                    onDisconnect = { viewModel.disconnect(peer.endpointId) },
                )
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))
                Text("Activity log", style = MaterialTheme.typography.titleMedium)
            }
            items(log, key = { "${it.timeMillis}-${it.text}-${it.hashCode()}" }) { LogRow(it) }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permissions needed", style = MaterialTheme.typography.titleMedium)
            Text(
                "BlueMob uses Bluetooth and Wi-Fi to talk directly to nearby phones, " +
                    "without cell towers or internet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequest) { Text("Grant") }
                TextButton(onClick = onOpenSettings) { Text("App settings") }
            }
        }
    }
}

@Composable
private fun WarningCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun IdentityCard(name: String, nodeId: String, editable: Boolean, onNameChange: (String) -> Unit) {
    var draft by remember(name) { mutableStateOf(name) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it.take(Identity.MAX_NAME_LENGTH)
                    if (draft.isNotBlank()) onNameChange(draft)
                },
                label = { Text("Your name") },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Device ID: $nodeId",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeshSwitchCard(running: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mesh", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (running) "On: visible to nearby phones" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = running, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, onConnect: () -> Unit, onPing: () -> Unit, onDisconnect: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (peer.state) {
                    PeerState.CONNECTED -> Icons.Filled.BluetoothConnected
                    PeerState.CONNECTING -> Icons.Filled.BluetoothSearching
                    PeerState.DISCOVERED -> Icons.Filled.Bluetooth
                },
                contentDescription = null,
                tint = if (peer.state == PeerState.CONNECTED) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (peer.state) {
                        PeerState.CONNECTED -> "Connected"
                        PeerState.CONNECTING -> "Connecting…"
                        PeerState.DISCOVERED -> "In range"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (peer.state) {
                PeerState.DISCOVERED -> Button(onClick = onConnect) { Text("Connect") }
                PeerState.CONNECTING -> Unit
                PeerState.CONNECTED -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onPing) { Text("Ping") }
                    OutlinedButton(onClick = onDisconnect) { Text("Drop") }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
private fun LogRow(line: LogLine) {
    Text(
        "${timeFormat.format(Date(line.timeMillis))}  ${line.text}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

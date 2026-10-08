package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.bluemob.app.chat.ChatGroup
import com.bluemob.app.chat.Rich
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra

/** Pick a name and the people: a group chat that works like every other chat, with or without signal. */
@Composable
fun NewGroupScreen(people: List<Person>, onBack: () -> Unit, onCreate: (String, Map<String, String>) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    SubScreen("New group", onBack) {
        item {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Group name") }, placeholder = { Text("e.g. Family, Trek team") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        item { GroupLabel("Who's in it · ${picked.size} picked") }
        if (people.isEmpty()) item {
            Text("People you've met or messaged appear here. Meet someone nearby, or message them by BlueMob ID first.", color = Extra.ink2, modifier = Modifier.padding(8.dp))
        }
        item {
            Group {
                people.forEach { p ->
                    val on = p.nodeId in picked
                    Row(Modifier.fillMaxWidth().clickable(enabled = on || picked.size < Rich.MAX_MEMBERS - 1) { picked = if (on) picked - p.nodeId else picked + p.nodeId }
                        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.avatar, p.name, p.nodeId, 40.dp, p.presence)
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Checkbox(on, null)
                    }
                }
            }
        }
        item {
            Button(onClick = { onCreate(name.trim(), people.filter { it.nodeId in picked }.associate { it.nodeId to it.name }) },
                enabled = name.isNotBlank() && picked.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Create group") }
            Text("Up to ${Rich.MAX_MEMBERS} people. Every message is end-to-end encrypted for each member.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** A group's members: add people, or leave. */
@Composable
fun GroupInfoScreen(group: ChatGroup, people: List<Person>, onBack: () -> Unit, onAdd: (Map<String, String>) -> Unit, onLeave: () -> Unit, onPerson: (String) -> Unit) {
    var adding by remember { mutableStateOf(setOf<String>()) }
    var confirmLeave by remember { mutableStateOf(false) }
    val candidates = people.filter { it.nodeId !in group.members }
    SubScreen(group.name, onBack) {
        item { GroupLabel("${group.members.size + 1} members") }
        item {
            Group {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🙂", style = MaterialTheme.typography.titleLarge); Text("You", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
                group.members.forEach { (id, n) ->
                    val p = people.firstOrNull { it.nodeId == id }
                    Row(Modifier.fillMaxWidth().clickable { onPerson(id) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p?.avatar, p?.name ?: n, id, 40.dp, p?.presence)
                        Text(p?.name ?: n, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    }
                }
            }
        }
        if (candidates.isNotEmpty() && group.members.size + 1 < Rich.MAX_MEMBERS) {
            item { GroupLabel("Add people") }
            item {
                Group {
                    candidates.forEach { p ->
                        val on = p.nodeId in adding
                        Row(Modifier.fillMaxWidth().clickable { adding = if (on) adding - p.nodeId else adding + p.nodeId }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.avatar, p.name, p.nodeId, 40.dp, p.presence)
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            Checkbox(on, null)
                        }
                    }
                }
            }
            if (adding.isNotEmpty()) item {
                Button(onClick = { onAdd(candidates.filter { it.nodeId in adding }.associate { it.nodeId to it.name }); adding = emptySet() },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Add ${adding.size}") }
            }
        }
        item {
            if (confirmLeave) Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Leave ${group.name}? Its messages are deleted from this phone.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
                TextButton(onClick = onLeave) { Text("Leave", color = Extra.rose) }
            } else Text("🚪  Leave group", color = Extra.rose, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp).clip(RoundedCornerShape(12.dp)).background(Extra.sand).clickable { confirmLeave = true }.padding(14.dp).fillMaxWidth())
        }
    }
}

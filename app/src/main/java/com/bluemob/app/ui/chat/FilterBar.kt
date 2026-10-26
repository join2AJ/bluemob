package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.theme.Extra

/** One menu in a filter bar: its current label, and the options. */
class FilterMenu(val label: String, val options: List<Pair<String, Boolean>>, val onPick: (Int) -> Unit)

/** A compact search pill plus dropdowns, on one line: used above the calls and files lists. */
@Composable
fun FilterBar(query: String, onQuery: (String) -> Unit, hint: String, menus: List<FilterMenu>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Extra.sand).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = Extra.ink3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(hint, color = Extra.ink3, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                BasicTextField(query, onQuery, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
            }
        }
        menus.forEach { m ->
            var open by remember { mutableStateOf(false) }
            Box {
                Text(m.label + " ▾", style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Extra.sand).clickable { open = true }.padding(horizontal = 12.dp, vertical = 9.dp))
                DropdownMenu(open, { open = false }) {
                    m.options.forEachIndexed { i, (label, on) ->
                        DropdownMenuItem(text = { Text((if (on) "✓  " else "     ") + label, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { open = false; m.onPick(i) })
                    }
                }
            }
        }
    }
}

package com.bluemob.app.ui.compass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.i18n.Text
import com.bluemob.app.peaks.FoundPlace
import com.bluemob.app.peaks.PeakArea
import com.bluemob.app.peaks.PeakLibrary
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.IconBadge
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import kotlinx.coroutines.launch

class PeakLibraryActions(
    val onBack: () -> Unit = {},
    /** Downloads the peaks within 150 km of a point as a named area; returns an error or null. */
    val onDownload: suspend (name: String, lat: Double, lon: Double) -> String? = { _, _, _ -> null },
    /** Online search; null when it failed. */
    val onSearch: suspend (String) -> List<FoundPlace>? = { emptyList() },
    val onRemoveArea: (PeakArea) -> Unit = {},
    val onRemoveMine: (String) -> Unit = {},
    /** Adds a missing peak here and asks the team to add it; returns a note to show. */
    val onAddMissing: suspend (name: String, lat: Double, lon: Double, ele: Int, note: String) -> String = { _, _, _, _, _ -> "" },
)

/** Peaks on this phone: download the areas you'll trek in, search by name, and add a peak that's missing. */
@Composable
fun PeakLibraryScreen(lib: PeakLibrary, here: GeoPoint?, actions: PeakLibraryActions, startAdding: Boolean = false) {
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<List<FoundPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(startAdding) }
    val offline = remember(q, lib) { lib.searchOffline(q) }
    fun download(name: String, lat: Double, lon: Double) {
        busy = name; note = null
        scope.launch { note = actions.onDownload(name, lat, lon) ?: "Saved: peaks around $name are on your phone for offline."; busy = null }
    }
    fun search() {
        if (PeakLibrary.cleanQuery(q).length < 2) return
        searching = true; note = null
        scope.launch { found = actions.onSearch(q); if (found == null) note = "Couldn't search: no internet. Try again when you're online."; searching = false }
    }

    SubScreen("Peaks on this phone", actions.onBack) {
        item {
            Text("The peak finder works offline with the peaks kept here. Download the areas you'll trek in while you have internet.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 12.dp))
        }
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(q, { q = it; found = null }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search a peak or a place: Hampta, Manali…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                Button(onClick = { search() }, enabled = !searching && PeakLibrary.cleanQuery(q).length >= 2, modifier = Modifier.padding(start = 8.dp)) {
                    if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Search")
                }
            }
        }
        note?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.BrandGreen, modifier = Modifier.padding(top = 8.dp)) } }
        if (offline.isNotEmpty()) {
            item { GroupLabel("Already on your phone") }
            item {
                Card {
                    offline.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Extra.line)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Landscape, Palette.BrandGreen, size = 34.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text("${p.eleM} m · ${coord(p.lat, p.lon)}", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                            }
                        }
                    }
                }
            }
        }
        found?.let { list ->
            item { GroupLabel(if (list.isEmpty()) "Nothing found online" else "Found online") }
            if (list.isEmpty()) item {
                Text("No peak or place with that name. Check the spelling, or add the peak yourself below.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            } else item {
                Card {
                    list.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = Extra.line)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(if (f.isPeak) Icons.Rounded.Landscape else Icons.Rounded.Place, if (f.isPeak) Palette.BrandGreen else Extra.sky, size = 34.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(f.name, style = MaterialTheme.typography.titleSmall)
                                Text(listOfNotNull(if (f.isPeak) "Peak" else "Place", f.eleM?.let { "$it m" }, coord(f.lat, f.lon)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                            }
                            if (busy == f.name) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else TextButton(onClick = { download(f.name, f.lat, f.lon) }, enabled = busy == null) { Text("Download area") }
                        }
                    }
                }
            }
        }
        item { GroupLabel("Areas on this phone") }
        item {
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.MyLocation, MaterialTheme.colorScheme.primary, size = 34.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Around me", style = MaterialTheme.typography.titleSmall)
                        Text(if (here == null) "Waiting for your position" else "Every named peak within 150 km", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                    if (busy == "Around you") CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = { here?.let { download("Around you", it.lat, it.lon) } }, enabled = here != null && busy == null) {
                        Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp)); Text(" Download")
                    }
                }
                lib.areas.forEach { a ->
                    HorizontalDivider(color = Extra.line)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Landscape, Palette.BrandGreen, size = 34.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall)
                            Text("${a.peaks.size} peaks · " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(a.at)),
                                style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                        }
                        IconButton(onClick = { download(a.name, a.lat, a.lon) }, enabled = busy == null) { Icon(Icons.Rounded.CloudDownload, "Update") }
                        IconButton(onClick = { actions.onRemoveArea(a) }) { Icon(Icons.Rounded.Delete, "Remove", tint = Extra.ink3) }
                    }
                }
            }
        }
        if (lib.mine.isNotEmpty()) {
            item { GroupLabel("Added by you") }
            item {
                Card {
                    lib.mine.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Extra.line)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.AddLocationAlt, Palette.Gold, size = 34.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text("${p.eleM} m · ${coord(p.lat, p.lon)}", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                            }
                            IconButton(onClick = { actions.onRemoveMine(p.id) }) { Icon(Icons.Rounded.Delete, "Remove", tint = Extra.ink3) }
                        }
                    }
                }
            }
        }
        item { GroupLabel("Can't find a peak?") }
        item {
            if (!adding) OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AddLocationAlt, null, Modifier.size(18.dp)); Text("  Add a missing peak")
            } else AddMissing(here, actions) { adding = false; note = it }
        }
        item {
            Text("Peak names: © OpenStreetMap contributors.", style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) { content() }
}

/** Name, position and height of a missing peak: it shows in the peak finder at once and goes to the team as a request. */
@Composable
private fun AddMissing(here: GeoPoint?, actions: PeakLibraryActions, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    var ele by rememberSaveable { mutableStateOf("") }
    var why by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Text)
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("It appears in your peak finder straight away, and we're asked to add it for everyone.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Peak name") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat, { lat = it }, Modifier.weight(1f), singleLine = true, keyboardOptions = num, label = { Text("Latitude") }, placeholder = { Text("30.8612") })
            OutlinedTextField(lon, { lon = it }, Modifier.weight(1f), singleLine = true, keyboardOptions = num, label = { Text("Longitude") }, placeholder = { Text("79.0634") })
        }
        if (here != null) TextButton(onClick = { lat = "%.5f".format(java.util.Locale.US, here.lat); lon = "%.5f".format(java.util.Locale.US, here.lon) }) {
            Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp)); Text("  I'm standing on it: use my position")
        }
        OutlinedTextField(ele, { ele = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Height (metres)") })
        OutlinedTextField(why, { why = it }, Modifier.fillMaxWidth(), label = { Text("Anything else? (optional)") }, placeholder = { Text("Local name, where you saw it listed…") })
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val la = PeakLibrary.parseCoord(lat); val lo = PeakLibrary.parseCoord(lon); val e = ele.toIntOrNull()
                error = PeakLibrary.problem(name, la, lo, e)
                if (error == null) { sending = true; scope.launch { val n = actions.onAddMissing(name, la!!, lo!!, e!!, why); sending = false; onDone(n) } }
            }, enabled = !sending) { Text(if (sending) "Sending…" else "Add and send request") }
            TextButton(onClick = { onDone(null) }) { Text("Cancel") }
        }
    }
}

private fun coord(lat: Double, lon: Double) = "%.4f, %.4f".format(java.util.Locale.US, lat, lon)

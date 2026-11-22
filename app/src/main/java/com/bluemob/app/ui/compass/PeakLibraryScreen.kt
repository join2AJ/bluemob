package com.bluemob.app.ui.compass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Star
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
import com.bluemob.app.peaks.Peak
import com.bluemob.app.peaks.Peaks
import com.bluemob.app.peaks.TrekRegions
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
    /** Keeps one peak found online, without downloading its whole area. */
    val onSavePeak: (FoundPlace) -> Unit = {},
    /** Adds a missing peak here and asks the team to add it; returns a note to show. */
    val onAddMissing: suspend (name: String, lat: Double, lon: Double, ele: Int, note: String) -> String = { _, _, _, _, _ -> "" },
)

/** Peaks on this phone: download the areas you'll trek in, search by name, and add a peak that's missing. */
@Composable
fun PeakLibraryScreen(lib: PeakLibrary, here: GeoPoint?, actions: PeakLibraryActions, startAdding: Boolean = false, startOpen: String? = null) {
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<List<FoundPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(startAdding) }
    var open by rememberSaveable { mutableStateOf(startOpen) }
    val offline = remember(q, lib) { lib.searchOffline(q) }
    val regions = remember(q) { if (q.isBlank()) TrekRegions.POPULAR else TrekRegions.search(q) }
    val kept = remember(lib) { lib.areas.map { it.name.lowercase() }.toSet() }
    fun download(name: String, lat: Double, lon: Double) {
        busy = name; note = null
        scope.launch { note = actions.onDownload(name, lat, lon) ?: "Saved: peaks around $name are on your phone for offline."; busy = null }
    }
    fun search() {
        if (PeakLibrary.cleanQuery(q).length < 2) return
        searching = true; note = null
        scope.launch { found = actions.onSearch(q); if (found == null) note = "Couldn't search: no internet. Try again when you're online."; searching = false }
    }
    @Composable fun DownloadButton(name: String, lat: Double, lon: Double, label: String = "Download") {
        if (busy == name) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else TextButton(onClick = { download(name, lat, lon) }, enabled = busy == null) {
            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp)); Text(" " + if (name.lowercase() in kept) "Update" else label)
        }
    }

    SubScreen("Peaks on this phone", actions.onBack) {
        item {
            Text("The peak finder works offline with the peaks kept here. Download the areas you'll trek in while you have internet.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 12.dp))
        }
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(q, { q = it; found = null }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Peak, trek, town or state") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                Button(onClick = { search() }, enabled = !searching && PeakLibrary.cleanQuery(q).length >= 2, modifier = Modifier.padding(start = 8.dp)) {
                    if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Search online")
                }
            }
        }
        note?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.BrandGreen, modifier = Modifier.padding(top = 8.dp)) } }

        // Results of the online search come first: that's what the user just asked for.
        found?.let { list ->
            item { GroupLabel(if (list.isEmpty()) "Nothing found online" else "Found online") }
            if (list.isEmpty()) item {
                Text("Nothing with that name. Check the spelling, try a nearby town, or add the peak yourself below.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            } else item {
                Card {
                    list.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = Extra.line)
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(if (f.isPeak) Icons.Rounded.Landscape else Icons.Rounded.Place, if (f.isPeak) Palette.BrandGreen else Extra.sky, size = 34.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(f.name, style = MaterialTheme.typography.titleSmall)
                                Text(listOfNotNull(f.eleM?.let { "$it m" }, f.area.ifBlank { null }).joinToString(" · ").ifBlank { coord(f.lat, f.lon) },
                                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 1)
                            }
                            val saved = lib.mine.any { it.name == f.name && kotlin.math.abs(it.lat - f.lat) < 0.01 }
                            if (f.isPeak && f.eleM != null) TextButton(onClick = { actions.onSavePeak(f); note = "${f.name} saved: it's in your peak finder now." }, enabled = !saved) {
                                Text(if (saved) "Saved" else "Save")
                            }
                            DownloadButton(f.name, f.lat, f.lon, if (f.isPeak) "Area" else "Download")
                        }
                    }
                }
            }
        }
        if (regions.isNotEmpty()) {
            item { GroupLabel(if (q.isBlank()) "Popular trek areas" else "Trek areas") }
            item {
                Card {
                    regions.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = Extra.line)
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(if (r.kind == "Trek") Icons.Rounded.Hiking else Icons.Rounded.Map, Extra.sky, size = 34.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                                Text("${r.kind} · peaks within 150 km", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                            }
                            DownloadButton(r.name, r.lat, r.lon)
                        }
                    }
                }
            }
            if (q.isNotBlank() && found == null) item {
                Text("Not what you meant? Tap Search online.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (offline.isNotEmpty()) {
            item { GroupLabel("Already on your phone") }
            item { Card { offline.forEachIndexed { i, p -> if (i > 0) HorizontalDivider(color = Extra.line); PeakRow(p) } } }
        }

        item { GroupLabel("On this phone · ${lib.all.size + Peaks.BUILT_IN.size} peaks") }
        item {
            Card {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.MyLocation, MaterialTheme.colorScheme.primary, size = 34.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Around me", style = MaterialTheme.typography.titleSmall)
                        Text(if (here == null) "Turn on location to download around you" else "Every named peak within 150 km", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                    if (here != null) DownloadButton("Around you", here.lat, here.lon)
                }
                lib.areas.forEach { a ->
                    HorizontalDivider(color = Extra.line)
                    AreaRow(Icons.Rounded.Landscape, Palette.BrandGreen, a.name,
                        "${a.peaks.size} peaks · " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(a.at)),
                        a.peaks, open == a.name, { open = if (open == a.name) null else a.name }) {
                        IconButton(onClick = { download(a.name, a.lat, a.lon) }, enabled = busy == null) { Icon(Icons.Rounded.CloudDownload, "Update") }
                        IconButton(onClick = { actions.onRemoveArea(a) }) { Icon(Icons.Rounded.Delete, "Remove", tint = Extra.ink3) }
                    }
                }
                if (lib.mine.isNotEmpty()) {
                    HorizontalDivider(color = Extra.line)
                    AreaRow(Icons.Rounded.AddLocationAlt, Palette.Gold, "Saved and added by you", "${lib.mine.size} peaks", lib.mine, open == "mine",
                        { open = if (open == "mine") null else "mine" }, onRemove = actions.onRemoveMine) {}
                }
                HorizontalDivider(color = Extra.line)
                AreaRow(Icons.Rounded.Star, Palette.Gold, "Famous peaks", "${Peaks.BUILT_IN.size} peaks · always on your phone", Peaks.BUILT_IN, open == "famous",
                    { open = if (open == "famous") null else "famous" }) {}
            }
        }
        item { GroupLabel("Can't find a peak?") }
        item {
            if (!adding) OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AddLocationAlt, null, Modifier.size(18.dp)); Text("  Add a missing peak")
            } else AddMissing(here, actions) { adding = false; note = it }
        }
        item {
            Text("Peak names and search: © OpenStreetMap contributors.", style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** An area of peaks: tap to see them, highest first. */
@Composable
private fun AreaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, sub: String, peaks: List<Peak>,
                    isOpen: Boolean, onToggle: () -> Unit, onRemove: ((String) -> Unit)? = null, trailing: @Composable () -> Unit) {
    var all by remember(isOpen) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, size = 34.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
        }
        trailing()
        Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (isOpen) "Hide peaks" else "Show peaks", tint = Extra.ink3, modifier = Modifier.padding(end = 10.dp))
    }
    if (isOpen) {
        val sorted = remember(peaks) { peaks.sortedByDescending { it.eleM } }
        Column(Modifier.fillMaxWidth().background(Extra.sand)) {
            (if (all) sorted else sorted.take(25)).forEach { p -> PeakRow(p, onRemove) }
            if (!all && sorted.size > 25) TextButton(onClick = { all = true }, modifier = Modifier.padding(start = 6.dp)) { Text("Show all ${sorted.size}") }
        }
    }
}

@Composable
private fun PeakRow(p: Peak, onRemove: ((String) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Landscape, null, tint = Palette.BrandGreen, modifier = Modifier.size(18.dp))
        Text(p.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1)
        Text("${"%,d".format(p.eleM)} m", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(horizontal = 8.dp))
        if (onRemove != null) IconButton(onClick = { onRemove(p.id) }) { Icon(Icons.Rounded.Delete, "Remove", tint = Extra.ink3) }
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

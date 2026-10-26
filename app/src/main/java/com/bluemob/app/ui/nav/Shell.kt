package com.bluemob.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.vector.ImageVector
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.ui.AppViewModel
import com.bluemob.app.ui.SystemActions
import com.bluemob.app.ui.SystemStatus
import com.bluemob.app.ui.chat.ChatFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

enum class Tab(val label: String, val icon: ImageVector) {
    NEARBY("Nearby", Icons.Outlined.Radar),
    CHATS("Chats", Icons.AutoMirrored.Outlined.Chat),
    COMPASS("Compass", Icons.Outlined.Explore),
    GUIDE("Guide", Icons.AutoMirrored.Outlined.MenuBook),
    YOU("You", Icons.Outlined.Person),
}

/** The back stack over the tabs. It holds route keys so Android can save and restore it. */
class Nav(private val stack: SnapshotStateList<String>, private val onTab: (Tab) -> Unit) {
    val top: Route? get() = stack.lastOrNull()?.let(Route::parse)
    val isEmpty: Boolean get() = stack.isEmpty()
    val size: Int get() = stack.size

    fun push(r: Route) { stack.add(r.key) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    /** Swaps the current screen for another (e.g. "New chat" → the chat). */
    fun replace(r: Route) { pop(); push(r) }
    fun clear() = stack.clear()
    fun goTab(t: Tab) { stack.clear(); onTab(t) }
}

/** What every feature's screens share from the main shell. Screens collect their own data from [vm]. */
class Shell(
    val vm: AppViewModel,
    val system: SystemStatus,
    val actions: SystemActions,
    val nav: Nav,
    val scope: CoroutineScope,
    /** Where this phone is: GPS, or the trail's estimate when there's no fix. */
    val hereFix: GeoPoint?,
    val headings: Flow<Float>,
    /** How many phones the last SOS reached (-1 when none was sent). */
    val sosReached: MutableState<Int>,
    val chatFiles: (String) -> ChatFiles,
    /** Buttons under Sky's replies. */
    val onSkyAction: (String) -> Unit,
    /** Switches on what helps an SOS get through ("location", "bluetooth", "mesh", "internet"). */
    val turnOnForSos: (String) -> Unit,
    val toggleLocation: (Boolean) -> Unit,
    /** Points the compass at someone and switches to it. */
    val showOnCompass: (String) -> Unit,
) {
    fun push(r: Route) = nav.push(r)
    fun pop() = nav.pop()

    /** After an SOS is sent: asks for the most important missing piece (Android shows one prompt at a time). */
    fun askForSosPieces(radios: com.bluemob.app.system.RadioState) {
        when {
            !system.permissionsGranted -> actions.requestMeshPermissions()
            !system.locationPermission -> actions.requestLocation()
            !radios.bluetooth -> actions.switchRadio(com.bluemob.app.system.Radio.BLUETOOTH, true)
            !radios.location -> actions.switchRadio(com.bluemob.app.system.Radio.LOCATION, true)
        }
    }
}

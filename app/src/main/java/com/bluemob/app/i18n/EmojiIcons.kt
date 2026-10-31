package com.bluemob.app.i18n

import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Support
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em

/**
 * Emoji written inside the app's own sentences and labels ("Tap 📎 in a chat") are drawn as Material icons in the
 * text's colour, so the app looks the same on every phone instead of mixing in the phone's emoji font. A string that is
 * only emoji (a reaction, an avatar) is left alone, and so are people's own messages (they use [RawText]).
 */
object EmojiIcons {
    private val icons: Map<String, ImageVector> by lazy {
        mapOf(
            "📎" to Icons.Rounded.AttachFile, "🎤" to Icons.Rounded.Mic, "🎙" to Icons.Rounded.Mic, "📞" to Icons.Rounded.Call,
            "🎥" to Icons.Rounded.Videocam, "📹" to Icons.Rounded.Videocam, "📍" to Icons.Rounded.LocationOn, "🧭" to Icons.Rounded.Explore,
            "📣" to Icons.Rounded.Campaign, "📢" to Icons.Rounded.Campaign, "📋" to Icons.Rounded.FactCheck, "🏁" to Icons.Rounded.SportsScore,
            "🔒" to Icons.Rounded.Lock, "💬" to Icons.AutoMirrored.Rounded.Chat, "⚠" to Icons.Rounded.WarningAmber, "⛺" to Icons.Rounded.Cabin,
            "🙋" to Icons.Rounded.PanTool, "👏" to Icons.Rounded.VolunteerActivism, "🚫" to Icons.Rounded.Block, "🧠" to Icons.Rounded.Psychology,
            "🚪" to Icons.AutoMirrored.Rounded.Logout, "📷" to Icons.Rounded.PhotoCamera, "👥" to Icons.Rounded.Groups, "👍" to Icons.Rounded.ThumbUp,
            "🛟" to Icons.Rounded.Support, "⚕" to Icons.Rounded.MedicalServices, "🩺" to Icons.Rounded.MedicalServices, "🔔" to Icons.Rounded.NotificationsActive,
            "🧪" to Icons.Rounded.Science, "🩸" to Icons.Rounded.Bloodtype, "🏅" to Icons.Rounded.MilitaryTech, "🔦" to Icons.Rounded.FlashlightOn,
            "🔋" to Icons.Rounded.BatteryChargingFull, "🗺" to Icons.Rounded.Map, "✅" to Icons.Rounded.CheckCircle, "❌" to Icons.Rounded.Cancel,
            "⏰" to Icons.Rounded.Alarm, "⏱" to Icons.Rounded.Timer, "🕒" to Icons.Rounded.Schedule, "📌" to Icons.Rounded.PushPin,
            "📤" to Icons.Rounded.Share, "🔊" to Icons.AutoMirrored.Rounded.VolumeUp, "🌐" to Icons.Rounded.Language, "🆘" to Icons.Rounded.Sos,
            "⭐" to Icons.Rounded.Star, "⛰" to Icons.Rounded.Landscape, "⏸" to Icons.Rounded.Pause, "🏍" to Icons.Rounded.TwoWheeler, "📱" to Icons.Rounded.Smartphone, "🚨" to Icons.Rounded.NotificationImportant, "📄" to Icons.Rounded.Description, "🎬" to Icons.Rounded.Movie, "🚶" to Icons.AutoMirrored.Rounded.DirectionsWalk, "🏃" to Icons.AutoMirrored.Rounded.DirectionsRun, "🚲" to Icons.AutoMirrored.Rounded.DirectionsBike, "🚴" to Icons.AutoMirrored.Rounded.DirectionsBike, "🚗" to Icons.Rounded.DirectionsCar, "🚌" to Icons.Rounded.DirectionsBus, "🚆" to Icons.Rounded.Train, "🏠" to Icons.Rounded.Home, "⏳" to Icons.Rounded.HourglassTop, "🔥" to Icons.Rounded.LocalFireDepartment, "💧" to Icons.Rounded.WaterDrop, "📶" to Icons.Rounded.SignalCellularAlt, "👤" to Icons.Rounded.Person, "🧑" to Icons.Rounded.Person, "✉" to Icons.Rounded.Mail, "🔕" to Icons.Rounded.NotificationsOff, "📵" to Icons.Rounded.PhonelinkErase, "🛰" to Icons.Rounded.Satellite, "🌙" to Icons.Rounded.DarkMode, "☀" to Icons.Rounded.LightMode, "🌅" to Icons.Rounded.WbTwilight,
        )
    }

    /** Splits [s] into plain text and emoji we draw as icons. */
    fun parts(s: String): List<Pair<Boolean, String>> {
        val out = ArrayList<Pair<Boolean, String>>()
        val plain = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ch = String(Character.toChars(cp))
            i += Character.charCount(cp)
            if (ch in icons) {
                if (plain.isNotEmpty()) { out += false to plain.toString(); plain.clear() }
                out += true to ch
                if (i < s.length && s[i] == '️') i++ // the "draw as emoji" marker
            } else plain.append(ch)
        }
        if (plain.isNotEmpty()) out += false to plain.toString()
        return out
    }

    /** True when [s] has an emoji we draw as an icon, next to real words. */
    fun applies(s: String): Boolean {
        if (s.none { it.isSurrogate() || it in '☀'..'➿' }) return false
        val p = parts(s)
        return p.any { it.first } && p.any { !it.first && it.second.any { c -> c.isLetterOrDigit() } }
    }

    fun build(s: String, tint: Color): Pair<AnnotatedString, Map<String, InlineTextContent>> {
        val used = LinkedHashMap<String, InlineTextContent>()
        val text = buildAnnotatedString {
            parts(s).forEach { (icon, t) ->
                if (!icon) append(t) else {
                    val key = "i:$t"
                    appendInlineContent(key, t)
                    used.getOrPut(key) {
                        InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
                            Icon(icons.getValue(t), null, tint = tint, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        return text to used
    }
}

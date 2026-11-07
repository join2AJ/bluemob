package com.bluemob.app.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.i18n.Text
import com.bluemob.app.safety.Daylight
import com.bluemob.app.safety.PressureTrend
import com.bluemob.app.settings.Spot
import com.bluemob.app.ui.components.IconBadge
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.util.SunMoon
import kotlin.math.roundToInt

/** Light left today and, with a base camp saved for this trip, the walk back to it, and when to turn back. */
@Composable
fun DaylightCard(here: GeoPoint, camp: Spot?, now: Long, modifier: Modifier = Modifier) {
    val sun = SunMoon.times(now, here.lat, here.lon)
    val dist = camp?.let { com.bluemob.app.util.Geo.distanceM(here, GeoPoint(it.lat, it.lon, 0f, it.time)) }
    val plan = Daylight.plan(sun.set, now, dist) ?: return
    val warn = plan.turnBack || plan.lightLeftMs < 60 * 60_000L
    val c = if (plan.turnBack) Palette.Danger else if (warn) Color(0xFFE07F2E) else Palette.Gold
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(c.copy(alpha = 0.12f)).border(1.dp, c.copy(alpha = 0.4f), MaterialTheme.shapes.large).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Rounded.WbTwilight, c)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("${SunMoon.span(plan.lightLeftMs)} of daylight left", style = MaterialTheme.typography.titleSmall)
            Text(when {
                plan.walkMs == null -> "Save your base camp in Compass, and BlueMob tells you when to turn back."
                plan.turnBack -> "Turn back now: base camp is about ${SunMoon.span(plan.walkMs)} away."
                else -> "Base camp is about ${SunMoon.span(plan.walkMs)} away. You'll get a warning when it's time to turn back."
            }, style = MaterialTheme.typography.bodySmall, color = if (plan.turnBack) c else Extra.ink2)
        }
    }
}

/** Air pressure and where it's heading, with the last hours drawn; hidden on phones without the sensor. */
@Composable
fun WeatherCard(samples: List<PressureTrend.Sample>, now: Long, modifier: Modifier = Modifier) {
    val last = samples.lastOrNull() ?: return
    val t = PressureTrend.tendency(samples, now)
    val level = PressureTrend.level(t)
    val c = when (level) {
        PressureTrend.Level.STORM -> Palette.Danger
        PressureTrend.Level.FALLING_FAST -> Color(0xFFE07F2E)
        PressureTrend.Level.FALLING -> Palette.Gold
        else -> Palette.BrandBlue
    }
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Cloud, c)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Weather: ${level.label}", style = MaterialTheme.typography.titleSmall, color = if (level >= PressureTrend.Level.FALLING) c else MaterialTheme.colorScheme.onSurface)
                Text("Air pressure ${last.hPa.roundToInt()} hPa" + (t?.let { " · %+.1f hPa in 3 h".format(it) } ?: " · a trend needs an hour of readings with GPS"),
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
        }
        Text(level.advice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        val pts = samples.filter { it.altM != null }.map { it.at to PressureTrend.seaLevel(it.hPa, it.altM!!) }
        if (pts.size >= 3) Canvas(Modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp)) {
            val t0 = pts.first().first; val t1 = maxOf(pts.last().first, t0 + 1)
            val lo = pts.minOf { it.second } - 0.5; val hi = pts.maxOf { it.second } + 0.5
            val path = Path()
            pts.forEachIndexed { i, (at, v) ->
                val x = (at - t0).toFloat() / (t1 - t0) * size.width
                val y = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, c, style = Stroke(4f))
            drawCircle(c, 6f, Offset(size.width, size.height - ((pts.last().second - lo) / (hi - lo)).toFloat() * size.height))
        }
        Text("From the phone's own pressure sensor, adjusted for your altitude. Works with no signal.", style = MaterialTheme.typography.labelSmall,
            color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
    }
}

package com.bluemob.app.trail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.bluemob.app.data.TrailPoint
import com.bluemob.app.data.Trip
import com.bluemob.app.util.Geo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

/**
 * A shareable picture of a trip: the trail to scale, coloured by travel mode, with start and end times, distance,
 * speeds, how they probably travelled, and a scale bar. Drawn without map tiles, so it works offline.
 */
object TripImage {
    private fun modeColor(m: TravelMode) = when (m) {
        TravelMode.STILL -> 0xFF9AA5A0.toInt(); TravelMode.FOOT -> 0xFF1F6B4F.toInt(); TravelMode.CYCLE -> 0xFF2F8FD8.toInt()
        TravelMode.BIKE -> 0xFFE08A2E.toInt(); TravelMode.CAR -> 0xFFD94F55.toInt()
    }

    fun render(trip: Trip, points: List<TrailPoint>): Bitmap {
        val w = 1080; val h = 1500
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF6F3EC.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1D2A24.toInt(); textSize = 54f; isFakeBoldText = true }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5C6B64.toInt(); textSize = 32f }
        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        c.drawText(trip.name.take(30), 60f, 100f, text)
        val start = points.firstOrNull()?.time ?: trip.startedAt
        val end = points.lastOrNull()?.time ?: trip.endedAt ?: start
        c.drawText("${fmt.format(Date(start))}  →  ${fmt.format(Date(end))}", 60f, 150f, small)

        // The map square.
        val top = 190f; val side = w - 120f; val left = 60f
        c.drawRoundRect(left, top, left + side, top + side, 32f, 32f, Paint().apply { color = 0xFFFFFFFF.toInt() })
        val a = TripAnalysis.of(points)
        if (points.isNotEmpty()) {
            val lat0 = points.map { it.lat }.average()
            val k = cos(Math.toRadians(lat0))
            fun xy(p: TrailPoint) = ((p.lon - points[0].lon) * 111_320 * k) to ((p.lat - points[0].lat) * 110_540)
            val ps = points.map { xy(it) }
            val minX = ps.minOf { it.first }; val maxX = ps.maxOf { it.first }; val minY = ps.minOf { it.second }; val maxY = ps.maxOf { it.second }
            val span = max(max(maxX - minX, maxY - minY), 50.0)
            val pad = side * 0.08f
            val scale = (side - 2 * pad) / span
            val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
            fun sx(p: Pair<Double, Double>) = (left + side / 2 + (p.first - cx) * scale).toFloat()
            fun sy(p: Pair<Double, Double>) = (top + side / 2 - (p.second - cy) * scale).toFloat()
            // Grid and scale bar.
            val gridM = listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0, 10_000.0, 25_000.0, 50_000.0).firstOrNull { span / it <= 6 } ?: 100_000.0
            val gp = (gridM * scale).toFloat()
            val gridPaint = Paint().apply { color = 0x14000000; strokeWidth = 2f }
            var g = left; while (g < left + side) { c.drawLine(g, top, g, top + side, gridPaint); g += gp }
            g = top; while (g < top + side) { c.drawLine(left, g, left + side, g, gridPaint); g += gp }
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1D2A24.toInt(); strokeWidth = 6f }
            c.drawLine(left + 40, top + side - 50, left + 40 + gp, top + side - 50, bar)
            c.drawText(if (gridM >= 1000) "${(gridM / 1000).toInt()} km" else "${gridM.toInt()} m", left + 40, top + side - 66, small)
            c.drawText("N ↑", left + side - 90, top + 60, small)
            val modeAt = a.legs.associate { it.to.time to it.mode }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
            points.zipWithNext().forEach { (p, q) ->
                line.color = modeAt[q.time]?.let { modeColor(it) } ?: 0xFF1F6B4F.toInt()
                line.pathEffect = if (q.estimated) DashPathEffect(floatArrayOf(18f, 14f), 0f) else null
                val pp = xy(p); val qq = xy(q)
                c.drawLine(sx(pp), sy(pp), sx(qq), sy(qq), line)
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            xy(points.first()).let { dot.color = 0xFF1F6B4F.toInt(); c.drawCircle(sx(it), sy(it), 18f, dot) }
            xy(points.last()).let { dot.color = 0xFFD94F55.toInt(); c.drawCircle(sx(it), sy(it), 18f, dot) }
        }

        // Numbers.
        var y = top + side + 80
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1D2A24.toInt(); textSize = 40f; isFakeBoldText = true }
        val mins = (end - start) / 60_000
        val stats = listOf(
            "Distance" to Geo.formatDistance(a.distanceM), "Time" to (if (mins < 60) "$mins min" else "${mins / 60} h ${mins % 60} min"),
            "Avg moving" to "%.1f km/h".format(a.avgMovingKmh), "Top speed" to "%.0f km/h".format(a.maxKmh),
        )
        stats.forEachIndexed { i, (k, v) ->
            val x = 60f + i * 250f
            c.drawText(v, x, y, big); c.drawText(k, x, y + 40, small)
        }
        y += 120
        a.likelyMode?.let { c.drawText("Probably ${it.label.lowercase()} ${it.emoji}" + if (a.climbM > 0) " · climbed ${a.climbM.toInt()} m" else "", 60f, y, big) }
        y += 60
        // Legend of modes with their share of the time.
        val total = a.timeByMode.values.sum().coerceAtLeast(1.0)
        var x = 60f
        TravelMode.entries.forEach { m ->
            val sec = a.timeByMode[m] ?: return@forEach
            c.drawCircle(x + 12, y - 12, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = modeColor(m) })
            val label = "${m.label} ${(sec * 100 / total).toInt()}%"
            c.drawText(label, x + 34, y, small)
            x += small.measureText(label) + 70
        }
        c.drawText("${points.size} points · ${points.count { !it.estimated }} from GPS · made with BlueMob", 60f, h - 50f, small)
        return bmp
    }
}

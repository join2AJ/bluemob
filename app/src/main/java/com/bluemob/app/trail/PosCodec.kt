package com.bluemob.app.trail

import org.json.JSONObject

/** PositionEstimate as compact JSON, for the mesh and for storage. */
object PosCodec {
    fun posJson(p: PositionEstimate) = JSONObject()
        .put("lat", p.lat).put("lon", p.lon).put("gps", p.gps).put("flat", p.fixLat).put("flon", p.fixLon).put("fat", p.fixAt)
        .put("facc", p.fixAccuracyM.toDouble()).put("walk", p.walkedM ?: -1.0).put("brg", p.travelBearingDeg ?: -1.0)
        .put("hdg", p.headingDeg?.toDouble() ?: -1.0).put("unc", p.uncertaintyM).put("at", p.at)

    fun parsePos(j: JSONObject?): PositionEstimate? {
        if (j == null) return null
        val lat = j.optDouble("lat")
        val lon = j.optDouble("lon")
        if (lat.isNaN() || lon.isNaN()) return null
        return PositionEstimate(
            lat, lon, j.optBoolean("gps"), j.optDouble("flat", lat), j.optDouble("flon", lon), j.optLong("fat"),
            j.optDouble("facc", 0.0).toFloat(), j.optDouble("walk", -1.0).takeIf { it >= 0 },
            j.optDouble("brg", -1.0).takeIf { it >= 0 }, j.optDouble("hdg", -1.0).takeIf { it >= 0 }?.toFloat(),
            j.optDouble("unc", 0.0), j.optLong("at"),
        )
    }


    fun encode(p: PositionEstimate?): String? = p?.let { posJson(it).toString() }
    fun decode(s: String?): PositionEstimate? = s?.let { runCatching { parsePos(JSONObject(it)) }.getOrNull() }
}

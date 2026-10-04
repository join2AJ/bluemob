package com.bluemob.app.mesh

/**
 * Encodes our identity into the Nearby "endpoint name" that is broadcast while advertising,
 * so a phone learns who is nearby before connecting.
 *
 * Format: `BM1|<nodeId>|<displayName>`. The prefix lets us ignore unrelated apps and
 * change the format later.
 */
object EndpointInfo {
    private const val PREFIX = "BM1"

    fun encode(nodeId: String, name: String): String = "$PREFIX|$nodeId|${name.replace("|", " ")}"

    /** Returns (nodeId, name), or null if this is not a BlueMob endpoint. */
    fun decode(endpointName: String): Pair<String, String>? {
        val parts = endpointName.split("|", limit = 3)
        if (parts.size != 3 || parts[0] != PREFIX || parts[1].isBlank()) return null
        return parts[1] to parts[2]
    }
}

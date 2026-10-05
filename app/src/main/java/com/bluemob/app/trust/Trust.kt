package com.bluemob.app.trust

import kotlin.math.roundToInt

/** What a rating says. [delta] is how many stars one full-weight rating moves the score. */
enum class RatingKind(val code: String, val label: String, val delta: Double, val positive: Boolean) {
    THANKS("thanks", "Appreciated their help", +0.5, true),
    GENUINE_SOS("genuine_sos", "Their SOS was real", +0.25, true),
    BAD_LANGUAGE("bad_language", "Bad or abusive language", -0.75, false),
    FAKE_SOS("fake_sos", "Fake or prank SOS", -1.5, false),
    ;

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code }
    }
}

/** One signed rating: [rater] says [kind] about [subject], about a specific SOS or message ([ctx]). */
data class Rating(
    val rater: String,
    val raterName: String,
    val subject: String,
    val kind: RatingKind,
    val ctx: String,
    val remark: String,
    val at: Long,
)

/** Someone's standing: 0 to 5 stars, with what it's made of. */
data class TrustScore(
    val stars: Double,
    val ratings: Int,
    val thanks: Int,
    val badLanguage: Int,
    val fakeSos: Int,
    val genuineSos: Int,
    /** Newest first, with remarks, for the profile. */
    val recent: List<Rating>,
) {
    /** "4.5" */
    val label: String get() = ((stars * 10).roundToInt() / 10.0).toString()
    val isNew: Boolean get() = ratings == 0

    /** Shown on an SOS from this person, to help judge whether it's genuine. */
    val sosWarning: String?
        get() = when {
            fakeSos == 0 -> null
            fakeSos == 1 -> "1 person reported an earlier SOS from them as fake"
            else -> "$fakeSos people reported earlier SOS calls from them as fake"
        }
}

/**
 * Turns signed ratings into stars. Everyone starts at [START]; appreciation and genuine SOS calls add, flags for bad
 * language and fake SOS calls take away; the result stays between 0 and 5. To keep it fair:
 * - each rater counts once per kind and SOS/message, and at most [MAX_PER_RATER] times per kind in total,
 *   so one person can't sink or inflate someone on their own;
 * - [weightOf] lets the app count people we've met in person more than strangers (keys are free to make);
 * - ratings older than [HALF_LIFE_MS] count half, so people can recover from old mistakes.
 */
object Trust {
    const val START = 4.0
    const val MAX_PER_RATER = 2
    const val HALF_LIFE_MS = 180L * 24 * 3_600_000

    fun score(subject: String, all: List<Rating>, now: Long, weightOf: (rater: String) -> Double = { 1.0 }): TrustScore {
        val about = all.filter { it.subject == subject && it.rater != subject }
            .groupBy { Triple(it.rater, it.kind, it.ctx) }.map { (_, rs) -> rs.maxBy { it.at } }
        var stars = START
        about.groupBy { it.rater to it.kind }.forEach { (key, rs) ->
            val w = weightOf(key.first).coerceIn(0.0, 1.0)
            rs.sortedByDescending { it.at }.take(MAX_PER_RATER).forEach { r ->
                val age = if (now - r.at > HALF_LIFE_MS) 0.5 else 1.0
                stars += r.kind.delta * w * age
            }
        }
        return TrustScore(
            stars = stars.coerceIn(0.0, 5.0),
            ratings = about.size,
            thanks = about.count { it.kind == RatingKind.THANKS },
            badLanguage = about.count { it.kind == RatingKind.BAD_LANGUAGE },
            fakeSos = about.filter { it.kind == RatingKind.FAKE_SOS }.distinctBy { it.rater }.size,
            genuineSos = about.filter { it.kind == RatingKind.GENUINE_SOS }.distinctBy { it.rater }.size,
            recent = about.sortedByDescending { it.at }.take(20),
        )
    }
}

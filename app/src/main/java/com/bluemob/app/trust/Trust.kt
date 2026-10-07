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

/** What people rate each other on, 1 to 5 stars each. */
enum class RatingCategory(val code: String, val label: String, val emoji: String, val question: String) {
    HELPFUL("c_helpful", "Helpful", "🤝", "Did they help, or try to?"),
    QUICK("c_quick", "Quick to respond", "⚡", "Did they answer or come quickly?"),
    RELIABLE("c_reliable", "Reliable", "🛡️", "Did they do what they said? Was their SOS real?"),
    CLEAR("c_clear", "Clear communication", "💬", "Were their messages clear and useful?"),
    RESPECTFUL("c_respect", "Respectful", "🙏", "Were they polite and respectful?"),
    ;

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code }
    }
}

/** One person's stars for someone in one category. */
data class CategoryRating(val rater: String, val raterName: String, val subject: String, val category: RatingCategory, val stars: Int, val remark: String, val at: Long)

/** A category's score: 0..5 (shown in quarter stars) from [count] ratings. */
data class CategoryScore(val stars: Double, val count: Int)

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
    /** Each category's score (categories nobody rated yet start at [Trust.START]). */
    val categories: Map<RatingCategory, CategoryScore> = emptyMap(),
    /** How many different people rated them in any category. */
    val raters: Int = 0,
    /** Category ratings with remarks, newest first. */
    val remarks: List<CategoryRating> = emptyList(),
) {
    /** Stars rounded to the nearest quarter, for display. */
    val quarters: Double get() = Trust.quarter(stars)
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
    /** How many "virtual" ratings at [START] each category starts with, so one or two ratings can't swing it wildly. */
    const val PRIOR = 2.0
    /** One rating per person per category per this long. */
    const val RERATE_MS = 30L * 24 * 3_600_000

    fun quarter(x: Double) = (Math.round(x * 4) / 4.0).coerceIn(0.0, 5.0)

    /** How older kinds of rating count in the categories. */
    fun legacy(r: Rating): CategoryRating? = when (r.kind) {
        RatingKind.THANKS -> CategoryRating(r.rater, r.raterName, r.subject, RatingCategory.HELPFUL, 5, r.remark, r.at)
        RatingKind.GENUINE_SOS -> CategoryRating(r.rater, r.raterName, r.subject, RatingCategory.RELIABLE, 5, r.remark, r.at)
        RatingKind.BAD_LANGUAGE -> CategoryRating(r.rater, r.raterName, r.subject, RatingCategory.RESPECTFUL, 1, r.remark, r.at)
        RatingKind.FAKE_SOS -> null
    }

    /**
     * Category scores: a weighted average that starts at [START] (worth [PRIOR] ratings). Each rater counts once per
     * category (their latest), weighted by [weightOf] (met in person = 1, stranger = ½) and halved after [HALF_LIFE_MS].
     * Overall = average of the categories, minus ½ star per person who reported a fake SOS (at most 2 stars).
     */
    fun standing(subject: String, cats: List<CategoryRating>, legacy: List<Rating>, now: Long, weightOf: (String) -> Double = { 1.0 }): TrustScore {
        val legacyCats = legacy.filter { it.subject == subject && it.rater != subject }.mapNotNull(::legacy)
        val all = (legacyCats + cats.filter { it.subject == subject && it.rater != subject })
            .groupBy { it.rater to it.category }.map { (_, rs) -> rs.maxBy { it.at } }
        val byCat = RatingCategory.entries.associateWith { c ->
            val rs = all.filter { it.category == c }
            var sum = START * PRIOR; var weights = PRIOR
            rs.forEach { r ->
                val w = weightOf(r.rater).coerceIn(0.0, 1.0) * (if (now - r.at > HALF_LIFE_MS) 0.5 else 1.0)
                sum += r.stars.coerceIn(1, 5) * w; weights += w
            }
            CategoryScore(sum / weights, rs.size)
        }
        val base = score(subject, legacy, now, weightOf)
        val fakes = base.fakeSos
        val overall = (byCat.values.map { it.stars }.average() - 0.5 * fakes.coerceAtMost(4)).coerceIn(0.0, 5.0)
        return base.copy(
            stars = overall, categories = byCat, raters = all.map { it.rater }.distinct().size,
            ratings = all.size + legacy.count { it.subject == subject && it.kind == RatingKind.FAKE_SOS },
            remarks = all.filter { it.remark.isNotBlank() }.sortedByDescending { it.at }.take(20),
        )
    }
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

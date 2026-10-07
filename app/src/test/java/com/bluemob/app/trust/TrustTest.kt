package com.bluemob.app.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustTest {
    private val now = 1_000_000_000_000L
    private fun r(rater: String, kind: RatingKind, ctx: String = "c", at: Long = now) = Rating(rater, rater, "ravi", kind, ctx, "", at)

    @Test fun newPeopleStartAtFour() {
        val s = Trust.score("ravi", emptyList(), now)
        assertEquals(4.0, s.stars, 0.001)
        assertTrue(s.isNew)
    }

    @Test fun appreciationAddsStarsUpToFive() {
        val s = Trust.score("ravi", listOf(r("asha", RatingKind.THANKS), r("meera", RatingKind.THANKS), r("tara", RatingKind.THANKS)), now)
        assertEquals(5.0, s.stars, 0.001)
        assertEquals(3, s.thanks)
    }

    @Test fun badLanguageAndFakeSosTakeStarsAway() {
        val s = Trust.score("ravi", listOf(r("asha", RatingKind.BAD_LANGUAGE), r("meera", RatingKind.FAKE_SOS, "sos1")), now)
        assertEquals(4.0 - 0.75 - 1.5, s.stars, 0.001)
        assertEquals("1 person reported an earlier SOS from them as fake", s.sosWarning)
    }

    @Test fun oneRaterCantSinkSomeoneAlone() {
        val spam = (1..20).map { r("troll", RatingKind.BAD_LANGUAGE, "m$it") }
        assertEquals(4.0 - 2 * 0.75, Trust.score("ravi", spam, now).stars, 0.001)
    }

    @Test fun ratingTheSameThingTwiceCountsOnce() {
        val s = Trust.score("ravi", listOf(r("asha", RatingKind.THANKS, "sos1", now - 10), r("asha", RatingKind.THANKS, "sos1", now)), now)
        assertEquals(4.5, s.stars, 0.001)
        assertEquals(1, s.ratings)
    }

    @Test fun strangersCountLessAndOldRatingsFade() {
        val stranger = Trust.score("ravi", listOf(r("bot1", RatingKind.FAKE_SOS)), now) { 0.5 }
        assertEquals(4.0 - 0.75, stranger.stars, 0.001)
        val old = Trust.score("ravi", listOf(r("asha", RatingKind.FAKE_SOS, at = now - Trust.HALF_LIFE_MS - 1)), now)
        assertEquals(4.0 - 0.75, old.stars, 0.001)
    }

    @Test fun nobodyCanRateThemselvesAndScoresStayInRange() {
        val self = Rating("ravi", "Ravi", "ravi", RatingKind.THANKS, "c", "", now)
        assertEquals(4.0, Trust.score("ravi", listOf(self), now).stars, 0.001)
        val awful = (1..10).flatMap { listOf(r("p$it", RatingKind.FAKE_SOS, "s$it"), r("p$it", RatingKind.BAD_LANGUAGE, "m$it")) }
        assertEquals(0.0, Trust.score("ravi", awful, now).stars, 0.001)
        assertNull(Trust.score("ravi", emptyList(), now).sosWarning)
    }
}

class CategoryTrustTest {
    private val now = 1_000_000_000_000L
    private fun c(rater: String, cat: RatingCategory, v: Int, at: Long = now) = CategoryRating(rater, rater, "ravi", cat, v, "", at)

    @Test fun quarterStarsRound() {
        assertEquals(4.25, Trust.quarter(4.2), 0.0001)
        assertEquals(4.5, Trust.quarter(4.4), 0.0001)
        assertEquals(5.0, Trust.quarter(7.0), 0.0001)
    }

    @Test fun newPersonStartsAtFourAndOneRatingMovesItGently() {
        assertEquals(4.0, Trust.standing("ravi", emptyList(), emptyList(), now).stars, 0.001)
        val one = Trust.standing("ravi", listOf(c("asha", RatingCategory.HELPFUL, 5)), emptyList(), now)
        assertTrue(one.categories.getValue(RatingCategory.HELPFUL).stars in 4.01..4.99)
        assertEquals(1, one.raters)
    }

    @Test fun onlyTheNewestRatingPerPersonAndCategoryCounts() {
        val s = Trust.standing("ravi", listOf(c("asha", RatingCategory.QUICK, 1, now - 1000), c("asha", RatingCategory.QUICK, 5, now)), emptyList(), now)
        assertEquals(1, s.categories.getValue(RatingCategory.QUICK).count)
        assertTrue(s.categories.getValue(RatingCategory.QUICK).stars > 4.0)
    }

    @Test fun selfRatingsAreIgnored() {
        val s = Trust.standing("ravi", listOf(CategoryRating("ravi", "Ravi", "ravi", RatingCategory.HELPFUL, 5, "", now)), emptyList(), now)
        assertEquals(0, s.raters)
    }
}

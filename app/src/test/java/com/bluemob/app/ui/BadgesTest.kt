package com.bluemob.app.ui

import com.bluemob.app.ui.profile.BadgeStats
import com.bluemob.app.ui.profile.Badges
import com.bluemob.app.ui.profile.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgesTest {
    @Test fun levelsGrowSlowerAsTheyGoUp() {
        assertEquals(1, Badges.levelFor(0)); assertEquals(1, Badges.levelFor(99)); assertEquals(2, Badges.levelFor(100))
        assertEquals(3, Badges.levelFor(300)); assertEquals(4, Badges.levelFor(600)); assertEquals(10, Badges.levelFor(4_500))
        (0..20_000 step 37).forEach { xp -> val l = Badges.levelFor(xp); assertTrue(Badges.xpForLevel(l) <= xp && xp < Badges.xpForLevel(l + 1)) }
    }

    @Test fun medalsAndXp() {
        val p = Badges.of(BadgeStats(guidesRead = 12, sosContactsAccepted = 1))
        val reader = p.tracks.first { it.id == "reader" }
        assertEquals(Tier.SILVER, reader.tier)
        assertEquals("13 more guides for Gold", reader.how())
        assertTrue("reader:1" in p.earnedKeys && "covered:0" in p.earnedKeys)
        assertEquals(50 + 150 + 50 + 12 * 10 + 50, p.xp)
    }

    @Test fun secretBadgesStayHiddenUntilWon() {
        assertTrue(Badges.of(BadgeStats()).tracks.first { it.id == "night" }.hidden)
        assertEquals(Tier.BRONZE, Badges.of(BadgeStats(nightMessages = 1)).tracks.first { it.id == "night" }.tier)
    }
}

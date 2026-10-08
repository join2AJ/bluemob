package com.bluemob.app.guide

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The packs the relay serves (server/guides/) must read cleanly in the app. */
class GuidePacksTest {
    private val dir = listOf(File("../server/guides"), File("server/guides")).firstOrNull { it.isDirectory }

    @Test fun everyPackParsesWithUniquePrefixedIds() {
        assumeTrue(dir != null)
        val files = dir!!.listFiles { f -> f.name.endsWith(".json") }!!
        assertTrue(files.size >= 4)
        val ids = mutableSetOf<String>()
        files.forEach { f ->
            val json = JSONObject(f.readText())
            val (info, articles) = GuidePacks.parse(json)
            assertEquals(json.getJSONArray("articles").length(), articles.size)
            articles.forEach { a ->
                assertTrue(a.id.startsWith(info.id + "-"))
                assertTrue("${a.id} needs steps", a.steps.size >= 2)
                assertTrue("duplicate ${a.id}", ids.add(a.id))
                assertTrue("${a.id} clashes with a built-in guide", GuideContent.articles.none { it.id == a.id })
            }
        }
    }
}

class GuideDashboardTest {
    @Test fun countsWhatsHereAndWhatsBeenRead() {
        val all = GuideContent.articles
        val first = all[0].id
        val second = all[1].id
        val d = GuideDashboard.of(all, saved = setOf(first, "gone"), read = mapOf(first to 10L, second to 20L, "gone" to 30L), isNew = { it == first }, packs = 0)
        assertEquals(all.size, d.total)
        assertEquals(1, d.saved)
        assertEquals(2, d.readCount)
        assertEquals(1, d.newCount)
        assertEquals(second, d.lastRead)
        assertEquals(all.size, d.byCategory.values.sum())
        // The guide of the day stays put all day, and moves on the next.
        assertEquals(GuideDashboard.ofTheDay(all, 100), GuideDashboard.ofTheDay(all, 100))
        assertTrue(GuideDashboard.ofTheDay(all, 100) != GuideDashboard.ofTheDay(all, 101))
    }
}

/** Every guide, built in or downloadable, has its own animation (not just its topic's). */
class GuideScenesTest {
    @Test fun everyGuideHasItsOwnScene() {
        val dir = listOf(File("../server/guides"), File("server/guides")).firstOrNull { it.isDirectory }
        val packIds = dir?.listFiles { f -> f.name.endsWith(".json") }?.flatMap { GuidePacks.parse(JSONObject(it.readText())).second.map { a -> a.id } }.orEmpty()
        val missing = (GuideContent.articles + GuideExtra.articles).map { it.id }.plus(packIds).filterNot { com.bluemob.app.ui.guide.GuideScenes.has(it) }
        assertTrue("No animation for: $missing", missing.isEmpty())
    }
}

class GuideQuizTest {
    @org.junit.Test fun everyBuiltInGuideGetsAQuizWithOneRightAnswer() {
        val all = GuideContent.all()
        all.forEach { a ->
            val qs = GuideQuiz.of(a, all)
            org.junit.Assert.assertTrue("${a.id} has questions", qs.isNotEmpty())
            qs.forEach { q -> org.junit.Assert.assertTrue(q.answer in q.options.indices); org.junit.Assert.assertEquals(q.options.size, q.options.distinct().size) }
        }
    }

    @org.junit.Test fun streakCountsDaysInARow() {
        val day = 86_400_000L
        val now = 1_760_000_000_000L
        val days = setOf(Streak.dayKey(now), Streak.dayKey(now - day), Streak.dayKey(now - 2 * day), Streak.dayKey(now - 5 * day))
        org.junit.Assert.assertEquals(3, Streak.of(days, now))
        org.junit.Assert.assertEquals(2, Streak.of(days - Streak.dayKey(now), now))
        org.junit.Assert.assertEquals(0, Streak.of(emptySet(), now))
    }
}

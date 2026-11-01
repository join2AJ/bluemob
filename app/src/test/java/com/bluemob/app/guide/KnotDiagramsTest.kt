package com.bluemob.app.guide

import com.bluemob.app.ui.guide.KnotDiagrams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KnotDiagramsTest {
    private val knots = KnotDiagrams.parse(File("src/main/assets/knots.json").readText())

    @Test fun everyKnotGuideHasAnAnimation() {
        GuideKnots.articles.filter { it.id != "knots-which" }.forEach { a ->
            assertTrue("${a.id} has no animation", a.id.removePrefix("knots-") in knots)
        }
    }

    @Test fun guideStepsAreTheAnimationCaptions() {
        GuideKnots.articles.forEach { a ->
            knots[a.id.removePrefix("knots-")]?.let { k -> assertEquals(a.id, k.steps, a.steps) }
        }
    }

    @Test fun everyStepIsDrawnAndEveryPointIsOnTheGrid() {
        knots.values.forEach { k ->
            k.ropes.forEach { r ->
                assertTrue(k.id, r.pts.size >= 2)
                r.pts.forEach { p -> assertTrue("${k.id} off the grid", p.x in -1f..17f && p.y in -1f..11f); assertTrue(k.id, p.step in k.steps.indices) }
            }
            // Each step except "pull tight" moves something: a traced part or a new shape.
            val traced = k.ropes.flatMap { r -> r.pts.map { it.step } }.toSet() + k.ropes.flatMap { it.morph.keys }
            k.steps.indices.filter { it != k.pull }.forEach { st -> assertTrue("${k.id} step $st draws nothing", st in traced) }
        }
    }

    @Test fun morphShapesMatchTheRope() {
        knots.values.forEach { k -> k.ropes.forEach { r -> r.morph.values.forEach { assertEquals(k.id, r.pts.size, it.size) } } }
    }
}

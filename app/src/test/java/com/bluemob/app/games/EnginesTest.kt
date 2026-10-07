package com.bluemob.app.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EnginesTest {
    private fun playAll(e: Engine, moves: List<Pair<Int, Int>>): List<Int> {
        var b = e.empty()
        moves.forEachIndexed { i, (who, spot) -> b = e.play(b, who, spot, i + 1)!!.first }
        return b
    }

    @Test fun infiniteTicTacToeKeepsThreeMarksAndRemovesTheOldest() {
        val e = InfiniteTicTacToe
        // X: 0, 1, 5 · O: 3, 4, 8 (no lines yet)
        var b = playAll(e, listOf(1 to 0, 2 to 3, 1 to 1, 2 to 4, 1 to 5, 2 to 8))
        assertEquals(3, b.count { e.owner(it) == 1 })
        assertEquals(0, e.fading(b, 1)) // X's oldest (cell 0) goes next
        b = e.play(b, 1, 6, 7)!!.first
        assertEquals(0, b[0]) // removed
        assertEquals(3, b.count { e.owner(it) == 1 })
        assertNull(e.play(b, 2, 3, 8)) // taken cells can't be played, including your own fading one
    }

    @Test fun infiniteTicTacToeWinsAfterRemovalAndNeverDraws() {
        val e = InfiniteTicTacToe
        val b = playAll(e, listOf(1 to 0, 2 to 3, 1 to 1, 2 to 4, 1 to 2))
        assertEquals(1, e.winner(b)?.first)
        assertTrue(e.over(b))
        // Play long random games: the board never fills and someone always can move.
        repeat(20) { seed ->
            var g = e.empty(); var who = 1; var n = 1
            val rnd = Random(seed)
            while (!e.over(g) && n < 200) { val m = e.computerMove(g, who, rnd.nextBoolean(), rnd)!!; g = e.play(g, who, m, n++)!!.first; who = 3 - who }
            assertTrue(g.count { it != 0 } <= 6)
        }
    }

    @Test fun computerTakesTheWinAndBlocks() {
        val e = InfiniteTicTacToe
        val win = playAll(e, listOf(2 to 0, 1 to 3, 2 to 1, 1 to 4))
        assertEquals(2, e.computerMove(win, 2, true))
        val block = playAll(e, listOf(1 to 0, 2 to 8, 1 to 1))
        assertEquals(2, e.computerMove(block, 2, true))
    }

    @Test fun dotsAndBoxesClosingABoxGivesAnotherTurn() {
        val e = DotsAndBoxes
        var b = e.empty()
        val sides = e.sides(0, 0)
        b = e.play(b, 1, sides[0], 1)!!.first
        b = e.play(b, 2, sides[1], 2)!!.first
        b = e.play(b, 1, sides[2], 3)!!.first
        val (after, again) = e.play(b, 2, sides[3], 4)!!
        assertTrue(again)
        assertEquals(2, after[e.LINES])
        assertEquals(0 to 1, e.points(after))
        assertNull(e.play(after, 1, sides[0], 5))
    }

    @Test fun dotsAndBoxesFullGameEndsWithAWinnerOrDraw() {
        val e = DotsAndBoxes
        var b = e.empty(); var who = 1; var n = 1
        while (!e.over(b)) {
            val m = e.computerMove(b, who, true, Random(n))!!
            val (nb, again) = e.play(b, who, m, n++)!!
            b = nb; if (!again) who = 3 - who
        }
        val (x, y) = e.points(b)
        assertEquals(9, x + y)
        if (x != y) assertNotNull(e.winner(b))
    }

    @Test fun computerDoesNotGiveAwayABoxWhenItCanAvoidIt() {
        val e = DotsAndBoxes
        var b = e.empty()
        val s = e.sides(1, 1)
        b = e.play(b, 1, s[0], 1)!!.first
        b = e.play(b, 2, s[1], 2)!!.first
        val move = e.computerMove(b, 1, false, Random(1))!!
        assertFalse(move in s)
    }

    @Test fun quizIsTheSameOnBothPhonesAndScoresRightAnswers() {
        val qs = SurvivalQuiz.questionsFor("g-abc")
        assertEquals(qs, SurvivalQuiz.questionsFor("g-abc"))
        var b = SurvivalQuiz.empty()
        b = SurvivalQuiz.play(b, 1, 0 * 4 + qs[0].right, 1)!!.first
        b = SurvivalQuiz.play(b, 2, 0 * 4 + (qs[0].right + 1) % 4, 1)!!.first
        assertEquals(1 to 0, SurvivalQuiz.score(b, qs))
        assertNull(SurvivalQuiz.play(b, 1, 0 * 4 + 1, 2)) // can't answer twice
    }

    @Test fun matchTurnsFollowExtraTurnsInDotsAndBoxes() {
        var m = Match("g-test", DotsAndBoxes.code, "peer", "Asha", iInvited = true, state = MatchState.PLAYING)
        assertTrue(m.myTurn)
        val s = DotsAndBoxes.sides(0, 0)
        m = MatchRules.move(m, 1, s[0], 1, 0); assertFalse(m.myTurn)
        m = MatchRules.move(m, 2, s[1], 2, 0); assertTrue(m.myTurn)
        m = MatchRules.move(m, 1, s[2], 3, 0); assertFalse(m.myTurn)
        m = MatchRules.move(m, 2, s[3], 4, 0) // they close the box: still their turn
        assertFalse(m.myTurn)
        assertEquals(2, m.turn)
    }

    @Test fun fiveInARowWinsBlocksAndTakesTheWin() {
        val n = FiveInARow.N
        var b = FiveInARow.empty()
        // Four of player 1 across the middle with an open end: the computer (2) must block.
        for (c in 2..5) b = FiveInARow.play(b, 1, 4 * n + c, 0)!!.first
        val block = FiveInARow.computerMove(b, 2, hard = true)!!
        assertTrue(block == 4 * n + 1 || block == 4 * n + 6)
        // Its own four: it completes five instead of blocking.
        var own = FiveInARow.empty()
        for (r in 0..3) own = FiveInARow.play(own, 2, r * n, 0)!!.first
        own = FiveInARow.play(own, 1, 8 * n + 8, 0)!!.first
        assertEquals(4 * n, FiveInARow.computerMove(own, 2, hard = true))
        val won = FiveInARow.play(own, 2, 4 * n, 0)!!.first
        assertEquals(2, FiveInARow.winner(won)?.first)
    }

    @Test fun everyQuizQuestionExplainsItself() {
        SurvivalQuiz.QUESTIONS.forEach { q ->
            assertTrue(q.text, q.why.isNotBlank())
            assertEquals(q.text, q.options.size, q.wrong.size)
            q.options.indices.filter { it != q.right }.forEach { assertTrue("${q.text}: ${q.options[it]}", q.wrong[it].isNotBlank()) }
        }
    }

    @Test fun classicTicTacToeIsHiddenButStillPlays() {
        assertFalse(TicTacToeEngine.listed)
        assertEquals(TicTacToeEngine, Engine.of(Match.TTT))
    }
}

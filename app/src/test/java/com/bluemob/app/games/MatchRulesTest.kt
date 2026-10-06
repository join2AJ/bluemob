package com.bluemob.app.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchRulesTest {
    private fun playing(game: String, iInvited: Boolean) =
        Match("g-test", game, "peer", "Asha", iInvited = iInvited, state = MatchState.PLAYING)

    @Test fun inviterMovesFirstThenTurnsAlternate() {
        var m = playing(Match.TTT, iInvited = true)
        assertTrue(m.myTurn)
        m = MatchRules.move(m, 1, 4, 1, 0)
        assertFalse(m.myTurn)
        // Their move, then mine again.
        m = MatchRules.move(m, 2, 0, 2, 0)
        assertTrue(m.myTurn)
        assertEquals(listOf(2, 0, 0, 0, 1, 0, 0, 0, 0), m.board)
    }

    @Test fun outOfTurnDuplicateAndStaleMovesAreIgnored() {
        val m = playing(Match.TTT, iInvited = false)
        // The other phone invited, so it moves first: my move now is out of turn.
        assertSame(m, MatchRules.move(m, 1, 4, 1, 0))
        val a = MatchRules.move(m, 2, 4, 1, 0)
        // The same packet arriving twice.
        assertSame(a, MatchRules.move(a, 2, 4, 1, 0))
        // A move for an old round.
        assertSame(a, MatchRules.move(a, 1, 0, 2, 5))
        // A taken cell.
        assertSame(a, MatchRules.move(a, 1, 4, 2, 0))
    }

    @Test fun winScoresAndAgainSwapsWhoStarts() {
        var m = playing(Match.TTT, iInvited = true)
        var n = 1
        for ((who, cell) in listOf(1 to 0, 2 to 3, 1 to 1, 2 to 4, 1 to 2)) m = MatchRules.move(m, who, cell, n++, 0)
        assertTrue(m.over)
        assertEquals(1, m.myScore)
        assertEquals(0, m.theirScore)
        assertFalse(m.myTurn)
        m = MatchRules.again(m, 1)
        assertEquals(1, m.round)
        assertFalse(m.over)
        // Round 1: the invitee starts.
        assertFalse(m.myTurn)
        assertEquals(1, m.myScore)
    }

    @Test fun againOnlyAfterTheRoundIsOver() {
        val m = playing(Match.C4, iInvited = true)
        assertSame(m, MatchRules.again(m, 1))
    }

    @Test fun connectFourDropsToTheBottomAndRejectsBadColumns() {
        var m = playing(Match.C4, iInvited = true)
        m = MatchRules.move(m, 1, 3, 1, 0)
        assertEquals(1, m.board[5 * ConnectFour.COLS + 3])
        assertSame(m, MatchRules.move(m, 2, 9, 2, 0))
        m = MatchRules.move(m, 2, 3, 2, 0)
        assertEquals(2, m.board[4 * ConnectFour.COLS + 3])
    }

    @Test fun noMovesBeforeTheInviteIsAccepted() {
        val m = Match("g-x", Match.TTT, "peer", "Asha", iInvited = true, state = MatchState.INVITING)
        assertFalse(m.myTurn)
        assertSame(m, MatchRules.move(m, 1, 0, 1, 0))
    }
}

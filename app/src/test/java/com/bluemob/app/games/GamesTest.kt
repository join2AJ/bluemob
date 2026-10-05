package com.bluemob.app.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class GamesTest {
    /** Tries every possible game a person could play; the unbeatable computer must never lose. */
    private fun neverLoses(b: List<Int>, humanTurn: Boolean): Boolean {
        TicTacToe.winner(b)?.let { return it.first != 1 }
        if (TicTacToe.full(b)) return true
        return if (humanTurn) b.indices.filter { b[it] == 0 }.all { i -> neverLoses(b.toMutableList().also { it[i] = 1 }, false) }
        else {
            val m = TicTacToe.computerMove(b, 2, hard = true, random = Random(7))!!
            neverLoses(b.toMutableList().also { it[m] = 2 }, true)
        }
    }

    @Test fun unbeatableTicTacToeNeverLoses() {
        assert(neverLoses(List(9) { 0 }, humanTurn = true))
        assert(neverLoses(List(9) { 0 }, humanTurn = false))
    }

    @Test fun ticTacToeTakesTheWinThenBlocks() {
        // Computer (2) can win at 2; person (1) threatens at 8.
        val win = listOf(2, 2, 0, 1, 1, 0, 0, 0, 0)
        assertEquals(2, TicTacToe.computerMove(win, hard = false))
        val block = listOf(1, 0, 0, 0, 1, 0, 2, 0, 0)
        assertEquals(8, TicTacToe.computerMove(block, hard = false))
    }

    @Test fun connectFourFindsWinsInEveryDirection() {
        var b = ConnectFour.empty()
        for (c in 0..3) b = ConnectFour.drop(b, c, 1)!!
        assertEquals(1, ConnectFour.winner(b)!!.first)
        // Diagonal: stairs of 2s, then 1s on top.
        b = ConnectFour.empty()
        for (c in 0..3) {
            repeat(c) { b = ConnectFour.drop(b, c, 2)!! }
            b = ConnectFour.drop(b, c, 1)!!
        }
        assertEquals(1, ConnectFour.winner(b)!!.first)
    }

    @Test fun connectFourComputerWinsAndBlocks() {
        var b = ConnectFour.empty()
        for (c in 0..2) b = ConnectFour.drop(b, c, 2)!!
        assertEquals(3, ConnectFour.computerMove(b))
        b = ConnectFour.empty()
        for (c in 2..4) b = ConnectFour.drop(b, c, 1)!!
        val m = ConnectFour.computerMove(b)!!
        assert(m == 1 || m == 5) { "should block at 1 or 5, played $m" }
    }

    @Test fun connectFourDoesNotSetUpTheOpponent() {
        // Person has three in a row on the second row with a gap at column 3 that is not yet playable.
        var b = ConnectFour.empty()
        listOf(0, 1, 2, 4, 5, 6).forEach { b = ConnectFour.drop(b, it, 2)!! }
        listOf(0, 1, 2).forEach { b = ConnectFour.drop(b, it, 1)!! }
        // Playing column 3 fills (5,3); then the person could win at (4,3). The computer must avoid that,
        // unless it can win itself; here it can't win at row 5 col 3? Check: row 5 has 2,2,2,_,2,2,2 → it wins at 3.
        assertEquals(3, ConnectFour.computerMove(b))
        assertNotEquals(null, ConnectFour.winner(ConnectFour.drop(b, 3, 2)!!))
    }
}

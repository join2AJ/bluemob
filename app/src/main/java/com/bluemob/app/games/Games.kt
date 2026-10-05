package com.bluemob.app.games

import kotlin.random.Random

/** 0 = empty, 1 = you, 2 = the other player (a person nearby or the computer). */
object TicTacToe {
    val LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6),
    )

    /** The winner (1 or 2) and their line, or null. */
    fun winner(b: List<Int>): Pair<Int, List<Int>>? =
        LINES.firstOrNull { (a, x, c) -> b[a] != 0 && b[a] == b[x] && b[a] == b[c] }?.let { b[it[0]] to it }

    fun full(b: List<Int>) = b.none { it == 0 }

    /**
     * The computer's move for [me]. [hard] plays perfectly (minimax); otherwise it wins or blocks when it can and
     * picks freely the rest of the time, so it can be beaten.
     */
    fun computerMove(b: List<Int>, me: Int = 2, hard: Boolean = true, random: Random = Random.Default): Int? {
        val free = b.indices.filter { b[it] == 0 }
        if (free.isEmpty()) return null
        val them = 3 - me
        free.firstOrNull { winner(b.with(it, me))?.first == me }?.let { return it }
        free.firstOrNull { winner(b.with(it, them))?.first == them }?.let { return it }
        if (!hard) return if (4 in free && random.nextBoolean()) 4 else free.random(random)
        val scored = free.map { it to minimax(b.with(it, me), them, me) }
        val best = scored.maxOf { it.second }
        return scored.filter { it.second == best }.map { it.first }.random(random)
    }

    private fun minimax(b: List<Int>, turn: Int, me: Int): Int {
        winner(b)?.let { return if (it.first == me) 10 - b.count { c -> c != 0 } else b.count { c -> c != 0 } - 10 }
        if (full(b)) return 0
        val scores = b.indices.filter { b[it] == 0 }.map { minimax(b.with(it, turn), 3 - turn, me) }
        return if (turn == me) scores.max() else scores.min()
    }

    private fun List<Int>.with(i: Int, v: Int) = toMutableList().also { it[i] = v }
}

/** 7 columns × 6 rows, index = row * 7 + col, row 0 at the top. */
object ConnectFour {
    const val COLS = 7
    const val ROWS = 6

    fun empty() = List(COLS * ROWS) { 0 }

    /** The row a piece dropped in [col] lands in, or null if the column is full. */
    fun dropRow(b: List<Int>, col: Int): Int? = (ROWS - 1 downTo 0).firstOrNull { b[it * COLS + col] == 0 }

    fun drop(b: List<Int>, col: Int, who: Int): List<Int>? {
        val row = dropRow(b, col) ?: return null
        return b.toMutableList().also { it[row * COLS + col] = who }
    }

    /** The winner and the four winning cells, or null. */
    fun winner(b: List<Int>): Pair<Int, List<Int>>? {
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val who = b[r * COLS + c]
            if (who == 0) continue
            for ((dr, dc) in dirs) {
                val cells = (0 until 4).map { k -> (r + dr * k) to (c + dc * k) }
                if (cells.all { (rr, cc) -> rr in 0 until ROWS && cc in 0 until COLS && b[rr * COLS + cc] == who }) {
                    return who to cells.map { (rr, cc) -> rr * COLS + cc }
                }
            }
        }
        return null
    }

    fun full(b: List<Int>) = (0 until COLS).all { dropRow(b, it) == null }

    /**
     * The computer's column: win now; block a win; avoid moves that let the other player win straight after;
     * then prefer the centre and building its own lines (a light look-ahead that's fun to play against).
     */
    fun computerMove(b: List<Int>, me: Int = 2, random: Random = Random.Default): Int? {
        val them = 3 - me
        val cols = (0 until COLS).filter { dropRow(b, it) != null }
        if (cols.isEmpty()) return null
        cols.firstOrNull { winner(drop(b, it, me)!!)?.first == me }?.let { return it }
        cols.firstOrNull { winner(drop(b, it, them)!!)?.first == them }?.let { return it }
        val safe = cols.filter { c ->
            val after = drop(b, c, me)!!
            (0 until COLS).none { o -> drop(after, o, them)?.let { winner(it)?.first == them } == true }
        }.ifEmpty { cols }
        val scored = safe.map { c -> c to score(drop(b, c, me)!!, me) - kotlin.math.abs(c - 3) * 2 }
        val best = scored.maxOf { it.second }
        return scored.filter { it.second >= best - 1 }.map { it.first }.random(random)
    }

    /** Counts open lines of 2 and 3 for [me], minus the other player's. */
    private fun score(b: List<Int>, me: Int): Int {
        var s = 0
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until ROWS) for (c in 0 until COLS) for ((dr, dc) in dirs) {
            val cells = (0 until 4).map { k -> (r + dr * k) to (c + dc * k) }
            if (!cells.all { (rr, cc) -> rr in 0 until ROWS && cc in 0 until COLS }) continue
            val v = cells.map { (rr, cc) -> b[rr * COLS + cc] }
            val mine = v.count { it == me }
            val theirs = v.count { it == 3 - me }
            if (theirs == 0) s += when (mine) { 3 -> 8; 2 -> 3; else -> 0 }
            if (mine == 0) s -= when (theirs) { 3 -> 10; 2 -> 3; else -> 0 }
        }
        return s
    }
}

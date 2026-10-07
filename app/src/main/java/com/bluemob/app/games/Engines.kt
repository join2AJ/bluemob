package com.bluemob.app.games

import kotlin.random.Random

/**
 * The rules of one game, from one phone's side of the board: 1 = this player, 2 = the other (a person or the
 * computer). Every engine is a pure function of the board, so both phones compute the same game from the same moves.
 */
interface Engine {
    val code: String
    val title: String
    val emoji: String
    val blurb: String
    /** Shown before the first game: (title, text). */
    val rules: List<Pair<String, String>>
    /** False for games where both players move whenever they like (the quiz). */
    val turnBased: Boolean get() = true
    /** Shown in the game list. Classic tic-tac-toe is hidden (Infinite tic-tac-toe replaces it) but still plays with older phones. */
    val listed: Boolean get() = true
    /** One line: how you win. */
    val goal: String get() = rules.lastOrNull()?.second ?: blurb
    fun empty(): List<Int>
    /** Plays [spot] for [who] as move number [n]. The new board, and whether [who] moves again; null if not allowed. */
    fun play(board: List<Int>, who: Int, spot: Int, n: Int): Pair<List<Int>, Boolean>?
    /** The winner (1 or 2) and the cells to highlight, or null. */
    fun winner(board: List<Int>): Pair<Int, List<Int>>?
    fun over(board: List<Int>): Boolean
    /** A move for player [me]; [hard] plays its best. */
    fun computerMove(board: List<Int>, me: Int, hard: Boolean, random: Random = Random.Default): Int?
    /** Score for each side, for games scored by points (dots and boxes, the quiz). */
    fun points(board: List<Int>): Pair<Int, Int>? = null

    companion object {
        val ALL: List<Engine> = listOf(TicTacToeEngine, InfiniteTicTacToe, ConnectFourEngine, FiveInARow, DotsAndBoxes, SurvivalQuiz)
        fun of(code: String): Engine? = ALL.firstOrNull { it.code == code }
    }
}

object TicTacToeEngine : Engine {
    override val code = Match.TTT
    override val title = "Tic-tac-toe"
    override val emoji = "⭕"
    override val blurb = "Three in a row. Easy or unbeatable."
    override val listed = false
    override val rules = listOf("Take turns" to "Place your mark on the 3×3 board.", "Win" to "Get 3 in a row: across, down or diagonal.")
    override fun empty() = List(9) { 0 }
    override fun play(board: List<Int>, who: Int, spot: Int, n: Int) =
        if (spot in 0..8 && board[spot] == 0) board.toMutableList().also { it[spot] = who } to false else null
    override fun winner(board: List<Int>) = TicTacToe.winner(board)
    override fun over(board: List<Int>) = winner(board) != null || TicTacToe.full(board)
    override fun computerMove(board: List<Int>, me: Int, hard: Boolean, random: Random) = TicTacToe.computerMove(board, me, hard, random)
}

/**
 * Infinite tic-tac-toe: each player keeps at most 3 marks. Placing a 4th removes your oldest (shown faded), so the
 * board never fills up and there are no draws. You can't play where your mark just disappeared: that cell is still
 * taken when you choose. A cell holds `move number * 10 + who`, so the oldest mark has the smallest number.
 */
object InfiniteTicTacToe : Engine {
    override val code = "ttt3"
    override val title = "Infinite tic-tac-toe"
    override val emoji = "♾️"
    override val blurb = "Only 3 marks each: your oldest disappears."
    override val rules = listOf(
        "Take turns" to "Players take turns placing marks on the 3×3 board.",
        "Max 3 marks" to "Each player can have only 3 marks on the board at a time.",
        "Oldest disappears" to "When you place your 4th mark, your oldest is removed. A faded mark shows which one will go next.",
        "Can't replace" to "You can't place your new mark where your mark just disappeared.",
        "Win" to "Get 3 of your marks in a row: across, down or diagonal.",
    )
    override fun empty() = List(9) { 0 }
    fun owner(v: Int) = v % 10

    /** The cell of [who]'s mark that goes next, if they already have 3. */
    fun fading(board: List<Int>, who: Int): Int? {
        val mine = board.indices.filter { board[it] != 0 && owner(board[it]) == who }
        return if (mine.size >= 3) mine.minByOrNull { board[it] / 10 } else null
    }

    override fun play(board: List<Int>, who: Int, spot: Int, n: Int): Pair<List<Int>, Boolean>? {
        if (spot !in 0..8 || board[spot] != 0 || winner(board) != null) return null
        val b = board.toMutableList()
        val old = fading(board, who)
        b[spot] = n * 10 + who
        if (old != null) b[old] = 0
        return b to false
    }

    override fun winner(board: List<Int>) = TicTacToe.winner(board.map { owner(it) })
    override fun over(board: List<Int>) = winner(board) != null

    override fun computerMove(board: List<Int>, me: Int, hard: Boolean, random: Random): Int? {
        val free = board.indices.filter { board[it] == 0 }
        if (free.isEmpty()) return null
        val them = 3 - me
        val n = (board.maxOrNull() ?: 0) / 10 + 1
        free.firstOrNull { c -> play(board, me, c, n)?.let { winner(it.first)?.first == me } == true }?.let { return it }
        // Block: a cell where they'd win next move (after their own oldest mark goes).
        free.firstOrNull { c -> play(board, them, c, n)?.let { winner(it.first)?.first == them } == true }?.let { return it }
        if (hard) {
            // Avoid moves that hand them a win on their next turn.
            val safe = free.filter { c ->
                val after = play(board, me, c, n)?.first ?: return@filter false
                after.indices.filter { after[it] == 0 }.none { o -> play(after, them, o, n)?.let { winner(it.first)?.first == them } == true }
            }
            if (safe.isNotEmpty()) return if (4 in safe) 4 else safe.random(random)
        }
        return if (4 in free && random.nextBoolean()) 4 else free.random(random)
    }
}

object ConnectFourEngine : Engine {
    override val code = Match.C4
    override val title = "Connect 4"
    override val emoji = "🔴"
    override val blurb = "Drop discs, line up four."
    override val rules = listOf("Drop" to "Tap a column to drop a disc to the lowest free spot.", "Win" to "Four in a row: across, down or diagonal.")
    override fun empty() = ConnectFour.empty()
    override fun play(board: List<Int>, who: Int, spot: Int, n: Int) =
        if (spot in 0 until ConnectFour.COLS) ConnectFour.drop(board, spot, who)?.let { it to false } else null
    override fun winner(board: List<Int>) = ConnectFour.winner(board)
    override fun over(board: List<Int>) = winner(board) != null || ConnectFour.full(board)
    override fun computerMove(board: List<Int>, me: Int, hard: Boolean, random: Random) = ConnectFour.computerMove(board, me, random)
}

/**
 * Dots and boxes on a 4×4 grid of dots (3×3 boxes). Draw one line a turn; finish a box and it's yours, and you go
 * again. Most boxes wins. Board: 12 horizontal lines (row by row), 12 vertical lines, then 9 box owners.
 */
object DotsAndBoxes : Engine {
    override val code = "dots"
    override val title = "Dots & Boxes"
    override val emoji = "🔲"
    override val blurb = "Close a box to keep it, and go again."
    override val rules = listOf(
        "Draw a line" to "Take turns joining two dots next to each other.",
        "Close a box" to "Draw the 4th side of a box and it's yours. Then you go again.",
        "Win" to "When every line is drawn, the most boxes wins.",
    )
    const val N = 3
    const val H = N * (N + 1)
    const val LINES = 2 * H
    override fun empty() = List(LINES + N * N) { 0 }

    /** The four lines around box (r, c). */
    fun sides(r: Int, c: Int) = listOf(r * N + c, (r + 1) * N + c, H + r * (N + 1) + c, H + r * (N + 1) + c + 1)
    /** Boxes touching line [l]. */
    fun boxesOf(l: Int): List<Pair<Int, Int>> = (0 until N).flatMap { r -> (0 until N).map { c -> r to c } }.filter { (r, c) -> l in sides(r, c) }

    override fun play(board: List<Int>, who: Int, spot: Int, n: Int): Pair<List<Int>, Boolean>? {
        if (spot !in 0 until LINES || board[spot] != 0) return null
        val b = board.toMutableList()
        b[spot] = who
        var closed = false
        boxesOf(spot).forEach { (r, c) ->
            if (sides(r, c).all { b[it] != 0 } && b[LINES + r * N + c] == 0) { b[LINES + r * N + c] = who; closed = true }
        }
        // Closing a box means another turn (unless the game just ended).
        return b to (closed && !over(b))
    }

    override fun points(board: List<Int>) = board.drop(LINES).count { it == 1 } to board.drop(LINES).count { it == 2 }
    override fun over(board: List<Int>) = board.take(LINES).none { it == 0 }
    override fun winner(board: List<Int>): Pair<Int, List<Int>>? {
        if (!over(board)) return null
        val (a, b) = points(board)
        return when { a > b -> 1 to emptyList(); b > a -> 2 to emptyList(); else -> null }
    }

    override fun computerMove(board: List<Int>, me: Int, hard: Boolean, random: Random): Int? {
        val free = (0 until LINES).filter { board[it] == 0 }
        if (free.isEmpty()) return null
        val sidesDrawn = { r: Int, c: Int, b: List<Int> -> sides(r, c).count { b[it] != 0 } }
        // Take a box if we can.
        free.firstOrNull { l -> boxesOf(l).any { (r, c) -> sidesDrawn(r, c, board) == 3 } }?.let { return it }
        // Don't draw a box's 3rd side (that gives it away), if there's any other choice.
        val safe = free.filter { l -> boxesOf(l).none { (r, c) -> sidesDrawn(r, c, board) == 2 } }
        if (safe.isNotEmpty()) return safe.random(random)
        if (!hard) return free.random(random)
        // Forced to give something away: give away the line that hands over the fewest boxes.
        return free.minByOrNull { l -> chainSize(board.toMutableList().also { it[l] = 3 - me }) }
    }

    /** How many boxes the other player could take in a row after a move. */
    private fun chainSize(board: MutableList<Int>): Int {
        var taken = 0
        while (true) {
            val l = (0 until LINES).firstOrNull { l -> board[l] == 0 && boxesOf(l).any { (r, c) -> sides(r, c).count { board[it] != 0 } == 3 } } ?: return taken
            board[l] = 2
            taken++
        }
    }
}

/**
 * A survival quiz from the guide. Both players answer the same questions, each at their own pace; a correct answer
 * scores a point. Board: for each question, this player's answer + 1, then the other's (0 = not answered yet).
 * The spot for a move is question * 4 + choice.
 */
object SurvivalQuiz : Engine {
    override val code = "quiz"
    override val title = "Survival quiz"
    override val emoji = "🧠"
    override val blurb = "Learn the guide by playing. Most right answers wins."
    override val rules = listOf("Answer" to "Same questions for both of you. Answer at your own pace.", "Win" to "One point per right answer. The guide explains each one.")
    override val turnBased = false

    /**
     * A question, why its answer is right ([why]), and why each other option is wrong ([wrong], same order as
     * [options]; the right one's entry is ignored). The list order must stay the same across versions: both phones
     * pick the same questions from it.
     */
    data class Question(val text: String, val options: List<String>, val right: Int, val guide: String, val why: String = "", val wrong: List<String> = emptyList())

    val QUESTIONS = listOf(
        Question("How long should you cool a burn under running water?", listOf("2 minutes", "5 minutes", "20 minutes", "1 hour"), 2, "burns",
            "20 minutes of cool running water stops the burn going deeper and eases pain, even if it's started a while ago.",
            listOf("Too short: the skin keeps 'cooking' underneath after 2 minutes.", "Still too short to pull the heat out of deeper layers.", "", "Longer than needed, and risks chilling the person (hypothermia), especially children.")),
        Question("Three of anything (fires, whistles, flashes) means…", listOf("All clear", "Help / distress", "Come here", "Danger, stay away"), 1, "signals",
            "Three of anything, repeated, is the international distress signal. Rescuers everywhere know it.",
            listOf("There's no standard 'all clear' signal of three.", "", "Not a standard signal: rescuers would read three as 'help'.", "Three means you need help, not a warning to keep away.")),
        Question("Boil water for at least how long at low altitude?", listOf("10 seconds", "1 minute", "10 minutes", "Until it smells clean"), 1, "purify",
            "A full rolling boil for 1 minute kills germs (3 minutes above about 2,000 m, where water boils cooler).",
            listOf("Not long enough at a full boil to be sure all germs are dead.", "", "Safe, but wastes fuel and water: 1 minute is enough.", "Smell tells you nothing about germs.")),
        Question("Chest compressions in adult CPR should be about…", listOf("1–2 cm deep", "5–6 cm deep", "10 cm deep", "As soft as possible"), 1, "cpr",
            "5–6 cm, hard and fast (100–120 a minute), so blood actually moves to the brain.",
            listOf("Too shallow: it won't push blood around the body.", "", "Too deep: risks serious injury without helping more.", "Soft compressions don't circulate blood. Push hard.")),
        Question("The 30-30 rule is about…", listOf("Water", "Lightning", "Fire", "Food"), 1, "lightning",
            "If thunder comes less than 30 s after the flash, take shelter; wait 30 minutes after the last thunder before going out.",
            listOf("The water rule of thumb is 3 days without it, not 30-30.", "", "Fire has no 30-30 rule.", "The food rule of thumb is about 3 weeks without it.")),
        Question("What do you do first in an earthquake?", listOf("Run outside", "Stand in a lift", "Drop, cover, hold on", "Open the windows"), 2, "quake",
            "Drop to your knees, take cover under something sturdy, and hold on until the shaking stops.",
            listOf("Most injuries happen from falling debris while people run.", "Lifts can stop or fall: never use one in a quake.", "", "Wastes time and puts you near breaking glass.")),
        Question("When you're lost, the 'S' in STOP stands for…", listOf("Search", "Sprint", "Stop", "Shout"), 2, "lost",
            "STOP: Stop, Think, Observe, Plan. Standing still first prevents getting more lost.",
            listOf("Searching right away usually takes people further from where they'll be looked for.", "Running wastes energy and gets you more lost.", "", "Shouting can help later, but the first step is to stop.")),
        Question("In a shelter, most body heat is lost to…", listOf("The sky", "The ground", "The wind only", "Your clothes"), 1, "shelter",
            "Cold ground pulls heat out of you fast. Insulate underneath with leaves, packs or branches first.",
            listOf("Some heat goes up, but much less than into the ground.", "", "Wind matters, but the ground takes more when you're lying down.", "Clothes keep heat in; they don't take it away (unless wet).")),
        Question("You can usually last about how long without water?", listOf("12 hours", "3 days", "2 weeks", "1 month"), 1, "find-water",
            "About 3 days (less in heat or with hard work). That's why water comes before food.",
            listOf("You'd be thirsty, but most people survive much longer than 12 hours.", "", "That's closer to food: without water you'd last days, not weeks.", "That's about food, not water.")),
        Question("For severe bleeding, the first thing to do is…", listOf("Give water", "Press hard on the wound", "Raise the head", "Wait for help"), 1, "bleeding",
            "Firm, direct pressure on the wound is the fastest way to slow serious bleeding. Keep pressing.",
            listOf("Water doesn't stop bleeding, and can cause vomiting if they need surgery.", "", "Raising the head doesn't slow bleeding; pressure does.", "Waiting lets them lose blood: press now, then get help.")),
    )
    const val ROUND = 6

    /** The questions for a match, in an order both phones agree on (seeded by the match ID). */
    fun questionsFor(matchId: String): List<Question> = QUESTIONS.shuffled(Random(matchId.hashCode())).take(ROUND)

    override fun empty() = List(ROUND * 2) { 0 }
    override fun play(board: List<Int>, who: Int, spot: Int, n: Int): Pair<List<Int>, Boolean>? {
        val q = spot / 4
        val choice = spot % 4
        val i = q * 2 + (who - 1)
        if (q !in 0 until ROUND || board[i] != 0) return null
        return board.toMutableList().also { it[i] = choice + 1 } to true
    }

    /** Points need the questions (which depend on the match), so the screen scores with [score]. */
    fun score(board: List<Int>, questions: List<Question>): Pair<Int, Int> =
        questions.indices.count { board[it * 2] - 1 == questions[it].right } to questions.indices.count { board[it * 2 + 1] - 1 == questions[it].right }

    override fun over(board: List<Int>) = board.none { it == 0 }
    override fun winner(board: List<Int>): Pair<Int, List<Int>>? = null
    override fun computerMove(board: List<Int>, me: Int, hard: Boolean, random: Random): Int? = null
}


/** Five in a row (gomoku) on a 9×9 board: like tic-tac-toe, but you need five, and the board is big enough to plan. */
object FiveInARow : Engine {
    const val N = 9
    override val code = "five"
    override val title = "Five in a row"
    override val emoji = "⚫"
    override val blurb = "A bigger board: get five in a line."
    override val rules = listOf(
        "Take turns" to "Place one stone at a time anywhere on the 9×9 board.",
        "Block" to "Watch for four of theirs in a line with an open end: block it, or they win next move.",
        "Win" to "Five of your stones in a straight line: across, down or diagonal.",
    )
    override fun empty() = List(N * N) { 0 }
    override fun play(board: List<Int>, who: Int, spot: Int, n: Int) =
        if (spot in board.indices && board[spot] == 0) board.toMutableList().also { it[spot] = who } to false else null

    private val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

    override fun winner(board: List<Int>): Pair<Int, List<Int>>? {
        for (i in board.indices) {
            val p = board[i]; if (p == 0) continue
            val r = i / N; val c = i % N
            for ((dr, dc) in dirs) {
                val cells = (0 until 5).map { k -> (r + dr * k) to (c + dc * k) }
                if (cells.all { (rr, cc) -> rr in 0 until N && cc in 0 until N && board[rr * N + cc] == p }) return p to cells.map { (rr, cc) -> rr * N + cc }
            }
        }
        return null
    }

    override fun over(board: List<Int>) = winner(board) != null || board.none { it == 0 }

    /** How good a cell is for [who]: longer open lines through it score much more. */
    private fun value(board: List<Int>, spot: Int, who: Int): Int {
        val r = spot / N; val c = spot % N
        var total = 0
        for ((dr, dc) in dirs) {
            var count = 1; var open = 0
            for (sign in listOf(1, -1)) {
                var k = 1
                while (true) {
                    val rr = r + dr * k * sign; val cc = c + dc * k * sign
                    if (rr !in 0 until N || cc !in 0 until N) break
                    val v = board[rr * N + cc]
                    if (v == who) { count++; k++ } else { if (v == 0) open++; break }
                }
            }
            total += when {
                count >= 5 -> 100_000
                count == 4 -> if (open > 0) 10_000 else 0
                count == 3 -> if (open == 2) 1_000 else 100
                count == 2 -> if (open == 2) 100 else 10
                else -> open
            }
        }
        return total
    }

    override fun computerMove(board: List<Int>, me: Int, hard: Boolean, random: Random): Int? {
        val free = board.indices.filter { board[it] == 0 }
        if (free.isEmpty()) return null
        if (free.size == board.size) return (N / 2) * N + N / 2
        val them = 3 - me
        // Attack and defence both count; defence a little less, so it wins when it can. Easy mode looks less far.
        val scored = free.map { it to value(board, it, me) + (value(board, it, them) * if (hard) 0.9 else 0.5).toInt() + random.nextInt(if (hard) 3 else 60) }
        return scored.maxByOrNull { it.second }?.first
    }
}

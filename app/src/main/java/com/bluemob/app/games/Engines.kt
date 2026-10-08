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
        Question("A man collapses at the bus stop. He isn't responding and isn't breathing normally. What first?", listOf("Splash water on his face", "Shout for help, call 112, start chest compressions", "Give him sugar or water", "Wait to see if he wakes up"), 1, "cpr",
            "Every minute without CPR cuts survival by about 10%. Get someone to call 112 (and find a defibrillator) while you push hard and fast in the centre of the chest.",
            listOf("Water won't restart a heart, and wastes minutes he doesn't have.", "", "Never put anything in the mouth of someone who isn't awake: it can block the airway.", "If he isn't breathing normally, waiting is the worst choice: start CPR now.")),
        Question("A snake bites your friend's ankle in a field. What should you do?", listOf("Cut the wound and suck out the venom", "Tie a tight cloth above the bite", "Keep them still and calm, remove rings and tight things, get to a hospital fast", "Kill the snake and bring it along"), 2, "snake",
            "Most bites can be treated with anti-venom at a hospital. Moving spreads venom faster, so keep the limb still (like a splint) and carry or drive them.",
            listOf("Cutting causes infection and bleeding, and sucking doesn't remove venom.", "Tight tourniquets can cost the limb and don't stop venom reliably.", "", "Trying to catch it risks a second bite. A photo from a distance is enough, if it's safe.")),
        Question("A street dog bites your child and breaks the skin. First?", listOf("Apply turmeric or chilli on it", "Wash the wound with soap and running water for 15 minutes, then get anti-rabies vaccine", "Bandage it tightly and wait a few days", "Only worry if the dog looks sick"), 1, "bleed",
            "Washing for 15 minutes removes much of the virus. Rabies is almost always fatal once symptoms start, but the vaccine (started the same day) prevents it.",
            listOf("Home remedies don't kill the virus and can infect the wound.", "", "Waiting is dangerous: the vaccine works best when started at once.", "Healthy-looking dogs can carry rabies. Every bite that breaks skin needs the vaccine.")),
        Question("Water is flowing fast across the road during heavy rain. It looks only knee deep. You should…", listOf("Drive through slowly in first gear", "Walk across holding hands", "Turn back and find another way", "Drive through fast so you don't stall"), 2, "flood",
            "15 cm of fast water can knock you off your feet, and 60 cm can float a car. The road under the water may be washed away.",
            listOf("Cars stall and get swept away in far less water than people expect.", "Moving water at knee height can still knock people down.", "", "Speed creates a bow wave and loses grip: you can be swept off the road.")),
        Question("Someone is stuck to a fallen electric wire. How do you help?", listOf("Pull them away by the hand", "Switch off the power at the mains, or push the wire away with dry wood or plastic", "Throw water on them", "Pull the wire with a wet cloth"), 1, "cpr",
            "If you touch them while current flows, you become part of the circuit. Cut the power first, or use something dry that doesn't conduct.",
            listOf("You'd get the same shock the moment you touch them.", "", "Water conducts electricity: you could both be electrocuted.", "Wet cloth conducts electricity.")),
        Question("It's 44 °C. A labourer is confused, his skin is hot and dry, and he's stopped sweating. This is…", listOf("Tiredness: let him rest in the sun", "Heatstroke: move to shade, cool him fast with water and fanning, call 112", "Normal in summer: give hot tea", "Hunger: give food"), 1, "heat",
            "Hot, dry skin with confusion means the body's cooling has failed. Heatstroke can kill within hours. Cool first, then hospital.",
            listOf("Staying in the sun makes heatstroke worse.", "", "Confusion and no sweating are never normal. Hot drinks add heat.", "Food doesn't help: his temperature is the danger.")),
        Question("You smell gas in the kitchen at night. What do you do?", listOf("Switch on the light to look for the leak", "Turn off the cylinder regulator, open windows and doors, go out, don't touch switches", "Light a match to test where it leaks", "Turn on the exhaust fan"), 1, "fire",
            "Any spark (switches, fans, phones near the gas) can ignite it. Cut the supply, let air in, get out, and call for help from outside.",
            listOf("A light switch can spark and ignite the gas.", "", "This can cause an explosion.", "The fan's motor and switch can spark.")),
        Question("A building is on fire and the corridor is full of smoke. How do you get out?", listOf("Take the lift, it's fastest", "Stay low under the smoke, feel doors before opening, use the stairs", "Run upright through the smoke holding your breath", "Hide in a cupboard until it's over"), 1, "fire",
            "Smoke rises: the cleaner air is near the floor. A hot door means fire behind it. Lifts can stop or open onto the fire.",
            listOf("Lifts can fail in a fire and trap you.", "", "Smoke kills faster than flames, and it's thickest at head height.", "Rescuers can't find you, and smoke fills small spaces.")),
        Question("Your clothes catch fire. What do you do?", listOf("Run to find water", "Stop, drop to the ground, and roll", "Wave your arms to put it out", "Pull the burning clothes off"), 1, "burns",
            "Running fans the flames. Dropping and rolling smothers them. Cover your face with your hands while you roll.",
            listOf("Running feeds the fire with air.", "", "Waving feeds the fire too.", "Pulling burning cloth burns your hands and can stick to skin.")),
        Question("Hot oil spills on your arm. What's the best thing to put on it?", listOf("Toothpaste", "Ice", "Cool running water for 20 minutes", "Ghee or butter"), 2, "burns",
            "Cool (not icy) running water for 20 minutes stops the burn going deeper. Then cover loosely with cling film or a clean cloth.",
            listOf("Toothpaste traps heat and can cause infection.", "Ice damages skin further and can cause frostbite.", "", "Fats hold the heat in and make the burn worse.")),
        Question("An adult is choking and can't speak or cough. What helps?", listOf("Give them water to drink", "5 firm blows between the shoulder blades, then 5 abdominal thrusts", "Put your fingers deep in their throat", "Make them lie down and rest"), 1, "choke",
            "Back blows and abdominal thrusts (Heimlich) push air out to force the object up. Repeat until it comes out, and call 112 if it doesn't.",
            listOf("They can't swallow with a blocked airway; water can make it worse.", "", "Blind finger sweeps can push the object deeper.", "Lying down doesn't clear the airway.")),
        Question("A biker is lying still after an accident, helmet on. What should you NOT do?", listOf("Call 112", "Remove the helmet to give them air", "Check whether they're breathing", "Keep traffic away from them"), 1, "fracture",
            "They may have a neck injury. Moving the head can paralyse them. Remove a helmet only if they're not breathing and you must open the airway.",
            listOf("Calling for help is the right thing to do.", "", "Checking breathing is right: watch the chest for 10 seconds.", "Protecting the scene keeps you both safe.")),
        Question("Someone faints at a crowded function. First aid?", listOf("Sit them up straight on a chair", "Lay them on their back and raise their legs", "Give them strong tea right away", "Shake them hard to wake them"), 1, "cpr",
            "Raising the legs sends blood back to the brain, and most people come round within a minute. If they don't, check breathing and call 112.",
            listOf("Sitting up keeps blood away from the brain and they may faint again.", "", "Never give drinks to someone who isn't fully awake.", "Shaking can hurt them, especially if they hit their head when they fell.")),
        Question("Your nose starts bleeding. What should you do?", listOf("Tilt your head back", "Lean forward and pinch the soft part of the nose for 10 minutes", "Lie flat on your back", "Blow your nose hard"), 1, "bleed",
            "Leaning forward stops blood running down your throat. Steady pressure for 10 minutes lets a clot form.",
            listOf("Blood runs down the throat, which can make you choke or vomit.", "", "Same problem as tilting back: blood goes down the throat.", "Blowing breaks the clot and restarts the bleeding.")),
        Question("Someone has bad diarrhoea and is getting weak. What drink helps most?", listOf("Cola or a soft drink", "ORS: 1 litre of clean water, 6 level teaspoons of sugar, half a teaspoon of salt", "Only plain water, a lot at once", "Nothing until it stops"), 1, "find-water",
            "Oral rehydration solution replaces both water and salts, and the sugar helps the body absorb them. Small sips, often.",
            listOf("Too much sugar draws water into the gut and can make diarrhoea worse.", "", "Water alone doesn't replace lost salts.", "Not drinking is how dehydration kills, especially children.")),
        Question("Lightning starts and you're on an open hill with no building nearby. What's safest?", listOf("Shelter under the tallest tree", "Lie flat on the ground", "Go down to lower ground, away from tall trees, and crouch on the balls of your feet", "Hold up a metal walking pole"), 2, "lightning",
            "Lightning hits high points. Crouching low with feet together reduces how much current can pass through you from the ground.",
            listOf("Lone tall trees attract strikes, and current spreads out from them.", "Lying flat gives ground current more of your body to travel through.", "", "Metal held up high makes you the tallest point.")),
        Question("Someone is drowning in a canal and you're a weak swimmer. What do you do?", listOf("Jump in right away", "Reach with a stick or rope, or throw something that floats, and shout for help", "Run to find a boat", "Swim out and grab them from the front"), 1, "flood",
            "Reach or throw, don't go. Panicking swimmers pull rescuers under. An empty bottle, a dupatta or a rope can save a life from the bank.",
            listOf("Many rescuers drown themselves this way.", "", "By the time you're back it may be too late. Use what's at hand.", "A panicking person will climb on you and push you under.")),
        Question("The ground shakes while you're inside a house. What first?", listOf("Run outside immediately", "Drop, take cover under a strong table, hold on", "Stand near the windows", "Take the lift down"), 1, "quake",
            "Most injuries come from falling objects and glass while people run. Stay put until the shaking stops, then go out carefully.",
            listOf("Running in a shaking building leads to falls and injuries from debris.", "", "Glass shatters in an earthquake.", "Lifts can jam or fall.")),
        Question("You're lost on a trek and people know your planned route. Best plan?", listOf("Keep walking fast to find the road", "Stop, stay put near the route, make yourself easy to see and hear", "Walk downhill along any stream at night", "Split up from your group to search wider"), 1, "lost",
            "Searchers look along your route. Moving makes you harder to find. Stay, shelter, and signal with three whistles or flashes.",
            listOf("Walking without a plan usually takes people further from where they're searched for.", "", "Walking at night risks falls; streams can lead to cliffs and waterfalls.", "Splitting up creates more lost people.")),
        Question("A rescue helicopter flies over. How do you show you need help?", listOf("Wave one arm", "Both arms raised up in a Y", "Sit and wave a cloth", "Lie down so they see your shape"), 1, "signals",
            "Both arms up in a Y means \"Yes, we need help\". One arm up and one down means \"No, we're fine\".",
            listOf("One arm can look like a friendly wave.", "", "From the air, a sitting person is hard to read.", "A lying person can look injured or be missed.")),
        Question("Your phone is at 5% and you're lost. What do you do with it?", listOf("Keep checking maps every few minutes", "Turn on battery saver, send your location once to someone, then keep it off or in airplane mode", "Play music to stay calm", "Keep calling different people"), 1, "battery-low",
            "One message with your location is worth more than an hour of searching maps. Save the rest for an emergency call.",
            listOf("The screen and GPS drain the battery fastest.", "", "Every percent matters: save it for calls.", "Repeated calls drain the phone; one clear message with your location is better.")),
        Question("Your SIM has no signal, but your phone shows \"Emergency calls only\". Can you call 112?", listOf("No, you need your own network", "Yes: emergency calls use any network that's in range", "Only if you have a balance", "Only from a landline"), 1, "signals",
            "In India and most countries, 112 connects through any operator's tower, even without balance. That's what \"Emergency calls only\" means.",
            listOf("Emergency calls use any operator's tower in range.", "", "Emergency calls are free.", "Any mobile phone can call 112.")),
        Question("No fire, no filter, only a clear plastic bottle and a sunny day. How can you make water safer?", listOf("Add a little salt", "Fill the clear bottle and leave it in full sun for 6 hours", "Stir it with a clean stick", "Leave it in the shade overnight"), 1, "purify",
            "The sun's UV rays kill most germs in clear water (SODIS). Use 2 days if it's cloudy. Filter cloudy water through cloth first.",
            listOf("Salt doesn't kill germs.", "", "Stirring does nothing to germs.", "Shade doesn't kill anything: it's the sun's UV that works.")),
        Question("A crowd starts pushing hard at a festival. How do you stay safe?", listOf("Push back against the crowd", "Keep your arms up in front of your chest and move diagonally with the flow toward the edge", "Bend down to pick up something you dropped", "Stand still and lock your knees"), 1, "lost",
            "Arms in front protect your chest so you can breathe. Moving diagonally with the crowd gets you out of the pressure without fighting it.",
            listOf("You can't win against crowd pressure, and you'll tire quickly.", "", "Bending down is how people get knocked over and trampled.", "Locked knees make you fall when the crowd surges.")),
        Question("Someone has been out in cold rain, is shivering hard and talking slowly. What helps?", listOf("Give them alcohol to warm up", "Get them out of wet clothes, wrap them in dry layers, give warm sweet drinks", "Rub their arms and legs hard", "Make them run to warm up"), 1, "hypo",
            "Wet clothes pull heat away fast. Dry layers, shelter and warm (not hot) sugary drinks help the body rewarm from the core.",
            listOf("Alcohol makes you feel warm but makes you lose heat faster.", "", "Hard rubbing can damage cold skin and send cold blood to the heart.", "Exhausting them uses up the energy they need to stay warm.")),
        Question("Someone's leg looks broken after a fall on rocks. What do you do?", listOf("Pull it straight", "Keep it still in the position you found it and support it, then get help", "Ask them to try walking on it", "Massage it to ease the pain"), 1, "fracture",
            "Moving a broken bone can tear blood vessels and nerves. Support it with padding or a splint, including the joints above and below.",
            listOf("Straightening can cause more damage. Leave that to doctors.", "", "Walking on a fracture can make it much worse.", "Massage moves the broken ends and causes more damage.")),
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

package com.bluemob.app.guide

/**
 * Knots and ropes, built into the app so they work with no internet (they used to be a download). The IDs match the old
 * pack's ("knots-reef"…) so reading history, favourites and the animations carry over.
 */
object GuideKnots {
    private fun k(id: String, title: String, minutes: Int, intro: String, steps: List<String>, avoid: List<String>) =
        Article("knots-$id", GuideCategory.KNOTS, title, minutes, intro, steps, avoid)

    val articles = listOf(
        k("which", "Which knot when", 2,
            "Five jobs cover almost everything outdoors. Learn one knot for each, and practise until your hands do it in the dark.",
            listOf(
                "A loop that won't slip (rescue, tying round a person or a tree): bowline, or the figure-eight loop if lives depend on it.",
                "Joining two ropes: sheet bend (different thickness) or double fisherman's (same thickness, for keeps).",
                "Tying to a post, tree or ring: clove hitch for a quick hold, two half hitches to finish any line.",
                "A tight line you can adjust: taut-line hitch for guy lines, trucker's hitch to pull a ridge line or a load really tight.",
                "A loop in the middle of a rope (to clip to, or to pull with several people): alpine butterfly.",
                "Climbing a rope or backing up a hold: prusik loop.",
                "Every knot: dress it (no crossed strands), pull every strand tight, and leave a tail at least a hand's width long.",
            ),
            listOf(
                "Never trust a knot you can't check by eye. If it looks wrong, untie it and start again.",
                "Never use rope that is cut, burnt, stiff with chemicals or badly worn for a person's weight.",
            )),
        k("reef", "Reef knot (joining two ends)", 2,
            "Ties two ends of the same rope together, like a bandage or a bundle. Flat and easy to undo.",
            listOf(
                "Make a U-bend in one end (blue). Bring the other end (orange) alongside.",
                "Take the orange end over the U, then under it.",
                "Go around behind both legs of the U.",
                "Come back the same way: under, then over the U. Both orange parts lie side by side.",
                "Pull all four parts tight. It lies flat; each rope's two parts stay together.",
            ),
            listOf(
                "Don't use it to join two different ropes or to hold a person's weight: it can slip.",
                "If the loops look twisted (a 'granny knot'), untie and redo the second cross the other way.",
            )),
        k("bowline", "Bowline (a loop that won't slip)", 3,
            "Makes a fixed loop at the end of a rope, for rescue, mooring or hanging. Easy to untie even after a heavy load.",
            listOf(
                "Make a small loop (the hole), with the end side on top. Leave a big loop below.",
                "Bring the end up through the hole from underneath.",
                "Pass it behind the standing part (round the tree).",
                "Bring it back down through the hole, beside itself.",
                "Hold the end against the big loop and pull the standing part tight.",
            ),
            listOf(
                "Don't leave a short tail: it can work loose when not loaded.",
                "Check the knot before trusting your weight to it.",
            )),
        k("figure8", "Figure-eight loop (the safest loop)", 3,
            "The loop climbers and rescuers trust with a life. Easy to check by eye, and it doesn't shake loose.",
            listOf(
                "Take an arm's length of the end. Make a loop that crosses over the standing part.",
                "Take the end around behind the standing part.",
                "Bring it over and back through the first loop: a figure eight.",
                "Pass the end around the tree (or through your harness).",
                "Follow the eight back with the end, strand beside strand, all the way.",
                "Pull all four strands tight: two neat parallel eights. Leave a tail.",
            ),
            listOf(
                "Crossed or twisted strands make it weaker: untie and follow the first eight again.",
                "After a hard pull it is stiff to untie: bend the knot back and forth to loosen it.",
            )),
        k("sheetbend", "Sheet bend (joining two different ropes)", 2,
            "Joins two ropes, even of different thickness or material: a thin cord to a thick rope, two pieces to make one long one.",
            listOf(
                "Make a U-bend in the end of the thicker rope.",
                "Pass the thin end up through the U from underneath.",
                "Take it around behind both legs of the U.",
                "Tuck it under itself, not through the U. Both short ends come out on the same side.",
                "Pull the thin rope's long part to tighten.",
            ),
            listOf(
                "If the two short ends are on opposite sides, it can slip: retie it.",
                "Don't use it for climbing or a person's weight.",
            )),
        k("doublefish", "Double fisherman's (joining for keeps)", 3,
            "Joins two ropes of similar thickness so they won't come apart, and makes a closed loop from a cord (for a prusik).",
            listOf(
                "Lay the two ends side by side, pointing opposite ways, overlapping an arm's length.",
                "Wrap the blue end twice around both ropes, back towards itself, then tuck it through both wraps.",
                "Do the same with the orange end on the other side.",
                "Pull the two long parts: the two knots slide together and sit side by side.",
            ),
            listOf(
                "It's very hard to untie after loading: use it where you want it to stay.",
                "Leave tails of at least a hand's width.",
            )),
        k("clove", "Clove hitch (tying to a post or tree)", 2,
            "Quickly ties a rope to a pole, branch or tree, for a ridge line, a tarp or a tent guy.",
            listOf(
                "Bring the rope across the front of the post.",
                "Wrap it once around the post.",
                "Cross over the first wrap, diagonally, and go around the post again.",
                "Tuck the end under that diagonal, beside the standing part.",
                "Pull both ends tight. The diagonal holds both ends against the post.",
            ),
            listOf("Don't rely on it alone for a load that moves back and forth: add two half hitches.")),
        k("halfhitch", "Two half hitches (finishing any line)", 1,
            "The quickest way to tie a rope's end to a tree, ring or post, and to lock other knots.",
            listOf(
                "The rope comes to the post (or tree, or ring).",
                "Pass the end around the post.",
                "Bring it over the long part, around behind it, and out through the gap: one half hitch.",
                "Make a second half hitch the same way, beside the first.",
                "Slide them snug against the post and pull tight.",
            ),
            listOf("Two hitches in opposite directions jam and twist: both go the same way.")),
        k("taut", "Taut-line hitch (adjustable tight line)", 2,
            "A loop you can slide to tighten or loosen a line: perfect for tent and tarp guy lines.",
            listOf(
                "The line comes from the tent to a peg.",
                "Pass it around the peg and bring the end back alongside the line.",
                "Wrap the end twice around the line, working back towards the peg.",
                "Then once more around the line, on the far side of the first two wraps.",
                "Pull tight. Slide the knot to tighten the line; it grips when loaded.",
            ),
            listOf("Don't use thin slippery cord: it may not grip.")),
        k("trucker", "Trucker's hitch (pull a line really tight)", 3,
            "Works like a pulley: pulls a ridge line, a load on a roof or a stretcher lashing as tight as you need.",
            listOf(
                "Tie one end to the first anchor.",
                "Halfway, make a small fixed loop in the rope.",
                "Take the free end around the second anchor and back through the loop.",
                "Pull the free end towards the first anchor: it pulls with about twice your strength.",
                "Hold it and finish with two half hitches below the loop.",
            ),
            listOf(
                "Don't pull a person's weight with it: it's for gear and lines.",
                "Rope rubbing on rope gets hot: pull steadily, not in jerks.",
            )),
        k("butterfly", "Alpine butterfly (a loop in the middle)", 3,
            "A strong loop in the middle of a rope, to clip a bag, or for several people to pull together, or to skip a damaged bit of rope.",
            listOf(
                "Lay the rope out where you want the loop.",
                "Make a loop and let it hang.",
                "Twist the loop twice, the same way: a figure-eight shape with a small opening in the middle.",
                "Fold the top loop down in front and push it up through the bottom opening.",
                "Hold the loop and pull both ends apart to tighten.",
            ),
            listOf("Check the loop doesn't move when you pull the ends: if it slides, start again.")),
        k("prusik", "Prusik (grips a rope, slides when you want)", 3,
            "A thinner cord loop that grips a thicker rope when pulled and slides when loose: to climb a rope, back up a hold, or make a handle on a line.",
            listOf(
                "Bring a closed loop of thinner cord to the rope.",
                "Wrap it around the rope three times, going up, each wrap inside the loop.",
                "Cross over at the top and wrap back down, beside the first wraps.",
                "Both legs come out in the middle. Make the wraps lie flat and parallel.",
                "Pull down on the loop: it grips. Push the wraps (not the loop) to slide it.",
            ),
            listOf(
                "Cord as thick as the rope won't grip: it must be thinner.",
                "Test it with your weight close to the ground before you rely on it.",
            )),
        k("lashing", "Square lashing (joining two poles)", 4,
            "Ties two poles together at a right angle: a shelter frame, a stretcher, a raised bed or a drying rack.",
            listOf(
                "Tie a clove hitch on the upright pole, just below the crossbar.",
                "Wrap over the crossbar, behind the upright, over the crossbar again, behind the upright: a square.",
                "Do it three or four times, each wrap tight and beside the last.",
                "Frap: wrap two or three times between the poles, around the wraps, to pull them tight.",
                "Finish with a clove hitch on the crossbar.",
            ),
            listOf(
                "For a stretcher, test it with someone light before lifting an injured person.",
                "Wet rope shrinks and stretches: check lashings each morning.",
            )),
    )
}

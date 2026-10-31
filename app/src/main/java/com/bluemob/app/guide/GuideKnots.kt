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
                "Hold one end in each hand.",
                "Cross the left end over the right and tuck it under (like the first step of tying shoes).",
                "Now cross the right end over the left and tuck it under.",
                "Pull both ends: the two loops lie flat, side by side.",
                "Remember: left over right, then right over left.",
            ),
            listOf(
                "Don't use it to join two different ropes or to hold a person's weight: it can slip.",
                "If the loops look twisted (a 'granny knot'), untie and redo the second cross the other way.",
            )),
        k("bowline", "Bowline (a loop that won't slip)", 3,
            "Makes a fixed loop at the end of a rope, for rescue, mooring or hanging. Easy to untie even after a heavy load.",
            listOf(
                "Make a small loop in the rope, with the short end on top (the 'rabbit hole').",
                "Bring the short end up through the loop from below (the rabbit comes out of the hole).",
                "Pass it behind the long part of the rope (round the tree).",
                "Bring it back down through the small loop (back down the hole).",
                "Hold the short end against the big loop and pull the long part tight.",
                "Leave at least a hand's width of tail.",
            ),
            listOf(
                "Don't leave a short tail: it can work loose when not loaded.",
                "Check the knot before trusting your weight to it.",
            )),
        k("figure8", "Figure-eight loop (the safest loop)", 3,
            "The loop climbers and rescuers trust with a life. Easy to check by eye, and it doesn't shake loose.",
            listOf(
                "Take an arm's length of the end. Make a loop, then cross the end over the main rope.",
                "Wrap the end once around the main rope, and tuck it back through the first loop: a figure eight.",
                "To tie it round something (a tree, a person's waist): pass the end around it first.",
                "Then follow the first eight back with the end, strand beside strand, all the way to where the rope comes out.",
                "Pull each of the four strands tight. You should see two neat parallel eights.",
                "Leave a tail of at least a hand's width.",
            ),
            listOf(
                "Crossed or twisted strands make it weaker: untie and follow the first eight again.",
                "After a hard pull it is stiff to untie: bend the knot back and forth to loosen it.",
            )),
        k("sheetbend", "Sheet bend (joining two different ropes)", 2,
            "Joins two ropes, even of different thickness or material: a thin cord to a thick rope, two pieces to make one long one.",
            listOf(
                "Make a U-bend in the end of the thicker rope and hold it.",
                "Pass the thin end up through the U from underneath.",
                "Take it around behind both legs of the U.",
                "Tuck it under itself (not through the U) across the front.",
                "Pull the thin rope's long part to tighten. Both short ends should come out on the same side.",
                "For extra hold, wrap round the U a second time before tucking: a double sheet bend.",
            ),
            listOf(
                "If the two short ends are on opposite sides, it can slip: retie it.",
                "Don't use it for climbing or a person's weight.",
            )),
        k("doublefish", "Double fisherman's (joining for keeps)", 3,
            "Joins two ropes of similar thickness so they won't come apart, and makes a closed loop from a cord (for a prusik).",
            listOf(
                "Lay the two ends side by side, pointing in opposite directions, with an arm's length overlapping.",
                "With the first end, wrap twice around the other rope, working back towards yourself, then tuck the end through both wraps.",
                "Do the same with the other end around the first rope.",
                "Pull the two long parts: the two knots slide together and sit snugly side by side.",
                "Check for neat X's on one side and four parallel lines on the other.",
            ),
            listOf(
                "It's very hard to untie after loading: use it where you want it to stay.",
                "Leave tails of at least a hand's width.",
            )),
        k("clove", "Clove hitch (tying to a post or tree)", 2,
            "Quickly ties a rope to a pole, branch or tree, for a ridge line, a tarp or a tent guy.",
            listOf(
                "Wrap the rope once around the post.",
                "Cross over the first wrap and go around the post again.",
                "Tuck the end under that second wrap.",
                "Pull both ends: two neat parallel wraps with an X across them.",
            ),
            listOf("Don't rely on it alone for a load that moves back and forth: add two half hitches.")),
        k("halfhitch", "Two half hitches (finishing any line)", 1,
            "The quickest way to tie a rope's end to a tree, ring or post, and to lock other knots.",
            listOf(
                "Pass the end around the tree or through the ring.",
                "Wrap it once around the long part and tuck it through the gap: one half hitch.",
                "Do it again in the same direction, beside the first.",
                "Slide them up snug and pull tight.",
            ),
            listOf("Two hitches in opposite directions jam and twist: both go the same way.")),
        k("taut", "Taut-line hitch (adjustable tight line)", 2,
            "A loop you can slide to tighten or loosen a line: perfect for tent and tarp guy lines.",
            listOf(
                "Pass the rope around a peg or tree and bring the end back along the line.",
                "Wrap the end twice around the line, working towards the peg.",
                "Then wrap once more around the line, outside the first two wraps.",
                "Pull tight. Slide the knot to adjust the tension; it grips when loaded.",
            ),
            listOf("Don't use thin slippery cord: it may not grip.")),
        k("trucker", "Trucker's hitch (pull a line really tight)", 3,
            "Works like a pulley: pulls a ridge line, a load on a roof or a stretcher lashing as tight as you need.",
            listOf(
                "Tie one end to a tree or anchor (a bowline or two half hitches).",
                "Halfway along, twist a small loop in the rope and push a bight (a U of rope) through it: a loop that holds.",
                "Take the free end around the second tree or anchor and back through that loop.",
                "Pull the free end towards the anchor: it pulls with about twice your strength.",
                "Hold the tension with one hand and finish with two half hitches below the loop.",
            ),
            listOf(
                "Don't pull a person's weight with it: it's for gear and lines.",
                "Rope rubbing on rope gets hot: pull steadily, not in jerks.",
            )),
        k("butterfly", "Alpine butterfly (a loop in the middle)", 3,
            "A strong loop in the middle of a rope, to clip a bag, or for several people to pull together, or to skip a damaged bit of rope.",
            listOf(
                "Wrap the rope around your hand three times: wraps 1, 2, 3 from the fingertips.",
                "Take wrap 2 (the middle one) over wrap 3 towards your fingertips.",
                "Carry it on over wrap 1, then tuck it under all the wraps back towards your wrist.",
                "Pull that bight out to make the loop, slide the knot off your hand.",
                "Hold the loop and pull both ends of the rope apart to tighten.",
            ),
            listOf("Check the loop doesn't move when you pull the ends: if it slides, start again.")),
        k("prusik", "Prusik (grips a rope, slides when you want)", 3,
            "A thinner cord loop that grips a thicker rope when pulled and slides when loose: to climb a rope, back up a hold, or make a handle on a line.",
            listOf(
                "Make a loop from cord about half as thick as the main rope (tie it with a double fisherman's).",
                "Lay the loop behind the main rope.",
                "Pass the far end of the loop through itself, around the rope, three times, each wrap inside the last.",
                "Dress the wraps so they lie flat and parallel, with the knot of the cord off to the side.",
                "Load the loop: it grips. Push the wraps (not the loop) to slide it along.",
            ),
            listOf(
                "Cord as thick as the rope won't grip: it must be thinner.",
                "Test it with your weight close to the ground before you rely on it.",
            )),
        k("lashing", "Square lashing (joining two poles)", 4,
            "Ties two poles together at a right angle: a shelter frame, a stretcher, a raised bed or a drying rack.",
            listOf(
                "Tie a clove hitch on the upright pole, just below where the crossbar will go.",
                "Wrap the rope over the crossbar, behind the upright, under the crossbar, behind the upright: a square.",
                "Do this three or four times, keeping each wrap tight and beside the last.",
                "Now 'frap': wrap two or three times between the poles, around the wraps, to pull them tight.",
                "Finish with a clove hitch on the crossbar.",
            ),
            listOf(
                "For a stretcher, test it with someone light before lifting an injured person.",
                "Wet rope shrinks and stretches: check lashings each morning.",
            )),
    )
}

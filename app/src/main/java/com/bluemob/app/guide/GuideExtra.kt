package com.bluemob.app.guide

/** Built-in guides about BlueMob itself (not generated from the web preview). */
object GuideExtra {
    val articles = listOf(
        Article(
            id = "find-bluemob",
            category = GuideCategory.NAVIGATION,
            title = "Find someone with BlueMob",
            minutes = 2,
            intro = "Someone sent an SOS or is lost, and you're going to them. BlueMob points the way, phone to phone, with no signal.",
            steps = listOf(
                "Open their SOS alert and tap I'm coming. A rescue group opens, and they see that you're on your way.",
                "Tap Find them (or Compass → pick their name). The arrow points at their last position; the distance and how old that position is are underneath.",
                "Walk with the phone held flat. Every phone in between passes their position on, so it keeps updating even when they're far.",
                "Within Bluetooth range (10–100 m) BlueMob says you're connected directly: you're close. Shout and look around.",
                "Tap Ring their phone: it whistles and flashes for 20 seconds so you can follow the sound.",
                "If their position is old or rough (±100 m or more), search in widening circles around it, and tell the group where you've looked.",
            ),
            avoid = listOf(
                "Don't switch BlueMob's mesh off on the way: you'd stop hearing their updates.",
                "Don't go alone into danger (floods, avalanches, cliffs). Use the group chat to bring more people.",
            ),
        ),
    )
}

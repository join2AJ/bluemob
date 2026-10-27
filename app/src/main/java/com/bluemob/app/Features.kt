package com.bluemob.app

/**
 * Parts of the app that are built and kept, but not shown right now, so BlueMob reads as a safety app for trips and
 * crowds. Nothing behind a switch is deleted: its screens, routes, data and tests stay, and setting the switch back
 * to true brings every way into it back (rows, cards, Sky's buttons, invites, celebrations).
 */
object Features {
    /** Games with people nearby or the computer: the Games row and card, Sky's "Play a game", game invites. */
    const val GAMES = false

    /** Levels, XP and medals: the level pill under your name, the Badges screen, medal confetti. */
    const val BADGES = false
}

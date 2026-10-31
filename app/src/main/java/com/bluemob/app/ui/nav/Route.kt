package com.bluemob.app.ui.nav

/**
 * Every screen above the tabs, as a type. The back stack keeps each route's [key] (a short string, so it survives
 * Android recreating the activity), and notifications open screens by key too ([parse] turns it back).
 *
 * Routes are grouped by feature; each group is drawn by its own file (ChatRoutes, SosRoutes, YouRoutes…).
 */
sealed interface Route {
    val key: String

    /** Chats, groups, files and Sky. */
    sealed interface ChatArea : Route
    data class Chat(val id: String) : ChatArea { override val key get() = "chat:$id" }
    data class Group(val id: String) : ChatArea { override val key get() = "group:$id" }
    data class Media(val peer: String) : ChatArea { override val key get() = "media:$peer" }
    data class Photo(val messageId: String) : ChatArea { override val key get() = "photo:$messageId" }
    data class Info(val messageId: String) : ChatArea { override val key get() = "info:$messageId" }
    data object NewChat : ChatArea { override val key = "newchat" }
    data object Starred : ChatArea { override val key = "starred" }
    data class Altitude(val id: String) : ChatArea { override val key get() = "altitude:$id" }
    data class Headcount(val id: String) : ChatArea { override val key get() = "headcount:$id" }
    data class TripPhotos(val id: String) : ChatArea { override val key get() = "trip-photos:$id" }
    data object NewGroup : ChatArea { override val key = "newgroup" }
    data object SkyAi : ChatArea { override val key = "skyai" }
    /** Start a trip group (its own screen is [Group], which shows trip groups their own way). */
    data object NewTrip : ChatArea { override val key = "new-trip" }
    data object JoinTrip : ChatArea { override val key = "join-trip" }
    /** Start a team in trip group [main]. */
    data class NewTeam(val main: String) : ChatArea { override val key get() = "new-team:$main" }
    /** A trip group's itinerary. */
    data class Itinerary(val group: String) : ChatArea { override val key get() = "itinerary:$group" }
    /** A trip group's buddy pairs. */
    data class Buddies(val group: String) : ChatArea { override val key get() = "buddies:$group" }

    /** Guide articles, quizzes and packs. */
    sealed interface GuideArea : Route
    data class Article(val id: String) : GuideArea { override val key get() = "article:$id" }
    data class Quiz(val id: String) : GuideArea { override val key get() = "quiz:$id" }
    data object GuidePacks : GuideArea { override val key = "guide-packs" }

    /** SOS, signals and rescue groups. */
    sealed interface SosArea : Route
    data object Sos : SosArea { override val key = "sos" }
    data object Signal : SosArea { override val key = "signal" }
    data object SosContacts : SosArea { override val key = "sos-contacts" }
    data object CheckIn : SosArea { override val key = "check-in" }
    data object Rescues : SosArea { override val key = "rescues" }
    data class Rescue(val id: String) : SosArea { override val key get() = "rescue:$id" }

    /** Games with people nearby or the phone. */
    sealed interface GamesArea : Route
    data object Games : GamesArea { override val key = "games" }
    data class Match(val id: String) : GamesArea { override val key get() = "match:$id" }
    data class Game(val id: String) : GamesArea { override val key get() = "game:$id" }

    /** Recorded trips. */
    sealed interface TripsArea : Route
    data object Trips : TripsArea { override val key = "trips" }
    data class Trip(val id: String) : TripsArea { override val key get() = "trip:$id" }

    /** The You tab's pages: people, account, settings. */
    sealed interface YouArea : Route
    data class Person(val id: String) : YouArea { override val key get() = "person:$id" }
    data class Verify(val id: String) : YouArea { override val key get() = "verify:$id" }
    data object News : YouArea { override val key = "news" }
    data object Account : YouArea { override val key = "account" }
    data object EmergencyCard : YouArea { override val key = "emergency-card" }
    data object HelperSignals : YouArea { override val key = "helper-signals" }
    data object Connections : YouArea { override val key = "connections" }
    data object AutoStart : YouArea { override val key = "autostart" }
    /** Connections & power, scrolled to and highlighting one row ("battery"). */
    data class Power(val highlight: String) : YouArea { override val key get() = "autostart:$highlight" }
    data object Audit : YouArea { override val key = "audit" }
    data object Badges : YouArea { override val key = "badges" }
    data object Activity : YouArea { override val key = "activity" }
    data object Bridge : YouArea { override val key = "bridge" }
    data object Backup : YouArea { override val key = "backup" }
    data object Storage : YouArea { override val key = "storage" }
    data object Diagnostics : YouArea { override val key = "diagnostics" }
    data object Report : YouArea { override val key = "report" }
    /** A field test, set up to use trip group [trip] ("" for none chosen yet). */
    data class FieldTest(val trip: String = "") : YouArea { override val key get() = if (trip.isEmpty()) "field-test" else "field-test:$trip" }

    /** Opened from a notification only, never kept on the stack. */
    sealed interface Action : Route
    /** The Chats tab, with the calls list. */
    data object Calls : Action { override val key = "calls" }
    /** "I'm coming" on an SOS alarm notification. */
    data class SosComing(val id: String) : Action { override val key get() = "sos-coming:$id" }
    /** The screen of whoever is in the call. */
    data object Call : Action { override val key = "call" }

    companion object {
        private val withId: Map<String, (String) -> Route> = mapOf(
            "chat" to ::Chat, "group" to ::Group, "media" to ::Media, "photo" to ::Photo, "info" to ::Info,
            "article" to ::Article, "quiz" to ::Quiz, "rescue" to ::Rescue, "match" to ::Match, "game" to ::Game,
            "trip" to ::Trip, "person" to ::Person, "verify" to ::Verify, "autostart" to ::Power, "sos-coming" to ::SosComing, "field-test" to ::FieldTest, "new-team" to ::NewTeam, "itinerary" to ::Itinerary, "buddies" to ::Buddies, "altitude" to ::Altitude, "headcount" to ::Headcount, "trip-photos" to ::TripPhotos,
        )
        private val plain: Map<String, Route> by lazy {
            listOf(NewChat, Starred, NewGroup, SkyAi, NewTrip, JoinTrip, FieldTest(), GuidePacks, Sos, Signal, SosContacts, CheckIn, Rescues, Games, Trips, News, Account, EmergencyCard, HelperSignals, Connections,
                AutoStart, Audit, Badges, Activity, Bridge, Backup, Storage, Diagnostics, Report, Calls, Call).associateBy { it.key }
        }

        /** The route a key names, or null for a key from an older version that no longer exists. */
        fun parse(key: String): Route? = plain[key] ?: key.indexOf(':').takeIf { it > 0 }?.let { i ->
            withId[key.substring(0, i)]?.invoke(key.substring(i + 1))?.takeIf { key.length > i + 1 }
        }
    }
}

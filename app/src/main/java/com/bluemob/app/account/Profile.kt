package com.bluemob.app.account

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the user told us at sign-up. Kept encrypted on the phone; only the blood group and age travel, inside an SOS. */
data class Profile(
    val phone: String? = null,
    val verified: Boolean = false,
    val age: Int? = null,
    val bloodGroup: String? = null,
    /** Optional, for account recovery and receipts later. */
    val email: String? = null,
)

object Emails {
    private val SHAPE = Regex("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{2,}$")
    fun clean(s: String) = s.trim().lowercase()
    /** Why it isn't an email address, or null if it looks fine (blank is fine too: it's optional). */
    fun problem(s: String): String? = if (s.isBlank() || SHAPE.matches(clean(s))) null else "Check the email address"
}

object BloodGroups {
    val ALL = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")
    const val UNKNOWN = "Don't know"
}

/** Checks and formats mobile numbers. India (+91) gets the full check; other countries a length check. */
object PhoneNumbers {
    fun digits(s: String) = s.filter { it.isDigit() }

    /** The number in international form (+91…), or null with a reason via [problem]. */
    fun e164(countryCode: String, number: String): String? = if (problem(countryCode, number) == null) "+" + digits(countryCode) + digits(number).trimStart('0') else null

    fun problem(countryCode: String, number: String): String? {
        val cc = digits(countryCode)
        val n = digits(number).trimStart('0')
        return when {
            cc.isEmpty() || cc.length > 3 -> "Check the country code"
            cc == "91" && n.length != 10 -> "Indian mobile numbers have 10 digits"
            cc == "91" && n.first() !in "6789" -> "Indian mobile numbers start with 6, 7, 8 or 9"
            n.length !in 6..14 -> "Check the number"
            else -> null
        }
    }

    /** "+919876543210" → "+91 98765 43210". */
    fun pretty(e164: String): String = if (e164.startsWith("+91") && e164.length == 13) "+91 ${e164.substring(3, 8)} ${e164.substring(8)}" else e164
}

/**
 * One-time codes for verifying the mobile number.
 *
 * This test build has no SMS provider, so the code is always [TEST_CODE] and nothing is sent. A real build sends the
 * code through the BlueMob relay's SMS provider instead; the screens stay the same.
 */
object Otp {
    const val TEST_CODE = "123456"
    const val LENGTH = 6
    const val RESEND_AFTER_S = 30

    fun check(code: String): Boolean = code == TEST_CODE
}

class ProfileStore(private val prefs: SharedPreferences) {
    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private fun read() = Profile(
        phone = prefs.getString(PHONE, null),
        verified = prefs.getBoolean(VERIFIED, false),
        age = prefs.getInt(AGE, -1).takeIf { it in 1..120 },
        bloodGroup = prefs.getString(BLOOD, null),
        email = prefs.getString(EMAIL, null),
    )

    /** The email the relay has: what to send it next time we're online, if different. */
    val syncedEmail: String? get() = prefs.getString(EMAIL_SYNCED, null)
    fun markEmailSynced(email: String) { prefs.edit().putString(EMAIL_SYNCED, email).apply() }

    fun setEmail(email: String?) {
        val e = email?.let { Emails.clean(it) }?.takeIf { it.isNotEmpty() && Emails.problem(it) == null }
        prefs.edit().putString(EMAIL, e).apply()
        _profile.value = read()
    }

    fun setVerifiedPhone(e164: String) { prefs.edit().putString(PHONE, e164).putBoolean(VERIFIED, true).apply(); _profile.value = read() }

    fun setDetails(age: Int?, bloodGroup: String?) {
        prefs.edit().putInt(AGE, age?.takeIf { it in 1..120 } ?: -1)
            .putString(BLOOD, bloodGroup?.takeIf { it in BloodGroups.ALL }).apply()
        _profile.value = read()
    }

    private companion object {
        const val PHONE = "phone"
        const val VERIFIED = "phone_verified"
        const val AGE = "age"
        const val BLOOD = "blood_group"
        const val EMAIL = "email"
        const val EMAIL_SYNCED = "email_synced"
    }
}

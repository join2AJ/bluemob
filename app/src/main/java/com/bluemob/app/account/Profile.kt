package com.bluemob.app.account

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the user told us at sign-up. Kept encrypted on the phone; the medical details travel only inside an SOS (and on
 * the lock-screen emergency card, if the user turns it on), so helpers and medics know.
 */
data class Profile(
    val phone: String? = null,
    val verified: Boolean = false,
    val age: Int? = null,
    val bloodGroup: String? = null,
    /** Optional, for account recovery and receipts later. */
    val email: String? = null,
    /** Date of birth, "yyyy-MM-dd". When given, [age] comes from it. */
    val dob: String? = null,
    /** One of [Sexes.ALL], or null if not given. */
    val sex: String? = null,
    /** Free text, [Medical.NONE] for "none known", or null if not given. */
    val allergies: String? = null,
    /** Conditions and regular medicines a medic should know about (asthma, diabetes, insulin…). */
    val conditions: String? = null,
) {
    /** "Female · Allergies: penicillin · Asthma": the details beyond blood group and age, for an SOS. */
    fun medicalNote(): String? = Medical.note(sex, allergies, conditions)
}

object Sexes {
    val ALL = listOf("Female", "Male", "Non-binary", "Other", "Prefer not to say")
}

object Medical {
    const val NONE = "None known"
    const val PRIVATE = "Prefer not to say"

    fun note(sex: String?, allergies: String?, conditions: String?): String? = listOfNotNull(
        sex?.takeIf { it in Sexes.ALL && it != Sexes.ALL.last() },
        when (allergies?.trim()) { null, "", PRIVATE -> null; NONE -> "No known allergies"; else -> "Allergies: ${allergies.trim()}" },
        conditions?.trim()?.takeIf { it.isNotEmpty() && it != PRIVATE }?.let { "Medical: $it" },
    ).joinToString(" · ").take(200).ifBlank { null }

    /** Age in whole years on [today] for a date of birth "yyyy-MM-dd", or null if it isn't a sensible date. */
    fun ageFrom(dob: String?, today: java.time.LocalDate = java.time.LocalDate.now()): Int? {
        val d = runCatching { java.time.LocalDate.parse(dob ?: return null) }.getOrNull() ?: return null
        if (d.isAfter(today)) return null
        return java.time.Period.between(d, today).years.takeIf { it in 0..120 }
    }
}

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
        age = Medical.ageFrom(prefs.getString(DOB, null)) ?: prefs.getInt(AGE, -1).takeIf { it in 1..120 },
        bloodGroup = prefs.getString(BLOOD, null),
        email = prefs.getString(EMAIL, null),
        dob = prefs.getString(DOB, null),
        sex = prefs.getString(SEX, null),
        allergies = prefs.getString(ALLERGIES, null),
        conditions = prefs.getString(CONDITIONS, null),
    )

    /** The email the relay has: what to send it next time we're online, if different. */
    val syncedEmail: String? get() = prefs.getString(EMAIL_SYNCED, null)
    fun markEmailSynced(email: String) { prefs.edit().putString(EMAIL_SYNCED, email).apply() }

    fun setEmail(email: String?) {
        val e = email?.let { Emails.clean(it) }?.takeIf { it.isNotEmpty() && Emails.problem(it) == null }
        prefs.edit().putString(EMAIL, e).apply()
        _profile.value = read()
    }

    /** When the user agreed to the privacy policy, and whether a parent or guardian agreed for them (under 18). */
    fun setConsent(at: Long, parental: Boolean) { prefs.edit().putLong(CONSENT_AT, at).putBoolean(CONSENT_PARENT, parental).apply() }
    val consentAt: Long get() = prefs.getLong(CONSENT_AT, 0L)

    fun setVerifiedPhone(e164: String) { prefs.edit().putString(PHONE, e164).putBoolean(VERIFIED, true).apply(); _profile.value = read() }

    fun setDetails(age: Int?, bloodGroup: String?) {
        prefs.edit().putInt(AGE, age?.takeIf { it in 1..120 } ?: -1)
            .putString(BLOOD, bloodGroup?.takeIf { it in BloodGroups.ALL }).apply()
        _profile.value = read()
    }

    /** Everything from the "About you" step: date of birth, sex, blood group, allergies and conditions (all optional but blood group). */
    fun setMedical(dob: String?, sex: String?, bloodGroup: String?, allergies: String?, conditions: String?) {
        prefs.edit().putString(DOB, dob?.takeIf { Medical.ageFrom(it) != null })
            .putInt(AGE, Medical.ageFrom(dob) ?: -1)
            .putString(SEX, sex?.takeIf { it in Sexes.ALL })
            .putString(BLOOD, bloodGroup?.takeIf { it in BloodGroups.ALL })
            .putString(ALLERGIES, allergies?.trim()?.take(120)?.ifEmpty { null })
            .putString(CONDITIONS, conditions?.trim()?.take(160)?.ifEmpty { null }).apply()
        _profile.value = read()
    }

    private companion object {
        const val PHONE = "phone"
        const val VERIFIED = "phone_verified"
        const val AGE = "age"
        const val BLOOD = "blood_group"
        const val EMAIL = "email"
        const val EMAIL_SYNCED = "email_synced"
        const val DOB = "dob"
        const val CONSENT_AT = "consent_at"
        const val CONSENT_PARENT = "consent_parent"
        const val SEX = "sex"
        const val ALLERGIES = "allergies"
        const val CONDITIONS = "conditions"
    }
}

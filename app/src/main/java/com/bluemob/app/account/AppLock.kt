package com.bluemob.app.account

import android.content.Context
import android.content.SharedPreferences
import com.bluemob.app.crypto.Crypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** How long to make someone wait after wrong PINs. Kept apart from storage so it can be tested. */
object PinPolicy {
    const val FREE_TRIES = 5
    const val MIN_PIN = 4
    const val MAX_PIN = 8

    /** Wait after [fails] wrong PINs in a row: none for the first 5, then 30 s, doubling up to 15 minutes. */
    fun waitMs(fails: Int): Long = if (fails < FREE_TRIES) 0 else minOf(30_000L shl (fails - FREE_TRIES).coerceAtMost(5), 15 * 60_000L)

    /** Too simple to protect anything: all one digit, or a straight run like 1234 / 9876. */
    fun tooSimple(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val d = pin.map { it - '0' }
        val steps = d.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }

    fun valid(pin: String) = pin.length in MIN_PIN..MAX_PIN && pin.all { it.isDigit() }

    fun hash(pin: String, salt: ByteArray, rounds: Int = ROUNDS): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, rounds, 256)).encoded

    const val ROUNDS = 120_000
}

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val triesBeforeWait: Int) : PinResult
    data class Wait(val seconds: Long) : PinResult
}

/**
 * Logging in to BlueMob: a PIN (and optionally the phone's fingerprint or face unlock) guards the app on this phone.
 *
 * There's no server account to sign in to: your account is your BlueMob ID and its key, which live on this phone.
 * The PIN is never stored, only a slow salted hash (PBKDF2, [PinPolicy.ROUNDS] rounds). The mesh keeps running
 * while BlueMob is locked, so messages and SOS still get through, and SOS can be sent from the lock screen.
 */
class AppLock(private val prefs: SharedPreferences, private val clock: () -> Long = System::currentTimeMillis) {
    constructor(context: Context) : this(com.bluemob.app.crypto.SecurePrefs.open(context, "account"))

    val hasPin: Boolean get() = prefs.contains(KEY_HASH)

    private val _pinSet = MutableStateFlow(hasPin)
    /** True once the account has a PIN (sign-up finished its first step). */
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()

    private val _locked = MutableStateFlow(hasPin)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _biometric = MutableStateFlow(prefs.getBoolean(KEY_BIO, false))
    val biometric: StateFlow<Boolean> = _biometric.asStateFlow()

    private val _lockAfterMs = MutableStateFlow(prefs.getLong(KEY_AFTER, DEFAULT_AFTER_MS))
    /** Lock again when BlueMob has been in the background this long (0 = every time it's left). */
    val lockAfterMs: StateFlow<Long> = _lockAfterMs.asStateFlow()

    private val _recoverySaved = MutableStateFlow(prefs.getBoolean(KEY_RECOVERY_SAVED, false))
    /** The user confirmed they wrote their recovery code down. */
    val recoverySaved: StateFlow<Boolean> = _recoverySaved.asStateFlow()

    private var backgroundSince = 0L

    fun setPin(pin: String) {
        require(PinPolicy.valid(pin))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        prefs.edit().putString(KEY_HASH, Crypto.encode(salt) + ":" + Crypto.encode(PinPolicy.hash(pin, salt)))
            .putInt(KEY_FAILS, 0).putLong(KEY_UNTIL, 0).apply()
        _pinSet.value = true
        _locked.value = false
    }

    /** Checks a PIN, counting wrong tries. Unlocks on success. */
    fun check(pin: String): PinResult {
        val now = clock()
        val until = prefs.getLong(KEY_UNTIL, 0)
        if (now < until) return PinResult.Wait((until - now + 999) / 1000)
        if (matches(pin)) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_UNTIL, 0).apply()
            _locked.value = false
            return PinResult.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val wait = PinPolicy.waitMs(fails)
        prefs.edit().putInt(KEY_FAILS, fails).putLong(KEY_UNTIL, if (wait > 0) now + wait else 0).apply()
        return if (wait > 0) PinResult.Wait(wait / 1000) else PinResult.Wrong(PinPolicy.FREE_TRIES - fails)
    }


    private fun matches(pin: String): Boolean {
        val stored = prefs.getString(KEY_HASH, null) ?: return false
        val (salt, hash) = stored.split(":").let { (Crypto.decode(it.getOrNull(0) ?: "") to Crypto.decode(it.getOrNull(1) ?: "")) }
        if (salt == null || hash == null) return false
        return java.security.MessageDigest.isEqual(PinPolicy.hash(pin, salt), hash)
    }

    /** Fingerprint or face unlock succeeded (checked by Android). */
    fun unlockedByBiometric() { if (_biometric.value) _locked.value = false }

    fun lockNow() { if (hasPin) _locked.value = true }

    fun setBiometric(on: Boolean) { prefs.edit().putBoolean(KEY_BIO, on).apply(); _biometric.value = on }
    fun setLockAfter(ms: Long) { prefs.edit().putLong(KEY_AFTER, ms).apply(); _lockAfterMs.value = ms }
    fun setRecoverySaved() { prefs.edit().putBoolean(KEY_RECOVERY_SAVED, true).apply(); _recoverySaved.value = true }

    fun onBackground() { backgroundSince = clock() }
    fun onForeground() {
        if (hasPin && backgroundSince > 0 && clock() - backgroundSince >= _lockAfterMs.value) _locked.value = true
        backgroundSince = 0
    }

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_UNTIL = "pin_wait_until"
        private const val KEY_BIO = "biometric"
        private const val KEY_AFTER = "lock_after_ms"
        private const val KEY_RECOVERY_SAVED = "recovery_saved"
        const val DEFAULT_AFTER_MS = 60_000L
    }
}

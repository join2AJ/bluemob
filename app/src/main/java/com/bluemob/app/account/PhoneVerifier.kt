package com.bluemob.app.account

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/** What happened while checking a phone number. */
sealed interface VerifyEvent {
    data object CodeSent : VerifyEvent
    /** The number is confirmed (the code was right, or Android read the SMS by itself). */
    data object Verified : VerifyEvent
    data class Error(val message: String) : VerifyEvent
}

/** Sends a one-time code to a phone number and checks it. */
interface PhoneVerifier {
    /** True when a real SMS goes out; false in test builds (code [Otp.TEST_CODE]). */
    val real: Boolean
    fun send(e164: String, resend: Boolean, onEvent: (VerifyEvent) -> Unit)
    fun check(code: String, onEvent: (VerifyEvent) -> Unit)
}

/** Test builds: no SMS, the code is always [Otp.TEST_CODE]. */
object TestVerifier : PhoneVerifier {
    override val real = false
    override fun send(e164: String, resend: Boolean, onEvent: (VerifyEvent) -> Unit) = onEvent(VerifyEvent.CodeSent)
    override fun check(code: String, onEvent: (VerifyEvent) -> Unit) =
        onEvent(if (Otp.check(code)) VerifyEvent.Verified else VerifyEvent.Error("That code isn't right. Check the SMS and try again."))
}

/**
 * Real SMS codes through Firebase Authentication. We only use it to prove the number is yours: BlueMob signs out of
 * Firebase straight after, and your BlueMob ID stays your device key as before.
 */
class FirebaseVerifier(private val activity: Activity) : PhoneVerifier {
    override val real = true
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    override fun send(e164: String, resend: Boolean, onEvent: (VerifyEvent) -> Unit) {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull() ?: return onEvent(VerifyEvent.Error("Phone sign-in isn't available right now."))
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = signIn(credential, onEvent)
            override fun onVerificationFailed(e: FirebaseException) = onEvent(VerifyEvent.Error(friendly(e)))
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id; resendToken = token
                onEvent(VerifyEvent.CodeSent)
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth).setPhoneNumber(e164).setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks)
            .apply { if (resend) resendToken?.let { setForceResendingToken(it) } }.build()
        runCatching { PhoneAuthProvider.verifyPhoneNumber(options) }.onFailure { onEvent(VerifyEvent.Error("Couldn't send the code. Check the internet and try again.")) }
    }

    override fun check(code: String, onEvent: (VerifyEvent) -> Unit) {
        val id = verificationId ?: return onEvent(VerifyEvent.Error("Send the code first."))
        signIn(PhoneAuthProvider.getCredential(id, code), onEvent)
    }

    private fun signIn(credential: PhoneAuthCredential, onEvent: (VerifyEvent) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        auth.signInWithCredential(credential)
            .addOnSuccessListener { auth.signOut(); onEvent(VerifyEvent.Verified) }
            .addOnFailureListener { onEvent(VerifyEvent.Error("That code isn't right. Check the SMS and try again.")) }
    }

    private fun friendly(e: FirebaseException): String = when {
        e.message?.contains("format", true) == true || e.message?.contains("invalid", true) == true -> "That number doesn't look right."
        e.message?.contains("quota", true) == true || e.message?.contains("many", true) == true -> "Too many codes sent. Try again later."
        else -> "Couldn't send the code. Check the internet and try again."
    }
}

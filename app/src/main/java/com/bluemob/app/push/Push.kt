package com.bluemob.app.push

import android.content.Context
import android.util.Log
import com.bluemob.app.BlueMobApp
import com.bluemob.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase, used for two things only, and only when the build has a Firebase project set (gradle.properties):
 * waking this phone when someone calls or messages while BlueMob is closed, and real SMS codes at sign-up.
 * Wake-ups carry no message content: the phone wakes, connects to the relay and fetches what's waiting itself. The one
 * exception is an SOS for an SOS contact, which carries the alert itself (note, position) so the alarm shows at once.
 */
object Push {
    val configured: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    @Volatile var ready = false
        private set

    fun init(context: Context): Boolean {
        if (!configured) return false
        ready = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build())
            true
        }.getOrElse { Log.w("BlueMobPush", "Firebase didn't start", it); false }
        return ready
    }

    /** This phone's push token, for the relay. */
    fun token(onToken: (String) -> Unit) {
        if (!ready) return
        runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> if (!t.isNullOrBlank()) onToken(t) } }
    }
}

/** Receives wake-ups from the relay. */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as? BlueMobApp)?.takeIf { it.startupError == null }?.live?.setPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as? BlueMobApp ?: return
        if (app.startupError != null) return
        val d = message.data
        when (d["k"]) {
            // Someone is calling: ring now (full screen), while BlueMob connects and the call itself comes through.
            "call" -> if (app.calls.call.value == null) app.notifier.incomingCall(d["name"]?.take(40).orEmpty().ifBlank { "Someone" }, d["video"] == "true")
            // A message is waiting on the relay: fetch it (the usual notification shows once it's here).
            "msg" -> app.bridge.syncNow()
            // An SOS from someone whose SOS contact we are: the full-screen alarm right away, then fetch the rest.
            "sos", "safe" -> { app.sosCircle.fromPush(d); app.bridge.syncNow() }
        }
    }
}

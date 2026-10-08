# Firebase setup: ringing when BlueMob is closed, and real SMS codes

BlueMob uses Firebase for two things only:

1. **Wake-ups.** When someone calls or messages a phone whose BlueMob is closed, the relay sends it a content-free push
   ("a call", "a message"). The phone rings or fetches the message from the relay itself.
   **SOS alarms:** when you send an SOS, each of your SOS contacts gets a push straight away, every time, never held
   back. It carries the alert itself (note, position, battery, blood group), so their phone shows a full-screen alarm
   with sound even when BlueMob is closed or the phone is locked, with **I'm coming** and **Open map** buttons.
   Pushes are free (Firebase Cloud Messaging has no per-message charge, and the free Spark plan is enough for them).
2. **Real SMS codes at sign-up**, instead of the test code `123456`. Only these cost money (Blaze plan).

Until this is set up, everything else works as before.

## 1. Create the project (5 minutes)

1. Go to <https://console.firebase.google.com> → **Add project** → name it `BlueMob`. Google Analytics isn't needed.
2. **Add app → Android**:
   - Package name: `com.bluemob.app`
   - SHA-1 (the test builds' signing key, kept in `app/debug.keystore`):
     `4C:F0:22:9F:89:C3:19:0F:F3:C6:42:36:18:55:D6:1F:3C:0A:E0:AB`
   - Then open **Project settings → Your apps → Android** and also add the SHA-256:
     `25:2F:70:8A:29:26:98:08:D6:C2:9F:87:2B:0D:50:E1:50:43:D2:D7:B8:36:B3:3D:B1:8B:92:DD:9B:A8:E3:FE`
3. Download `google-services.json`. You don't add it to the app. Copy four values from it into `gradle.properties`:

   | gradle.properties | in google-services.json |
   |---|---|
   | `firebaseAppId` | `client[0].client_info.mobilesdk_app_id` |
   | `firebaseApiKey` | `client[0].api_key[0].current_key` |
   | `firebaseProjectId` | `project_info.project_id` |
   | `firebaseSenderId` | `project_info.project_number` |

   These four values aren't secret: they're inside every Android app that uses Firebase.

## 2. SMS codes

**Build → Authentication → Get started → Sign-in method → Phone → Enable.**

- Google charges per SMS sent, so phone sign-in needs the **Blaze (pay as you go)** plan. Check the current price for
  India on the Firebase pricing page, and set a budget alert under *Usage and billing*.
- For testing without spending, add test numbers under **Phone → Phone numbers for testing**, e.g. `+91 99999 00001`
  with code `123456`.

## 3. Wake-ups (the relay sends them)

1. **Project settings → Service accounts → Generate new private key.** This downloads a JSON file. **It is secret:**
   don't commit it or share it.
2. In Render: **bluemob → Environment → Add environment variable**:
   - Key: `FCM_SERVICE_ACCOUNT`
   - Value: paste the whole JSON file's contents
3. Save. Render redeploys, and the log shows `Firebase wake-ups on`.

The Firebase Cloud Messaging API is enabled by default for new projects. If the log shows errors, enable
**Firebase Cloud Messaging API (V1)** in Google Cloud Console → APIs.

## 4. Build

Send Claude the four values (or set them in `gradle.properties` yourself) and build a new APK. On each phone:

- Open BlueMob once while online: it registers for wake-ups.
- Then close BlueMob completely and call that phone from another one. It rings like a phone call. Tap it, and the call
  connects as BlueMob opens. The caller sees "Ringing …'s phone (BlueMob was closed)".

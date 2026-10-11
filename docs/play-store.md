# Publishing BlueMob on Google Play

## Done in the app and server (0.25)

| Requirement | Where |
|---|---|
| Delete account in the app | You → Account & backup → Delete my account (relay forgets the ID, number fingerprint, email, backups and waiting messages; the phone is wiped) |
| Delete account on the web | `https://YOUR-RELAY/delete-account` |
| Privacy policy | `https://YOUR-RELAY/privacy` (set `CONTACT_EMAIL` on the server) |
| Block and report people | Open someone's profile → Block / Report (reasons, optional last 10 messages) |
| Emergency disclaimer | SOS screen: "can't promise anyone receives it… also call 112"; privacy policy "Safety" |
| Release signing | `keystore.properties` or `BLUEMOB_KEYSTORE*` environment variables (see `app/build.gradle.kts`) |
| Crash and error reports | Sent automatically (can be turned off in Diagnostics); team alerted through `ALERT_WEBHOOK` |

## You do these

1. **Upload key.** `keytool -genkeypair -v -keystore bluemob-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`.
   Keep two copies offline. Create `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
   Build with `./gradlew :app:bundleRelease` and upload the `.aab`. Turn on Play App Signing.
2. **Firebase.** Add the release key's SHA-1 and SHA-256 in Firebase; put `FCM_SERVICE_ACCOUNT` on the server.
3. **Server.** Make sure Render (or your own server) runs the latest `server/` code: check `https://YOUR-RELAY/privacy` opens.
4. **Play Console declarations.**
   - Foreground services: *remote messaging* (calls and messages while closed), *connected device* (Bluetooth mesh),
     *microphone/camera* (calls). Record a short video of each.
   - Full-screen intent: incoming calls and SOS alarms.
   - Data safety form: use the table in the privacy policy. Data is encrypted in transit; users can request deletion.
   - Content rating questionnaire: users can message each other; there is block and report.
   - Target audience: 13+.
5. **Closed test.** New personal developer accounts need at least 12 testers opted in for 14 days before production.
6. **Store listing.** Category *Communication*. Don't call it an official or government emergency app.
7. **Target API.** Google raises the minimum target API every August; check the current requirement before uploading
   and raise `targetSdk` if needed.

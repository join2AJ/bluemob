# BlueMob

Offline mesh messaging for Android. Phones talk to each other directly over Bluetooth and
Wi-Fi without cell towers or internet. If any phone in the mesh has internet, it can carry
the others' messages to the outside world.

```
 A (no signal) ──BT/Wi-Fi──► B (no signal) ──BT/Wi-Fi──► C (has internet) ──► Firebase ──► D (far away)
```

## Web preview

`web/index.html` is a clickable web version of the app with the same screens and design. Browsers
can't link phones over Bluetooth, so nearby people (Asha, Ravi, Meera, Kabir) are simulated there.
It also plays out the internet-bridge idea: message Dee, 2,000 km away, and watch the message hop
through Meera, who has internet.

## Roadmap

| Phase | Feature | Android | Web preview |
|---|---|---|---|
| 1 | Discover and connect to nearby phones (Bluetooth / Wi-Fi) | ✅ | simulated |
| 2 | Chat saved on the phone, store-and-forward, exactly-once, delivered / read receipts | ✅ 0.3 | ✅ |
| – | Pine & Sand design system, 5 tabs, SOS button everywhere | ✅ 0.3 | ✅ |
| – | Sky (on-device helper), survival guide, SOS (send, pass on, alert, signals), compass | ✅ 0.3 | ✅ |
| 3 | Chat messages hopping through several phones (A → B → C) | | simulated |
| 4 | Internet bridge + BlueMob relay (Firebase): far-away friends, SMS to loved ones, experts | | simulated |
| 5 | End-to-end encryption | | |
| 6 | Games, trip money, insights, offline maps | | ✅ |
| 7–8 | Voice and video calls | | |

## Android 0.3: what's real on the phone

- **Design:** "Pine & Sand" tokens (`ui/theme`), bundled Bricolage Grotesque + Figtree fonts (SIL OFL, licences in
  `app/src/main/assets/licenses`), light and dark, floating tab bar, SOS button on every tab.
- **Messaging** (`chat/MessageRepository.kt`, `data/Database.kt`): messages are saved in a Room database with a unique ID.
  If the person isn't in range, the message waits and goes over Bluetooth / Wi-Fi the moment they connect. It's re-sent on
  each connection until a delivery receipt arrives; the receiver keeps every ID it has accepted and discards second copies,
  so each message shows exactly once. Read receipts go back when the chat is opened, or on the next connection.
  Tap a sent message for its receipts, delivery paths and full history.
- **Sky** (`bot/SkyBot.kt`): runs inside the app, no internet. Answers from the survival guide, explains the app, and gives
  live facts (who's nearby, your ID, battery, waiting messages, SOS status).
- **Survival guide** (`guide/`): 20 articles in 9 topics, search, saved guides. Generated from `web/src/features.js`.
- **SOS** (`sos/`): two-tap send with an optional note and your last position; re-sent to every phone that connects until
  "I'm safe", and passed on by each phone that hears it (up to 5 hops). Receiving one opens a full-screen alert with
  "I'm coming", "Show me the way" and "How to help". The SOS signal uses the screen, the real flashlight, a 2.8 kHz
  tone, or all three, with a default you choose.
- **Compass** (`compass/`, `ui/compass`): phone compass sensor + GPS, no data. Targets: saved spots and friends who share
  their location.
- **Battery:** shortcuts to Android's Battery Saver and to let BlueMob keep running while it's on.

## Getting the app

**Without Android Studio:** every push builds an APK on GitHub Actions. Open the repo's
**Actions** tab → latest *Android build* run → download **BlueMob-debug-apk**, unzip it and install
`app-debug.apk` on each phone (allow "Install unknown apps"). Each CI build is signed with a fresh
debug key, so uninstall the old version before installing a new one.

**With Android Studio:** open this folder and press Run, or from a terminal:

```
./gradlew assembleDebug     # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest # unit tests
```

## Testing

**Automated** (`./gradlew testDebugUnitTest`): the delivery engine with two simulated phones (waits until in range,
exactly-once after a re-send, read receipts), 26 Sky questions, geo maths, and screenshots of 13 screens.
The web preview has its own Sky test: `node web/tests/sky_answers.test.js`.

**On one phone:** go through the intro, ask Sky questions, read the guide, try the SOS signal (flashlight and sound),
and the compass (outdoors for GPS).

**With 2 or more phones** (emulators can't use Bluetooth with each other):

1. Install the APK on each phone, go through the intro and tap **Allow** for nearby devices.
2. On Android 12 and older, switch **Location** on in quick settings (no internet is used).
3. Turn on airplane mode, then turn Bluetooth and Wi-Fi back on.
4. Each phone shows the other as **Online** on the Nearby radar.
5. **Store-and-forward:** turn the mesh off on phone B, send B a message from phone A (it shows the clock),
   then turn B's mesh back on. The message arrives, and A's tick goes to delivered, then read when B opens the chat.
6. **SOS:** send an SOS from one phone. The other opens the alert. With three phones in a line, the middle one passes it on.
7. Turn on **Share my location** on both phones to see distance on the radar and walk to each other with the compass.

If something fails, send a screenshot of **You → Mesh activity log**.

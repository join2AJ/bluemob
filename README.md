# BlueMob

Offline mesh messaging for Android. Phones talk to each other directly over Bluetooth and
Wi-Fi without cell towers or internet. If any phone in the mesh has internet, it can carry
the others' messages to the outside world.

```
 A (no signal) ──BT/Wi-Fi──► B (no signal) ──BT/Wi-Fi──► C (has internet) ──► Firebase ──► D (far away)
```

## Web preview

> **Frozen at the 0.6 feature set.** New features are built for Android only; the web preview stays as a clickable
> demo of the idea and isn't updated with each release.

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
| 3 | Message anyone by BlueMob ID: messages hop phone to phone (A → B → C), end-to-end encrypted | ✅ 0.5 | simulated |
| – | Verified IDs (key-based), signed SOS / lost / rescue packets, background service + notifications | ✅ 0.5 | |
| 4 | Internet bridge + BlueMob relay (Firebase): far-away friends, SMS to loved ones, experts | | simulated |
| 5 | End-to-end encryption | ✅ 0.5 (messages) | |
| – | Trail, base camp, "walking straight?", lost mode with position estimate | ✅ 0.4 | |
| – | SOS contacts (SMS), connections panel, tamper-evident audit trail | ✅ 0.4 | ✅ audit |
| 6 | Games vs the computer | ✅ 0.4 | ✅ |
| 6 | Games with people nearby, trip money, insights, offline maps | | ✅ |
| 7–8 | Voice and video calls | | |

## Android 0.22: do not disturb, safer and smarter Sky AI, fixes

- **Do not disturb** (You tab): for 1 hour, 8 hours, until 7 am or until turned off. Messages, missed calls and
  invites arrive silently, and calls don't ring (the caller is told; it's logged as missed). SOS alerts and rescue
  groups always sound; calls from your SOS contacts can still ring.
- **Sky's AI knows BlueMob**: every question carries the phone's situation (SOS, battery, internet, people nearby) and
  the best-matching survival guide, so urgent answers follow checked advice: most important action first, then steps.
- **Safety**: talk of suicide or self-harm gets kind support with Tele-MANAS 14416 and 112, never advice; requests to
  hurt others are declined. This works with or without the AI.
- **Trips**: one vehicle per ride. The mode only changes where they stopped or walked for 3+ minutes, so a car
  slowing at a junction no longer turns into a cycle. Zooming the trail map no longer crashes.
- **Rescue groups**: "I'm coming" and "I'm safe" are re-sent every few minutes to members who were offline, and a
  rescue with no news for a day closes on helpers' phones, so it no longer stays "On the way".

## Android 0.21: Sky's offline AI

- **Ask Sky anything, with no internet.** In the Sky chat, tap "Make Sky smarter, offline" and download an AI brain
  once: Lite (Qwen 2.5 0.5B, 547 MB) or Standard (Qwen 2.5 1.5B, 1.6 GB, for phones with 6 GB+ memory). It's never
  in the APK and never downloaded unless the user asks. Wi-Fi only by default; the file is checked against its
  SHA-256 before use.
- **Runs on the phone** with Google's MediaPipe LLM engine. Questions never leave the phone. Answers appear as they're
  written. The model is loaded only while in use and released after a few idle minutes.
- Sky still answers its own status questions and taught answers itself, and links the checked survival guide for
  first-aid topics. Gemma or another MediaPipe `.task` model can be brought in from a file. Switch off or delete
  any time.
- The online Smart Sky from 0.20 is removed: nothing Sky does goes to a server.

## Android 0.19: trips you can replay and export, hunting mode, a new call screen

- **Trips** are always reachable (Compass → All trips), even with recording off, and recorded in more detail (every
  5 m, with speed and altitude). Each trip has a zoomable map, **playback** with a time slider (10×, 60×, 300×), a
  speed chart, and **how they travelled** (on foot, cycle, motorbike, car or bus, judged from speed). **Export picture +
  data** shares a picture of the trail with its numbers, a CSV of every point (date, time, position, accuracy, speed,
  altitude, travel mode) and a GPX file.
- **Save this spot** asks for a name and keeps the date and time.
- **SOS hunting mode:** while an SOS is on, the phone keeps scanning for phones nearby. People coming to help can make
  it sound, flash its light or light up its screen for 20 seconds, to find someone who can't answer. On by default;
  You → Safety → "Helpers can signal my phone" turns it off.
- **Call screen:** dark, with glowing blue waves round the caller that move with the voices.

## Android 0.18: a fuller dashboard, new game look, real-life quiz

- **Your activity** now shows time spent in each part of BlueMob (Chats, Calls, Guide, Settings…) with a bar per day,
  insights (who you talk to most, busiest time and day, calls answered, average call), how your messages travelled
  (phone to phone, carried by other phones, internet) and your learning (guides, quizzes, streak). Time is counted only
  while BlueMob is on screen, and stays on the phone.
- **Games:** new drawn icons for every game, cleaner game cards, a timeline-style "How to play", a score bar that shows
  whose turn it is, and X and O marks that draw themselves.
- **Survival quiz:** 25 new questions from real situations (a collapse at a bus stop, a dog bite, a gas leak, a flooded
  road, heatstroke, a crowd crush…), each explaining why the answer is right and why the others are wrong.
- The emergency grid at the top of the Guide is gone.

## Android 0.17: groups, a smarter compass, emergency mode

- **Chats:** group chats (each message reaches every member end-to-end encrypted, by any path), replies and
  reactions (long-press a message), pinned chats, search inside messages, repeated calls grouped as one row with a
  count, and a missed-call badge.
- **Nearby (radar kept):** each person's battery and when they were last seen, filters (online, in range, SOS, low
  battery), and **Check on everyone**, which asks every phone nearby "Are you OK?" and shows the answers.
- **Compass:** the sun on the dial, daylight left, sunset and the moon; **back to base** (straight to camp, or
  **retrace my trail**); walking time and altitude; share my location; figure-8 calibration help; and a map of you,
  spots and friends that needs no download.
- **Guide:** emergency buttons (CPR, bleeding, burns, choking, snake bite, lost), a reading streak, and a **Test
  yourself** quiz after every guide.
- **You:** badges, an emergency card on the lock screen (blood group and SOS contacts), and a light, dark or auto theme.
- **Calls:** voice repairs itself when Android drops the microphone or speaker mid-call, and the call screen shows
  voice counters when there's trouble.

## Android 0.16: SOS alarm push, contacts who join later, optional email

- **SOS alarm push.** Your SOS reaches each SOS contact as a Firebase push straight away, never held back. Their phone
  shows a full-screen alarm with sound even when BlueMob is closed, with **I'm coming** and **Open map** buttons.
  "I'm safe" clears it. Pushes are free. "I'm coming" also tells you in your chat, with how far away they are.
- **No deadline for SOS contacts.** People who aren't on BlueMob yet stay on your list. Every few hours BlueMob checks
  whether they've signed up with that number, and asks them automatically. **Invite** texts them from your own SMS app.
  Your SOS still reaches everyone nearby meanwhile.
- **Optional email** at sign-up and in Account, kept for account recovery and receipts later. It's stored on the
  relay against your BlueMob ID and never shown to anyone.
- **Full-screen alerts check** in Connections & power, for Android 14+ phones that block alarms over the lock screen.

## Android 0.15: SOS contacts in the app, ratings by category, your activity

- **SOS contacts live in BlueMob.** Add 1 to 4 people by phone number. They get a request in BlueMob and accept or
  decline. At least one has to accept. Your SOS and "I'm safe" reach them over the internet, through phones nearby, or
  later as a chat message. No SMS gateway is used.
- **Ratings in five categories:** helpful, quick to respond, reliable, clear communication, respectful. Scores show in
  quarter stars, and everyone starts at 4. You can only rate people you've met, chatted with or shared a rescue with,
  once every 30 days. Your rating shows under your name in the You tab. Tap it to see each category and what people said.
- **Your activity:** messages, calls (dialled, received, missed), talk time, SOS and rescues joined. Charts can be
  filtered to 7, 30 or 90 days, or all time.

## Android 0.14: smarter Sky, games, a picture for every guide

- **Fixes**: the intro shows only before sign-up ("Replay the intro" works); full screen brightness only for the
  screen signal; one list of guide packs; plain-language internet screen (no server address); messages count as read
  only while the chat is on screen; waiting receipts go as soon as the internet link reconnects.
- **Sky**: synonyms ("electricity" → charging), weighted search across guides, "did you mean" instead of a wrong guess,
  and **Teach Sky**: answers you teach it are kept on your phone.
- **Games**: classic tic-tac-toe hidden (Infinite replaces it), new How to play, emoji and quick-phrase reactions,
  the quiz explains why an answer is wrong and why the right one is right, new **Five in a row**.
- **Guides**: a different animation for every guide (the shadow stick shows how to find north step by step, knots tie
  themselves), *Find someone with BlueMob*, new **Knots**, **Fire craft** and **Shelters** packs.
- **Chats**: one Rescues box (asked for help / helped), exact dates and times in rescue groups, delete a chat with a
  long press (not Sky), Diagnostics → **Report a problem** (read reports at `/v1/reports?token=REPORTS_TOKEN`).

## Android 0.13: simpler settings, files over the internet, wake-ups, BlueMob Cloud

- **You tab regrouped**: Connections & power (mesh, reachable when closed, Bluetooth / Wi-Fi rules, battery), SOS (with
  contacts), Account & backup, Storage & data, Diagnostics (audit trail, relay, logs, technical details). Sky lives in
  the Guide tab. Link checks and routing details in chats are off unless turned on in Diagnostics.
- **Files over the internet**: photos, documents and voice notes reach people who aren't nearby through the relay,
  still encrypted with a key only the two phones have; only the recipient can download, and it's deleted after.
- **Wake-ups (Firebase, optional)**: calls ring and messages arrive with BlueMob closed; real SMS codes at sign-up.
  Set up in `docs/firebase.md`; without it, everything works as before.
- **BlueMob Cloud backups**: the same password-locked backup file, kept on the relay (newest 3); restore on a new
  phone after restoring the ID with the recovery code.
- **Start-up**: background starts no longer leave a false "BlueMob couldn't start"; an unexplained stop is retried once.

## Android 0.12: faster internet, calls through friends, encrypted calls

- **Messages over the internet in about a second**: they go straight over the relay's live channel when the other
  phone is online; otherwise the relay stores them and pokes the phone (or the friend carrying it) the moment one
  arrives, instead of waiting for the next 20-second sync.
- **Calls hop through friends**: phones share a small route table, so a call can go A → B → C (up to 5 hops) when
  A and C aren't in range of each other. **One phone with internet is the whole group's way out**: it tells the
  relay which phones it can reach, and calls and messages for them come through it.
- **End-to-end encrypted calls** (both phones on 0.12+): voice and video are sealed with a key only the two phones can
  derive, so neither the phones in between nor the relay can listen in or inject sound.
- **No more one-way voice when the screen locks**: a microphone foreground service keeps the mic live during calls.
- **Internet video adapts**: up to 640 px at 12 fps, stepping down when frames start to queue.
- Calls keep ringing for 20 s while the other phone reconnects; chats show "Online on the internet" or
  "Reachable through Asha"; decline with a quick message.
- **Calls history filters** (missed, received, dialled, video, voice; today / 7 / 30 days; search) and a **Files** tab
  for everything shared in all chats, filtered by type or extension and sorted by date, size or name.
- **Survival guide dashboard**: counts, reading progress, continue reading, guide of the day, topics; a NEW tag for a
  day after a pack downloads; step-by-step mode, read aloud, text size, share, tick-off steps, a CPR beat, and an
  animated illustration for each topic.

## Android 0.11: consent, trips, backup, more games, guide packs

- **Nothing turns on by itself**: the mesh asks before starting with Bluetooth or Wi-Fi off, pauses when Bluetooth is
  switched off (instead of letting Nearby switch it back on), and "Turn on automatically" lets people choose defaults.
- **Trips**: every trail is a trip with its own history and a to-scale map; share as GPX for offline map apps.
- **Backup**: one compressed file, AES-256-GCM with the user's password; saved to the phone, a picked folder, or any
  app via "Save to…" (Google Drive, OneDrive); daily / weekly / monthly; restore merges into the phone.
- **Games**: Infinite tic-tac-toe, Dots & Boxes and a Survival quiz, against the computer or people (nearby or online).
- **Guide packs**: download extra guides (mountains, monsoon, heat, wildlife) from the relay; they work offline.

## Android 0.8: login, recovery code, mixed versions

- **Sign up**: choose a name, create a 6-digit PIN, optionally turn on fingerprint/face unlock, and write down your
  recovery code. There's no server account: your account is your BlueMob ID and its key, kept on the phone.
- **Log in**: PIN or fingerprint when BlueMob opens, and again after it's been in the background (you choose:
  right away, 1, 5 or 30 minutes). Five wrong PINs start a wait that doubles up to 15 minutes. The PIN is stored
  only as a salted PBKDF2 hash. The mesh keeps running while locked, and **SOS works from the lock screen**
  (hold the button for a second).
- **Recovery code**: 28 characters for new accounts (a 120-bit seed the key is derived from), 56 for accounts made
  before 0.8 (the key itself). It's the only way to move a BlueMob ID to a new phone, and is shown only after the PIN.
- **Mixed versions**: see *Compatibility* below.

## Compatibility between versions

| Feature | Works between |
|---|---|
| Finding each other, messages, receipts, message by ID, SOS, lost mode, rescue groups, ratings | 0.5 and every newer version |
| "Ring their phone", games with people, voice and video calls | 0.7 and newer on both phones |
| Login, recovery code | Per phone (nothing to agree on) |

0.4 and older use an unsigned protocol (`BM1`) and can't link with 0.5+. Instead of ignoring them silently, newer
phones show "*name* is nearby on a different BlueMob version". When a feature needs a newer version on the other
phone, BlueMob says so ("Asha has BlueMob 0.6… ask them to update") instead of waiting forever.

Rules that keep this working: every hello carries the app version and a list of capabilities; new features add new
packet types and capabilities and never change what existing packets mean; unknown packet types and fields are
ignored; the protocol number in the endpoint name (`BM2`) changes only when old phones truly can't understand new
ones.

## Android 0.6: encrypted storage, internet bridge, star ratings

- **Everything on the phone is encrypted.** The database (messages, rescue groups, trail, audit trail, ratings) uses
  SQLCipher (AES-256); its key is sealed by Android Keystore. All settings files (identity, contacts, SOS numbers, keys)
  are encrypted, names included (`crypto/SecurePrefs.kt`). Older plain data is converted on first launch. Backups and
  phone-to-phone transfer are blocked, and release builds are shrunk and obfuscated with R8.
- **Audit trail, hardened** (`audit/`): every entry is also signed with the phone's private key, a protected checkpoint
  catches deletions from the end, and phones you meet keep a signed copy of your newest entry and hand it back later
  (witnesses). The audit screen shows each check, plus whether storage is encrypted.
- **Internet bridge** (`bridge/`, `server/`): people who met over Bluetooth keep talking from anywhere. A phone with
  internet uploads messages it sends or carries to the BlueMob relay, and downloads messages for itself and the phones
  around it. Find someone you've never met by ID: the relay knows public keys. The relay only sees signed, encrypted
  packets. Set its address in You → Internet bridge; deploy it with `server/README.md`.
- **Star ratings** (`trust/`): everyone has 0 to 5 stars, starting at 4. Being thanked for help adds stars; bad
  language flags and fake SOS reports take them away; a confirmed real SOS adds a little. Remarks show on each profile.
  An SOS alert shows the sender's stars and warns if someone reported an earlier SOS from them as fake. After a rescue,
  helpers say whether the SOS was real, and the person helped can thank who came. Ratings are signed, spread over the
  mesh and the relay, and one person can only move someone's stars a little.
- **Visible IDs**: every card on Nearby shows the person's BlueMob ID and stars; chats show the full ID.

## Android 0.5: security, reach and reliability

All phones need 0.5: it speaks a new protocol (`BM2`) and won't link with older versions. IDs change once (see below).

- **Message anyone by BlueMob ID** (`mesh/MeshRouter.kt`, Chats → "Message anyone by BlueMob ID"): type someone's ID, even if
  you've never met. In range, it goes straight to them. Otherwise it's handed to phones nearby, who carry it and pass it on
  as people move (store-carry-forward, "Binary Spray and Wait": at most 8 copies, so the mesh doesn't flood). Receipts come
  back the same way and tell carriers to drop their copies. If we don't have their key yet, the mesh is asked for it; any
  phone that knows it can answer. Carried messages expire after 3 days, with limits per sender.
- **End-to-end encryption** (`crypto/Crypto.kt`): ECDH P-256 + HKDF + AES-256-GCM per pair of phones. Carriers can't read or
  change messages.
- **Verified identities**: each phone has a P-256 key pair (private key wrapped by an Android Keystore key). The BlueMob ID
  is derived from the public key, so it can't be claimed by another phone. Hellos sign Nearby's per-connection token;
  SOS, "I'm safe", lost-mode and rescue-group packets are signed, so nobody can fake someone's SOS or cancel it.
  Existing IDs change once to the key-based ID (recorded in the audit trail).
- **Background**: a foreground service keeps the mesh on with the screen off (You → "Stay on in the background").
  Notifications for SOS (alarm channel, over the lock screen only while the alert shows), messages and rescue groups.
- **Hardening**: payload size limits, field length caps, bounded "seen" sets, batched contact saves, protocol version.
- See the full audit and roadmap: `docs/audit.html`.

## Android 0.4: what's new

- **Rescue groups** (`rescue/`, `ui/rescue`): tapping **I'm coming** on an SOS joins that SOS's group and opens the
  rescue screen. Helpers see how far away the person is, which way to go (arrow + Navigate), where they are (GPS or
  estimate), their note and battery, a matching guide, and a "before you set off" checklist. Everyone in the group
  (the person in need and every helper) sees who's coming, how far each is and when they last updated, and shares one
  chat with quick replies. Helpers and the person share their position every 45 s; "I'm here" and "I can't come" update
  the group; "I'm safe" ends it. Group messages travel the mesh like an SOS (passed on, up to 5 hops) and are re-sent
  to phones that connect later. Groups also appear at the top of Chats, and new activity shows a banner.

- **Trail and lost mode** (`trail/`, `ui/compass/TrailViews.kt`): opt-in, because it keeps GPS on. Draws your trail on a
  north-up map (solid = GPS, dashed = estimated), keeps the last GPS fix and your base camp, and tells you whether you're
  walking straight, curving left or right, weaving, or walking in a circle. When GPS drops out, the step counter and compass
  carry your position forward (dead reckoning) with an honest margin of error. **I'm lost** shares that estimate with
  everyone nearby every minute (GPS, or last fix + metres walked + direction in degrees), passed on like an SOS, and it's
  also included in every SOS. Helpers see "Asha is lost" on Nearby and can walk to her with the compass.
- **SOS**: new animated SOS circle (ripples, breathing, a countdown ring after the first tap), **SOS contacts** (stored on
  the phone only) that you can text with one tap from your SMS app, "What will be sent", and "Preview: receive an SOS".
- **Connections** (`system/Radios.kt`, You → Bluetooth, Wi-Fi, GPS, internet): shows what's on, warns on Nearby when
  something BlueMob needs is off, and each switch opens the matching Android panel (Android doesn't let apps flip radios).
- **Audit trail** (`audit/`, You → Audit trail): SOS sent and received, messages, receipts, connections and shared positions,
  each entry chained to the one before with SHA-256. There's no edit or delete in the app, a database trigger refuses
  changes, "Delete all messages" keeps it, and the screen verifies the chain. (Someone with root access could still rewrite
  the file; the chain makes that detectable, not impossible.)
- **Games**: Tic-tac-toe (easy or unbeatable) and Connect 4 against the computer, for when no one is around.
- **Sky**: critical-battery advice ("At 1%, act now") and how to recharge without a socket; ideas for free time; calmer
  replies to questions it can't answer (SOS only for urgent ones).

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
- **Survival guide** (`guide/`): 22 articles in 9 topics, search, saved guides. Generated from `web/src/features.js`
  with `node tools/gen_guide.js`.
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
exactly-once after a re-send, read receipts), 35 Sky questions, trail maths and dead reckoning, the audit chain (edits and
removals are caught), the game opponents (the unbeatable one is checked against every possible game), geo maths, and
rescue groups (joining, moving, arriving, leaving, late SOS), the router on a simulated network of moving phones
(carried delivery, key lookup, tampering and forgery rejected, copy limits, expiry), one end-to-end test of message store +
router across three phones, and the internet bridge against the real relay server (two phones in different cities, finding someone by ID, tampered
packets refused), star scoring (caps, fading, no self-rating), audit signatures (a forger who recomputes every hash is
still caught, deletions from the end are caught), and screenshots of 30 screens. Relay tests: `node --test server/relay.test.js`.
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
7. **Message by ID across phones:** with three phones A, B, C: keep C away from A. On A, Chats → "Message anyone by BlueMob
   ID", type C's ID (C: Chats → same screen shows it). Bring A next to B, then walk B over to C. C gets the message;
   its receipt comes back the next time B meets A.
8. **Rescue group:** with three phones, send an SOS from A. On B tap **I'm coming**, then on C too. All three see each
   other in the group, with distances, and can chat. Tap "I'm safe" on A to end it.
9. **Lost mode:** on phone A, Compass → **I'm lost**. Phone B shows "A is lost" with A's position. Walk somewhere with
   A; in airplane mode indoors (no GPS) A's estimate keeps moving with its steps.
10. Turn on **Share my location** on both phones to see distance on the radar and walk to each other with the compass.

If something fails, send a screenshot of **You → Mesh activity log**.

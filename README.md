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

## Android 0.37.2: modern chat actions, ratings and icons

- **Attach panel like WhatsApp:** the paperclip opens a panel of round icons: Gallery, Video, Document,
  Location (sends your position with a map link) and My contact (your name and BlueMob ID). Back closes it.
- **Long-press a message:** the header becomes an action bar (close, count, Reply, Copy, Info, Delete), the
  message is highlighted, and the reactions sit in a pill above it. Tap another message to pick it instead.
- **Delete for me:** removes the message (and its file) from this phone only, after a confirm. Anyone who
  already got it keeps their copy.
- **Ratings:** a big score card, then one row per category with a tinted icon, the score and a bar; the
  "Rate" form uses the same icons. "How stars work" folds away.
- **Your activity:** Safety & learning is one compact card with the number after each label
  ("Messages 4"); Time in BlueMob is one stacked bar with a legend; Insights is a short list.
- **Icons everywhere:** emoji used as icons are now Material icons on tinted circles or tiles: Calls list
  (call direction and call-back buttons), Chats (new chat, pinned, trip group), Nearby, Compass, Trip
  groups, Itinerary, News, Report, Rescues, the fingerprint unlock, the SOS flashlight, helper signals,
  and each SOS setting now has its own icon instead of the same warning sign.

## Android 0.37.1: your screenshot feedback

- **Rescue screens have their own dot pattern**, in warm orange so they're told apart from chats. It hides both whose
  phone shows it and whose rescue it is, changing every hour. Admin page → Screenshot lookup → "Whose rescue
  screenshot is this?" (16 × 9 dots).
- **Bluetooth and Wi-Fi settings in boxes:** each choice is a row with a radio button and a line on what it means.
- **Full silence:** a second do-not-disturb under the first. It silences every BlueMob sound, even SOS alarms from
  people nearby, trip alerts and calls. Your own SOS still goes out. Turning it on asks a small sum; turning it off
  doesn't.
- **Tabs remember where you were:** come back from Storage & data (or any screen) and the You tab, like every other
  tab, is still scrolled where you left it.
- **Under your name:** a Trust tile (rating and how many) and a This week tile (your activity), instead of the old
  text lines. Picture, name and ID use proper icons.
- **Report a problem:** topics with icons in coloured circles; steps numbered as shown (no more 1, 3, 4); a Trip
  groups topic; Games hidden while games are.
- **Replies and the message menu:** the quoted message has an accent bar and the writer's name; the "Replying to"
  card above the box has a reply icon and a proper close button; long-press shows reactions in a pill, then Reply,
  Copy and Message info with icons.

## Android 0.37: trip tabs, trip mode, overdue alerts, buddy pairs, announcements

- **Trip group screen in tabs:** People, Plan, and Settings (admin or captain) or More (everyone else). The heading
  card, the latest announcement and "the trip has ended" stay above the tabs.
- **Trip mode on Nearby:** during a trip, the top card shows the last roll call, the next stop, any overdue
  checkpoints and your buddy, with Roll call (admin or captain), Chat and SOS buttons.
- **Overdue at a checkpoint:** 30 minutes after a checkpoint's planned time, the admin's or captain's phone rings if
  people haven't checked in, naming them. Once per checkpoint, up to 12 hours after it was due.
- **Buddy pairs:** the admin (or a team's captain) pairs people up (tap two people, or pair the rest in order). If a
  buddy's phone hasn't been near for 20 minutes, your phone tells you; after 40 minutes it tells the group, so the
  admin knows. Everyone sees their buddy in the people list and on the trip card.
- **Announcements with "Got it":** the admin or a captain taps 📣 Announce. Everyone gets it in the chat and at the
  top of the trip screen with a "👍 Got it" button. The sender sees "10 of 12 got it", and who hasn't.

## Android 0.36: chat dates, and a privacy and security audit

- **Chat dates:** each day starts with its own label (Today, Yesterday, a weekday, or the date). Before, every chat showed
  "Today" at the top, so older messages looked like today's. "1 member" instead of "1 members"; trip groups show 🧭.
- **Audit:** `docs/compliance.md` is now an audit of the whole app against DPDP (with the 2025 Rules), GDPR, HIPAA (and
  the FTC health breach rule), ISO 27001, ISO 22301, ISO 31000 and NIST CSF 2.0. It has a record of processing, a
  short impact assessment, a risk register with owners, and what's still open. Fixed in this version:
  - Before joining a trip group (code, QR or passcode), a notice says what the group sees: name, roll-call answers,
    checkpoints reached, last heard; never positions. Members see it in the group too.
  - When a trip ends (the admin ends it, or a day after its end date), tracking stops: roll calls, automatic checks,
    check-ins and joining. The group and its chat stay, so people can keep in touch, until each person leaves. The
    record of who reached which checkpoint is erased 30 days after the trip. Field-test results are erased 30 days after
    the test.
  - Download my data now includes trip groups (your role, the plan, your check-ins) and the field test.
  - The privacy policy (English and Hindi) covers trip groups, join codes, showing a group nearby and the
    chat-background code, with how long each is kept.
  - Passcodes: after 20 wrong tries in an hour from any phones, a group stops taking passcodes and tells its admin.
    Turning it on again gives a new passcode.
  - Admin page: optional second factor. Set `ADMIN_TOTP_SECRET` (base32) on Render and add it to an authenticator
    app; then sign-in needs the token and the 6-digit code, and gives an 8-hour session. Every sign-in and admin
    action (allowed or refused) is logged for a year: Admin log tab.
  - `docs/incident-response.md`: DPDP Rules (Board report within 72 hours), CERT-In (6 hours), FTC health breach rule,
    and what to do if the seal key or authenticator secret leaks.

## Android 0.35: itinerary, checkpoints, and joining nearby with a passcode

- **Itinerary:** the admin plans the main group and a captain plans their team: stops with a day, time, place
  name and note. Everyone sees the plan in time order, with the next stop marked. The trip group screen shows the
  next stop in an Itinerary card. Saving sends the plan to everyone, and anyone who joins later gets it with the
  welcome message. Other phones ignore a plan from anyone who isn't the admin, or the team's captain.
- **Checkpoints:** a stop can be a checkpoint, optionally with its place ("Use where I am") and a 50, 150 or 500 m
  circle. A phone inside the circle checks in by itself, if its location is on and the fix is recent and accurate.
  Anyone can also tap "📍 I'm here". Check-ins are group messages, so they reach the admin as soon as any phone can
  carry them, including when a team on its own path comes back in range. Each checkpoint shows who passed it, and
  when.
- **Team on a different path:** a captain's team roll call is summed up in the team chat once it's done ("4 of 5
  reached. Not reached: Ravi"). The admin is in every team, so it reaches them when the team is back in reach. On the
  trip group screen, anyone not reached shows when they were last heard from, and how (in the chat, in a team, or at
  a checkpoint).
- **Join nearby with a passcode:** the admin turns on "Show to people nearby" and gets a 4-digit passcode (or a new
  one). Phones connected straight to the admin's phone see the group under Nearby (and on the Join screen), tap it,
  and type the passcode the admin says out loud. Only the admin's phone checks the passcode; it's never sent to
  anyone. After 5 wrong tries in an hour, that phone has to wait. A wrong passcode gets a clear answer.

## Android 0.34: trip groups with an admin, teams and captains

- **Where:** Groups tab → Trip group card (New trip group, Join with a code, "How is it different from a group?").
  Groups now also show in the Chats list, with All / People / Groups filters.
- **Roles:** whoever makes the trip group is its admin. The admin adds teams (Team 1, Team 2…), each with a captain
  and its people. The admin is in every team; a captain sees the main group and their team. Anyone can still chat
  privately with anyone. Only the admin's phone can change the group's name and settings: other phones ignore changes
  from anyone else.
- **Automatic roll call:** the admin (or a team's captain) picks Off, 5, 10, 15, 30 or 60 min. Their phone sends the
  roll call, and rings if a phone hasn't answered within 3 minutes.
- **The trip ends:** the admin can set no end, 1–3 days or a week (when making the group or later). When it ends, the
  admin is asked to end it or keep it going another day. Without an answer it ends by itself a day later. (Since 0.36
  the group and its chat stay after the trip; only tracking stops.)
- **Numbered groups:** the name suggestion counts up ("Group 1", "Group 10"…) for operators running regular batches.
- **Join codes look random.** Share one as text, or show it as a QR code. Inside it:
  - **for BlueMob's admin only:** who made the group and when, who made this code and when, the phone and app
    version. This part is sealed to the admin's public key (P-256 ECDH, then AES-256-GCM). Only the server's
    `ADMIN_SEAL_KEY` opens it: admin page → Trip code.
  - **for the joining phone:** the group and whom to ask, scrambled with a key inside the app (a phone has to read it
    to join).
- **Server:** set `ADMIN_SEAL_KEY` on Render to the private key (64 hex characters) that matches
  `TripCode.ADMIN_PUBLIC` in the app. Keep it nowhere else. To change the pair, make a new one
  (`node -e 'const e=require("crypto").createECDH("prime256v1");e.generateKeys();console.log(e.getPublicKey("hex"),e.getPrivateKey("hex"))'`),
  put the public half in `TripCode.ADMIN_PUBLIC`, and ship a new app version. Codes made by older versions then
  open only with the old key.

## Android 0.33: a safety app for trips and crowds

- **Trip groups:** Nearby → "Going somewhere together?" → name it → everyone scans one QR code before setting off.
  Whoever shows the code adds the person who scanned it and tells the whole group. A trip group is a normal group
  chat too.
- **Roll call:** one tap in the trip group. Every member's phone answers by itself (its delivery receipt), and the
  screen shows how each one was reached: directly, through N other phones, or over the internet, and how fast.
  Members get "✅ I'm OK" and "🆘 I need help" buttons under it.
- **Field test:** You → Diagnostics → Field test (or a trip group → Field test with this group). While it runs,
  each phone notes every minute that it's alive, the battery every 5 minutes, and sends a roll call every 5, 10 or 15
  minutes. The result shows delivery %, time to reach, paths, battery per hour, and how often the phone paused or
  closed BlueMob. Share it as text or raw data. The plan for a real field day is in `docs/field-day.md`.
- **Games and badges are hidden, not deleted.** `app/src/main/java/com/bluemob/app/Features.kt` has two switches,
  `GAMES` and `BADGES`. Set one back to `true` and every way in returns: the Games row and card, Sky's "Play a game",
  game invites, the level pill and medal confetti. All their code, screens and tests stay.
- **Wording:** the intro and the store listing now lead with safety for treks, yatras and melas.
- **Docs:** `docs/partners.md` (a one-pager for trek operators, yatra organisers and volunteer groups) and
  `docs/lora.md` (the plan for kilometre range with a LoRa radio, for after the field day).

## Android 0.32: your feedback on Nearby, chats, guide, safety and games

- **Battery card (Nearby):** shows whether Battery Saver is on and BlueMob may keep running. Tapping it opens
  Connections & power with "Phone Battery Saver" highlighted.
- **Chat background:** a quiet outdoor pattern with a faint code that changes every hour and hides this phone's
  BlueMob ID. Admin page → Screenshot lookup turns a code plus the time back into the BlueMob ID.
- **Calls and Files:**
  - one compact search line with dropdowns (period; sort and format), then type chips with counts;
  - New group and Rescues appear only in Groups, no longer twice.
- **Survival guide:**
  - search comes first;
  - I'm lost and Ask Sky side by side;
  - progress in one line;
  - compact topics;
  - the download link moved to the bottom.
- **Emergency card and Helpers can signal** are now rows with an arrow under You → Safety, like Connections &
  power. Their options open on their own screens.
- **Helpers can signal my phone:** sound, flash, screen light and microphone each have their own switch. With
  microphone on, a helper's voice call during your SOS answers by itself after 3 seconds.
- **Emergency card:**
  - a high-priority silent notification, since some phones only put those on the lock screen;
  - a preview of what the card shows;
  - a live check with a Fix button;
  - "Add to Android's emergency info", which every lock screen's Emergency button opens.
- **Games:**
  - step-by-step How to play (goal, then one rule per page, with Back and Next);
  - boards centred at a comfortable size;
  - a coloured "Your move" pill.

## Android 0.31: check-in timer, fall detection, Hindi guides, one look

- **Check-in timer** (You → Safety): "I'll be back by 6 pm". You get a reminder 15 minutes before and a question at the
  time. 10 minutes later comes a 30-second countdown, then an SOS with your note and last position. Works with
  BlueMob closed.
- **Fall detection** (You → Safety, off by default): after a hard fall with no movement, a 30-second vibrating
  countdown with Cancel, then an SOS.
- **Hindi everywhere:**
  - the survival guides, Sky's replies and notifications are in Hindi;
  - read-aloud uses a Hindi voice;
  - Sky understands Hindi and Hinglish questions ("pani nahi hai");
  - people's own messages and names are never translated.
- **One look:**
  - brand colours and gradients in one place;
  - one set of corner sizes;
  - shared header-card and switcher components;
  - `docs/design.md` has the rules.
- **Play Store kit:**
  - `docs/store-listing.md`: listing text in English and Hindi, plus the data safety answers;
  - `docs/test-plan.md`: one-week internal test;
  - screenshots in `docs/store/en` and `docs/store/hi`;
  - the privacy policy in Hindi at `/privacy/hi`.

## Android 0.30: new Chats, Rescues and Activity designs; emergency card fixed

- **Emergency card on the lock screen now shows.** Android 13 and newer hide "silent" notifications from the lock
  screen, so the card moved to a normal (still silent) channel.
- **Chats:**
  - tabs are Chats, Groups, Calls and Files;
  - New chat, New group and Rescues are three small buttons;
  - a rescue happening now shows as a red banner on top;
  - Groups has its own tab, with live rescue groups first.
- **Rescues:**
  - a summary of how often you asked, helped, and how many came for you;
  - All, Asked and Helped filters;
  - a timeline by month with status, who came, messages and how long it lasted.
- **Your activity:**
  - a headline card with the change from the period before;
  - trend lines;
  - bars you can tap, with day names;
  - rings for how messages travelled and time per part of the app;
  - a weekday × hour heat map;
  - swipeable insight cards.

## Android 0.29: Hindi, power button SOS, verified contacts, badges as a game

- **हिन्दी:** You → Language → हिन्दी (or Auto, to follow the phone). About 1,400 phrases on every screen are translated.
  The survival guides and Sky's answers are still in English.
- **Power button SOS:** press the power button 5 times quickly, even with the phone locked. It vibrates and counts down
  5 seconds with a Cancel button, then sends the SOS. Turn it on under You → Safety. It needs Background on.
- **Verify with a QR code:** on someone's profile, scan each other's code (or compare a 60-digit safety number). A ✓
  shows next to their name in the chat. It resets if their key ever changes.
- **Calls:** a recorded or duplicated piece of a call can't be played twice (a replay window).
- **Badges as a game:**
  - levels and XP, with ranks from Newcomer to Legend;
  - Bronze, Silver, Gold and Diamond medals for each badge;
  - strengths from other people's ratings (Helpful, Reliable…);
  - a secret badge;
  - three missions a day;
  - confetti when you win a medal.
- **Look:** the tab bar is docked to the bottom in a brighter blue-to-green, and "BlueMob" at the top is in brand colours.

## Android 0.28: faster start, easier with TalkBack and large text, tidier code

- **Faster start:** a Baseline Profile ships with the app, so Android compiles the start-up code ahead of time.
- **Accessibility:**
  - screen titles and section headings are announced as headings;
  - switches say their name and state;
  - star ratings are read as "4 of 5 stars";
  - push-to-talk works with TalkBack;
  - screens are checked at 150% text size.
- **Code tidy-up:**
  - every screen is a typed route (`ui/nav/Route.kt`) instead of a string;
  - each feature draws its own screens (`ChatRoutes`, `SosRoutes`, `YouRoutes`, `GameRoutes`, `TripRoutes`, `GuideRoutes`);
  - account, reports and trips have their own view models.
- **Server:** stores its data in SQLite (`node:sqlite`, Node 22) and imports the old `relay.jsonl` once. Every server
  test runs on GitHub on each push.
- **Security review:** `docs/crypto.md` describes the encryption for an expert to check, with known weaknesses and
  the option of moving to Google Tink.

## Android 0.27: speed colours, your own travel mode, messages to people

- **Trips:** colour the trail and chart by **speed**:
  - stopped;
  - under 20 km/h, 20–40, 40–60, 60–80 and over 80;
  - with the time spent in each.

  Or colour by travel mode as before.
- **Correct the travel mode:** tap On foot, Cycle, Motorbike or Car or bus, and every moving stretch counts as that.
- **GPS jumps:** speeds faster than any vehicle are ignored, so no more 1232 km/h tops.
- **"Last seen" includes the internet:** a chat says "Online 2 h ago on the internet" after calls and messages, not
  only when you last met nearby.
- **Messages to particular people:** the admin page can send news to chosen BlueMob IDs. Only a phone that proves it
  owns that ID (by signing the request) receives it. Reports with technical details include the sender's ID to reply to.

## Android 0.26: reliable rescue groups, admin page, privacy rights

- **Rescue groups over the internet now always arrive.** "I'm coming", "I'm safe", arrivals and messages also travel
  as hidden chat messages, which the relay keeps until delivered. Before, they only reached phones online at that very
  second, so a helper's phone could stay "on the way" after the SOS ended. When the person in need ends the rescue,
  helpers' alerts close too.
- **Admin page** at `/admin` on the relay: overview, reports (filter, photos, status, notes), and news to every phone.
- **Privacy:**
  - sign-up asks for agreement to the privacy policy, and existing users are asked once;
  - age check: 13+, and under-18s need a parent or guardian to agree;
  - "Download my data" in Account;
  - grievance officer on `/privacy`;
  - `docs/incident-response.md` for breaches.

## Android 0.25: ready for the Play Store checklist

- **About you at sign-up:** date of birth (age is worked out), sex (with "Prefer not to say"), blood group,
  allergies ("None known" / "Prefer not to say" / type them) and medical conditions. They travel only inside an SOS
  and on the optional lock-screen card.
- **News from BlueMob:** the team posts alerts and updates from the relay (`/v1/announce`); every phone gets a
  notification and a list in You. Urgent ones sound even during do not disturb.
- **Automatic error reports:** crashes and background errors go to the team when there's internet (only the error and
  phone model; can be turned off in Diagnostics). The relay can alert the team through `ALERT_WEBHOOK`.
- **Guide progress no longer resets after updates** (also downloaded guide packs and app-usage time): the encrypted
  settings store now keeps a list of its entries; older progress is recovered.
- **Play Store requirements:** delete account (in the app and at `/delete-account`), privacy policy at `/privacy`,
  block and report people, "also call 112" on the SOS screen, release signing. See `docs/play-store.md`.
- **Report a problem** redesigned: topic tiles, numbered steps, proper photo tiles.
- `docs/compliance.md` maps BlueMob to ISO 27001, ISO 22301, ISO 31000, NIST CSF, DPDP and GDPR.

## Android 0.24: a Background switch, like Wi-Fi and Bluetooth

- **Background** sits with Bluetooth, Wi-Fi, GPS and Internet (Nearby → Connections, and the strip on Nearby).
  On: calls, messages and SOS reach you with BlueMob closed. Off: BlueMob signs out of the internet 30 seconds
  after it's closed (not during a call or an SOS) and never asks to run in the background.

## Android 0.23: reachable when closed

- **Calls and messages reach you with BlueMob closed.** "Reachable when closed" is now on by default and no longer
  needs the mesh: BlueMob keeps a quiet notification and stays signed in to the relay, so internet calls ring
  (full screen, over the lock screen) and messages arrive. With the mesh on, people nearby reach you too.
- Starts again by itself after the phone restarts or BlueMob updates. Asks once to be allowed to run with Battery
  Saver on. Turn it off in You → Connections & power.

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

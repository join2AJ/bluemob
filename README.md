# BlueMob

Offline mesh messaging for Android. Phones talk to each other directly over Bluetooth and
Wi-Fi without cell towers or internet. If any phone in the mesh has internet, it can carry
the others' messages to the outside world.

```
 A (no signal) ──BT/Wi-Fi──► B (no signal) ──BT/Wi-Fi──► C (has internet) ──► Firebase ──► D (far away)
```

## Roadmap

| Phase | Feature | Status |
|---|---|---|
| 1 | Project setup, permissions, discover & connect to nearby phones | ✅ Done |
| 1.5 | Design system, intro carousel, radar dashboard, Sky practice bot, profile | ✅ Done |
| 2 | Text chat between two phones (basic in-memory chat works; saving history is next) | ⏳ In progress |
| 3 | Multi-hop mesh relaying (A → B → C) | |
| 4 | Internet gateway + Firebase relay (A → B → C → internet → D, and back), SMS fallback | |
| 5 | End-to-end encryption and identities | |
| 6 | Location sharing, voice notes, images | |
| 7 | Live voice calls | |
| 8 | Live video calls | |

## What's in the app

| Screen | What it does |
|---|---|
| **Intro carousel** | Five animated slides on what BlueMob does, then pick a name and an avatar. Unbuilt features are labelled *Coming soon*. |
| **Radar** | Mesh on/off, counts of who's online / in range / met, an animated radar placing people by real direction and distance (when both share GPS), internet status, and a people list with distance, link type and *last seen*. |
| **Chats** | Sky the practice bot, pinned on top, plus everyone you've met. Unread badges and typing indicators. |
| **Chat** | Bubbles with sent ✓ / delivered ✓✓ ticks, typing dots, quick-reply chips for Sky, and a link-speed check (ping) for real people. |
| **You** | Name, avatar, mesh and location-sharing switches, replay intro, forget people, and a mesh activity log for testers. |

**Sky** (`bot/SkyBot.kt`) is a small offline keyword bot, so someone who is alone can try the app and
feel what a real chat is like.

### Design system (`ui/theme`)

"Open sky & wild meadow": leaf green brand, sky blue for connection, sun amber accent, warm off-white
backgrounds, and a deep forest night theme. Rounded shapes (12–32 dp), one spacing scale (`Space`),
and a sky-to-meadow `Gradients.horizon()` used for hero surfaces. Illustrations are drawn in code,
so they stay crisp and need no internet.

### Under the hood

- **Google Nearby Connections** (`P2P_CLUSTER`) handles discovery and links over Bluetooth/BLE,
  upgrading to Wi-Fi when possible. Needs Google Play Services on the phone.
- Each phone has a permanent random **device ID** plus a name and avatar, exchanged on connect.
- Phones **auto-connect** to every BlueMob phone they find (`mesh/NearbyMeshTransport.kt`).
- **Contacts** (`contacts/ContactsStore.kt`) remember everyone met, with last-seen time and last position.
- **Location** (`location/LocationTracker.kt`) uses GPS only while the user shares it. GPS needs no internet.

### Screenshots

Rendered on the JVM with Paparazzi: run `./gradlew recordPaparazziDebug`; images are in
[`app/src/test/snapshots/images`](app/src/test/snapshots/images).

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

**On one phone:** go through the intro, then open **Chats → Sky** and chat with the bot.

**With 2 or more real Android phones** (emulators cannot use Bluetooth with each other):

1. Install the APK on each phone, go through the intro and tap **Allow** for nearby devices.
2. On Android 12 and older, switch **Location** on in quick settings (no internet is used).
3. Turn on airplane mode, then turn Bluetooth and Wi-Fi back on to prove no cell signal is needed.
4. Within a few seconds each phone shows the other as **Online** on the Radar.
5. Open a chat and send messages. Ticks turn ✓✓ when delivered. Tap the speed icon to check the link.
6. Turn on **Share my location** on both phones (outdoors helps GPS) to see the real distance on the radar.
7. Walk apart: people go *In range*, then *Last seen …*.

If something fails, send a screenshot of **You → Mesh activity log**.

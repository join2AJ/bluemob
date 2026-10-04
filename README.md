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
| 2 | Text chat between two phones | ⏳ Next |
| 3 | Multi-hop mesh relaying (A → B → C) | |
| 4 | Internet gateway + Firebase relay (A → B → C → internet → D, and back), SMS fallback | |
| 5 | End-to-end encryption and identities | |
| 6 | Location sharing, voice notes, images | |
| 7 | Live voice calls | |
| 8 | Live video calls | |

## How it works (Phase 1)

- **Google Nearby Connections** (`P2P_CLUSTER` strategy) handles discovery and links. It uses
  Bluetooth/BLE and upgrades to Wi-Fi when possible. Needs Google Play Services on the phone.
- Every phone gets a permanent random **device ID** (`identity/Identity.kt`) plus a display name.
  These are broadcast while advertising (`mesh/EndpointInfo.kt`).
- Phones **auto-connect** to every BlueMob phone they find (`mesh/NearbyMeshTransport.kt`).
- The **Ping** button measures round-trip time over the link, which is useful for checking range.

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

## Testing Phase 1

You need **2 or more real Android phones** (emulators cannot use Bluetooth with each other).

1. Install the APK on each phone and open it.
2. Tap **Grant** and allow the Nearby devices / Location permissions.
3. On Android 12 and older, switch **Location** on in quick settings (no GPS signal or internet is used).
4. Give each phone a different name and turn on the **Mesh** switch.
5. Turn on airplane mode, then turn Bluetooth and Wi-Fi back on to prove no cell signal is needed.
6. Within a few seconds each phone should list the other as **Connected**. Tap **Ping** and check the log.
7. Walk apart to see how far the link holds.

If something fails, send a screenshot of the activity log.

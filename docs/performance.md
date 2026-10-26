# Performance

## Baseline profile

`app/src/main/baseline-prof.txt` lists the code BlueMob runs at start-up and on its first screens (Nearby, Chats,
SOS). Android compiles that code ahead of time instead of interpreting it on first use. Typically that makes cold start
and first scrolls 20–30% faster. `androidx.profileinstaller` installs the profile on first run, including for APKs
shared outside Play; Play also uses it for its cloud profiles. Compose, Room and other libraries ship their own
profiles, which are installed the same way.

The rules are written by hand with wildcards, per package. For exact, measured rules, generate them on a device or
emulator:

1. Add a `:baselineprofile` module (Android Studio → New Module → Baseline Profile Generator).
2. Write a `BaselineProfileRule` test that starts BlueMob, opens Nearby, Chats, a chat and the SOS screen.
3. Run `./gradlew :app:generateBaselineProfile` with a device connected; it writes
   `app/src/release/generated/baselineProfiles/baseline-prof.txt`.

Measure before and after with a Macrobenchmark `StartupBenchmark` (cold start, `CompilationMode.Partial()` vs `None()`).

## Check after a release build

`adb shell cmd package compile -f -m speed-profile com.bluemob.app` applies the profile at once (otherwise Android
does it when the phone is idle and charging).

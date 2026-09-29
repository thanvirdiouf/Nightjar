# Nightjar

An original, offline Android sleep journal, activity tracker and smart alarm.
Kotlin, Jetpack Compose and Room. GPL-3.0-or-later licensed.

- Track mattress motion or microphone intensity using a foreground service.
- View activity-based awake/light/deep estimates, nightly reports and 7/30-day trends.
- Schedule an exact wake-up deadline with an optional early wake window.
- Play original ambient sounds with a timer or estimated sleep-onset stop.
- Opt into short local recordings of candidate noise events.
- Add notes/tags, export CSV/JSON, and back up or restore a local ZIP archive.
- No account, internet permission, cloud sync, Play Services, Firebase, or ads.

Phone motion and sound do not measure sleep stages. The phase labels and 0–100
score are unvalidated estimates, not medical measurements. See [ALGORITHMS.md](ALGORITHMS.md).

## Build

Use OpenJDK 21 (Java 17 bytecode), Android SDK platform 36, build tools 36.0.0.
Set `sdk.dir` in an untracked `local.properties`, or set `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

The debug APK is in `app/build/outputs/apk/debug/`. The release APK is unsigned:
keep your release signing key outside the repository. Android 8.0/API 26 or newer.

For this WSL/Windows setup, [DEVELOPMENT.md](DEVELOPMENT.md) describes SDK/ADB
paths. Start the dedicated `Nightjar_Test_API_36` AVD and run:

```sh
./scripts/test-emulator
./scripts/verify-lifecycle
```

Device tests use actual emulator sensors and audio services, create their own
temporary nights, then remove those nights. Do not run them during a user session
or with a user alarm armed. The lifecycle script also reboots the dedicated AVD,
temporarily changes and restores its timezone, and verifies recovery after
force-stopping a test session. Run the main test script first to install its test runner.

## Privacy and data

Motion samples and microphone buffers are aggregated into roughly 30-second
intervals. Raw microphone audio is discarded unless local noise clips are enabled.
Candidate clips are bounded to six seconds, with a cooldown and a limit of 300 per
session. Clip retention is configurable from 1–90 days; pruning runs on app open
and session start. Deleting a night deletes its clips.

Android automatic cloud backup and device transfer are disabled. Local ZIP
backups include notes, settings and retained clips. Exports are not encrypted;
choose a local document location if you want the backup to remain offline.
Restoration merges new nights and skips nights with matching start/end/mode.
Recording consent and external audio-file access are not restored.

## Alarm behavior

Tap a built-in alarm tone to select it and hear a five-second preview at the
configured app volume using Android's alarm stream. Selecting another tone replaces
the preview. Stop preview, leaving the screen, backgrounding the app, or a ringing
alarm stops playback. Previewing a tone does not arm an alarm.

`AlarmManager.setAlarmClock` provides a deadline independent of tracking.
Within the selected early window, fresh light/awake estimates can begin playback.
The deadline is cancelled only after playback starts. A missing selected audio
file falls back to the original Dawn tone. Dismiss and snooze controls are in the
app and notification. Check Android alarm volume and device battery settings.

A stopped or killed tracking process leaves a recoverable saved session. On
resume, the missing interval is marked unknown. Android force-stop can suppress
alarms and services until the app is opened again. Test on a physical phone before
depending on an overnight alarm; emulator tests cannot prove vendor battery
behavior, real mattress sensitivity or clinical accuracy.

## Distribution

See [F-DROID.md](F-DROID.md) for source publishing and a build recipe template.
The repository currently has no public forge URL or release tag. Public publishing
and an F-Droid submission are separate release steps.

## License

Original Nightjar code and assets use GPL-3.0-or-later. See [LICENSE](LICENSE)
and [LICENSING.md](LICENSING.md) for the grant, third-party exceptions and source
distribution obligations. Third-party libraries keep their own licenses.

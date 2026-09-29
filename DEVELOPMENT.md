# Android development setup

The repository lives in Ubuntu WSL at /home/diouf/sleepProject. Build with the
Linux SDK and run with the Windows Android emulator.

## Tools

OpenJDK 21 (Java 17 bytecode); Android platform 36; Build Tools 36.0.0;
Gradle wrapper 8.13. Set `sdk.dir` in an untracked `local.properties`, or set
`ANDROID_HOME`. The app supports Android 8.0 / API 26 or newer.
The user environment is loaded from ~/.config/sleepProject/android-env.sh:

- Linux SDK: /home/diouf/Android/Sdk
- Windows SDK: C:\Users\tandi\AppData\Local\Android\Sdk
- Windows emulator acceleration: WHPX
- Dedicated test AVD: Nightjar_Test_API_36, AOSP Android 16 / API 36, Pixel 6a
- ADB serials can change between launches; both test scripts select the AVD by name.

The previous medium_phone AVD was deleted at the owner's request.
SleepProject_API_36 remains as an unused earlier AOSP setup device.

## Build and test

```bash
cd /home/diouf/sleepProject
source scripts/android-env.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
./scripts/test-emulator
./scripts/verify-lifecycle
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The release APK
is under `app/build/outputs/apk/release/` and is unsigned; keep the release
signing key outside the repository.

Start Nightjar_Test_API_36 from Android Studio's Device Manager before running
these commands. The first script locates the AVD by name, builds and installs both
APKs, then runs the LifecycleProbe test runner, which delegates to AndroidJUnitRunner
for the test suite. It fails unless the runner reports success. The lifecycle script
uses explicit probe operations in the test APK to verify reboot restoration,
timezone rescheduling, and recovery after force-stop. It changes only the dedicated
AVD and restores its timezone and app settings. Device tests use actual emulator
sensors and audio services, create temporary nights, and remove them afterward.
The lifecycle script also reboots the dedicated AVD and temporarily changes its
timezone. Run the main test script first to install its test runner. Never run
either workflow during a user tracking session or with a user alarm armed.
Test logs are saved under the Git-ignored .local/verification directory.

## Windows ADB from Ubuntu

```bash
./scripts/adb devices -l
./scripts/adb -s emulator-5554 shell getprop sys.boot_completed
./scripts/adb -s emulator-5554 install --no-incremental -r app/build/outputs/apk/debug/app-debug.apk
./scripts/adb -s emulator-5554 shell am start -W -n org.nightjar.sleep/.MainActivity
```

The wrapper forwards to Windows adb and converts Linux APK paths with wslpath.
Use the serial currently reported by adb rather than assuming 5554 forever.

The initial setup diagnostic app was separate from Nightjar. Current verification
uses the real Gradle app, its unit tests, and instrumented tests on the dedicated
AOSP emulator. Release builds are optimized by R8 and remain unsigned until an
owner-controlled signing key is supplied outside Git.

## Runtime and data behavior

Motion samples and microphone buffers are aggregated into roughly 30-second
intervals. Raw microphone audio is discarded unless local noise clips are enabled.
Candidate clips are bounded to six seconds, with a cooldown and a limit of 300 per
session. Clip retention is configurable from 1–90 days; pruning runs on app open
and session start. Deleting a night deletes its clips.

Android automatic cloud backup and device transfer are disabled. Local ZIP
backups include notes, settings, and retained clips. Exports are not encrypted;
choose a local document location if the backup should remain offline. Restore
merges new nights and skips nights with matching start, end, and mode. Recording
consent and access to external audio files are not restored.

Tapping a built-in alarm tone selects it and starts a five-second preview at the
configured app volume using Android's alarm stream. Choosing another tone
replaces the preview. Stopping preview, leaving or backgrounding the screen, or
a ringing alarm stops playback. Previewing does not arm an alarm.

`AlarmManager.setAlarmClock` provides a deadline independent of tracking. Within
the selected early window, fresh light or awake estimates can begin playback.
The deadline is cancelled only after playback starts. A missing selected audio
file falls back to the original Dawn tone. Dismiss and snooze controls are in
the app and notification.

A stopped or killed tracking process leaves a recoverable saved session. On
resume, the missing interval is marked unknown. Android force-stop can suppress
alarms and services until the app is opened again. Emulator tests cannot prove
vendor battery behavior or real mattress sensitivity; test on a physical phone
before depending on an overnight alarm. Sleep phase and score estimates are not
clinical measurements; see [ALGORITHMS.md](ALGORITHMS.md).

## Distribution

See [F-DROID.md](F-DROID.md) for source publishing and a build recipe template.
The repository currently has no public forge URL or release tag. Public
publishing and an F-Droid submission are separate release steps. See
[LICENSING.md](LICENSING.md) for third-party exceptions and source distribution
obligations.

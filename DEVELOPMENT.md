# Android development setup

The repository lives in Ubuntu WSL at /home/diouf/sleepProject. Build with the
Linux SDK and run with the Windows Android emulator.

## Tools

OpenJDK 21; Android platform 36; Build Tools 36.0.0; Gradle wrapper 8.13.
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

Start Nightjar_Test_API_36 from Android Studio's Device Manager before running
these commands. The first script locates the AVD by name, builds and installs both
APKs, then runs the LifecycleProbe test runner, which delegates to AndroidJUnitRunner
for the test suite. It fails unless the runner reports success. The lifecycle script
uses explicit probe operations in the test APK to verify reboot restoration,
timezone rescheduling, and recovery after force-stop. It changes only the dedicated
AVD and restores its timezone and app settings. Never run either workflow during
a user tracking session or with a user alarm armed.
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

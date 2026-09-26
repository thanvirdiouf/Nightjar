# Android development setup

The source repository lives in Ubuntu WSL at /home/diouf/sleepProject.
Build in Ubuntu and run the app on the Windows Android emulator.

## Installed tools

- Ubuntu: OpenJDK 21, Android command-line tools 22.0, Android platforms
  36 and 37.0, Build Tools 36.0.0, and Platform Tools 37.0.1.
- Linux SDK: /home/diouf/Android/Sdk.
- Windows SDK: C:\Users\tandi\AppData\Local\Android\Sdk.
- Windows emulator hardware acceleration: WHPX is usable.
- Created AOSP Android 16 test device: SleepProject_API_36.
- Verified running device: medium_phone, serial emulator-5554, Android 16 / API 36.

## Ubuntu environment

New Ubuntu terminal sessions load the Android environment automatically.
For an existing terminal, run:

```bash
cd /home/diouf/sleepProject
source scripts/android-env.sh
```

Machine-specific paths are stored in ~/.config/sleepProject/android-env.sh.
The local.properties file points Gradle at the Linux SDK and is ignored by Git.

## Connect to the Windows emulator

Start a device from Android Studio's Device Manager. Use the project adb
helper from Ubuntu to reach the Windows adb server:

```bash
./scripts/adb devices -l
./scripts/adb -s emulator-5554 shell getprop sys.boot_completed
```

The helper converts local APK filenames to Windows paths during installation:

```bash
./scripts/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

The example APK path becomes available after the Android app project is created
and built. The current repository contains the specification and setup helpers;
the app itself has not been implemented yet.

## Setup verification

A separate diagnostic APK was compiled, dexed, packaged, aligned, and signed
using the Linux SDK. It was installed on emulator-5554 and its Activity launched
successfully. Its name in the emulator is Sleep Project Setup Check.

Diagnostic build files live under ~/.cache/sleepProject and are outside the
repository. This verified the SDK-to-emulator path; a Gradle app build will be
verified after the app project is created. Gradle will use the project's wrapper.

# Verification record — 2026-09-28

Nightjar was built in Ubuntu WSL and tested on the Windows-hosted
Nightjar_Test_API_36 AVD: Pixel 6a, AOSP Android 16/API 36. Its current serial is
emulator-5554; scripts select the AVD by name because serials can change.

## Results

- 20 JVM tests pass: 12 sleep analysis, 4 alarm policy, 4 noise detector.
- All 16 instrumented tests pass together (221.224 seconds): 7 AppDeviceTest,
  8 LocalDataTest and 1 ReportUiTest.
- Android lint completes with 0 errors and 25 warnings. Warnings concern pinned
  dependency/target versions, synchronous preference commits and Kotlin style.
- The optimized R8 release builds successfully.
- A copy of the release was signed with the existing development key, installed,
  and exercised through visible UI controls. Production output remains unsigned.
- Release smoke checks pass for motion tracking, foreground service while the
  home screen is visible, saved report, session deletion, ambient playback/stop,
  exact-alarm registration and cancellation.
- A separate release check confirms the screen was asleep for 35 seconds and a
  valid motion interval was saved. The resumed UI showed one saved interval and
  a light-sleep estimate rather than missing data.
- Reboot restoration, timezone rescheduling and real process-interruption
  recovery all pass through the test-only LifecycleProbe runner.

## What the tests establish

Core device tests exercise actual emulator motion/microphone services, Activity
recreation and background tracking, persistence after database reopening,
microphone disclosure, exact alarms without tracking, fallback from an unavailable
audio file, snooze, early-wake duplicate rejection, ambient playback, actual
one-minute timer expiry and three sensor-driven asleep intervals stopping audio.

The idle-alarm test enables deep idle temporarily when disabled by the AOSP image,
asserts IDLE with the Activity stopped, schedules the alarm, verifies playback,
and restores the prior idle configuration. It does not infer Doze from merely
issuing a force-idle command.

The reboot workflow checks Android's alarm registration before launching
instrumentation or opening the app. The timezone workflow verifies a new alarm
token and the correct selected local time, then restores the timezone/settings.
The recovery workflow collects an interval, force-stops the app, checks the visible
Resume session control, resumes the same session, and verifies the unknown gap
and a completed session.

Data tests cover backup round-trip including notes/tags/audio, duplicate skipping,
CSV escaping and JSON contents, malformed import rollback, recovery gaps,
playable detector-produced clips, retention including orphaned recordings,
concurrent/truncated sound-cache recovery and invalid numeric preferences.

The UI test creates and removes three temporary synthetic nights. It checks the
report, note saving, weekly/monthly trends and light theme. Screenshots were
visually inspected. An intermittent failure was traced to a leftover alarm-test
snackbar covering Save; test setup/cleanup now clears unrelated notices. The
combined suite then passed.

## Evidence and reproduction

Local logs and screenshots are retained in the ignored .local/verification/:

- final-device-run.log and device-tests-*.log
- final-build-and-lint.log
- system-lifecycle-run.log, probe-*.log, alarm-after-boot.txt, recovery-ui.xml
- release-smoke.log, release-*.png, release-armed-alarm.txt
- release-screen-off.log, release-screen-off-power.txt,
  release-screen-off-result.xml and release-screen-off-result.png

The screen-off shell workflow was interrupted during cleanup after the successful
sensor assertion; cleanup was resumed separately. The saved power and UI evidence
record the screen-off result.

Unit XML reports are under app/build/test-results/testDebugUnitTest/.
Run ./scripts/test-emulator, then ./scripts/verify-lifecycle. The second command
reboots the dedicated AVD and tests timezone/process changes. Do not run either
during a user tracking session or with a user alarm armed.

The manifest has no INTERNET permission or automatic cloud/device-transfer backup.
Locked dependencies contain no Play Services, Firebase, Facebook SDK or Billing
identifiers. Audio and artwork are original.

## Limits

Runtime verification covers API 36 on this AVD. API 26 is the declared minimum,
not a claim that every supported Android release was tested. Physical overnight
sensitivity, battery use and vendor restrictions remain to be measured on a phone.
Sleep stages and scores are unvalidated estimates. No public repository, production
signature, F-Droid acceptance or independently reproducible build is claimed.

## Alarm tone preview update — 2026-09-28

Two additional targeted AlarmTonePreviewTest device tests pass (15.21 seconds).
They exercise all three built-in tones through the UI, automatic five-second
stopping, replaying the selected tone, manual stopping without changing selection,
rapid replacement during preparation, and actual MediaPlayer release when the
Activity backgrounds. Previewing leaves the alarm plan unchanged.

Debug/test APKs compile, install and run on Nightjar_Test_API_36. Android lint
passes. Evidence: .local/verification/tone-preview-build.log and
.local/verification/tone-preview-device-tests.log. These are additional focused
checks; the 16-test baseline recorded above predates the preview feature.

## GPL-3.0-or-later migration — verified 2026-09-29

Original Nightjar code/assets, source headers, documentation, About text and
F-Droid metadata now declare GPL-3.0-or-later. Third-party notices and the original
Gradle wrapper headers/JAR are preserved. LICENSING.md documents the scope and
corresponding-source obligations; this is not patent or ownership clearance.

The scoped audit checked 112 locked debug/release runtime dependency coordinates,
including metadata artifacts. All declare Apache-2.0; ListenableFuture inherits
its declaration from guava-parent:26.0-android. The distinct embedded runtime
license text found was also Apache-2.0. Details are recorded in
THIRD_PARTY_NOTICES.txt and the local license-audit.json/dependency-notices.json.

Debug and optimized release builds pass, and lint reports no errors. All three
license documents in the debug APK, unsigned release APK, and development-signed
release copy match the canonical repository files byte-for-byte. The release
Settings entry and all three document selectors were exercised on the emulator;
the GPL screen screenshot was visually inspected. No new sleep/alarm algorithm
tests were needed for this license and About-screen change.

Evidence: .local/verification/gpl-build.log, gpl-ui-check.log, gpl-licenses.png,
gpl-third-party.xml and gpl-apache.xml.

# Verification record — 2026-09-27

The dedicated Nightjar_Test_API_36 AVD uses AOSP Android 16/API 36. The former
medium_phone AVD was deleted at the user's request. Build tools run in Ubuntu WSL.

Completed:
- Debug and Android-test APKs compile and install.
- 20 JVM tests pass: 12 sleep analysis, 4 alarm policy, 4 noise detector.
- 11 AppDeviceTest/LocalDataTest device tests pass.
- ReportUiTest passes separately after an interaction correction.
- Android lint passes with no errors.
- The optimized R8 release builds successfully from the current source.
- The manifest has no INTERNET permission. Dependency locks contain no Play
  Services, Firebase, Facebook SDK or Play Billing identifiers.
- Icon and procedural sounds are original source-controlled assets/code.

Core device tests cover foreground/background motion capture, activity recreation,
disk reopening, microphone disclosure and samples, exact alarms independent of
tracking, snooze, early-wake duplicate rejection, ambient start/stop, backup
round-trip including audio/notes/tags, duplicate skipping, malformed-import
rollback, missing-data recovery, playable clips and retention.

The idle test requests forced idle but does not yet assert the resulting state;
it is not sufficient by itself to prove Doze delivery.

The UI test creates three temporary synthetic nights, checks reports/trends,
edits notes/tags and switches to the light theme, then deletes those nights.
Its report-chart, trend and light-theme screenshots were visually inspected.

Corrections found during verification include deadline cancellation ordering,
fallback for unavailable alarm files, complete duration accounting, missing-data
and overlap handling, reset of scoring history after gaps, permission state
through rotation, keyboard dismissal/insets, and Windows ADB CRLF handling.
A UI test was corrected to wait until a saved-note snackbar stopped covering a
control. Lint passed after being rerun sequentially following a concurrent-source
analysis crash.

Local evidence is in the ignored .local/verification directory:
extended-device-run.log, report-ui-tests.log, ui-correction-build.log and
final-release-build.log. Unit XML reports are in
app/build/test-results/testDebugUnitTest/.

Run ./scripts/test-emulator for the repeatable workflow. IMPLEMENTATION.md lists
the remaining runtime checks; this record is a checkpoint, not a full completion
claim.

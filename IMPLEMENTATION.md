# Implementation and verification plan

The goal is the complete offline Android app in project-specs.md, implemented,
built, installed, and tested on emulator-5554. This checklist records progress;
an unchecked item remains required. Optional Wear OS and narrated meditation
are stretch features, as specified in the original document.

- [ ] Kotlin/Compose Android project, pinned Gradle wrapper, Apache-2.0 license
- [ ] Room sessions, epochs, noise events, tags and process-safe persistence
- [ ] Accelerometer foreground tracking, notification, wake lock, stop/recovery
- [ ] Causal live scoring, retrospective estimates, missing-data handling
- [ ] Nightly metrics and stepped estimate chart; transparent score formula
- [ ] Start/stop interface and permission handling
- [ ] Exact deadline alarm, early wake window, duplicate prevention
- [ ] Alarm playback, tone selection, volume ramp, dismiss and snooze
- [ ] Boot/time-change alarm reconciliation
- [ ] Microphone source and privacy disclosure
- [ ] Opt-in candidate noise detection, local clips, playback and retention
- [ ] Journal, weekly/monthly trends, sleep notes/tags and correlations
- [ ] CSV/JSON export and local backup/restore
- [ ] Original offline ambient sounds and timer/sleep-onset stopping
- [ ] Settings, calibration, dark/light theme and accessible error/empty states
- [ ] Analysis and alarm unit tests
- [ ] Room/foreground-service/UI instrumented tests
- [ ] Emulator runtime verification and corrections
- [ ] Release build, FOSS dependency/asset audit and F-Droid build notes

Phone-only sleep phase labels must be clearly identified as unvalidated estimates.
No fake results or placeholder actions should remain in the shipped app.
Physical overnight microphone/accelerometer accuracy and vendor battery
restrictions require real-device validation; emulator checks cannot establish those.
Public forge hosting and F-Droid submission need an actual remote/release URL and
are distribution steps, not automatically performed by this local build task.

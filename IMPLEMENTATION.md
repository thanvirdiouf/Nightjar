# Implementation audit

The core app in project-specs.md is implemented and locally verified. Nightjar
uses Kotlin, Compose and Room with a small application container for dependency
injection and Compose Canvas charts. Hilt and a charting library were suggestions,
not requirements. Optional Wear OS and guided narration are not included.

| Specification area | Implementation and evidence |
| --- | --- |
| Motion and microphone sessions | Foreground sensing, 30-second aggregation, persistent notification, stop controls; real emulator samples, background/recreation tests and release screen-off check |
| Classification and metrics | Causal scoring, configurable thresholds, sustained sleep onset, duration/latency/awakening/quality metrics; 12 analysis unit tests |
| Smart alarm | Exact independent deadline, fresh early-window estimates, tone/file selection, volume ramp, snooze/dismiss; policy and device tests |
| Alarm lifecycle | Confirmed-idle delivery, reboot restoration before opening the app, timezone rescheduling; device suite and explicit lifecycle probes |
| Local noise clips | Explicit consent, bounded candidate detection and WAV storage, playback, retention and deletion; detector and local-data tests |
| Ambient sounds | Six original generated sounds, real timer expiry and sensor-driven sleep-onset stopping; device tests and release playback |
| Nightly reports | Phase chart, metrics, missing-data display, clips, notes/tags and deletion; UI test and release report flow |
| Trends | 7/30-day quality, duration, bed/wake rhythm, noise counts and tag comparisons; UI test with temporary synthetic nights |
| Settings | Sensing mode, calibration, theme, alarm and recording preferences; persistence and theme tests |
| Export and restore | CSV/JSON, local ZIP including retained clips, duplicate skipping, validation and rollback; data round-trip and malformed-input tests |
| Interrupted tracking | Saved session recovery with unknown gaps; real force-stop/reopen/resume probe |
| Privacy and offline use | No internet permission, cloud backup or proprietary service SDKs; manifest and dependency-lock audit |
| Build and licensing | Apache-2.0, original assets, pinned/locked dependencies, lint, debug and optimized release builds |
| Distribution preparation | Listing text and F-Droid recipe template; public repository, tag, production signing and submission remain owner-controlled release steps |

See VERIFICATION.md for exact test scope and evidence. The dedicated
Nightjar_Test_API_36 AVD replaced medium_phone and works successfully; no fallback
was needed.

Remaining real-device validation: an overnight run, device-specific sensitivity,
battery drain, and vendor background restrictions. Emulator success is not evidence
of clinical sleep-stage accuracy or compatibility testing across every Android
version. Labels and scores are explicitly described as unvalidated estimates.

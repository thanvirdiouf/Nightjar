# Implementation and verification plan

The goal remains the complete offline app in project-specs.md. Optional Wear OS
and narration remain stretch features. The dedicated AVD is Nightjar_Test_API_36;
its ADB serial may change between launches.

Implemented: Compose navigation, Room persistence, motion/microphone foreground
tracking, live scoring, nightly metrics/chart, exact and early alarms, audio ramp,
dismiss/snooze, local noise clips and retention, ambient playback, notes/tags,
7/30-day trends, CSV/JSON exports, ZIP backup/restore, themes and sensitivity
settings. Original assets, Apache-2.0 licensing, dependency locks and F-Droid
preparation notes are included.

Verified so far: 20 unit tests, 11 core device tests, a separate report/trend/theme
UI test, Android lint, and the optimized R8 release build. See VERIFICATION.md.

Remaining work before the full completion audit:
- [ ] Verify alarm reconciliation after boot and time changes.
- [ ] Assert actual Doze state in the idle alarm test.
- [ ] Exercise ambient timer expiry and sleep-onset stopping.
- [ ] Test tracking recovery after a full process interruption.
- [ ] Harden sound caching against rapid switching/interrupted generation.
- [ ] Install and smoke-test the optimized release.
- [ ] Complete the final requirement-by-requirement audit.

Physical overnight sensitivity and vendor battery behavior require a real phone.
Sleep phases remain unvalidated estimates. Source publishing, owner-controlled
release signing, a public release tag and F-Droid submission are distribution
steps that require real owner-supplied destinations/credentials.

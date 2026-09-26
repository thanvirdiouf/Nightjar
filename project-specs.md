# Open-Source Sleep Tracking & Smart Alarm App — Functional & Technical Specification

## Purpose of this document
This is a build spec for an original, open-source Android app inspired by the general
category of sound/motion-based sleep trackers (e.g., Sleep Cycle, Sleep as Android,
Sleep Monitor). It describes **functionality and architecture only** — no proprietary
code, assets, copy, or UI graphics from any existing app are included or should be
copied. The sleep-staging approach described below uses **actigraphy**, a decades-old,
published, non-proprietary technique (movement/sound amplitude thresholding to infer
sleep vs. wake vs. light/deep sleep). Note: some commercial apps hold patents on
specific implementations of "wake during a lightest-sleep window" alarm logic (e.g.
US 8493220). Implement the smart-alarm feature using the generic, independently-
described algorithm below rather than any patented specifics, and consult a lawyer
before commercial release if this matters to you.

---

## 1. High-Level Concept

A mobile app that:
1. Tracks a user's sleep overnight using the phone's **microphone** or **accelerometer**.
2. Estimates **sleep phases** (awake / light / deep) from movement and sound intensity over time.
3. Wakes the user with a **smart alarm** that fires at the most opportune (lightest-sleep) moment within a user-defined window, rather than at a fixed time.
4. Presents nightly and long-term **sleep reports and trends**.
5. Offers a **sound library** (white noise, nature sounds, etc.) to aid falling asleep.
6. Optionally logs **sleep notes/tags** (caffeine, exercise, stress) to correlate with sleep quality.

---

## 2. Core Feature List

### 2.1 Sleep Tracking Session
- User places phone on the mattress (accelerometer mode) or on a nightstand (microphone mode) before bed and starts a tracking session.
- App must run a foreground service (with persistent notification) to keep sensors sampling while the screen is off — Android will kill background sensor access otherwise.
- Two sensing modes, user-selectable:
  - **Accelerometer mode**: samples `TYPE_ACCELEROMETER` at ~5–10 Hz, computes a rolling movement-intensity signal from the magnitude of acceleration deltas.
  - **Microphone mode**: samples short audio buffers periodically (e.g., every few seconds), computes short-time energy/RMS amplitude (and optionally an FFT-based feature) as a proxy for movement/sound intensity. Never needs to record or store raw audio for tracking purposes — only derived amplitude values (unless the user opts into snore recording, see 2.4).
- Session stores a time series: `(timestamp, intensity_value)` at a fixed interval (e.g., every 30–60 seconds), plus raw high-frequency samples optionally discarded after aggregation to save space/battery.

### 2.2 Sleep Phase Classification (actigraphy-based)
This is a standard, published approach — not proprietary:
- Divide the night into fixed epochs (typically 30 or 60 seconds).
- For each epoch, compute an activity score (sum or weighted average of movement/sound intensity in that epoch, with neighboring epochs weighted less — a common technique is the Cole-Kripke or Sadeh algorithm, both published academic actigraphy scoring formulas).
- Classify each epoch:
  - **Awake**: activity score above a high threshold.
  - **Light sleep**: activity score in a mid-range (movement present but reduced).
  - **Deep sleep**: activity score below a low threshold (near-total stillness/silence) for a sustained run of epochs.
- Optionally smooth the classification with a simple moving average or hidden Markov model to avoid noisy epoch-to-epoch flapping.
- Derived nightly metrics:
  - Time to fall asleep (sleep latency): time from session start to first sustained "asleep" run.
  - Total sleep time, time awake in bed, number of awakenings.
  - % time in light vs deep sleep.
  - A composite 0–100 "sleep quality score" — a weighted formula you define (e.g., combining sleep efficiency = total sleep time / time in bed, deep sleep %, and number of awakenings). Document your own weighting; don't need to reverse-engineer anyone else's formula.

### 2.3 Smart Alarm
- User sets a **target wake time** and a **window size** (e.g., 20–30 minutes before the target time).
- During the window, the app continuously evaluates the live activity score.
- As soon as the activity score crosses into the "light sleep/awake" threshold within the window, trigger the alarm early (gentle wake during naturally lighter sleep).
- If no such crossing is detected by the target time, trigger the alarm at the target time regardless (hard deadline).
- Alarm playback: gradually increasing volume ramp, selectable tone/melody, Android `AlarmManager` exact-alarm scheduling (`setAlarmClock` or `setExactAndAllowWhileIdle` + a foreground service to handle ramp-up playback reliably even in Doze mode).

### 2.4 Sound & Noise Detection ("what kept you up")
- Optional feature: while tracking via microphone, run a simple energy-threshold + duration classifier to flag intervals of elevated, sustained low-frequency periodic energy as candidate **snoring** events, and short bursts as candidate **talking/noise** events.
- If enabled, save short audio clips (a few seconds) only around flagged events, with clear user consent and an in-app privacy notice, and store locally (or encrypted) — do not upload audio anywhere in an open-source/self-hosted build unless the user explicitly wants cloud sync.
- Nightly report lists timestamps + playback of these clips, plus a count of "noise events" as a trend metric.

### 2.5 Sound Library for Falling Asleep
- Bundle or stream a library of loopable ambient tracks (rain, white/pink/brown noise, ocean, fan hum, etc. — use royalty-free/CC0 sourced audio, not ripped from any commercial app).
- Sleep timer to auto-stop playback after N minutes or when sleep onset is detected.
- Optional short "wind-down" guided narration/meditation track support (text-to-speech or bundled audio).

### 2.6 Reports & Trends
- Nightly report screen: sleep quality score, hypnogram-style graph of phases over the night (a stepped area/line chart), duration breakdown, noise events list.
- Trends screen: weekly/monthly line or bar charts of sleep quality, duration, and bedtime/wake-time consistency over time.
- Optional correlation feature: user-entered daily tags (caffeine, alcohol, exercise, stress, screen time before bed) plotted against sleep quality score to help users spot patterns.

### 2.7 Wearable Companion (optional/stretch goal)
- A companion Wear OS app that just relays accelerometer data from the wrist to the phone over Bluetooth, since wrist motion can be a cleaner signal than phone-on-mattress. Not required for MVP.

### 2.8 Settings
- Sensing mode toggle (accelerometer/microphone).
- Snore/noise recording on/off + storage/retention length.
- Wake window size, alarm tone picker, volume ramp settings.
- Data export (CSV/JSON of nightly sessions) and local backup/restore.
- Dark/light theme.

---

## 3. Suggested Architecture (Android, Kotlin)

```
app/
 ├─ core/
 │   ├─ sensors/          // AccelerometerSource, MicrophoneSource (implement a common SleepSignalSource interface)
 │   ├─ analysis/         // EpochScorer, SleepPhaseClassifier, SleepQualityCalculator
 │   ├─ alarm/            // SmartAlarmScheduler, AlarmPlaybackService
 │   └─ storage/          // Room database: SleepSession, Epoch, NoiseEvent, DailyTag entities
 ├─ tracking/
 │   └─ TrackingForegroundService.kt   // owns sensor sampling + wakelock + notification while tracking
 ├─ ui/
 │   ├─ home/             // start/stop tracking, quick status
 │   ├─ report/           // nightly report screen (hypnogram chart)
 │   ├─ trends/           // weekly/monthly charts
 │   ├─ alarm/            // alarm setup screen
 │   ├─ sounds/           // sound library player
 │   └─ settings/
 └─ di/                   // Hilt modules
```

### Suggested tech stack
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Local DB**: Room (entities: `SleepSession`, `Epoch(timestamp, intensity, phase)`, `NoiseEvent(timestamp, durationMs, clipPath, type)`, `DailyTag`)
- **Background work**: Foreground Service for the active tracking session (required — Android will suspend sensor/mic access otherwise); `WorkManager` not suitable for continuous overnight sampling but fine for nightly report generation/export jobs.
- **Charts**: a Compose charting lib (e.g., Vico) for the hypnogram and trend graphs.
- **Alarm scheduling**: `AlarmManager.setAlarmClock()` for reliability across Doze/battery optimization; request `SCHEDULE_EXACT_ALARM`/ignore battery optimization permission with clear rationale UI (Android 12+ requirements).
- **DI**: Hilt.
- **Testing**: Unit tests for the epoch scoring/classification logic (this is the algorithmic heart of the app and should be tested against synthetic signals), instrumented tests for the foreground service lifecycle.

---

## 4. Data Model (example)

```kotlin
@Entity
data class SleepSession(
    @PrimaryKey val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val sensingMode: SensingMode, // ACCELEROMETER or MICROPHONE
    val sleepQualityScore: Int?,  // 0-100, computed at session end
    val sleepLatencyMinutes: Int?,
    val totalSleepMinutes: Int?,
    val deepSleepPercent: Float?,
    val awakenings: Int?
)

@Entity
data class Epoch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val epochStart: Instant,
    val activityScore: Float,
    val phase: SleepPhase // AWAKE, LIGHT, DEEP
)

@Entity
data class NoiseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Instant,
    val durationMs: Long,
    val type: NoiseType, // SNORE, TALK, OTHER
    val clipPath: String?
)
```

---

## 5. Key Algorithms to Implement (plain-language pseudocode)

### 5.1 Epoch activity scoring (Sadeh-style, simplified)
```
for each 30s epoch:
    window = activity values from epoch-2 .. epoch+2  (5 epochs centered on current)
    score = w0*mean(window) + w1*std(window) + w2*log(current_epoch_value + 1)
    // weights w0,w1,w2 are tunable constants you calibrate empirically
```

### 5.2 Phase classification
```
if score > HIGH_THRESHOLD: phase = AWAKE
elif score > LOW_THRESHOLD: phase = LIGHT
else: phase = DEEP
```
(Thresholds should be configurable/calibratable per device, since raw accelerometer/mic sensitivity varies by phone model.)

### 5.3 Smart alarm trigger loop
```
while now < targetWakeTime:
    if now >= (targetWakeTime - windowMinutes):
        if currentEpochPhase in [LIGHT, AWAKE]:
            triggerAlarm()
            break
    sleep(pollingIntervalSeconds)
if now >= targetWakeTime:
    triggerAlarm()  // hard deadline fallback
```

---

## 6. Non-Functional Requirements
- **Battery**: mic/accelerometer sampling all night must be battery-efficient — batch writes to DB (don't write every sample), use low sample rates, release wakelocks promptly.
- **Privacy**: all audio/sensor data stored locally by default; no cloud upload without explicit opt-in; clear in-app disclosure before first use of microphone.
- **Permissions**: `RECORD_AUDIO` (mic mode), `FOREGROUND_SERVICE`, `SCHEDULE_EXACT_ALARM`, notification permission (Android 13+).
- **Offline-first**: entire app should function with no network connection.

---

## 7. F-Droid Readiness Requirements

Since this is targeted at F-Droid distribution, the build must satisfy F-Droid's
inclusion policy from day one — retrofitting this later is painful. Concretely:

- **License**: pick an OSI-approved FOSS license for the whole app (GPL-3.0-or-later
  and Apache-2.0 are both common F-Droid choices). Include a `LICENSE` file in the repo
  root. Any bundled audio/sound assets need their own compatible license too (CC0 or
  CC-BY are safest — avoid anything "non-commercial only," since that's not FLOSS-compatible
  even for a free app).
- **No Google Play Services / Firebase**: do not use `com.google.android.gms.*`,
  Firebase (Crashlytics, Analytics, Cloud Messaging), or any proprietary ad SDK.
  This also means:
  - Use `AlarmManager` (plain Android SDK) for alarms — not FCM push.
  - If you want crash reporting, use a FOSS option like **ACRA** instead of Crashlytics.
  - No Play Billing for any paid-tier features — if you ever add a "supporter" tier,
    it needs a FLOSS-compatible payment path or should just be a voluntary donation link.
- **No proprietary build tools**: stick to the standard Android SDK/AGP/Gradle
  toolchain (F-Droid tolerates the SDK/NDK itself as an unavoidable exception, but
  avoid Oracle JDK or other proprietary build dependencies — use OpenJDK).
- **Dependencies only from public, well-known repos**: Maven Central / Google's
  Android support repo are fine; avoid random prebuilt AARs/JARs of unclear provenance.
  Prefer well-known FOSS libraries (Room, Compose, Hilt are all fine — they're AOSP/Google
  open-source libraries, distinct from proprietary Play Services).
- **Public VCS**: host source on a git forge F-Droid supports (GitHub, GitLab, Codeberg,
  self-hosted git, etc.), kept up to date, with tagged releases matching version codes.
- **Reproducible builds**: keep the Gradle build simple and deterministic (pin
  dependency versions, avoid fetching things at build time from arbitrary URLs) so
  F-Droid's build server can reproduce your APK from source.
- **Anti-Features disclosure**: if you keep the optional snore/audio-clip recording
  or any local-only analytics, that's fine, but be transparent about it in the app's
  F-Droid metadata description — F-Droid flags things like "Tracking" only if data
  leaves the device, so a fully local, opt-in recording feature should not need any
  Anti-Features tag as long as nothing is transmitted anywhere.
- **fdroiddata submission**: once you have a tagged release, you (or someone) submits
  a merge request to the `fdroiddata` repo with a build recipe (`metadata/<applicationId>.yml`)
  describing the git repo URL, license, and build steps; F-Droid's own infra then builds
  and signs the APK from source — you don't upload a binary yourself.

### A note on timing (2026)
Worth knowing before you invest heavily in this path: <cite index="43-1,44-1">Google announced new developer verification requirements taking effect in 2026 that will require verified developer IDs and signing keys for apps, including sideloaded ones — F-Droid has publicly said this could threaten its ability to distribute apps built from anonymous or pseudonymous contributions.</cite> This is an evolving policy fight, not yet fully in effect, but it's worth checking F-Droid's own blog for the latest status before you're deep into a release, since it could affect how (or whether) unverified developers can distribute via sideloading in your target regions.

### On the patent question, since you're in India
US Patent 8493220 (the "smart alarm" patent) is a US patent — patent rights are
territorial, so it has no direct legal force in India by itself. However, F-Droid
distributes globally, including to users in the US, so it's still safer to build the
generic/independent actigraphy-based algorithm described in Section 5 rather than
anything closely mirroring that patent's specific claimed method. This isn't legal
advice — if you plan a serious public release, a quick consult with an India-based
IP lawyer familiar with software patents would be worthwhile, since India's own
patent-eligibility rules for software are also distinct from the US's.

---

## 8. Suggested MVP Build Order (for Claude Code)
1. Room database + entities.
2. Accelerometer-based `SleepSignalSource` + foreground tracking service + notification.
3. Epoch scorer + phase classifier (unit-testable in isolation with synthetic data).
4. Basic "start/stop tracking" UI + nightly report screen with hypnogram chart.
5. Smart alarm scheduling + alarm playback with volume ramp.
6. Microphone sensing mode as an alternative source.
7. Noise/snore event detection + clip storage.
8. Trends screen + CSV export.
9. Sound library player + sleep timer.
10. Settings screen, permissions flow, polish.


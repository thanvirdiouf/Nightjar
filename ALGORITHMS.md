# Activity and sleep estimates

This is an original transparent heuristic inspired by the general actigraphy
approach in the project specification. It is not an implementation or validation
of the published Cole–Kripke or Sadeh coefficients.

## Signals and aggregation

Motion uses a 5 Hz accelerometer request with up to 1 second of batching. The
Euclidean magnitude of successive vector deltas is multiplied by 0.12 and bounded
to 0–1. Microphone input uses 16 kHz mono PCM; normalized RMS multiplied by 16 is
bounded to 0–1. A session saves roughly 30-second mean-intensity epochs.

An interval needs at least three samples spanning sufficient time and a sample
within five seconds of its end. Missing signal is UNKNOWN, never deep sleep.
Processing uses elapsed real time for interval lengths and wall time for display.

## Causal scoring

For the current intensity plus up to four prior valid epoch intensities:

`score = 0.6 × mean + 0.3 × current + 0.1 × population standard deviation`

Default quiet threshold: 0.08. Default awake threshold: 0.35. At or above the
awake threshold the estimate is AWAKE. Five consecutive valid quiet epochs are
needed for DEEP. Other valid epochs are LIGHT. Invalid epochs reset the quiet
run and history. Thresholds can be calibrated and are frozen per session.

Sleep onset is the beginning of three consecutive valid LIGHT/DEEP intervals.
Only asleep intervals from that onset count toward sleep duration. Awake runs
lasting at least one minute after onset count as awakenings. Unknown intervals
break continuity and lower coverage. Light/deep percentages use estimated sleep
time. Before sustained sleep onset, valid quiet intervals count as awake-in-bed time.
Sleep + awake-in-bed + unknown time cover the whole session. Overlaps are clipped
to avoid double-counting, and omitted gaps are explicitly treated as unknown.

## Quality score

The original 0–100 formula uses:
- 60% sleep efficiency (estimated sleep / total time in bed).
- 25% duration progress toward 7.5 hours, capped at 100%.
- 15% continuity, with one twelfth deducted for each awakening, capped at zero.

A score is withheld when the session is under three minutes or data coverage is
under 50%. This score is a personal journal aid, not a validated health score.

## Noise candidates

An opt-in detector checks RMS >= 0.018, ignores events shorter than 0.2 seconds,
and closes events after about 0.33 seconds of quiet or a six-second clip limit.
Low-frequency periodic content is checked on box-filtered/downsampled 1 kHz
audio using normalized autocorrelation at 50–250 Hz. At least one second of
elevated signal and 45% periodic frames are needed for POSSIBLE_SNORING.
Other events are NOISE_BURST. These labels can be wrong: fans, playback and speech
can trigger them. No speech recognition, identity inference or diagnostic claim
is made. There is a five-second cooldown and 300 saved events per session.

## Trends

Bed/wake consistency uses circular standard deviation on clock times so times
around midnight stay adjacent. Tag comparisons require at least two scored nights
with and without a tag; association does not establish cause.

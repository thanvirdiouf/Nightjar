#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Nightjar contributors
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo 'Usage: scripts/package-release.sh vMAJOR.MINOR.PATCH' >&2
  exit 2
fi
tag="$1"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
metadata='app/build/outputs/apk/release/output-metadata.json'
if [[ ! -f "$metadata" ]]; then
  echo 'Build the release APK before packaging it.' >&2
  exit 1
fi
apk_name="$(python3 - "$tag" "$metadata" <<'PY'
import json, pathlib, re, sys

tag, path = sys.argv[1:]
if not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', tag):
    raise SystemExit(f'Invalid release tag {tag!r}; expected vMAJOR.MINOR.PATCH.')
metadata = json.loads(pathlib.Path(path).read_text())
if metadata.get('applicationId') != 'org.nightjar.sleep' or metadata.get('variantName') != 'release':
    raise SystemExit('Release metadata does not describe the Nightjar release variant.')
elements = metadata.get('elements', [])
if len(elements) != 1 or elements[0].get('filters'):
    raise SystemExit('Expected exactly one universal release APK.')
entry = elements[0]
if entry.get('versionName') != tag[1:]:
    raise SystemExit(f"Tag {tag} does not match APK versionName {entry.get('versionName')!r}.")
if not isinstance(entry.get('versionCode'), int) or entry['versionCode'] < 1:
    raise SystemExit('Release APK needs a positive versionCode.')
name = entry.get('outputFile', '')
if pathlib.PurePath(name).name != name or not name.endswith('.apk'):
    raise SystemExit('Unexpected release APK filename in metadata.')
print(name)
PY
)"
unsigned_apk="app/build/outputs/apk/release/$apk_name"
[[ -f "$unsigned_apk" ]] || { echo "Missing $unsigned_apk" >&2; exit 1; }
for name in ANDROID_SIGNING_KEY_BASE64 ANDROID_KEY_ALIAS ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_PASSWORD; do
  [[ -n "${!name:-}" ]] || { echo "Missing signing secret: $name" >&2; exit 1; }
done
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk" ]] || { echo 'Set ANDROID_HOME or ANDROID_SDK_ROOT.' >&2; exit 1; }
zipalign="$sdk/build-tools/36.0.0/zipalign"
apksigner="$sdk/build-tools/36.0.0/apksigner"
[[ -x "$zipalign" && -x "$apksigner" ]] || { echo 'Android Build Tools 36.0.0 are required.' >&2; exit 1; }

umask 077
work_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/nightjar-sign.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
printf '%s' "$ANDROID_SIGNING_KEY_BASE64" | base64 --decode > "$work_dir/release.jks"
"$zipalign" -f -p 4 "$unsigned_apk" "$work_dir/aligned.apk"
mkdir -p dist
release_apk="dist/nightjar-${tag}.apk"
"$apksigner" sign \
  --ks "$work_dir/release.jks" \
  --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
  --key-pass env:ANDROID_KEY_PASSWORD \
  --v4-signing-enabled false \
  --out "$release_apk" "$work_dir/aligned.apk"
"$apksigner" verify --verbose --min-sdk-version 26 "$release_apk"
chmod 644 "$release_apk"
(cd dist && sha256sum "nightjar-${tag}.apk" > SHA256SUMS.txt)
chmod 644 dist/SHA256SUMS.txt
printf 'Packaged %s and dist/SHA256SUMS.txt\n' "$release_apk"

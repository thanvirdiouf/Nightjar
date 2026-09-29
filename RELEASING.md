# GitHub CI and releases

The [CI workflow](.github/workflows/ci.yml) runs on pull requests to `main`,
changes pushed to `main`, and manual dispatch. It builds the debug APK, runs
unit tests and Android lint, and keeps the debug APK as a short-lived Actions
artifact. The [release workflow](.github/workflows/release.yml) runs on pushed
`vMAJOR.MINOR.PATCH` tags. It repeats unit tests and lint, builds the optimized
release APK, signs and verifies it, then publishes the signed APK and its
`SHA256SUMS.txt` file to GitHub Releases with generated release notes. GitHub
also provides source archives for the tag. These workflows do not push code or
tags and do not submit to F-Droid.

## One-time signing setup

An Android release must be signed with a key that you control. Create a
long-lived release keystore if you do not already have one:

```sh
keytool -genkeypair -v -keystore nightjar-release.jks -alias nightjar \
  -keyalg RSA -keysize 4096 -validity 10000
```

Store the keystore and its passwords securely outside this repository, with a
backup. Future updates to this app must use the same signing key. Add these
**repository Actions secrets** in GitHub Settings → Secrets and variables →
Actions:

| Secret | Value |
| --- | --- |
| `ANDROID_SIGNING_KEY_BASE64` | Base64 encoding of the complete `.jks` file, with no line breaks (`base64 -w 0 /secure/path/nightjar-release.jks`) |
| `ANDROID_KEY_ALIAS` | Alias in the keystore, such as `nightjar` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_PASSWORD` | Key password; use the keystore password if they are the same |

Base64 is only a transport encoding; the GitHub secret is what protects the key.
Never commit the `.jks` file, passwords, or encoded key. No local signing key is
needed for the CI workflow. GitHub-hosted Ubuntu runners already have the
Android SDK, and the workflows install SDK platform 36 and Build Tools 36.0.0
if needed. There are no additional packages to install on your machine for
GitHub CI.

## Cut a release

1. Update `versionName` and increment `versionCode` in
   [`app/build.gradle.kts`](app/build.gradle.kts). The first configured release
   is `0.1.0` with code `1`; increment the code for every future update.
2. Review the change and let CI pass on `main`. Create an annotated tag matching
   `versionName` with a `v` prefix, for example `v0.1.0`, on the intended commit.
3. Push the commit and tag when ready:

   ```sh
   git push origin main
   git push origin v0.1.0
   ```

The release job checks that the tagged commit is on `main`, that the tag matches
the built APK's `versionName`, and that `versionCode` is positive. It stops before publishing if the version does
not match, signing secrets are missing, tests or lint fail, or APK signature
verification fails. Only a successfully signed APK is published; the unsigned
Gradle output is never uploaded as a release asset. The release APK name is
`nightjar-vMAJOR.MINOR.PATCH.apk`.

To check the downloaded file, run `sha256sum -c SHA256SUMS.txt` in the directory
containing it. For manual local packaging after `./gradlew :app:assembleRelease`,
set the four signing variables and `ANDROID_HOME`, then run
`./scripts/package-release.sh v0.1.0`. Its output is in the ignored `dist/`
directory. See [DEVELOPMENT.md](DEVELOPMENT.md) for the local build setup and
[F-DROID.md](F-DROID.md) for separate F-Droid preparation.

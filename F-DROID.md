# F-Droid preparation

The app uses Apache-2.0 code, an original vector icon, and procedural audio created
from source at runtime. There are no downloaded assets, proprietary SDKs, tracking
endpoints or internet permission. AndroidX, Kotlin and coroutines are open source;
the Gradle wrapper distribution checksum is pinned.

The build uses the standard Android SDK/AGP, Gradle and OpenJDK toolchain.
Dependencies resolve from Maven Central and Google's public Android repository;
the Gradle Plugin Portal supplies build plugins.

Before submission:
1. Publish this Git repository to the forge chosen by the owner.
2. Tag the reviewed release corresponding to versionCode 1/versionName 0.1.0.
3. Replace the repository and tag tokens below with real values.
4. Run the F-Droid build tooling against that tag and submit its metadata recipe.
5. Complete a physical-device overnight reliability and battery check.

Template (not a submitted or valid repository-specific recipe yet):

```yaml
Categories:
  - Sports & Health
License: Apache-2.0
SourceCode: REPLACE_WITH_PUBLIC_REPOSITORY_URL
IssueTracker: REPLACE_WITH_ISSUE_TRACKER_URL
Summary: Private sleep activity estimates and a gentle alarm
Description: |-
  Offline motion or microphone sleep activity estimates, a nightly journal,
  local trends, exact wake-up alarms and original ambient sounds.
  Optional short noise recordings remain on the device. Phone-only sleep phase
  labels are unvalidated estimates, not medical measurements.
RepoType: git
Repo: REPLACE_WITH_PUBLIC_GIT_URL
Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: REPLACE_WITH_RELEASE_TAG
    subdir: app
    gradle:
      - yes
AutoUpdateMode: None
UpdateCheckMode: None
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

No public repository, release tag or F-Droid submission is claimed by this local
project. Store listing text is provided in `fastlane/metadata/android/en-US/`.

Metadata field reference: [F-Droid Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/).

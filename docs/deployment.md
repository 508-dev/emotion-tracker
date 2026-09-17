# Releases and store submission

Emotion Tracker follows the sibling Soundboard release model: Conventional
Commits → release-please PR → version tag → APK/AAB builds. Signing keys,
store accounts, and submissions are configured by the maintainer. Nothing in
this setup submits the app to f-droid.org or promotes a Play release.

## What runs automatically

- PRs and main: version/policy drift checks, release-script tests, shellcheck,
  ktlint, unit tests, Android lint, debug APK and unsigned release APK/AAB.
  Artifacts are retained for 14 days.
- Main: release-please opens or updates a release PR. It owns `version.txt`,
  `CHANGELOG.md`, and `.release-please-manifest.json`. The workflow commits
  derived Gradle literals and store release notes onto that PR.
- Merging a release PR creates `vX.Y.Z` and a GitHub Release. The same workflow
  validates that tag and runs checks before building release artifacts.
- With signing configured: the signed APK and SHA-256 attach to GitHub Releases.
  APK, AAB and R8 mapping are also retained as workflow artifacts for 30 days.
  Without a signing key, setup builds remain unsigned workflow artifacts.
- Play uploads require `ENABLE_PLAY_PUBLISH=true` and credentials; uploads go
  to **internal**, with **draft** status for review in Play Console.
- A self-hosted F-Droid index requires `ENABLE_FDROID_PUBLISH=true`. It is
  published to `gh-pages`, preserving previous APKs. This is a separate
  distribution channel from the official f-droid.org repository.

Release creation and publishing deliberately share a workflow: resources
created with `GITHUB_TOKEN` do not trigger another workflow. Existing failed
releases can be retried via **Actions → Release → Run workflow → tag**. Supply
an existing GitHub Release tag; the workflow verifies it against that checkout's
version. A Play versionCode already uploaded cannot be reused as a new release;
disable its publishing variable when retrying only another target.

## Versioning

`version.txt` starts at the existing development version, `0.1.0`. The derived
versionCode is now `1000` (previously `1`):

```text
major * 1,000,000 + minor * 1,000 + patch
```

Minor and patch must be at most 999; codes must be between 1 and 2,100,000,000.
Only plain MAJOR.MINOR.PATCH is supported. `scripts/sync-version.sh --check`
fails on stale Gradle literals or missing store changelogs. Keep the literals
in `app/build.gradle.kts`: F-Droid parses them textually. The release tag format
is explicitly `vX.Y.Z`, unlike Soundboard's component-prefixed tags.

The first automatically selected version depends on eligible commits. Do not
create a pretend baseline release just to initialize the automation. To ship
0.1.0 manually as the first release, commit the reviewed setup, tag that commit
`v0.1.0`, create its GitHub Release, then dispatch Release with that tag. This
procedure publishes externally and should be done deliberately. Subsequent
releases use release-please. If overriding its version, keep its manifest,
`version.txt`, CHANGELOG and generated files in sync.

## One-time GitHub setup

1. Enable squash merging with PR titles as squash commit subjects. `PR title`
   checks Conventional Commits. Require CI before merging, including release PRs.
2. Allow Actions to create pull requests. Prefer a fine-grained
   `RELEASE_PLEASE_TOKEN` with Contents and Pull requests read/write on this repo
   so bot-created PRs receive CI; the default token fallback does not trigger it.
3. Create the **Prod** environment. Both release jobs declare that environment.
   Add the secrets in [secrets.md](secrets.md), and any desired protection rules.
   Reviewers on this environment also gate release-PR maintenance.
4. Leave `ENABLE_PLAY_PUBLISH` and `ENABLE_FDROID_PUBLISH` unset until their
   destinations are ready. Set them to the literal `true` as repository or
   Prod-environment variables when enabling those targets.
5. For self-hosted F-Droid, configure Pages to serve **gh-pages / root** after
   the first publish creates the branch. The landing page gives the URL and
   signing fingerprint. The policy is hosted beside it at
   `https://508-dev.github.io/emotion-tracker/privacy-policy.html`.
   Verify that URL is publicly live before using it in Play Console.

## Signing and cross-store updates

Use an Emotion Tracker key, not a debug key or Soundboard's key. Back it up
securely. Local builds read gitignored `keystore.properties`; CI uses
`EMOTION_TRACKER_KEYSTORE_*` and `EMOTION_TRACKER_KEY_*` variables populated
from Prod secrets. An incomplete configuration fails instead of silently
producing an unsigned release. With no configuration, source builds stay unsigned.
Signed CI builds disable Gradle's configuration cache to keep credentials out
of the saved configuration, and delete their temporary keystore afterwards.

If switching between GitHub/F-Droid and Play without reinstalling is important,
choose the same **app signing certificate** when enrolling in Play App Signing.
An upload key is not necessarily the certificate Play uses on installed APKs.
Choose the strategy before publishing. F-Droid can preserve an upstream
signature only after verifying a reproducible build; that verification has not
been performed by this setup.

## Google Play preparation

1. Create the Play Console app for `dev.co508.emotiontracker`. Complete account
   verification, testing requirements and all required App content forms.
2. Decide Play App Signing, build a signed AAB, and complete the first manual
   upload/setup in Console. The upload action cannot create the app.
3. Grant a Google service account access to this app and testing releases;
   store its JSON as `PLAY_SERVICE_ACCOUNT_JSON` in Prod.
4. Enter listing text from `fastlane/metadata/android/en-US/`. Add the store
   icon, feature graphic and real screenshots described in its `images/README.md`.
   CI uploads release notes and binaries, not the initial listing or graphics.
5. Publish [privacy-policy.html](privacy-policy.html) at a public URL. It is
   generated from the same text shown offline in Settings → Privacy policy.
   Self-hosted F-Droid publishing copies it to Pages; alternatively host this
   static file on 508.dev before enabling that repository.
6. Complete Data safety accurately: the app itself does not transmit journal
   data or include analytics. It permits Android-managed backup/device transfer
   and user-selected CSV exports, which are disclosed in the policy. Review
   Google's definitions rather than treating this doc as a prefilled declaration.
7. Complete the health-apps declaration. Mood journaling may fall under mental
   or behavioral wellness; choose based on current Play guidance. The listing
   describes personal reflection and makes no diagnostic or treatment claims.
8. Enable internal draft uploads when setup is complete. Smoke-test the minified
   release build (wheel, notes, chart panning, reminders, CSV, privacy policy),
   then review and promote in Console. Production promotion stays manual.

Official references checked during setup:
[review preparation](https://support.google.com/googleplay/android-developer/answer/9859455),
[Data safety](https://support.google.com/googleplay/android-developer/answer/10787469),
[health declaration](https://support.google.com/googleplay/android-developer/answer/14738291),
[upload action](https://github.com/r0adkll/upload-google-play).

## Official F-Droid preparation

After a signed release exists, follow [fdroiddata instructions](../fdroid/fdroiddata/README.md).
The renderer fills the template using a real tag's commit/version and the APK
certificate fingerprint. Submit its output to fdroiddata only after their
metadata checks and rebuild pass. It watches `vX.Y.Z` tags and uses the shared
Fastlane listing. No workflow files a submission or merges it automatically.

Build preparation includes disabling AGP dependency-info blobs and preserving
native-library symbols to avoid builder-dependent stripping. The Gradle daemon
is pinned to Java 25, as in Soundboard; CI explicitly installs Java 25. Verify
that F-Droid can provide it (the committed daemon criteria include download
URLs), along with platform 37/build tools. Reproducibility remains a separate
check on their infrastructure, not something a successful CI build guarantees.

## Local validation

```bash
./scripts/sync-version.sh --check
./scripts/sync-privacy.sh --check
./scripts/test-release.sh
shellcheck scripts/*.sh
./gradlew check assembleDebug assembleRelease bundleRelease
```

For deliberate local signing, see `keystore.properties.example`. Do not install
an unsigned release APK. The AAB is uploaded to Play, not installed directly.

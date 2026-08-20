# Development

No host services, no Docker Compose infra, no ports to coordinate — this is
a single Android app with a local database. The whole "dev loop" is
Gradle/Android Studio.

## First-Time Setup

1. Install **Android Studio** (current stable channel). It bundles a
   matching JDK, the Android SDK, platform-tools, and an emulator.
2. Open this repository's root directory in Android Studio — it finds
   `settings.gradle.kts` and syncs automatically.
3. If you'd rather not install Android Studio: install the Android
   command-line tools + SDK platform 37 + build-tools 36 yourself, set
   `ANDROID_HOME`, then `./gradlew` works standalone. This is the harder
   path for a first Android project; Android Studio is recommended instead.
4. To sign local release builds, copy `keystore.properties.example` to
   `keystore.properties` and generate a keystore — see that file's comments.
   Debug builds don't need this.

## Commands

```bash
./gradlew tasks                 # sanity-check the build loads
./gradlew ktlintCheck           # lint/format check
./gradlew testDebugUnitTest     # JVM unit tests, no emulator needed
./gradlew lintDebug             # Android lint
./gradlew assembleDebug         # build the debug APK
./gradlew installDebug          # build + install to a connected device/emulator
./gradlew check assembleDebug   # everything CI runs
```

`scripts/lint.sh`, `scripts/test.sh`, and `scripts/check-all.sh` wrap the
same tasks for CI/local consistency.

## Running The App

From Android Studio: pick a device/emulator in the toolbar and hit Run. From
the CLI, with a device connected or emulator running:

```bash
./gradlew installDebug
adb shell am start -n dev.508.emotiontracker.debug/dev.508.emotiontracker.MainActivity
```

(Debug builds get a `.debug` application-id suffix — see `app/build.gradle.kts`
— so debug and a real release build can be installed side by side.)

## Editing The Emotion Tree

`app/src/main/assets/emotion_tree.json` is plain data, not generated. Edit
it directly, then run `./gradlew testDebugUnitTest` — `EmotionTreeTest`
parses the exact shipped file and fails on duplicate ids, blank labels, or
bad hex colors. See the README's "The emotion tree" section and
`DECISIONS.md` for why entries reference a node by stable id rather than a
tree path.

## Database Schema Changes

Room schema history is exported to `app/schemas/` (see the `ksp { arg(...) }`
block in `app/build.gradle.kts`) and is committed — it's how Room verifies
migrations. Bump `@Database(version = ...)` in `AppDatabase.kt` and add a
`Migration` when the entity shape changes; don't just edit the entity and
expect existing installs to update in place.

## Workspace Scratch

Do not commit `.context/` — it's gitignored workspace-local scratch for
agents. Durable knowledge belongs in `docs/`, `README.md`, or `DECISIONS.md`.

## Agent Notes

- Prefer `./gradlew <task>` over calling `kotlinc`/`aapt`/etc. directly.
- `testDebugUnitTest` and `ktlintCheck` don't need an emulator;
  `installDebug` and instrumented tests (`connectedDebugAndroidTest`, once
  `androidTest/` exists — see `DECISIONS.md` → "Deferred: Instrumented
  Coverage") do.
- If `ANDROID_HOME`/`ANDROID_SDK_ROOT` isn't set and Android Studio isn't
  installed, say so rather than guessing at a build result.

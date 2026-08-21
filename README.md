# Emotion Tracker

A free, offline-first Android app for recording how you feel, as fast as
possible, against a timeline. See [`SPEC.md`](SPEC.md) for the full product
spec (deleted once the MVP ships; after that, work moves to tickets).

## What it is

Open the app, tap through an emotion wheel (Positive/Negative → progressively
specific feelings), and save. That's the whole interaction — the app is
optimized for capturing a feeling in a few taps, not for data entry. A
journal view shows saved entries grouped by day, with optional text notes.
Reminders lets you set any number of daily times to get a notification
nudging you to record how you're feeling (tapping it opens the wheel).
Settings has an "Export to CSV" placeholder and a triple-confirmed "delete
all entries."

The app has no backend, no network permission, and no analytics. Everything
lives in a local Room (SQLite) database on the device.

## License

GNU GPL v3 (see [`LICENSE`](LICENSE)), © 508.dev LLC. The goal is an F-Droid
listing; F-Droid requires the whole app — including all dependencies it
ships — to be free software, so keep new dependencies AndroidX/Kotlin-stdlib
class, not proprietary SDKs (no Google Play Services, no Firebase, no
closed-source analytics).

## Tooling

- **Kotlin** + **Jetpack Compose** (Material 3) for UI — the emotion wheel is
  a custom `Canvas` composable, which is why Compose rather than Views.
- **Room** for local storage.
- **Navigation Compose** for the hamburger menu's four destinations.
- **Gradle** (Kotlin DSL) with a version catalog at `gradle/libs.versions.toml`.
- **ktlint** (via the `org.jlleitschuh.gradle.ktlint` Gradle plugin) for lint/format.
- No dependency injection framework — one small hand-rolled composition root
  in `EmotionTrackerApp`. Revisit only if the module graph actually grows.

If this is your first Android project: install **Android Studio** (current
stable channel). It bundles the JDK, Android SDK, platform-tools, and an
emulator, which sidesteps most version-matching pain. Open this repo's root
directory directly — `settings.gradle.kts` is what Android Studio looks for.
Don't point it at a system JDK; let Android Studio manage its own embedded
one under **Settings → Build Tools → Gradle**.

See [`docs/tooling.md`](docs/tooling.md) for exact pinned versions and why,
and [`docs/development.md`](docs/development.md) for day-to-day commands.

## Quickstart

```bash
git clone <this repo>
cd emotion-tracker
./gradlew tasks          # sanity-check the build loads (needs Android SDK; see docs/development.md)
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Or just open the repo root in Android Studio and hit Run — that's the
normal path and handles SDK/emulator setup for you.

## Layout

```text
app/                    The single Gradle module (all app code lives here for now)
  src/main/java/...     Kotlin source, package dev.co508.emotiontracker
  src/main/assets/emotion_tree.json   The emotion wheel's content — see below
  src/main/res/         Android resources (strings, themes, launcher icon)
  src/test/             JVM unit tests
gradle/libs.versions.toml   Dependency version catalog
docs/                    Durable project documentation
extras/github/           Opt-in GitHub hygiene (CODEOWNERS, gitleaks, dependency review)
scripts/                 Thin wrappers around ./gradlew for CI and local use
SPEC.md                  Product spec (delete at MVP; see top of this file)
AGENTS.md                Canonical agent operating instructions
DECISIONS.md             This project's architecture decisions and why
```

## The emotion tree

The wheel's content — every level, label, and color — is
`app/src/main/assets/emotion_tree.json`, a plain nested JSON tree. Edit it
directly to add, rename, recolor, or re-nest emotions; no code changes
needed. Each node has a stable `id`; journal entries reference only that id
(not a full path), so restructuring the tree doesn't corrupt history. If an
`id` is later removed, existing entries that used it just fall back to
showing the raw id — see `EmotionRepository.resolve`.

`app/src/test/.../EmotionTreeTest.kt` parses this exact file on every test
run and fails the build if an edit breaks it (duplicate ids, blank labels,
bad hex colors).

## Read Next

1. `SPEC.md` — the product spec this MVP is being built against.
2. `AGENTS.md` — agent operating instructions (Codex, Claude Code, Cursor all point here).
3. `DECISIONS.md` — why Kotlin/Compose/Room/no-DI/GPL-3/these SDK levels.
4. `docs/tooling.md`, `docs/development.md`, `docs/deployment.md`.

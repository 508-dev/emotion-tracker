# AI Agent Development Guide

## Environment

- This is a single-module native Android app (Kotlin + Jetpack Compose,
  Gradle Kotlin DSL). There is no Node/Python/Ruby/Docker stack — don't
  reach for `bun`, `uv`, `bundle`, or `docker compose` here.
- Building, testing, and running the app requires the Android SDK. Prefer
  `./gradlew <task>` over invoking tools directly. `./gradlew tasks` and
  `./gradlew testDebugUnitTest` don't need an emulator; `installDebug` and
  instrumented tests do.
- If `ANDROID_HOME`/`ANDROID_SDK_ROOT` isn't set and Android Studio isn't
  installed, say so rather than guessing at a build result — see
  `docs/development.md` for setup.
- Treat `keystore.properties` (gitignored, see `keystore.properties.example`)
  as a secret. Never print its contents or commit a real one.

## Repository Shape

- `AGENTS.md`: this file — canonical agent operating instructions.
- `MANIFEST.md`: devkit file inventory this repo was generated from; useful
  if pulling in another devkit convention pack later (e.g. a backend).
- `DECISIONS.md`: this project's architecture decisions and why.
- `SPEC.md`: the MVP product spec. Delete it once the MVP ships (per its own
  first paragraph) — after that, work moves to tickets.
- `app/`: the single Gradle module. All app code lives here.
- `docs/`: contributor-facing documentation.
- `extras/github/`: optional GitHub hygiene (CODEOWNERS, gitleaks, dependency review).
- `scripts/`: thin wrappers around `./gradlew` for CI and local use.
- `.context/`: gitignored workspace-local scratch for agents.

## The Emotion Tree

`app/src/main/assets/emotion_tree.json` is the emotion wheel's content —
labels, colors, and nesting — and is meant to be edited directly, no code
changes required. Each node has a stable `id`; journal entries reference only
that `id`, not a path, so restructuring the tree is safe. See `DECISIONS.md`
→ "Emotion Tree As Dev-Editable JSON" before changing how it's referenced.
`EmotionTreeTest` parses the shipped file on every test run — run
`./gradlew testDebugUnitTest` after editing the JSON.

## Editing Rules

- Read target files, callers, and existing tests before editing.
- Keep edits surgical. Do not reformat unrelated files.
- Add or update tests (`app/src/test/`) when behavior changes.
- Update `docs/` when changing developer workflows or dependency pins.
- Update `gradle/libs.versions.toml` version comments/context when bumping a
  dependency deliberately; don't bump opportunistically outside of Renovate.
- Keep dependencies AndroidX/Kotlin-stdlib class — see `DECISIONS.md` →
  "GPL-3, Targeting F-Droid" before adding anything that isn't itself free
  software (no Google Play Services, no Firebase, no closed-source SDKs).
- No dependency injection framework is in use by design (see `DECISIONS.md`).
  Don't introduce Hilt/Koin without discussing it first.

## Validation

Before calling work complete, run the narrowest relevant checks:

```bash
./gradlew ktlintCheck
./gradlew testDebugUnitTest
```

For broader changes, or before opening a PR:

```bash
./gradlew check
```

`./gradlew check` runs lint, unit tests, and ktlint together. It does not
require an emulator. `./gradlew assembleDebug` additionally confirms the app
packages; `installDebug` and instrumented tests need a connected device or
emulator.

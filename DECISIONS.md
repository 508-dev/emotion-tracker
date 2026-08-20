# Emotion Tracker Decisions

Last reviewed: 2026-08-20

This is the decision record for this project, converted from the 508 Devkit's
generic constitution once the target platform (native Android) was known.
Treat it the same way the devkit's version was treated: the authority to
consult before re-litigating a choice below, and the place to add a new entry
when a comparable decision gets made.

## Native Android, Not A Web/Service Stack

Decision: build a single-module native Android app (Kotlin + Jetpack
Compose). Drop the devkit's Bun/TypeScript, Python, Ruby, and Docker Compose
conventions entirely — none apply to an offline mobile app with no backend.

Why: `SPEC.md` specifies an Android app aimed at F-Droid. There is no server
component, so the devkit's host-app/Compose-infra split, worktree ports, and
env-var contract don't have anything to attach to.

Deviate when: a companion backend (sync, backup) gets added later. If that
happens, revisit whether a `stacks/`-style convention pack from the devkit is
worth pulling back in for that service, in its own module or repo.

## Jetpack Compose Over Views

Decision: use Compose (Material 3) for all UI, not the XML View system.

Why: the emotion wheel is a custom radial, animated, gesture-driven control.
Compose's `Canvas` + `pointerInput` APIs are a direct fit; building the same
thing with Views would mean a custom `View` subclass doing the same math with
none of Compose's declarative state handling.

Deviate when: a future screen needs something Compose genuinely can't do well
yet. Unlikely for this app's scope.

## Room For Storage, No Backend

Decision: local-only persistence via Room (SQLite). No network calls, no
`INTERNET` permission, no sync.

Why: the spec doesn't ask for sync or multi-device access, and F-Droid/GPL-3
FOSS distribution favors an app that works fully offline with no telemetry
surface at all.

Deviate when: a user-requested backup/export/sync feature is scoped and
approved. Keep it opt-in and clearly disclosed if it ever adds a network
permission.

## Emotion Tree As Dev-Editable JSON, Referenced By Stable ID

Decision: the emotion wheel's content lives in
`app/src/main/assets/emotion_tree.json`, a plain nested tree with stable
per-node `id`s. Journal entries store only the leaf `id`, never a full path.

Why: per spec, the tree will get tweaked over time. Storing an id instead of
a path means restructuring the tree (renaming, recoloring, re-nesting) never
invalidates history. `EmotionTreeTest` parses the shipped file on every test
run so a bad edit fails CI, not a device in someone's pocket.

Deviate when: never, without discussing — this is the one piece of "dev-modify
friendly format" the spec explicitly called out.

## No Dependency Injection Framework

Decision: one hand-rolled composition root (`EmotionTrackerApp` builds the
repository once) instead of Hilt or Koin.

Why: three screens sharing one repository is not a graph a DI framework earns
its keep on, and this is the author's first Android project — fewer moving
parts to learn matters more than the ceremony DI would save here.

Deviate when: the module/dependency graph actually grows past what's easy to
wire by hand in `EmotionTrackerApp`. Hilt (fully open source, no Play
Services dependency) would be the natural choice then.

## GPL-3, Targeting F-Droid

Decision: license the app GNU GPL v3 (see `LICENSE`), copyright 508.dev LLC.
Avoid any dependency that isn't itself free software, since F-Droid requires
the entire shipped app — including dependencies — to qualify.

Why: per spec's stated objective.

Deviate when: never, without discussing — this is a stated product goal, not
an implementation detail.

## SDK Levels: minSdk 26, targetSdk 36, compileSdk 37

Decision: `minSdk = 26` (Android 8.0, Oreo), `targetSdk = 36`, `compileSdk =
37`, set 2026-08-20 against then-current AGP 9.2.0 / Compose BOM
2026.08.00.

Why: API 26 provides `java.time` natively, so entry timestamps don't need
core library desugaring — a real simplification, not just a version pick.
`targetSdk 36` avoids the (at the time) very recent API 37 behavior changes
landing untested; bump it once they've been reviewed. `compileSdk 37` is
needed by the pinned Compose BOM.

Deviate when: a concrete device-reach requirement demands a lower `minSdk`,
or `targetSdk`/`compileSdk` need bumping to pick up newer library versions —
check `docs/tooling.md` for the current pins first.

## Deferred: Instrumented (`androidTest`) Coverage

Decision: MVP ships with JVM unit tests only (`app/src/test/`) covering the
emotion tree and repository logic. No `androidTest/` instrumented UI tests
yet.

Why: getting the core logic right and covered mattered more than emulator-run
UI tests for a first pass, and this machine has no Android SDK/emulator to
run them against during initial development.

Deviate when: the wheel's tap-to-angle hit-testing or navigation flows need
regression protection an emulator can give that a JVM test can't — add
`androidTest/` with Compose UI testing (`androidx.compose.ui.test`) at that
point rather than continuing to defer it indefinitely.

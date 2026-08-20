# Tooling

Single stack: Kotlin + Jetpack Compose + Gradle (Kotlin DSL), Android-only.
See `DECISIONS.md` for why (Compose over Views, Room over a backend, no DI
framework).

## Pinned Versions

Tracked in `gradle/libs.versions.toml`. Set 2026-08-20 against then-current
stable releases:

| Component | Version | Notes |
| --- | --- | --- |
| Android Gradle Plugin | 9.2.0 | Requires Gradle 9.4.1+, JDK 17. |
| Gradle (wrapper) | 9.4.1 | `gradle/wrapper/gradle-wrapper.properties`, sha256-verified. |
| Kotlin | 2.3.20 | |
| KSP | 2.3.11 | Used for Room's annotation processor. KSP now versions independently of Kotlin — if Android Studio flags a mismatch, bump this, not Kotlin. |
| Compose BOM | 2026.08.00 | Pins all `androidx.compose.*` artifact versions together. |
| Room | 2.8.4 | |
| Navigation Compose | 2.9.8 | Classic (not Nav3) — simplest fit for 3 flat destinations. |
| Lifecycle | 2.11.0 | |
| kotlinx.serialization | 1.11.0 | Parses `emotion_tree.json`. |
| kotlinx.coroutines | 1.11.0 | |
| ktlint Gradle plugin | 14.2.0 | `org.jlleitschuh.gradle.ktlint`. |
| compileSdk | 37 | Android 17. Required by the Compose BOM above. |
| targetSdk | 36 | One below compileSdk; bump once API 37's behavior changes are reviewed. |
| minSdk | 26 | Android 8.0 — gives `java.time` natively, no desugaring needed. |

Bump versions deliberately: Renovate opens PRs against
`gradle/libs.versions.toml` on a 7-day cooldown (`renovate.json`), which is
the expected update path. Don't hand-edit versions opportunistically outside
of that unless fixing something broken.

## Required Checks

```bash
./gradlew ktlintCheck
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Or all at once: `./gradlew check assembleDebug` (what CI runs; see
`scripts/check-all.sh`).

## IDE

Android Studio is the expected IDE — it bundles a matching JDK, the Android
SDK, platform-tools, and an emulator. Let it manage its own embedded JDK
(**Settings → Build Tools → Gradle**) rather than pointing it at a system
`java`; AGP 9.2.0 requires JDK 17 specifically; there is no guarantee the
system JDK matches (this dev machine currently has JDK 26 installed, for
example, which AGP 9.2.0 cannot build with directly).

## Dependency Safety

- Renovate: `minimumReleaseAge: "7 days"` (`renovate.json`). Renovate
  natively understands Gradle version catalogs — no extra config needed for
  it to open PRs against `gradle/libs.versions.toml`.
- Gradle wrapper integrity is verified via `distributionSha256Sum` in
  `gradle/wrapper/gradle-wrapper.properties`. Update both together when
  bumping the wrapper version.
- CI builds against the committed wrapper only (no floating Gradle version).

## Workflow Permissions

Keep workflow permissions at `contents: read` unless a job explicitly calls
PR APIs or posts PR comments. Dependency Review and Gitleaks stay opt-in via
`extras/github/*.yml.example`; see `docs/github-workflows.md`.

## Free-Software Constraint (F-Droid)

Every dependency this app ships must itself be free software — F-Droid
builds from source and requires the whole app, transitively, to qualify. In
practice: stick to AndroidX and Kotlin-stdlib-class libraries. No Google Play
Services, no Firebase, no closed-source SDKs. Compose's dynamic color API
(`dynamicLightColorScheme`/`dynamicDarkColorScheme`, used in
`ui/theme/Theme.kt`) is fine — it's a pure AndroidX/Compose API with no Play
Services dependency, despite the "Material You" branding.

#!/usr/bin/env bash
# Everything CI runs: release metadata, policy sync, shell checks, ktlint,
# Android lint, unit tests, and debug/release artifacts.
# No emulator required.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
./scripts/sync-version.sh --check
./scripts/sync-privacy.sh --check
./scripts/test-release.sh
shellcheck scripts/*.sh
./gradlew check assembleDebug assembleRelease bundleRelease

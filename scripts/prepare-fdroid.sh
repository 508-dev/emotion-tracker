#!/usr/bin/env bash
# Render official F-Droid metadata only after a real signed release exists.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
tag=${1:?usage: scripts/prepare-fdroid.sh vX.Y.Z APK_CERT_SHA256}
fingerprint=${2:?supply the APK signing certificate SHA-256, not the APK file hash}
[[ $tag =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid release tag' >&2; exit 1; }
fingerprint=$(printf '%s' "$fingerprint" | tr -d ':' | tr '[:upper:]' '[:lower:]')
[[ $fingerprint =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid certificate fingerprint' >&2; exit 1; }
commit=$(git rev-parse --verify "refs/tags/$tag^{commit}")
version=${tag#v}
gradle=$(git show "$commit:app/build.gradle.kts")
code=$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+)$/\1/p' <<< "$gradle")
name=$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)"$/\1/p' <<< "$gradle")
[[ $code =~ ^[0-9]+$ && $name == "$version" ]] || { echo 'Tag does not match Gradle version literals' >&2; exit 1; }
sed -e "s/@VERSION@/$version/g" -e "s/@CODE@/$code/g" \
    -e "s/@COMMIT@/$commit/g" -e "s/@FINGERPRINT@/$fingerprint/g" \
    fdroid/fdroiddata/dev.co508.emotiontracker.yml.template

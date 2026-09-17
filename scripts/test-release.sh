#!/usr/bin/env bash
# Exercise version generation in an isolated fixture, never the working tree.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
export LC_ALL=C.UTF-8
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/scripts" "$fixture/app"
cp scripts/sync-version.sh "$fixture/scripts/"
cat > "$fixture/app/build.gradle.kts" <<'GRADLE'
android {
    defaultConfig {
        versionCode = 1
        versionName = "0.0.1"
    }
}
GRADLE
printf '# Changelog\n\n## 0.1.0\n\n* First release.\n' > "$fixture/CHANGELOG.md"
printf '0.1.0\n' > "$fixture/version.txt"
"$fixture/scripts/sync-version.sh" > /dev/null
"$fixture/scripts/sync-version.sh" --check
[[ $("$fixture/scripts/sync-version.sh" --print) == $'version=0.1.0\nversion_code=1000' ]]

# A missing NEW changelog and stale literals must both fail CI's drift check.
printf '0.1.1\n' > "$fixture/version.txt"
if "$fixture/scripts/sync-version.sh" --check > /dev/null 2>&1; then
    echo 'Expected stale version detection' >&2; exit 1
fi
"$fixture/scripts/sync-version.sh" > /dev/null
"$fixture/scripts/sync-version.sh" --check
test -f "$fixture/fastlane/metadata/android/en-US/changelogs/1000.txt"
test -f "$fixture/fastlane/metadata/android/en-US/changelogs/1001.txt"

for invalid in 01.2.3 1.1000.0 1.0.1000 0.0.0 2100.0.1 9999.0.0 '1.2.3-rc1' '1. 2.3'; do
    printf '%s\n' "$invalid" > "$fixture/version.txt"
    if "$fixture/scripts/sync-version.sh" --print > /dev/null 2>&1; then
        echo "Accepted invalid version: $invalid" >&2; exit 1
    fi
done
printf '1.2.3\n' > "$fixture/version.txt"
[[ $("$fixture/scripts/sync-version.sh" --print) == $'version=1.2.3\nversion_code=1002003' ]]

# Long UTF-8 release notes must neither split a character nor fail with SIGPIPE.
{
    printf '# Changelog\n\n## 1.2.3\n\n'
    for ((i=0; i<2000; i++)); do printf '情緒'; done
    printf '\n'
} > "$fixture/CHANGELOG.md"
"$fixture/scripts/sync-version.sh" > /dev/null
"$fixture/scripts/sync-version.sh" --check
notes=$(cat "$fixture/fastlane/metadata/android/en-US/changelogs/1002003.txt")
[[ ${#notes} -eq 499 ]]
iconv -f UTF-8 -t UTF-8 "$fixture/fastlane/metadata/android/en-US/changelogs/1002003.txt" > /dev/null

# Submission metadata must resolve the tag's commit, never the working-tree tip.
cp scripts/prepare-fdroid.sh "$fixture/scripts/"
mkdir -p "$fixture/fdroid/fdroiddata"
cp fdroid/fdroiddata/dev.co508.emotiontracker.yml.template "$fixture/fdroid/fdroiddata/"
git -C "$fixture" init --quiet
git -C "$fixture" add .
git -C "$fixture" -c user.name=Test -c user.email=test@example.invalid commit --quiet -m fixture
git -C "$fixture" tag v1.2.3
fingerprint=$(printf '%064d' 0)
metadata=$("$fixture/scripts/prepare-fdroid.sh" v1.2.3 "$fingerprint")
[[ $metadata == *"commit: $(git -C "$fixture" rev-parse HEAD)"* ]]
[[ $metadata == *'versionCode: 1002003'* ]]
git -C "$fixture" tag v1.2.4
if "$fixture/scripts/prepare-fdroid.sh" v1.2.4 "$fingerprint" > /dev/null 2>&1; then
    echo 'Accepted a tag that disagrees with the app version' >&2; exit 1
fi
printf 'Release script tests passed.\n'

#!/usr/bin/env bash
# Publish the same policy text displayed inside the app as a static HTML page.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
mode=${1:-write}
[[ $mode == write || $mode == --check ]] || { echo 'usage: scripts/sync-privacy.sh [--check]' >&2; exit 2; }
page=$(mktemp)
trap 'rm -f "$page"' EXIT
{
    cat <<'HTML'
<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Emotion Tracker — Privacy Policy</title>
<style>body{font:16px/1.6 system-ui,sans-serif;max-width:48rem;margin:2rem auto;padding:0 1rem}pre{font:inherit;white-space:pre-wrap}</style>
<main><pre>
HTML
    sed -e 's/\&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' app/src/main/res/raw/privacy_policy.txt
    printf '</pre></main>\n</html>\n'
} > "$page"
if [[ $mode == --check ]]; then
    diff -u docs/privacy-policy.html "$page"
else
    cp "$page" docs/privacy-policy.html
fi

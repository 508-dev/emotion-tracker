# Release secrets

The app has no runtime API keys or backend credentials. Release automation uses
signing material and publishing credentials, configured in GitHub **Settings →
Environments → Prod → Environment secrets**. Never commit or print their values.

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded Emotion Tracker signing keystore |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias, e.g. `emotion-tracker` |
| `RELEASE_KEY_PASSWORD` | Key password (same as store password for PKCS12) |
| `RELEASE_PLEASE_TOKEN` | Fine-grained repo Contents/PR write token so release PRs trigger CI |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play service account JSON with this app's testing-release access |

Create a new app key only if one has not already been used for this application:

```bash
keytool -genkeypair -v -keystore release.keystore -alias emotion-tracker \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore
```

Keep the key and passwords in durable secure storage outside git. Supply Base64
to GitHub through a private channel, not a captured terminal/chat transcript.
Do not reuse Soundboard's private key. Debug builds use Android's debug key.

Store these in `pass` under `508/android/emotiontracker/`, one entry per
secret, **named exactly as the secret appears in GitHub** (not a shorthand —
`pass show 508/android/emotiontracker/<SECRET_NAME>` should always work):

```bash
pass insert 508/android/emotiontracker/RELEASE_KEYSTORE_BASE64    # the base64 -w0 output above
pass insert 508/android/emotiontracker/RELEASE_KEYSTORE_PASSWORD
pass insert 508/android/emotiontracker/RELEASE_KEY_ALIAS           # emotion-tracker, the -alias value above
pass insert 508/android/emotiontracker/RELEASE_KEY_PASSWORD        # same value as RELEASE_KEYSTORE_PASSWORD — PKCS12 has no separate key password
pass insert 508/android/emotiontracker/RELEASE_PLEASE_TOKEN
```

(The existing `508/android/emotiontracker/keypass` /
`keystorebase64` / `keystorepassword` / `releasekeyalias` entries predate
this convention — migrate their values into the renamed entries above rather
than adding to the mismatch.)

For `RELEASE_PLEASE_TOKEN`: create a **fine-grained personal access token**
scoped to this repository only, with exactly **Contents: Read and write** and
**Pull requests: Read and write**.

For local release builds, copy `keystore.properties.example` to the gitignored
`keystore.properties` and fill it privately. CI maps GitHub secrets to:

- `EMOTION_TRACKER_KEYSTORE_FILE`
- `EMOTION_TRACKER_KEYSTORE_PASSWORD`
- `EMOTION_TRACKER_KEY_ALIAS`
- `EMOTION_TRACKER_KEY_PASSWORD`

If any of these environment values are set, they take precedence as a complete
set over the local file. Partial signing configuration fails with a message
that names only the missing configuration, never a value.

Optional self-hosted F-Droid uses the app key to sign its repository index,
as Soundboard does. This couples repository trust and app identity: rotating
that key affects both, and clients pin the repository certificate fingerprint.
Its transient `FDROID_*` environment variables are also secrets. Git auth uses
an askpass helper instead of putting the token in a remote URL. Temporary
keystores/configuration are excluded from published paths and cleaned up.

`ENABLE_PLAY_PUBLISH` and `ENABLE_FDROID_PUBLISH` are non-secret variables,
not credentials. Both default off. Store setup and signing policy are described
in [deployment.md](deployment.md).

If a key leaks, report privately to caleb@508.dev and evaluate the applicable
store's key upgrade/recovery process. Blindly replacing an app-signing key can
make updates incompatible with installed copies. Never print keystore contents,
passwords, Base64, service account JSON, or token values while investigating.

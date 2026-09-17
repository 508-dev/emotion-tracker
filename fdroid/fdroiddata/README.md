# Official F-Droid submission

The template here targets reproducible builds with the developer's APK signing
certificate, like Soundboard's current submission. It deliberately contains no
invented release commit or signing fingerprint. It is not ready to submit until
the first signed APK has been published and F-Droid can reproduce it.

After that release, render the template with real values:

```bash
./scripts/prepare-fdroid.sh v0.1.0 APK_SIGNING_CERTIFICATE_SHA256 > /tmp/dev.co508.emotiontracker.yml
```

Use the actual first release tag (it may differ from v0.1.0). Read the public
certificate fingerprint using `apksigner verify --print-certs` on its APK.
The script resolves the tag to a full commit SHA and reads versions at that
commit. Do not supply the APK's file hash or Soundboard's signing fingerprint.

Copy the result into a checkout of fdroiddata as
`metadata/dev.co508.emotiontracker.yml`. Run `fdroid rewritemeta`, `fdroid lint`,
`fdroid checkupdates`, and a build/verification there, inspect any changes,
then submit a merge request. F-Droid may request adjustments to the build
recipe, JDK provisioning or categories. A local successful build does not
prove F-Droid reproducibility. If using F-Droid's own signing instead, remove
`Binaries` and `AllowedAPKSigningKeys` deliberately and document that those
installs cannot update developer-signed installs in place.

Store descriptions and screenshots are read from `fastlane/metadata/android/`.
This official submission is separate from `fdroid/metadata/`, which describes
the optional self-hosted binary repository.

References: [submission guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
[reproducible builds](https://f-droid.org/docs/Reproducible_Builds/).

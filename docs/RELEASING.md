# Release setup

The GitHub Actions workflow publishes two signed APKs and a SHA-256 checksum file when a `v*` tag is pushed. Release tags must match `versionName` in `app/build.gradle`.

## One-time repository secrets

Create a persistent Android signing key locally. Back it up securely: the same key is required for updates to install over earlier releases.

```bash
keytool -genkeypair -v -keystore handystudy-release.jks -alias handystudy -keyalg RSA -keysize 3072 -validity 10000
```

Set these **Actions secrets** in the GitHub repository settings:

| Secret | Value |
| :--- | :--- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the complete `.jks` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias, for example `handystudy` |
| `ANDROID_KEY_PASSWORD` | Key password |

Do not commit the keystore or passwords. The workflow fails closed when secrets are missing. Debug APKs use Android's debug key and cannot update a separately signed release APK.

## Publish

Update `versionCode` and `versionName`, merge the change, then push the matching tag, for example `v1.0.6`. The workflow builds `HandyStudy-1.0.6-android-universal.apk`, `HandyStudy-1.0.6-android-arm64-v8a.apk`, and `SHA256SUMS.txt`, and attaches them to a GitHub Release. The app's update check reads the latest release tag and opens its download page; it does not silently install packages.

The public build contains only the original sample bank. It does not package third-party educational content.

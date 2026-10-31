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

Update `versionCode` and `versionName`, merge the change, then push the matching tag, for example `v1.0.7`. The workflow builds `HandyStudy-1.0.7-android-universal.apk`, `HandyStudy-1.0.7-android-arm64-v8a.apk`, and `SHA256SUMS.txt`, and attaches them to a GitHub Release. The app's update check reads the latest release tag and opens its download page; it does not silently install packages.

The public build contains only the original sample bank. It does not package third-party educational content.

## Local upgradeable APK

The local build must use the **same private keystore, alias and passwords** as GitHub Actions. Repository Secrets cannot be downloaded from GitHub; keep a secure backup of the original signing keystore. A different key, including Android's debug key, cannot update an installed GitHub Release APK.

In PowerShell, set these environment variables in the current session from your secure local copy, without writing passwords into this repository:

```powershell
$env:ANDROID_KEYSTORE_FILE = 'C:\secure\handystudy-release.jks'
$env:ANDROID_KEYSTORE_PASSWORD = '<keystore password>'
$env:ANDROID_KEY_ALIAS = '<key alias>'
$env:ANDROID_KEY_PASSWORD = '<key password>'
.\tools\build_apk.ps1 -StandardPenOnly
```

The script now builds a signed release APK by default at `app/build/outputs/apk/release/app-release.apk` and verifies its signature with `apksigner`. `-StandardPenOnly` matches the GitHub universal APK; omit it for the native pen library build. `-DebugBuild` is available for development but its APK cannot upgrade an installed GitHub Release. Do not commit the keystore, passwords, or shell transcripts containing them.

Starting with v1.0.10, the signing certificate SHA-256 fingerprint is `49a36334aea762d12ade60486429c601e19adbf7556a48e90491a8d922d45a33`. The local script loads a Windows-encrypted configuration from `%APPDATA%\HandyStudy\signing\signing.clixml` when available, then rejects a release APK with a different certificate. Back up the accompanying `release.jks` and credentials securely. GitHub Actions uses the same keystore through repository Secrets. Builds signed with the former v1.0.9 certificate (`2f05b5d121139a5afe5424e82a640fcf2d0653d22a0213b664dfaf3b73f8ae44`) cannot be upgraded in place to the new signer; export app data before uninstalling the old app.

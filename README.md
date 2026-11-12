# AMBAY Billing v2 (Android)

Native Kotlin + Jetpack Compose POS & Billing app for **AMBAY MOBILE ACCESSORIES**, built with an iOS-inspired frosted-glass design (`dev.chrisbanes.haze`), offline-first Room storage (integer paise), hardware-backed Keystore EC P-256 signatures, CameraX + ML Kit scanning, Bluetooth ESC/POS thermal printing, and sharp vector GST PDF generation.

## Configuration

Set these values in `gradle.properties`, via `-P` Gradle flags, or as GitHub Actions repository variables/secrets:

- `AMBAY_BASE_URL`: Cloudflare Worker registry URL (default `https://ambay-registry.workers.dev`)
- `AMBAY_UPDATE_REPO`: GitHub `owner/repo` for in-app updates (default `parakhmewara2002-gif/ambay-android`)
- `AMBAY_GOOGLE_WEB_CLIENT_ID`: OAuth 2.0 Web Client ID from Google Cloud Console for Credentials Manager Google Sign-In

## Build & Test

Run unit tests (no device required):
```bash
gradle testDebugUnitTest
```

Build signed release APK:
```bash
gradle assembleRelease
```

Enable R8 full-mode shrinking:
```bash
gradle assembleRelease -PAMBAY_MINIFY=true
```

The committed keystore `app/ambay-release.jks` ensures every CI build (`v1.0.<GITHUB_RUN_NUMBER>`) installs cleanly over previous builds.

## Pairing with Website Server

1. On the Owner website/app, open **More > Linked Devices > Link a device (QR)**.
2. On the new Android phone, complete the first-launch wizard and scan the `AMBPAIR1....` QR code (valid 60 seconds, single use).

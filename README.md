# AMBAY Billing — Android App

Production-quality Android application for **AMBAY MOBILE ACCESSORIES** built with Kotlin, Jetpack Compose (Material 3), Room, WorkManager, Retrofit, CameraX, and Android Keystore hardware security.

---

## 1. Project Overview & Architecture

- **Application Name:** AMBAY Billing
- **Package Name:** `com.ambay.billing`
- **Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 (Android 15)
- **Language & UI:** Kotlin 2.0, Jetpack Compose with Material 3
- **Brand Colors:** Deep Blue (`#0B3D91`), Light Blue (`#1E5BB8`), Accent Gold (`#FF9800`)
- **UI Text Language:** Hinglish ("Scan karo", "Print karo", "Galat password", "Scan Next Item", "Dhanyavaad")
- **Architecture:** Clean Architecture + MVVM + Repository pattern with Hilt Dependency Injection.

```
com.ambay.billing/
├── core/
│   ├── auth/          # AuthManager, BiometricHelper, GoogleRecoveryManager
│   ├── crypto/        # CanonicalJson (RFC 8785), SignatureConverter, DeviceKeyManager, EnvelopeVerifier, PasswordHasher, SecurityAudit
│   ├── database/      # Room database (AmbayDatabase, Entities, DAOs)
│   ├── model/         # Item, Sale, SaleLine, Customer, Money (Integer Paise), PairingModels, SyncModels
│   ├── network/       # Retrofit, AmbayApiService, SigningInterceptor, EnvelopeInterceptor, ClockSync, CatalogSource
│   ├── print/         # EscPosBuilder (58mm/80mm), BluetoothPrintManager, PdfReceiptGenerator
│   ├── scan/          # CameraX BarcodeAnalyzer (ML Kit), BeepManager
│   └── sync/          # AmbayRepository, SalesSyncWorker, HeartbeatWorker
├── di/                # AppModule (Hilt dependency injection)
├── ui/
│   ├── components/    # AmbayAppBar, ScanViewfinder, ReceiptView, NumCountUp
│   ├── screens/       # LoginScreen, SetupWizardScreen, ForgotPasswordDialog, StaffMainScreen, StaffScanTab, StaffPrintTab, CartBottomSheet, OwnerWebViewScreen
│   └── theme/         # Color (#0B3D91), Theme, Type, Shapes
├── AmbayBillingApp.kt # Application class (@HiltAndroidApp)
└── MainActivity.kt    # Single activity container with Android 12 SplashScreen, Lifecycle Auto-Lock, FLAG_SECURE
```

---

## 2. Security & Authentication Model

### Passwords & Android Keystore
- Passwords are **never stored in plain text**.
- Salted **PBKDF2-HMAC-SHA256** with **210,000 iterations** and a 16-byte cryptographically secure salt.
- Hashes and authentication tokens are stored in `EncryptedSharedPreferences` backed by the `AndroidKeyStore` master key (`AES256_GCM` + `AES256_SIV`).
- **Exponential Lockout:** 5 failed attempts trigger progressive lockouts (30 s, 60 s, 120 s, 240 s, max 300 s / 5 min).
- **Biometric Unlock:** Optional `BiometricPrompt` support enabled automatically after the first successful password login.
- **FLAG_SECURE:** Automatically active on password entry, setup wizard, and PIN screens to prevent screen recording and screenshot leakage.
- **Auto-Lock:** Monitors application lifecycle (`ProcessLifecycleOwner`) and locks the session if backgrounded for more than 2 minutes.

### Recovery Gmail & Password Reset
- **Owner Reset:** Initiates Google Sign-In via `CredentialManager` / Google Identity Services. If the signed-in account equals the registered recovery Gmail, owner creates a new password. If mismatched, returns: `"Ye Gmail registered nahi hai"` and displays the masked address (e.g. `ow***@gmail.com`).
- **Staff Reset:** Staff cannot reset their own password. The owner must authenticate on that device using the recovery Gmail account to reset the staff password.

---

## 3. Staff Mode (Fast POS)

Staff mode is restricted strictly to two tabs: **SCAN** and **PRINT**.

### SCAN Tab
- **CameraX + ML Kit:** High-definition preview (1080p, continuous autofocus, tap-to-focus, pinch zoom, flashlight toggle).
- **Viewfinder:** Compact overlay occupying ~40% of the screen height, leaving the scanned items list visible below.
- **Exact Barcode Match:** Scans EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, and QR codes. Matches `item.code` or `item.fac` strictly; never fuzzy matches.
- **Scan Reaction:** Camera pauses immediately upon detection, plays audio beep + haptic vibration, adds 1 unit to cart, and slides up an item summary card (Name, Model, Price, Max Discount, Min Price, Stock, Warranty W/N) with a **"Scan Next Item"** button. If not found, slides up a red warning card: `"Ye code kisi item me nahi mila"`.
- **Cart Bottom Sheet:**
  - Item quantities (`+` / `-`).
  - Line-level discount validation: rejected if discount exceeds `item.maxDiscount * qty`. Minimum price is enforced as `price - maxDiscount`; price can never be negative.
  - Customer name and phone (optional).
  - Payment mode: `Cash`, `UPI`, `Card`, or `Credit`.
  - Running totals with animated number count-up.
- **Save and Print:**
  - Creates a Without-Bill sale (`WB-0001...`) with a permanent UUID v4 transaction ID.
  - Deducts local stock once and idempotently via movement ID `wsale:<txn>:<lineIndex>`.
  - Enqueues sale in Room database for offline-first background upload via WorkManager.

### PRINT Tab
- **Sales History:** Filter by "Aaj" (Today), "Pichhle 7 Din" (Last 7 Days), or search by Sale No, customer, or item name.
- **Receipt Preview:** Sharp vector rendering previewing thermal receipt format.
- **ESC/POS Thermal Printing:**
  - Supports 58 mm and 80 mm thermal printers over Bluetooth SPP (`00001101-0000-1000-8000-00805F9B34FB`).
  - Formats headers: AMBAY MOBILE ACCESSORIES, date/time, sale no, line items, discount, net total, payment mode, and `"Dhanyavaad! Phir Padhaarein."`.
  - Sends ESC/POS cut command (`GS V 0`).
  - Remembers selected Bluetooth printer MAC address.
- **PDF Sharing & Reprint:** Fallback to Android PrintManager and direct PDF share via FileProvider.

---

## 4. Owner Mode (Hardened Native Shell & WebView)

- Hosts the live website: `https://ambay-billing.pages.dev`.
- **Storage & Offline:** DOM Storage, IndexedDB, and Service Workers enabled to maintain offline PWA storage.
- **Hardening:**
  - Block file access (`allowFileAccess = false`, `allowContentAccess = false`).
  - Prohibit mixed content (`MIXED_CONTENT_NEVER_ALLOW`).
  - Safe Browsing enabled.
  - Strict URL whitelist: allows only `https://ambay-billing.pages.dev`, `accounts.google.com`, `*.googleapis.com`, and `cdnjs.cloudflare.com`.
- **JavaScript Bridge (`@JavascriptInterface` "Android"):**
  - `Android.scan()`: Triggers native fast CameraX scanner and passes decoded barcode back to the webpage.
  - `Android.printReceipt(escPosOrHtml)`: Prints receipts through the native Bluetooth ESC/POS engine.
  - `Android.saveFile(name, base64)`: Saves downloaded backup JSON, PDF, and XLSX files directly into the Android Public Downloads folder (`MediaStore.Downloads`).
  - `Android.share(title, text, url)`: Triggers Android system share sheet.
- **Native Owner Extras:**
  - Top app bar with server sync status.
  - "Staff View" switcher to monitor cashier interface.
  - Staff password management dialog.
  - Lock & logout actions.

---

## 5. Server Communication & Cryptography

### Hardware Device Key
- EC P-256 (ES256) key pair generated in `AndroidKeyStore` (`secp256r1`).
- Uses StrongBox Keymaster if supported by the device hardware.
- Exports `spki = base64url(publicKey.encoded)` (SubjectPublicKeyInfo DER).
- Key ID `kid = base64url(first 12 bytes of SHA-256(spki))`.
- ECDSA signatures returned by Android in ASN.1 DER format are converted to raw 64-byte `r||s` (IEEE P1363) without padding.

### Pairing with Website Server
1. The owner website displays a pairing QR code in Settings -> Linked Devices (`AMBPAIR1.<base64url JSON>`).
2. The Android app scans or pastes the payload `{v:1, u:serverUrl, t:token, i:issuerId, e:expiresAtMs, r:{skid,alg,spki}}`.
3. The app generates its hardware key and sends `POST /v1/pair/redeem`:
   ```json
   {
     "token": "<token>",
     "alg": "ES256",
     "spki": "<base64url_spki>",
     "nonce": "<random_16_bytes_base64url>",
     "pop": "<base64url_sig>",
     "name": "Android Cashier",
     "platform": "android"
   }
   ```
   Where `pop` is the signature over:
   `"AMBENROLL1\n" + sha256hex("pair:" + token) + "\n" + kid + "\n" + nonce`
4. The server returns a signed envelope enrolling the device as a `cashier`. The app pins the server public keys.

### Authenticated Request Headers
Every request after pairing carries:
- `X-Amb-Kid`: Device key ID.
- `X-Amb-Ts`: Clock-corrected epoch milliseconds (synchronized via `serverTs`).
- `X-Amb-Nonce`: Unique random base64url string (8–64 characters).
- `X-Amb-Sig`: Base64url raw signature over:
  `"AMBREQ1\n" + METHOD + "\n" + pathAndQuery + "\n" + ts + "\n" + nonce + "\n" + sha256hex(exactBodyString or "")`

### Envelope Verification & Clock Skew
- Every server response envelope `{p: {...}, skid, alg, sig}` is cryptographically verified before processing over:
  `"AMBSRV1\n" + canonicalJson(p)`
- Supports verification for both `ES256` and `Ed25519`.
- If server returns HTTP 401 `clock_skew`, the app executes an immediate clock resynchronization via `GET /v1/health?nonce=...`.
- If server returns HTTP 403 `key_revoked`, local credentials are wiped and the app notifies the user: `"Is device ko owner ne logout kar diya"`.

---

## 6. How to Build & Run

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer.
- JDK 17.
- Android SDK Platform 35 and Build-Tools 35.0.0.

### Configuring BASE_URL
The default server URL is configured in `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "DEFAULT_BASE_URL", "\"https://ambay-registry.parakhmewara.workers.dev\"")
buildConfigField("String", "WEBVIEW_URL", "\"https://ambay-billing.pages.dev\"")
```
It can also be dynamically configured via the server pairing QR code or through the hidden owner configuration.

### Running Unit Tests
Execute the comprehensive test suite verifying Canonical JSON, DER signature conversion, envelope verification, paise math, and discount rules:
```bash
./gradlew test
```

### Building the Debug APK
```bash
./gradlew assembleDebug
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Building the Signed Release APK
1. Generate an upload keystore:
   ```bash
   keytool -genkey -v -keystore ambay-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ambay
   ```
2. Configure release signing in `app/build.gradle.kts` or supply environment variables:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file(System.getenv("KEYSTORE_PATH") ?: "ambay-release-key.jks")
           storePassword = System.getenv("KEYSTORE_PASSWORD")
           keyAlias = System.getenv("KEY_ALIAS") ?: "ambay"
           keyPassword = System.getenv("KEY_PASSWORD")
       }
   }
   ```
3. Run the release build:
   ```bash
   ./gradlew assembleRelease
   ```
The optimized, ProGuard/R8-minified APK will be produced at:
`app/build/outputs/apk/release/app-release.apk`

---

## 7. Real Device Verification Checklist

The following items involve physical hardware and external identity providers, which should be verified on a physical Android handset:

1. **CameraX Barcode Scanning on Physical Sensor:**
   - Physical autofocus and low-light scanning with LED torch toggle.
   - Scanning physical product barcodes (EAN-13, Code 128 on phone accessories packaging).
2. **Bluetooth ESC/POS Thermal Printing:**
   - Pairing with physical 58 mm and 80 mm Bluetooth thermal receipt printers (e.g. POS-5802, Epson, thermal mobile printer).
   - Verifying paper feed, line alignment, Hindi text rendering, QR code readability, and paper cut.
3. **Google Sign-In Credential Manager:**
   - Requires generating the production SHA-1 fingerprint of the signing key (`./gradlew signingReport`) and linking it inside the Google Cloud Console OAuth 2.0 Client IDs.
4. **Hardware KeyStore StrongBox:**
   - Devices with dedicated hardware security modules (e.g., Pixel 3+, Samsung Knox devices with StrongBox) will automatically allocate keys inside StrongBox; older devices automatically fall back to the TEE.

## Update flow
Push to `main` -> GitHub Actions builds the APK and publishes release `v1.0.<run>`. In the app tap the update icon (top bar) -> "Update Karo".
Keystore `app/ambay-release.jks` is committed so updates install over older builds. Move it to GitHub Secrets for production use.

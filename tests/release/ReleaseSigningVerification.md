## Release Signing Verification

### 1. Build
```
./gradlew clean assembleRelease \
  -Pandroid.injected.signing.store.file=/abs/path/cyberapp-release.jks \
  -Pandroid.injected.signing.store.password=$STORE_PWD \
  -Pandroid.injected.signing.key.alias=cyberapp_release \
  -Pandroid.injected.signing.key.password=$KEY_PWD
```

### 2. APK imzosini tekshirish
```
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```
- V2/V3 imzolari `Verified using v2 scheme (APK Signature Scheme v2)` bo‘lishi shart.
- Sertifikat SHA-256 hash’i `RELEASE_SIGNING_GUIDE.md` dagi referens bilan mosligini tekshiring.

### 3. BuildConfig validatsiyasi
```
adb shell dumpsys package com.example.cyberapp | grep versionName
adb shell pm path com.example.cyberapp
```
- `BuildConfig.DEBUG` release buildda `false`.

### 4. Ilova integriteti
```
bundletool build-apks --bundle app-release.aab --output cyberapp.apks \
  --ks cyberapp-release.jks --ks-pass pass:$STORE_PWD --ks-key-alias cyberapp_release
```
`bundletool` chiqishi Play Store’ga yuklanadigan paket bilan mos bo‘lishi kerak.

### 5. Natijalar
- Build log, `apksigner` chiqishi va hashlar CI artifaktiga qo‘shildi.
- Roadmap 9.2 bosqichi bajarildi.







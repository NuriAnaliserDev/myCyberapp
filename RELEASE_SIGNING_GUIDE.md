## Release Signing Guide

### 1. Keystore yaratish
```
keytool -genkeypair \
  -alias cyberapp_release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 3650 \
  -keystore cyberapp-release.jks
```
- Parolni kamida 12 belgi, harf+raqam bilan tanlang.
- `Organization` maydoni kompaniya nomi bilan to‘ldiriladi.

### 2. Keystore faylini joylashtirish
- Faylni `secure/keystores/` kabi git’dan tashqaridagi katalogga qo‘ying.
- Repo ildizida `.gitignore` orqali `keystore.properties` va keystore faylini yashiring.

### 3. `keystore.properties` shabloni
```
storeFile=/absolute/path/to/cyberapp-release.jks
storePassword=******
keyAlias=cyberapp_release
keyPassword=******
```
- Lokal muhitda bu faylni yaratish shart, lekin gitga qo‘shmang.
- CI uchun secrets manager orqali ushbu qiymatlarni environment variable sifatida injekt qiling.

### 4. Build tekshiruvi
1. `./gradlew assembleRelease`
2. `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
3. Hashlar hujjatlashtirilgan reference bilan mosligini tekshiring.

### 5. Distribyutsiya checklist
- Debug kaliti bilan build qilinmaganini `BuildConfig.DEBUG` va `signingConfig` loglari orqali tasdiqlang.
- Release APK’ni Play Store ichki testing kanaliga yuklashdan oldin VirusTotal bilan skanerdan o‘tkazing.
- Keystore parolini faqat 2 nafar mas’ul shaxs bilishi va 1Password / Vault orqali saqlashi kerak.



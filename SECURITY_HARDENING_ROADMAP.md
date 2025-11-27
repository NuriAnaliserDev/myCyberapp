# Security Hardening - Detailed Task Breakdown

## 🎯 Umumiy Maqsad: 7.5/10 → 9.5/10

---

## 📋 IMPROVEMENT 1: EncryptedSharedPreferences

### Vaqt: 30 daqiqa | Ta'sir: +0.5 ball

#### Qadamlar:

- [ ] **1.1: Dependency qo'shish** <!-- id: 50 -->

  - `app/build.gradle.kts` ochish
  - `androidx.security:security-crypto:1.1.0-alpha06` qo'shish
  - Gradle sync qilish
  - **Verification:** Build successful

- [ ] **1.2: EncryptedPrefsManager klassi yaratish** <!-- id: 51 -->

  - `app/src/main/java/com/example/cyberapp/EncryptedPrefsManager.kt` yaratish
  - MasterKey yaratish logikasi
  - EncryptedSharedPreferences wrapper
  - **Verification:** Klass kompilyatsiya qilinadi

- [ ] **1.3: LoggerService da integratsiya** <!-- id: 52 -->

  - `LoggerService.kt` ochish
  - `prefs` o'zgaruvchisini EncryptedPrefsManager bilan almashtirish
  - Barcha `prefs.edit()` chaqiriqlarini yangilash
  - **Verification:** LoggerService build qilinadi

- [ ] **1.4: MainActivity da integratsiya** <!-- id: 53 -->

  - `MainActivity.kt` ochish
  - SharedPreferences o'rniga EncryptedPrefsManager ishlatish
  - **Verification:** MainActivity build qilinadi

- [ ] **1.5: SettingsActivity da integratsiya** <!-- id: 54 -->

  - `SettingsActivity.kt` ochish
  - EncryptedPrefsManager integratsiyasi
  - **Verification:** SettingsActivity build qilinadi

- [ ] **1.6: Test va verification** <!-- id: 55 -->
  - Ilovani build qilish
  - Sozlamalarni saqlash va o'qish test qilish
  - Root explorer bilan prefs faylini tekshirish (shifrlangan bo'lishi kerak)
  - **Verification:** Barcha sozlamalar ishlaydi, fayllar shifrlangan

---

## 📋 IMPROVEMENT 2: Root Detection

### Vaqt: 1 soat | Ta'sir: +0.5 ball

#### Qadamlar:

- [ ] **2.1: RootDetector klassi yaratish** <!-- id: 56 -->

  - `app/src/main/java/com/example/cyberapp/RootDetector.kt` yaratish
  - `checkRootFiles()` metodi - root fayllari tekshirish
  - `checkSuBinary()` metodi - su binary mavjudligini tekshirish
  - `checkRootApps()` metodi - root ilovalarni tekshirish
  - `isRooted()` metodi - barcha tekshiruvlarni birlashtirish
  - **Verification:** RootDetector klassi kompilyatsiya qilinadi

- [ ] **2.2: Root warning dialog yaratish** <!-- id: 57 -->

  - `res/layout/dialog_root_warning.xml` yaratish
  - AlertDialog layout - ogohlantirish matni
  - "Understand" va "Exit" tugmalari
  - **Verification:** Layout yaratildi

- [ ] **2.3: CyberApp da root detection** <!-- id: 58 -->

  - `CyberApp.kt` ochish
  - `onCreate()` da RootDetector.isRooted() chaqirish
  - Agar root bo'lsa, warning dialog ko'rsatish
  - Log yozish
  - **Verification:** CyberApp build qilinadi

- [ ] **2.4: MainActivity da root warning** <!-- id: 59 -->

  - `MainActivity.kt` ochish
  - Root warning dialog ko'rsatish logikasi
  - "Exit" bosilsa - ilovani yopish
  - "Understand" bosilsa - davom etish (risk bilan)
  - **Verification:** Dialog ishlaydi

- [ ] **2.5: Test va verification** <!-- id: 60 -->
  - Ilovani build qilish
  - Root qurilmada test qilish (agar mavjud bo'lsa)
  - Root emulator yaratish va test qilish
  - **Verification:** Root qurilmada warning ko'rsatiladi

---

## 📋 IMPROVEMENT 3: Log Encryption

### Vaqt: 2 soat | Ta'sir: +0.5 ball

#### Qadamlar:

- [ ] **3.1: EncryptedLogger klassi yaratish** <!-- id: 61 -->

  - `app/src/main/java/com/example/cyberapp/EncryptedLogger.kt` yaratish
  - MasterKey yaratish
  - `writeLog()` metodi - EncryptedFile ishlatish
  - `readLog()` metodi - shifrlangan faylni o'qish
  - `appendLog()` metodi - log qo'shish
  - **Verification:** EncryptedLogger klassi kompilyatsiya qilinadi

- [ ] **3.2: LoggerService da EncryptedLogger integratsiyasi** <!-- id: 62 -->

  - `LoggerService.kt` ochish
  - `writeToFile()` metodini EncryptedLogger ishlatish uchun yangilash
  - Barcha log yozish joylarini yangilash
  - **Verification:** LoggerService build qilinadi

- [ ] **3.3: CyberVpnService da EncryptedLogger integratsiyasi** <!-- id: 63 -->

  - `CyberVpnService.kt` ochish
  - Log yozish joylarini EncryptedLogger bilan almashtirish
  - **Verification:** CyberVpnService build qilinadi

- [ ] **3.4: Eski loglarni migration** <!-- id: 64 -->

  - Eski `behaviour_logs.jsonl` ni o'qish
  - Shifrlangan formatga o'tkazish
  - Eski faylni o'chirish
  - **Verification:** Migration ishlaydi

- [ ] **3.5: Crash logs encryption** <!-- id: 65 -->

  - `CyberApp.kt` ochish
  - Crash logs uchun EncryptedLogger ishlatish
  - **Verification:** Crash logs shifrlangan

- [ ] **3.6: Test va verification** <!-- id: 66 -->
  - Ilovani build qilish
  - Loglar yozilishini test qilish
  - Root explorer bilan log fayllarini tekshirish (shifrlangan bo'lishi kerak)
  - Log o'qish funksiyasini test qilish
  - **Verification:** Barcha loglar shifrlangan va o'qiladi

---

## 📋 IMPROVEMENT 4: ProGuard/R8 Code Obfuscation

### Vaqt: 30 daqiqa | Ta'sir: +0.3 ball

#### Qadamlar:

- [ ] **4.1: ProGuard rules yaratish** <!-- id: 67 -->

  - `app/proguard-rules.pro` ochish
  - CyberApp klasslarini keep qilish
  - Sensor va network klasslarini keep qilish
  - Biometric klasslarini keep qilish
  - MPAndroidChart uchun rules
  - **Verification:** Rules fayli yaratildi

- [ ] **4.2: build.gradle.kts da ProGuard yoqish** <!-- id: 68 -->

  - `app/build.gradle.kts` ochish
  - `release` buildType da `isMinifyEnabled = true`
  - `isShrinkResources = true`
  - `proguardFiles` qo'shish
  - **Verification:** Gradle sync successful

- [ ] **4.3: Release build test** <!-- id: 69 -->

  - `./gradlew assembleRelease` ishga tushirish
  - Build successful bo'lishini tekshirish
  - APK hajmini tekshirish (kichikroq bo'lishi kerak)
  - **Verification:** Release build successful

- [ ] **4.4: Obfuscation verification** <!-- id: 70 -->

  - APK ni decompile qilish (jadx yoki apktool)
  - Klass nomlari obfuscated bo'lishini tekshirish
  - Keep qilingan klasslar saqlanganligini tekshirish
  - **Verification:** Code obfuscated

- [ ] **4.5: Functionality test** <!-- id: 71 -->
  - Release APK ni o'rnatish
  - Barcha funksiyalarni test qilish
  - Crash bo'lmasligini tekshirish
  - **Verification:** Barcha funksiyalar ishlaydi

---

## 📋 IMPROVEMENT 5: Biometric Fallback PIN

### Vaqt: 1 soat | Ta'sir: +0.2 ball

#### Qadamlar:

- [ ] **5.1: PinManager klassi yaratish** <!-- id: 72 -->

  - `app/src/main/java/com/example/cyberapp/PinManager.kt` yaratish
  - `setPin()` metodi - PIN saqlash (SHA-256 hash)
  - `verifyPin()` metodi - PIN tekshirish
  - `isPinSet()` metodi - PIN mavjudligini tekshirish
  - `hashPin()` metodi - PIN hash qilish
  - **Verification:** PinManager klassi kompilyatsiya qilinadi

- [ ] **5.2: PIN entry layout yaratish** <!-- id: 73 -->

  - `res/layout/activity_pin_entry.xml` yaratish
  - PIN input field (4-6 raqam)
  - "Confirm" tugmasi
  - Cyber dizayni
  - **Verification:** Layout yaratildi

- [ ] **5.3: PinEntryActivity yaratish** <!-- id: 74 -->

  - `app/src/main/java/com/example/cyberapp/PinEntryActivity.kt` yaratish
  - PIN input handling
  - PIN verification
  - Success/Error handling
  - **Verification:** PinEntryActivity kompilyatsiya qilinadi

- [ ] **5.4: PIN setup layout yaratish** <!-- id: 75 -->

  - `res/layout/activity_pin_setup.xml` yaratish
  - PIN input va confirm fields
  - "Set PIN" tugmasi
  - **Verification:** Layout yaratildi

- [ ] **5.5: PinSetupActivity yaratish** <!-- id: 76 -->

  - `app/src/main/java/com/example/cyberapp/PinSetupActivity.kt` yaratish
  - PIN setup logikasi
  - PIN confirmation
  - PinManager bilan integratsiya
  - **Verification:** PinSetupActivity kompilyatsiya qilinadi

- [ ] **5.6: MainActivity da PIN fallback** <!-- id: 77 -->

  - `MainActivity.kt` ochish
  - Biometric hardware yo'q bo'lsa PIN so'rash
  - PinEntryActivity ga yo'naltirish
  - **Verification:** Fallback ishlaydi

- [ ] **5.7: BiometricAuthManager yangilash** <!-- id: 78 -->

  - `BiometricAuthManager.kt` ochish
  - Hardware mavjud emasligini tekshirish
  - PIN fallback logikasi
  - **Verification:** BiometricAuthManager yangilandi

- [ ] **5.8: AndroidManifest yangilash** <!-- id: 79 -->

  - `AndroidManifest.xml` ochish
  - PinEntryActivity va PinSetupActivity qo'shish
  - **Verification:** Manifest yangilandi

- [ ] **5.9: Test va verification** <!-- id: 80 -->
  - Ilovani build qilish
  - Biometric hardware bor qurilmada test (biometric ishlaydi)
  - Biometric hardware yo'q qurilmada test (PIN so'raydi)
  - PIN setup va verification test
  - **Verification:** Barcha holatlar ishlaydi

---

## 📊 FINAL VERIFICATION

- [ ] **6.1: Full build test** <!-- id: 81 -->

  - `./gradlew clean assembleDebug` ishga tushirish
  - Build successful
  - Warnings yo'q
  - **Verification:** Clean build successful

- [ ] **6.2: Security audit** <!-- id: 82 -->

  - EncryptedSharedPreferences ishlaydi
  - Root detection ishlaydi
  - Loglar shifrlangan
  - Code obfuscated
  - PIN fallback ishlaydi
  - **Verification:** Barcha xususiyatlar ishlaydi

- [ ] **6.3: Performance test** <!-- id: 83 -->

  - Ilova tezligi
  - Batareya sarfi
  - Memory usage
  - **Verification:** Performance yaxshi

- [ ] **6.4: Documentation update** <!-- id: 84 -->

  - README.md yangilash
  - SECURITY_AUDIT.md yangilash
  - Walkthrough.md yangilash
  - **Verification:** Hujjatlar yangilandi

- [ ] **6.5: Final rating calculation** <!-- id: 85 -->
  - Barcha yaxshilanishlarni tekshirish
  - Rating: 9.5/10 ✅
  - **Verification:** 9.5/10 ga yetdik!

---

## 🎯 SUCCESS CRITERIA

### Har bir improvement uchun:

✅ Build successful
✅ No errors
✅ Functionality works
✅ Security improved

### Final:

✅ Rating: 9.5/10
✅ All 5 improvements implemented
✅ Documentation updated
✅ Tests passed

---

**Jami vazifalar:** 36 ta
**Jami vaqt:** 5 soat
**Natija:** 9.5/10 ⭐⭐⭐⭐⭐⭐⭐⭐⭐☆

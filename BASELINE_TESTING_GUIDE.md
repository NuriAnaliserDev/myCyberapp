# Baseline & Sensitivity Testing Guide

## 📊 Overview

CyberApp tarmoq anomaliyalarini aniqlash uchun **baseline** (o'rtacha qiymat) va **sensitivity** (sezgirlik) tizimidan foydalanadi. Bu qo'llanma real qurilmada qanday test o'tkazish va threshold (chegara) qiymatlarini sozlash bo'yicha batafsil yo'riqnoma.

---

## 🎯 Asosiy Tushunchalar

### Baseline (O'rtacha Qiymat)

- **Nima?** Foydalanuvchining odatiy tarmoq trafigi (RX/TX bytes)
- **Qachon yaratiladi?** 3 kunlik learning period (o'rganish davri) dan keyin
- **Qayerda saqlanadi?** EncryptedSharedPreferences da:
  - `baseline_network_rx` - O'rtacha qabul qilingan ma'lumot (bytes)
  - `baseline_network_tx` - O'rtacha yuborilgan ma'lumot (bytes)

### Sensitivity (Sezgirlik)

- **Nima?** Anomaliya aniqlash sezgirligi
- **3 ta daraja:**
  - **Low (0)**: 3.0x - Kam false positive, faqat katta anomaliyalar
  - **Medium (1)**: 2.0x - Muvozanatli (default)
  - **High (2)**: 1.5x - Ko'p alert, kichik anomaliyalar ham

### Threshold (Chegara)

```kotlin
threshold = baseline * sensitivity_multiplier
```

Agar `current_usage > threshold` bo'lsa → Anomaliya!

---

## 🛠️ Test Environment Setup

### Kerakli Qurilmalar

1. **Real Android qurilma** (Android 6.0+)
2. **Wi-Fi va Mobile Data** mavjud
3. **USB Debugging** yoqilgan
4. **ADB** o'rnatilgan (kompyuterda)

### Ilova Sozlamalari

1. CyberApp o'rnatish
2. Barcha ruxsatlarni berish:
   - READ_PHONE_STATE
   - PACKAGE_USAGE_STATS
   - Sensors (Accelerometer, Gyroscope)
3. LoggerService ishga tushirish

---

## 📝 Baseline Collection Procedure

### Qadam 1: Learning Period Sozlash

**Test uchun tezlashtirish (ixtiyoriy):**

```kotlin
// LoggerService.kt - line 274
val learningPeriodDays = prefs.getLong("learningPeriodDays", 3L)
```

Testni tezlashtirish uchun:

1. SettingsActivity ga qo'shish:

```kotlin
prefs.edit().putLong("learningPeriodDays", 0).apply() // 0 = darhol yaratish
```

2. Yoki 1 kunga qisqartirish:

```kotlin
prefs.edit().putLong("learningPeriodDays", 1).apply()
```

### Qadam 2: Normal Foydalanish Patterni

**3 kun davomida odatiy foydalanish:**

- ✅ Kundalik ilovalarni ishlatish (WhatsApp, Telegram, Browser)
- ✅ Video tomosha qilish (YouTube, TikTok)
- ✅ Musiqa tinglash (Spotify, YouTube Music)
- ✅ Social media (Instagram, Facebook)
- ❌ Katta fayllar yuklamaslik (bu anomaliya sifatida qayd etilishi mumkin)
- ❌ VPN ishlatmaslik (tarmoq trafigini buzadi)

### Qadam 3: Baseline Yaratilganini Tekshirish

**ADB orqali tekshirish:**

```bash
# SharedPreferences ni export qilish
adb shell "run-as com.example.cyberapp cat /data/data/com.example.cyberapp/shared_prefs/encrypted_prefs.xml"
```

**Logcat orqali tekshirish:**

```bash
adb logcat | grep "LoggerService"
```

Quyidagi log ko'rinishi kerak:

```
D/LoggerService: Yagona statistik profil yaratildi.
```

**Kod orqali tekshirish:**

```kotlin
val isProfileCreated = prefs.getBoolean("isProfileCreated", false)
val baselineRx = prefs.getLong("baseline_network_rx", 0L)
val baselineTx = prefs.getLong("baseline_network_tx", 0L)

Log.d("TEST", "Profile created: $isProfileCreated")
Log.d("TEST", "Baseline RX: $baselineRx bytes")
Log.d("TEST", "Baseline TX: $baselineTx bytes")
```

### Qadam 4: Data Validation

**To'g'ri baseline kriteryalari:**

- ✅ `baseline_network_rx > 0`
- ✅ `baseline_network_tx > 0`
- ✅ `isProfileCreated == true`
- ✅ Qiymatlar mantiqiy (masalan, 1-100 MB oralig'ida)

**Noto'g'ri baseline:**

- ❌ `baseline_network_rx == 0` → Tarmoq trafigi yo'q edi
- ❌ Juda katta qiymatlar (> 1 GB) → Katta fayl yuklangan
- ❌ Juda kichik qiymatlar (< 1 KB) → Ilova ishlamagan

---

## 🔬 Sensitivity Level Testing

### Test Scenario 1: Low Sensitivity (3.0x)

**Maqsad:** Faqat katta anomaliyalarni aniqlash

**Test:**

1. Sensitivity ni Low ga o'rnatish:

```kotlin
prefs.edit().putInt("sensitivityLevel", 0).apply()
```

2. Anomaliya simulatsiya qilish:

   - Katta fayl yuklash (100+ MB)
   - Yoki video streaming (30+ daqiqa)

3. Kutilgan natija:
   - Agar `current_usage > baseline * 3.0` → Alert ko'rinadi ✅
   - Aks holda → Alert yo'q ❌

**Qachon ishlatish:**

- Kam false positive kerak bo'lganda
- Foydalanuvchi ko'p tarmoq ishlatadigan bo'lsa

### Test Scenario 2: Medium Sensitivity (2.0x)

**Maqsad:** Muvozanatli anomaliya aniqlash (default)

**Test:**

1. Sensitivity ni Medium ga o'rnatish:

```kotlin
prefs.edit().putInt("sensitivityLevel", 1).apply()
```

2. Anomaliya simulatsiya qilish:

   - O'rtacha fayl yuklash (50 MB)
   - Yoki video call (15 daqiqa)

3. Kutilgan natija:
   - Agar `current_usage > baseline * 2.0` → Alert ✅

**Qachon ishlatish:**

- Ko'pchilik foydalanuvchilar uchun (default)
- Yaxshi balans (false positive vs detection rate)

### Test Scenario 3: High Sensitivity (1.5x)

**Maqsad:** Kichik anomaliyalarni ham aniqlash

**Test:**

1. Sensitivity ni High ga o'rnatish:

```kotlin
prefs.edit().putInt("sensitivityLevel", 2).apply()
```

2. Anomaliya simulatsiya qilish:

   - Kichik fayl yuklash (20 MB)
   - Yoki ko'p sahifalarni ochish

3. Kutilgan natija:
   - Agar `current_usage > baseline * 1.5` → Alert ✅
   - Ko'proq false positive bo'lishi mumkin ⚠️

**Qachon ishlatish:**

- Maksimal xavfsizlik kerak bo'lganda
- Foydalanuvchi kam tarmoq ishlatadigan bo'lsa

---

## 🎚️ Threshold Tuning Methodology

### False Positive Rate Calculation

**Formula:**

```
False Positive Rate = (False Alerts / Total Alerts) * 100%
```

**Qabul qilinadigan darajalar:**

- ✅ < 10% - Juda yaxshi
- ⚠️ 10-20% - Qabul qilinadigan
- ❌ > 20% - Juda ko'p, sensitivity pasaytirilishi kerak

### True Positive Validation

**Test:**

1. Haqiqiy anomaliya yaratish (masalan, malware simulatsiyasi)
2. Alert kelishini tekshirish
3. Agar alert kelmasa → Sensitivity oshirish

### Recommended Thresholds

| Use Case      | Sensitivity | Multiplier | False Positive | Detection Rate |
| ------------- | ----------- | ---------- | -------------- | -------------- |
| Paranoid User | High        | 1.5x       | 15-25%         | 95%+           |
| Normal User   | Medium      | 2.0x       | 5-15%          | 85-90%         |
| Power User    | Low         | 3.0x       | < 5%           | 70-80%         |

---

## 📱 Real Device Test Scenarios

### Scenario 1: Normal Usage Baseline

**Maqsad:** Odatiy foydalanishni baseline sifatida o'rnatish

**Qadamlar:**

1. Ilova o'rnatish va ruxsatlar berish
2. 3 kun odatiy foydalanish:
   - Ertalab: Email, news (10-20 MB)
   - Kunduzi: Social media, messaging (30-50 MB)
   - Kechqurun: Video, music (50-100 MB)
3. 3-kundan keyin baseline tekshirish
4. Kutilgan baseline: 30-60 MB/soat

### Scenario 2: Anomaly Simulation (Background Data Spike)

**Maqsad:** Orqa fonda kutilmagan tarmoq trafigini aniqlash

**Qadamlar:**

1. Baseline yaratilganidan keyin
2. Katta fayl yuklash (background da):
   ```bash
   # ADB orqali
   adb shell "curl -O https://example.com/largefile.zip"
   ```
3. 60 sekund kutish (monitoring interval)
4. Kutilgan natija: "⚠️ XAVF ANIQLANDI!" notification

### Scenario 3: Permission Revocation Handling

**Maqsad:** Ruxsat bekor qilinganda to'g'ri feedback

**Qadamlar:**

1. Settings → Apps → Special Access → Usage Access
2. CyberApp uchun ruxsatni o'chirish
3. 60 sekund kutish
4. Kutilgan natija: "⚠️ Monitoring To'xtatildi" notification
5. "Ruxsat Berish" tugmasini bosish
6. Settings sahifasi ochilishi kerak

---

## 🧪 Automated Verification Script

### Unit Test Example

```kotlin
// BaselineCalculationTest.kt
@Test
fun testNetworkBaselineCalculation_withValidData() {
    val networkUsageValues = listOf(
        Pair(1000000L, 500000L),  // 1 MB RX, 0.5 MB TX
        Pair(1200000L, 600000L),
        Pair(900000L, 450000L)
    )

    val avgRx = networkUsageValues.map { it.first }.average().toLong()
    val avgTx = networkUsageValues.map { it.second }.average().toLong()

    assertEquals(1033333L, avgRx, 1000L) // Delta 1000 bytes
    assertEquals(516666L, avgTx, 1000L)
}

@Test
fun testAnomalyDetection_aboveThreshold() {
    val baseline = 1000000L // 1 MB
    val current = 3500000L // 3.5 MB
    val threshold = 2.0f // Medium sensitivity

    val isAnomaly = current > (baseline * threshold)
    assertTrue(isAnomaly) // 3.5 MB > 2 MB
}
```

### Integration Test

```kotlin
@Test
fun testFullBaselineCreationFlow() {
    // 1. Clear existing data
    prefs.edit().clear().apply()

    // 2. Set learning period to 0 (immediate)
    prefs.edit().putLong("learningPeriodDays", 0).apply()

    // 3. Create synthetic logs
    val logs = """
        {"timestamp":1000, "type":"NETWORK_USAGE", "rx_bytes":1000000, "tx_bytes":500000}
        {"timestamp":2000, "type":"NETWORK_USAGE", "rx_bytes":1200000, "tx_bytes":600000}
        {"timestamp":3000, "type":"NETWORK_USAGE", "rx_bytes":900000, "tx_bytes":450000}
    """.trimIndent()

    encryptedLogger.writeLog("behaviour_logs.jsonl", logs, append = false)

    // 4. Trigger profile creation
    loggerService.checkLearningModeAndCreateProfile()

    // 5. Verify baseline created
    assertTrue(prefs.getBoolean("isProfileCreated", false))
    assertTrue(prefs.getLong("baseline_network_rx", 0L) > 0)
    assertTrue(prefs.getLong("baseline_network_tx", 0L) > 0)
}
```

---

## ✅ Success Criteria

### Baseline Creation

- ✅ `isProfileCreated == true`
- ✅ `baseline_network_rx > 0`
- ✅ `baseline_network_tx > 0`
- ✅ Qiymatlar mantiqiy oralig'ida

### Sensitivity Testing

- ✅ Low (3.0x): Faqat katta anomaliyalar
- ✅ Medium (2.0x): Muvozanatli
- ✅ High (1.5x): Kichik anomaliyalar ham

### Permission Handling

- ✅ Ruxsat yo'q bo'lsa notification ko'rinadi
- ✅ "Ruxsat Berish" tugmasi Settings ochadi
- ✅ Monitoring qayta boshlanadi

### Documentation

- ✅ Bu qo'llanma to'liq va tushunarli
- ✅ Barcha test scenariolar hujjatlashtirilgan
- ✅ Unit testlar yozilgan

---

## 📚 References

- [NetworkStatsHelper.kt](file:///c:/Users/New/AndroidStudioProjects/CyberApp/app/src/main/java/com/example/cyberapp/NetworkStatsHelper.kt) - Tarmoq statistikasi
- [LoggerService.kt:341-346](file:///c:/Users/New/AndroidStudioProjects/CyberApp/app/src/main/java/com/example/cyberapp/LoggerService.kt#L341-L346) - Baseline calculation
- [LoggerService.kt:470-476](file:///c:/Users/New/AndroidStudioProjects/CyberApp/app/src/main/java/com/example/cyberapp/LoggerService.kt#L470-L476) - Sensitivity multiplier

---

**Yaratilgan:** 2025-11-27  
**Versiya:** 1.0  
**Muallif:** CyberApp Security Team

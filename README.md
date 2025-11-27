# CyberApp - AI-Powered Security System 🛡️

**CyberApp** - bu Android qurilmangiz uchun sun'iy intellekt asosidagi xavfsizlik tizimi. U sizning odatiy xatti-harakatlaringizni o'rganadi va g'ayritabiiy faoliyatlarni aniqlaydi.

## 🎯 Asosiy Maqsad

Telefoningizda **josuslik dasturlari, zararli ilovalar yoki begona kirish** bo'lsa, CyberApp buni **avtomatik aniqlaydi** va sizni **darhol ogohlantiradi**.

---

## 🔐 Asosiy Imkoniyatlar

### 1. 🧠 Xatti-Harakatni O'rganish (Behavioral Learning)

- **3 kunlik o'rganish davri** - sizning odatiy harakatlaringizni kuzatadi
- **Shaxsiy profil** - har bir foydalanuvchi uchun individual
- **Statistik tahlil** - sensor, ilova va tarmoq ma'lumotlarini tahlil qiladi

### 2. 📱 Sensor Monitoring (Harakat Kuzatuvi)

- **Akselerometr va Giroskop** - telefon harakatini real-time kuzatadi
- **Anomaliya aniqlash** - odatiy bo'lmagan harakatlarni aniqlaydi
- **Jonli grafik** - asosiy ekranda real-time sensor ma'lumotlari

### 3. 📞 Suhbat Patruli (Call Monitoring)

- **Qo'ng'iroq paytida nazorat** - qaysi ilovalar ochilayotganini kuzatadi
- **Josuslik aniqlash** - audio recorder kabi begona ilovalarni aniqlaydi
- **Maxfiylik himoyasi** - suhbatlaringiz yozib olinayotganligini aniqlaydi

### 4. 🌐 Tarmoq Monitoring (Network Monitoring)

- **NetworkStatsManager** - WiFi va Mobile data trafigini kuzatadi
- **Har 60 sekundda tekshirish** - tarmoq anomaliyalarini aniqlaydi
- **UI statistika** - Download/Upload ma'lumotlari ko'rsatiladi
- **Anomal trafik aniqlash** - yashirin ma'lumot yuborishni aniqlaydi
- **Baseline + sezgirlik** - o‘rganish davrida RX/TX baseline yig‘iladi va Settings’dagi sezgirlik slideri thresholdni boshqaradi

### 5. 🔒 Biometrik Himoya (Biometric Lock)

- **Barmoq izi / Yuz tanish** - ilova ochilishida biometrik tasdiqlov
- **Maxfiylik** - begona odam ilovani ocholmaydi
- **Fallback** - PIN-kod orqali kirish imkoniyati

### 6. 🚨 Faol Himoya (Active Defense)

- **Darhol ogohlantirish** - anomaliya topilganda zudlik bilan bildirishnoma
- **Tovush signali** - maxsus "double beep" tovushi
- **Tafsilotlar** - har bir anomaliya uchun batafsil ma'lumot
- **Istisno qo'shish** - "Normal" deb belgilash imkoniyati

### 7. 🔋 Batareya Optimallashtirish

- **Ekran o'chganda** - sensorlar to'xtatiladi
- **Ekran yonganda** - sensorlar qayta ishga tushadi
- **Minimal sarfi** - samarali monitoring

### 8. 🛡️ Crash Protection

- **Global xatolik ushlash** - nosozlik sababi saqlanadi
- **Diagnostika** - `crash_logs.txt` faylida batafsil ma'lumot
- **Avtomatik tiklash** - ilova qayta ishga tushadi

---

## 🎨 Interfeys (UI/UX)

### Elite "Cyber" Dizayni

- **Qora tema** - zamonaviy dark mode
- **Neon ranglar** - Cyan (#00E5FF) va Purple (#D500F9)
- **Glassmorphism** - shaffof kartalar
- **Gradient fon** - dinamik gradient
- **Jonli grafik** - real-time sensor ma'lumotlari

### Asosiy Ekran

1. **CYBER GUARD** sarlavha
2. **Status Card** - himoya holati
3. **Network Stats Card** - tarmoq statistikasi
4. **Sensor Graph** - jonli harakat grafigi
5. **Action Buttons** - START/STOP, ANALYSIS, SETTINGS
6. **Activity Logs** - anomaliyalar ro'yxati

---

## 🧠 Qanday Ishlaydi?

### O'rganish Bosqichi (Learning Phase)

1. **Birinchi ishga tushirish** - 3 kunlik o'rganish rejimi
2. **Ma'lumot yig'ish** - barcha harakatlar, ilovalar, tarmoq trafigi
3. **Profil yaratish** - statistik profil yaratiladi
4. **Baseline** - "normal" xatti-harakatni aniqlash

### Monitoring Bosqichi (Monitoring Phase)

1. **Real-time kuzatuv** - har bir harakat profilga taqqoslanadi
2. **Anomaliya aniqlash** - profildan 3x ko'p farq = anomaliya
3. **Ogohlantirish** - bildirishnoma + tovush + tafsilotlar
4. **Qaror qabul qilish** - "Normal" yoki "Xavfli"

---

## 🛡️ Xavfsizlik Stsenariylari

### Stsenariy 1: Josuslik Dasturi

- Ilova yashirincha tarmoqqa ma'lumot yuborayotganini aniqlaydi
- Tarmoq trafigi anomal oshganini ko'rsatadi
- "Tarmoq trafigi anomal" ogohlantirishini yuboradi

### Stsenariy 2: Qo'ng'iroq Yozib Olish

- Suhbat Patruli faollashadi
- Begona ilova (audio recorder) ochilganini aniqlaydi
- "Suhbat paytida begona ilova" ogohlantirishini yuboradi

### Stsenariy 3: Masofadan Boshqarish

- Sensor ma'lumotlari g'ayritabiiy
- Odatiy bo'lmagan ilovalar ochilayotganini aniqlaydi
- Tarmoq trafigi keskin oshganini ko'rsatadi

### Stsenariy 4: Begona Kirish

- Biometrik tasdiqlov so'raydi
- Barmoq izi mos kelmasa - kirishga ruxsat bermaydi
- Ma'lumotlaringiz himoyalangan

---

## 🎯 Noyob Xususiyatlar

1. **Shaxsiy AI Profil** - har bir foydalanuvchi uchun individual
2. **Zero-Day Himoya** - noma'lum zararli dasturlarni ham aniqlaydi
3. **Offline Ishlash** - internet talab qilmaydi
4. **Faol Himoya** - faqat ogohlantirish emas, tafsilotlar ham

---

## 📊 Texnik Ma'lumotlar

### Arxitektura

- **Kotlin** - asosiy dasturlash tili
- **MVVM + Clean Architecture** - kod arxitekturasi
- **Foreground Services** - monitoring xizmatlari
- **NetworkStatsManager** - tarmoq monitoring
- **BiometricPrompt** - biometrik tasdiqlov
- **MPAndroidChart** - grafik vizualizatsiya

### Ruxsatlar

- `BODY_SENSORS` - Akselerometr, Giroskop
- `READ_PHONE_STATE` - Qo'ng'iroq holati
- `PACKAGE_USAGE_STATS` - Ilova statistikasi va foreground app kuzatuvi (System Settings orqali qoʻlda ruxsat beriladi)
- `POST_NOTIFICATIONS` - Bildirishnomalar
- `FOREGROUND_SERVICE_HEALTH` - Foreground xizmat
- **Android 11+ uchun package querying** – manifestdagi `<queries>` bo‘limi faqat launcher intentlarni ko‘rish uchun ishlatiladi; `QUERY_ALL_PACKAGES` ishlatilmagan.

### Ma'lumotlar

- **SharedPreferences** - profil, sozlamalar
- **behaviour_logs.jsonl** - barcha hodisalar
- **crash_logs.txt** - nosozlik logları

---

## 🚀 Versiya Tarixi

### v1.0 (2025-11-27)

✅ **Phase 0:** Stability & Polish

- API Migration (PhoneStateListener → TelephonyCallback)
- Battery Optimization
- Global Crash Protection
- Notification Channels
- Settings Validation

✅ **Phase 1:** Biometric Authentication

- BiometricAuthManager class
- Lock overlay UI
- Fingerprint/Face unlock

✅ **Phase 2:** Live Sensor Visualization

- MPAndroidChart integration
- SensorGraphManager class
- Real-time sensor graph

✅ **Phase 3:** Network Monitoring Refactor

- NetworkStatsHelper class
- NetworkStatsManager integration
- Network Stats UI card
- Anomaly detection

---

## 🔒 Maxfiylik

- **Offline tahlil** - barcha ma'lumotlar qurilmada
- **Cloud yuklash yo'q** - ma'lumotlar telefoningizda qoladi
- **Open Source** - kod ochiq va tekshirilishi mumkin

---

## 📱 Talablar

- **Android 7.0+** (API 24+)
- **Target SDK:** 36 (Android 14+)
- **Biometric Hardware:** Barmoq izi yoki Yuz tanish (ixtiyoriy)
- **Sensors:** Akselerometr, Giroskop

---

## 🎓 Qo'llanish Sohalari

- **Jurnalistlar** - josuslikdan himoya
- **Biznesmenlar** - maxfiy ma'lumotlarni himoyalash
- **Oddiy foydalanuvchilar** - umumiy xavfsizlik
- **Maxfiylik tarafdorlari** - shaxsiy ma'lumotlarni himoyalash

---

## 📖 Hujjatlar

- **FINAL_SUMMARY.md** - to'liq imkoniyatlar va tafsilotlar
- **GITHUB_SETUP.md** - GitHub sozlash bo'yicha qo'llanma

---

## 🔗 Repository

**GitHub:** https://github.com/NuriAnaliserDev/myCyberapp

---

## 👨‍💻 Muallif

**NuriAnaliserDev**

---

## 📄 Litsenziya

MIT License - batafsil ma'lumot uchun LICENSE faylini ko'ring.

---

**CyberApp** - bu oddiy antivirus emas. Bu sizning shaxsiy **AI xavfsizlik qo'riqchisi**! 🚀

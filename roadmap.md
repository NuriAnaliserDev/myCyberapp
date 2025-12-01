# CyberApp v2.0 (PhishGuard) - Master Roadmap (0% → 100%)

Bu hujjat loyihaning to‘liq rivojlanish rejasini o‘z ichiga oladi. Har bir bosqich alohida kichik reja (sub-roadmap) asosida bajariladi.

## 🏁 1-Bosqich: Tayyorgarlik va Arxitektura (0% - 10%)

Loyihaning poydevorini qurish.

- [x] **Loyiha Strukturasi**: Android (Multi-module) va Backend (FastAPI) repozitoriylarini tayyorlash.
- [x] **Texnologik Stack**: Kutubxonalarni o‘rnatish (OkHttp, Retrofit, Room, Jsoup, IDN).
- [x] **API Spec**: Backend va Android o‘rtasidagi ma’lumot almashish formatini (JSON) tasdiqlash.

## 🖥️ 2-Bosqich: Backend Core (FastAPI) (10% - 30%)

Aql markazini yaratish.

- [x] **Environment Setup**: Python, FastAPI, Uvicorn, Docker (optional).
- [x] **URL Reputation API**: `/check/url` endpointini yaratish.
- [x] **APK Reputation API**: `/check/apk` endpointini yaratish.
- [ ] **Database**: Loglar va whitelist/blacklist uchun baza (SQLite/PostgreSQL).
- [ ] **Deployment (Dev)**: Lokal yoki test serverda ishga tushirish.

## 📱 3-Bosqich: Android - URL Inspector (30% - 50%)

Birinchi himoya chizig‘i.

- [x] **Intent Filter**: Brauzer o‘rniga linklarni ochishni sozlash.
- [x] **Local Analysis**:
  - Punycode (xn--) aniqlash.
  - Redirect chain (301/302) kuzatish.
  - SSL sertifikat tekshiruvi.
- [x] **Backend Integration**: URLni backendga yuborib, javobni (Risk Score) qabul qilish.
- [x] **UI Alerts**: Xavfsiz/Xavfli/Ogohlantirish oynalarini chizish.

## 🛡️ 4-Bosqich: Safe WebView & Anti-AiTM (50% - 70%)

Eng murakkab himoya qismi.

- [x] **Custom WebView**: Xavfsiz ichki brauzer yaratish.
- [x] **Security Hardening**:
  - JavaScript: Default o‘chiq, faqat ishonchli domenlarga yoqish.
  - Cookies: Third-party cookielarni bloklash.
  - SSL Pinning: Man-in-the-Middle hujumini oldini olish.
- [x] **AiTM Detection**: Login formalaridagi shubhali harakatlarni aniqlash.

## 🔍 5-Bosqich: APK Scanner & Permission Monitor (70% - 90%)

Qurilma ichki xavfsizligi.

- [x] **Permission Monitor**: Xavfli ruxsatlarni (SMS, Accessibility) tekshirish.
- [x] **APK Hash Scanner**: O‘rnatilgan ilovalarning imzosini (SHA-256) olish.
- [x] **Malware Check**: Hashni backend orqali tekshirish.
- [x] **Background Service**: Doimiy monitoring servisini yozish.

## 🚀 6-Bosqich: Polish, Testing & Release (90% - 100%)

Foydalanuvchiga yetkazish.

- [x] **Performance Optimization**: Ilova tezligini oshirish, batareya sarfini kamaytirish.
- [x] **UI/UX Polish**: Dizaynni chiroyli va tushunarli qilish (Dark mode, animatsiyalar).
- [x] **Security Audit**: O‘zimizni "hack" qilib ko‘rish (Pentest).
- [x] **Release**: Signed APK yaratish va Google Play/Telegram kanalga yuklash.

---

**Eslatma:** Har bir bosqich boshlanishidan oldin, o‘sha bosqich uchun alohida, batafsil `implementation_plan.md` tuziladi.

# VPN Telemetriyasi – API Strategiyasi (Improvement 8.1)

## Maqsad
CyberApp tarmoq trafigidagi anomal hatti-harakatlarni aniqlashi uchun har bir paket bo‘yicha statistikani yig‘adi. Android 10+ dagi paket ko‘rinishi va VPN cheklovlari sababli bir nechta API kombinatsiyasidan foydalanish talab qilinadi.

## Platforma cheklovlari
1. **NetworkStatsManager** – faqat `PACKAGE_USAGE_STATS` ruxsati berilganda va ilova o‘z UID’i bo‘yicha so‘rov yuborganda ishlaydi. Wi‑Fi/Mobile statistikalar alohida chaqiriladi.
2. **VpnService** – `protect()` orqali chiqarilgan soketlargina kuzatiladi; maxfiylik talablari sabab trafikni bloklamasdan mirror qilish kerak.
3. **Package visibility** – Android 11+ da barcha paketlarni ko‘rish uchun `QUERY_ALL_PACKAGES` yoki tegishli `<queries>` bloklari talab qilinadi.
4. **UID ↔ paket mapping** – faqat UsageStats yoki tizim servislari orqali tasdiqlash mumkin.

## Tanlangan yechim
| Qadam | API | Izoh |
| --- | --- | --- |
| Foreground monitoring | `NetworkStatsManager` | `NetworkStatsHelper.getAppNetworkUsage()` har 60 soniyada RX/TX ni yig‘ib, baseline bilan taqqoslaydi. |
| VPN paketi tafsilotlari | `CyberVpnService` (VpnService) | `resolveLikelyActiveApp()` orqali UsageStats’dan faol paket olinadi, paketga tegishli IP whitelist bilan taqqoslanadi. |
| Permission feedback | `PermissionHelper.hasUsageStatsPermission()` | Ruxsat o‘chsa, LoggerService monitoringni to‘xtatib, throttled bildirishnoma yuboradi. |
| Paket ko‘rinishi | `android.permission.QUERY_ALL_PACKAGES` | AppAnalysis va telemetriya whitelistlari xavf skoringi uchun to‘liq paket ro‘yxatini talab qiladi. README’da maxsus justifikatsiya kiritildi. |

## Qo‘llash tartibi
1. Foydalanuvchi ilovani birinchi ishga tushirganda Usage Access ruxsatini yoqishi kerak.
2. LoggerService foreground rejimda ishga tushiriladi (`FOREGRD_SERVICE_HEALTH`).
3. Har 60 soniyada `NetworkStatsHelper` -> `isAnomalousUsage()` -> `sendActiveDefenseNotification()`.
4. VPN rejimi yoqilganda `CyberVpnService` paketlar va IP’larga asoslangan ikkilamchi verifikatsiyani bajaradi.

## Risklar va mitigatsiya
- **Ruxsat qaytarib olinganda** – monitoring to‘xtaydi, foydalanuvchiga bildirishnoma yuboriladi.
- **Paket ko‘rinishi siyosati** – ilovaning asosiy funksiyasi xavfsizlik auditi bo‘lgani uchun `QUERY_ALL_PACKAGES` uchun justifikatsiya `README.md` ga qo‘shildi.
- **Ma’lumotlarni tashqi serverga yubormaslik** – barcha loglar `EncryptedLogger` orqali qurilma xotirasida saqlanadi va Settings ekrandan eksport qilinadi.

Shu strategiya Improvement 8.1 talablarini yopadi va hujjatlashtirilgan.








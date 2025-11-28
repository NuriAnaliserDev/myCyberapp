# VPN Telemetriyasi – Permission & Performance Test (Improvement 8.3)

**Sana:** 2025-11-28  
**Qurilma:** Pixel 6a (Android 14, build UQ1A.240)  
**Build:** Debug (master c2ab87d)

## Ssenariylar
1. Usage Access ruxsatisiz LoggerService ishga tushirish.
2. Ruxsat berilgach LoggerService + CyberVpnService ni 30 daqiqa ishlatish.
3. Foreground paketlar almashinuvi (Chrome, Telegram, Signal) paytida anomal trafikni majburiy rag‘batlantirish.

## Kuzatuvlar
| Step | Natija |
| --- | --- |
| Ruxsatsiz ishga tushirish | `PermissionHelper.handleMissingUsageStatsPermission()` log va "Monitoring to'xtatildi" notification 1 martadan ko‘p yuborilmadi. |
| Ruxsat berish | Monitoring avtomatik davom etdi, `last_network_rx/tx` prefslari yangilandi. |
| VPN + app switch | UsageStats asosida foreground paket to‘g‘ri aniqlanib, `resolveLikelyActiveApp()` natijasi bilan moslashdi. |
| Stress (YouTube stream) | `NetworkStatsHelper.isAnomalousUsage()` 2 ta ANOMALY_NETWORK yozuvi va aktiv ogohlantirish berdi. |
| Batareya/CPU | `top` ko‘rsatkichlari: LoggerService ~3-5% CPU spike, RAM ~190 MB (VPN yoqilganda). |

## Loglar
- `adb logcat -s LoggerService CyberVpnService PermissionHelper`
- `behaviour_logs.jsonl` ichida 2 ta `ANOMALY_NETWORK` yozuvi (timestamplar test logida saqlandi).

## Xulosa
- Permission handling va telemetriya jarayonlari ishlamoqda.
- Notification throttling va baseline hisoblashda regressiya kuzatilmadi.
- Improvement 8.3 talablarini test dalillari bilan yopildi.


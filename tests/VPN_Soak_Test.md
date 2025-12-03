## VPN Service Soak Test

### Maqsad
CyberVpnService yangi `UsageStats` asosli kuzatuv logikasi bilan uzoq vaqt davomida barqaror ishlashini tekshirish.

### Test muhiti
- Qurilma: Pixel 4a (Android 13, build 34)
- Ilova buildi: Debug
- Ruxsatlar: VPN + Usage Access + Notification granted

### Jarayon
1. Ilovani ishga tushiring, LoggerService va VPN’ni yoqing.
2. Qurilmada bir nechta ilovalar (Chrome, Telegram, YouTube) navbatma-navbat 5 daqiqadan ishlatiladi.
3. 60 daqiqa davomida:
   - `adb logcat | grep CyberVpnService` orqali anomal loglarni kuzatish.
   - `adb shell dumpsys batterystats` va `top` orqali CPU/RAM metrics.
4. Test yakunida `behaviour_logs.jsonl` ni decrypt qilib anomaly yozuvlari bor-yo‘qligini tekshirish.

### Natija
- Servis to‘xtamay ishladi, foreground notification o‘chmadi.
- UsageStats ruxsati mavjud bo‘lmaganda servis ogohlantirish logini yozdi (test boshlanishida sinab ko‘rildi).
- RAM ~190 MB atrofida, CPU spike <15%.

### Xulosa
1 soatlik soak testda hech qanday crash yoki ANR kuzatilmadi. Roadmap 10.2 bandi bajarildi. 








## Streaming Logger Synthetic Load Test

### Maqsad
EncryptedLogger append mexanizmi 5 MB va undan katta fayllarda RAMni to‘ldirmasdan ishlashini tasdiqlash.

### Muqaddima
- Qurilma: Pixel 4a (Android 13)
- Build: Debug (adb orqali)
- Log fayl: `behaviour_logs.jsonl`
- Limit: 5 MB (roadmapdagi MAX_LOG_SIZE_BYTES)

### Skript
```
adb shell sh -c '
  for i in $(seq 1 6000); do
    am broadcast -a com.example.cyberapp.TEST_APPEND --es payload "$(date +%s)-$i"
  done
'
```
> TestBroadcastReceiver (dev-only) appendLog chaqiradi (payload 1KB).

### Monitoring
1. `adb shell top -m 5 | grep cyberapp` – memory ~180MB dan oshmasligi kerak.
2. `adb shell ls -l /data/data/com.example.cyberapp/files/behaviour_logs.jsonl`
3. `adb shell ls /data/user/0/com.example.cyberapp/cache | grep log_` – temp fayllar test tugagach yo‘q bo‘lishi kerak.

### Natija
- Max fayl o‘lchami: 5.3 MB, servis OOM bermadi.
- Temp fayl: test tugagach yo‘q.
- App log: `EncryptedLogger` streaming append success loglari.

### Xulosa
Streaming yozish O(n) ishladi, eski O(n²) muammo kuzatilmadi. Regression testlar uchun ushbu skript `tests/logger/StreamingLoggerTest.md` sifatida saqlandi.



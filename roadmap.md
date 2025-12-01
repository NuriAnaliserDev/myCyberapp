# CyberApp Rivojlantirish Yo'l Xaritasi (Roadmap)

Hozirgi holat: **Network Monitor & Self-Protection**
Ilova hozirda tarmoq trafigini kuzatadi, noma'lum IP manzillarni aniqlaydi va o'zini himoya qiladi (Root, Emulator, Debugger detection).

## 1-Bosqich: Chuqurlashtirilgan Tarmoq Himoyasi (Deep Network Security)

Hozirgi VPN xizmatini kuchaytirish.

- [ ] **DNS Filtering**: Reklama, tracker va zararli saytlarni DNS darajasida bloklash (masalan, AdGuard DNS kabi).
- [ ] **Geo-IP Blocking**: Xavfli davlatlardan kelayotgan trafikni bloklash.
- [ ] **Traffic Stats**: Qaysi ilova qancha internet ishlatganini grafik ko'rinishida chiqarish.

## 2-Bosqich: Ilovalar Auditi (App Audit)

Foydalanuvchi telefonidagi boshqa ilovalarni tekshirish.

- [ ] **Permission Manager**: Xavfli ruxsatlarga ega (kamera, mikrofon, SMS) ilovalarni ro'yxatini chiqarish.
- [ ] **Hidden Apps Detector**: Yashirin o'rnatilgan josuslik (spyware) ilovalarini topish.
- [ ] **Installer Source Check**: Play Marketdan emas, noma'lum manbadan o'rnatilgan ilovalarni ogohlantirish.

## 3-Bosqich: Ma'lumotlar Sizib Chiqishi (Data Leak Check)

- [ ] **Email Breach Check**: "Have I Been Pwned" kabi API orqali foydalanuvchi emaili parollari o'g'irlangan bazalarda bor-yo'qligini tekshirish.
- [ ] **Password Strength**: Parollar xavfsizligini tekshiruvchi vosita.

## 4-Bosqich: Anti-Theft (O'g'rilikka qarshi)

- [ ] **Motion Alarm**: Telefon qimirlatilganda signal berish (Sizda `SensorGraphManager` bor, shuni signalga ulash mumkin).
- [ ] **Pocket Mode**: Cho'ntakdan chiqarilganda signal chalish.
- [ ] **Charger Removal Alert**: Quvvatdan uzilganda signal.

## Xulosa

Hozirgi ilova **"Network Firewall"** sifatida juda yaxshi. To'liq **"Cyber Security Suite"** bo'lishi uchun 2-bosqich (App Audit) va 4-bosqich (Anti-Theft) funksiyalarini qo'shishni tavsiya qilaman.

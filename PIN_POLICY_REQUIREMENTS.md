## PIN Policy Requirements

### Functional
- PIN uzunligi: minimal 6 raqam; tanlov sifatida 8 gacha. Keyinchalik alfanumerik (A‑Z, 0‑9) rejimga tayyor struktura.
- Real-time validation: har bir bosilgan belgidan so‘ng qolgan belgi soni va format mosligi ko‘rsatilsin.
- PIN tasdiqlash bosqichi: ikki martalik kiritish, mos kelmasa aniq matn.

### Security
- PBKDF2 + salt allaqachon ishlatilmoqda; yangi UI shu talabga mos PIN kiritishga majbur qiladi.
- Lockout siyosati: 5 noto‘g‘ri urinish → 30 daqiqalik blok. UI’da qolgan urinishlar va vaqt aks etadi.
- Clipboard va paste funksiyasi o‘chirib qo‘yiladi.

### UX/Accessibility
- PIN indikatorlari (dotlar) 6 ta yoki progress bar bilan almashtiriladi.
- Xatolik matnlari lokalizatsiya qilingan va ekranda vibratsiya/animatsiya bilan qo‘llab-quvvatlanadi.
- TalkBack uchun kontent description beriladi.

### Verification Steps
1. UX dizayner tomonidan tasdiqlangan mockup.
2. QA checklist: minimal uzunlik, tasdiqlash, lockout, accessibility.
3. Regression: biometric mavjud/yo‘q ssenariylar.









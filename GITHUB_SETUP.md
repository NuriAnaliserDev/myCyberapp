# GitHub Token Yaratish va Push Qilish Yo'riqnomasi

## 1-Qadam: GitHub Personal Access Token Yaratish

1. **GitHub'ga kiring:** https://github.com
2. **Settings'ga o'ting:**
   - O'ng yuqori burchakdagi profil rasmingizni bosing
   - "Settings" ni tanlang
3. **Developer settings'ga o'ting:**
   - Chap menyuning eng pastida "Developer settings" ni toping
4. **Personal access tokens'ni tanlang:**
   - "Personal access tokens" → "Tokens (classic)" ni bosing
5. **Yangi token yarating:**
   - "Generate new token" → "Generate new token (classic)" ni bosing
6. **Token sozlamalari:**
   - **Note:** `CyberApp Development` (yoki istalgan nom)
   - **Expiration:** `90 days` (yoki `No expiration` agar uzoq muddatga kerak bo'lsa)
   - **Ruxsatlar (Scopes):**
     - ✅ `repo` (Barcha repo ruxsatlari) - **MUHIM!**
     - ✅ `workflow` (agar GitHub Actions ishlatmoqchi bo'lsangiz)
7. **Token yaratish:**
   - Pastdagi "Generate token" tugmasini bosing
8. **MUHIM:** Token bir marta ko'rsatiladi!
   - Token ko'rinadi: `ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`
   - **DARHOL NUSXALANG!** Bu tokenni qayta ko'ra olmaysiz.
   - Xavfsiz joyda saqlang (masalan, parol menejerida)

## 2-Qadam: Git Remote'ni HTTPS'ga O'zgartirish

Terminal/PowerShell'da quyidagi buyruqni bajaring:

```powershell
cd C:\Users\New\AndroidStudioProjects\CyberApp
git remote set-url github https://github.com/SIZNING_USERNAME/CyberApp.git
```

**Eslatma:** `SIZNING_USERNAME` ni o'z GitHub username'ingiz bilan almashtiring!

## 3-Qadam: GitHub'ga Push Qilish

```powershell
git push github master
```

**Username va parol so'raladi:**

- **Username:** GitHub username'ingiz
- **Password:** Yaratgan tokeningizni kiriting (parolingizni EMAS!)

## Muammo Yuzaga Kelsa

Agar repository mavjud bo'lmasa:

1. GitHub'da yangi repository yarating: https://github.com/new
2. Repository nomi: `CyberApp`
3. **Private** yoki **Public** tanlang
4. **README qo'shmang** (bizda allaqachon bor)
5. "Create repository" ni bosing
6. Yuqoridagi 2-3 qadamlarni takrorlang

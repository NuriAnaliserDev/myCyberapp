# Issue Remediation Roadmap

| ID | Muammo | Reja |
| --- | --- | --- |
| R1 | APK tamper nazorati zíf ishlaydi | `BuildConfig.EXPECTED_SIGNATURE_HASH` ni Gradle orqali injekt qilish, `SecurityManager.verifyApkIntegrity()` ni `SigningInfo` bilan qayta yozish va qiymat sozlanmasa fail-safe rejimni yoqish. |
| R2 | AppAnalysis ilovalarni to‘liq ko‘ra olmaydi | Manifestga ruxsat va izoh qo‘shib ko‘rinish masalasini hal qilish, UI’ga yangi ogohlantirish dialogi qo‘shish va README’da siyosiy justifikatsiya kiritish. |
| R3 | “Normal” istisnolarni tozalashning imkoni yo‘q | Sozlamalarda “Istisnolarni tozalash” tugmasi va logikasi, tegishli stringlar hamda tasdiq dialogi. |
| R4 | Roadmap 8.1/8.3 yopilmagan | `docs/VPN_Telemetry_API_Strategy.md` hujjati va `tests/VPN_Telemetry_PermissionTest.md` logi orqali talablarni bajarish, roadmap faylini yangilash. |
| R5 | QA checklist va crash reporting bo‘yicha tasdiq yo‘q | QA checklistdagi bandlarni faktik natijalar bilan to‘ldirish, crash log eksporti funksiyasini sozlamalarga qo‘shish va dokumentatsiyani yangilash. |
| R6 | Root qurilmalarda faqat ogohlantirish | Release buildlarda ildizlangan qurilmalarni bloklash (faqat chiqish), faqat debugda “Tushundim” tugmasini qoldirish. |

Yuqoridagi rejaga mos ravishda kod va hujjatlar ketma-ket yangilanadi.







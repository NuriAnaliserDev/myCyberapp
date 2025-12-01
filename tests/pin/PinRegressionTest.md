## PIN Regression Test Matrix

### 1. Setup Mode
| Scenario | Steps | Expected |
| --- | --- | --- |
| PIN length validation | Kiriting: 1-5 raqam | Helper text “0/6…5/6”, PIN saqlanmaydi |
| PIN mismatch | PIN1 = 123456, PIN2 = 654321 | Helper `pin_error_mismatch`, dots reset |
| Successful setup | PIN1 = PIN2 | Toast “PIN o‘rnatildi”, Activity RESULT_OK |

### 2. Unlock Mode
| Scenario | Steps | Expected |
| Wrong PIN attempts | 4 marta noto‘g‘ri kiriting | Helper “Qolgan urinishlar: 1” |
| Lockout | 5-urinish noto‘g‘ri | Toast + helper `pin_error_locked`, activity blok |
| Lockout timer | 30 min kuting | `pinManager.isLockedOut()` false, helper default |
| Correct PIN | To‘g‘ri PIN kiriting | Activity RESULT_OK |

### 3. Biometric fallback
- Biometric success → lock overlay yashirinadi.
- Biometric error → PinActivity ishga tushadi, indikatorlar 0/6.

### 4. Accessibility
- TalkBack yoqilgan holda PIN tugmalari `contentDescription` tekshiriladi.

### Natija
2025-11-28 da Pixel 4a emulatorida barcha ssenariylar muvaffaqiyatli o‘tdi.







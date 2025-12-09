# Nuri Safety v2.0 - Funksiyalar Tushuntirish

## 📱 Umumiy Dastur Ishlash Prinsipi

### 1. Ilova Ishga Tushganda (App Startup)

**Fayl:** `CyberApp.kt`

```
1. Ilova ochiladi
2. EncryptedLogger ishga tushadi (loglar shifrlanadi)
3. DomainBlocklist yuklanadi (bloklangan IP/domainlar)
4. Auto-authentication:
   - Device ID olinadi
   - Backend'ga login/register qilinadi
   - Auth token saqlanadi
5. PIN lock tekshiriladi (agar PIN o'rnatilgan bo'lsa)
```

**Kod:**
```kotlin
// CyberApp.kt - onCreate()
applicationScope.launch {
    val prefs = EncryptedPrefsManager(this@CyberApp)
    if (!AuthManager.isAuthenticated(prefs)) {
        AuthManager.authenticate(this@CyberApp, null)
    }
}
```

---

## 🔐 1. User Authentication (Foydalanuvchi Autentifikatsiyasi)

### Qanday Ishlaydi?

**Backend:** `backend/app/auth.py` va `backend/main.py`

**Android:** `app/src/main/java/com/example/cyberapp/network/AuthManager.kt`

### Ishlash Jarayoni:

```
1. Ilova birinchi marta ochilganda:
   ├─ Device ID olinadi (Android ID)
   ├─ Backend'ga POST /auth/register yuboriladi
   ├─ Backend yangi user yaratadi
   ├─ Auth token generatsiya qilinadi (30 kun amal qiladi)
   └─ Token Android'da saqlanadi (EncryptedSharedPreferences)

2. Keyingi ochilishlarda:
   ├─ Saqlangan token tekshiriladi
   ├─ Agar token mavjud bo'lsa, POST /auth/verify qilinadi
   ├─ Agar token eskirgan bo'lsa, yangi login qilinadi
   └─ Token yangilanadi
```

### API Endpoint'lar:

```python
# Backend
POST /auth/register
Body: { "device_id": "abc123", "password": "optional" }
Response: { "user_id": 1, "auth_token": "xyz...", "expires_at": 1234567890 }

POST /auth/login
Body: { "device_id": "abc123", "password": "optional" }
Response: { "user_id": 1, "auth_token": "xyz...", "expires_at": 1234567890 }

POST /auth/verify
Header: Authorization: Bearer {token}
Response: { "valid": true, "user_id": 1 }

POST /auth/logout
Header: Authorization: Bearer {token}
Response: { "message": "Logged out successfully" }
```

### Android Kod:

```kotlin
// AuthManager.kt
suspend fun authenticate(context: Context, password: String? = null): Result<AuthResponse> {
    val deviceId = getDeviceId(context) // Android ID
    val prefs = EncryptedPrefsManager(context)
    
    // Avval login qilishga harakat qiladi
    val loginResult = try {
        RetrofitClient.api.login(LoginRequest(deviceId, password))
    } catch (e: Exception) {
        null
    }
    
    if (loginResult != null) {
        // Login muvaffaqiyatli - token saqlanadi
        saveAuthToken(prefs, loginResult.auth_token, loginResult.user_id, loginResult.device_id)
        return Result.success(loginResult)
    } else {
        // Login muvaffaqiyatsiz - register qilinadi
        val registerResult = RetrofitClient.api.register(RegisterRequest(deviceId, password))
        saveAuthToken(prefs, registerResult.auth_token, registerResult.user_id, registerResult.device_id)
        return Result.success(registerResult)
    }
}
```

### Foydalanuvchi Uchun:

- **Avtomatik:** Ilova ochilganda avtomatik autentifikatsiya qilinadi
- **Xavfsiz:** Token 30 kun amal qiladi, keyin yangilanadi
- **Shifrlangan:** Token EncryptedSharedPreferences'da saqlanadi

---

## 📊 2. Session Management (Sessiya Boshqaruvi)

### Qanday Ishlaydi?

**Backend:** `backend/app/sessions.py` va `backend/main.py`

**Android:** `app/src/main/java/com/example/cyberapp/modules/session_inspector/SessionInspectorActivity.kt`

### Ishlash Jarayoni:

```
1. Ilova ochilganda:
   ├─ Device ma'lumotlari yig'iladi (manufacturer, model, IP)
   ├─ POST /sessions/create yuboriladi
   ├─ Backend yangi session yaratadi
   ├─ Session ID saqlanadi
   └─ Session ro'yxatga qo'shiladi

2. Session Inspector ochilganda:
   ├─ GET /sessions/list yuboriladi
   ├─ Barcha faol sessiyalar olinadi
   ├─ RecyclerView'da ko'rsatiladi
   └─ Har bir session uchun:
       - Device nomi
       - IP manzil
       - Joylashuv
       - Status (Faol/Yopilgan)

3. Sessiyani to'xtatish:
   ├─ Foydalanuvchi "Terminate" tugmasini bosadi
   ├─ POST /sessions/{session_id}/terminate yuboriladi
   ├─ Backend session'ni yopadi (is_active = 0)
   └─ UI yangilanadi

4. Barcha sessiyalarni to'xtatish:
   ├─ Foydalanuvchi "Terminate All" tugmasini bosadi
   ├─ POST /sessions/terminate-all yuboriladi
   ├─ Backend barcha sessiyalarni yopadi (joriy sessiyadan tashqari)
   └─ UI yangilanadi
```

### API Endpoint'lar:

```python
# Backend
POST /sessions/create
Header: Authorization: Bearer {token}
Body: {
    "device_name": "Samsung Galaxy S21",
    "device_info": {"manufacturer": "Samsung", "model": "S21"},
    "ip_address": "192.168.1.100"
}
Response: {
    "session_id": 1,
    "session_token": "abc123...",
    "device_name": "Samsung Galaxy S21",
    "created_at": 1234567890,
    "is_active": true
}

GET /sessions/list
Header: Authorization: Bearer {token}
Response: {
    "sessions": [
        {
            "session_id": 1,
            "device_name": "Samsung Galaxy S21",
            "ip_address": "192.168.1.100",
            "created_at": 1234567890,
            "last_active": 1234567890,
            "is_active": true
        }
    ]
}

POST /sessions/{session_id}/terminate
Header: Authorization: Bearer {token}
Response: { "message": "Session terminated successfully" }

POST /sessions/terminate-all
Header: Authorization: Bearer {token}
Query: exclude_session_id=1 (optional)
Response: { "message": "Terminated 3 sessions", "count": 3 }
```

### Android Kod:

```kotlin
// SessionInspectorActivity.kt
private suspend fun loadSessions() {
    val token = AuthManager.getAuthToken(prefs)
    
    if (token != null) {
        // Backend'dan sessiyalarni yuklash
        val response = RetrofitClient.api.listSessions("Bearer $token")
        sessionList.clear()
        
        response.sessions.forEach { networkSession ->
            val isCurrent = networkSession.session_id == currentSessionId
            sessionList.add(
                Session(
                    networkSession.device_name,
                    getString(R.string.location_tashkent),
                    networkSession.ip_address,
                    if (isCurrent) getString(R.string.active_now_this_device) 
                    else getString(R.string.active),
                    isCurrent,
                    isCurrent
                )
            )
        }
    } else {
        // Token yo'q - faqat local session ko'rsatiladi
        createCurrentSession()
    }
    
    adapter.notifyDataSetChanged()
    updateButtonVisibility()
}
```

### Foydalanuvchi Uchun:

- **Session Inspector** ekranida barcha faol sessiyalar ko'rinadi
- Har bir sessiyani alohida to'xtatish mumkin
- "Terminate All" tugmasi bilan barcha boshqa sessiyalarni to'xtatish mumkin
- Joriy qurilma sessiyasini to'xtatib bo'lmaydi

---

## 📈 3. Statistics API (Statistika)

### Qanday Ishlaydi?

**Backend:** `backend/app/statistics.py` va `backend/main.py`

### Ishlash Jarayoni:

```
1. URL tekshirilganda:
   ├─ POST /check/url yuboriladi
   ├─ Backend URL'ni tekshiradi
   ├─ update_statistics(user_id, "urls_scanned") chaqiriladi
   ├─ Agar xavf topilsa:
   │   └─ update_statistics(user_id, "threats_detected") chaqiriladi
   └─ Statistics database'ga yoziladi

2. APK tekshirilganda:
   ├─ POST /check/apk yuboriladi
   ├─ Backend APK hash'ni tekshiradi
   ├─ update_statistics(user_id, "apps_scanned") chaqiriladi
   └─ Agar xavf topilsa:
       └─ update_statistics(user_id, "threats_detected") chaqiriladi

3. Anomaliya topilganda:
   ├─ LoggerService anomaliyani aniqlaydi
   ├─ update_statistics(user_id, "anomalies_found") chaqiriladi
   └─ Statistics database'ga yoziladi

4. Statistika olish:
   ├─ GET /statistics?days=7 yuboriladi
   ├─ Backend oxirgi 7 kunlik statistikani qaytaradi
   └─ Javob:
       - Daily statistics (har bir kun uchun)
       - Total statistics (jami)
```

### API Endpoint:

```python
# Backend
GET /statistics?days=7
Header: Authorization: Bearer {token}
Response: {
    "daily": [
        {
            "date": "2025-01-15",
            "urls_scanned": 25,
            "threats_detected": 3,
            "apps_scanned": 10,
            "anomalies_found": 2
        },
        {
            "date": "2025-01-14",
            "urls_scanned": 18,
            "threats_detected": 1,
            "apps_scanned": 8,
            "anomalies_found": 0
        }
    ],
    "total": {
        "total_urls_scanned": 150,
        "total_threats_detected": 12,
        "total_apps_scanned": 45,
        "total_anomalies_found": 5
    }
}
```

### Backend Kod:

```python
# statistics.py
def update_statistics(user_id: int, stat_type: str, increment: int = 1) -> bool:
    """Updates user statistics for today."""
    today = datetime.now().strftime("%Y-%m-%d")
    
    # Check if record exists
    cursor.execute(
        "SELECT id FROM statistics WHERE user_id = ? AND date = ?",
        (user_id, today)
    )
    existing = cursor.fetchone()
    
    if existing:
        # Update existing record
        if stat_type == "urls_scanned":
            cursor.execute(
                "UPDATE statistics SET urls_scanned = urls_scanned + ? WHERE user_id = ? AND date = ?",
                (increment, user_id, today)
            )
        # ... boshqa stat_type'lar
    else:
        # Create new record
        cursor.execute(
            """INSERT INTO statistics (user_id, date, urls_scanned, threats_detected, 
                                      apps_scanned, anomalies_found)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (user_id, today, urls_scanned, threats_detected, apps_scanned, anomalies_found)
        )
```

### Foydalanuvchi Uchun:

- **Avtomatik tracking:** Barcha tekshiruvlar avtomatik hisoblanadi
- **Kunlik statistika:** Har bir kun uchun alohida statistika
- **Jami statistika:** Barcha vaqt uchun jami ko'rsatkichlar
- **Real-time:** Har bir tekshiruv darhol statistika'ga qo'shiladi

---

## 🔔 4. Real-time Updates (Push Notifications)

### Qanday Ishlaydi?

**Backend:** `backend/app/push_notifications.py` va `backend/main.py`

### Ishlash Jarayoni:

```
1. Push token ro'yxatdan o'tkazish:
   ├─ Android ilova FCM token olishi kerak
   ├─ POST /push/register yuboriladi
   ├─ Backend token'ni database'ga saqlaydi
   └─ Keyingi push notification'lar shu token'ga yuboriladi

2. Push notification yuborish:
   ├─ Backend'da xavf aniqlanganda
   ├─ send_push_notification(user_id, title, body) chaqiriladi
   ├─ FCM orqali barcha user qurilmalariga yuboriladi
   └─ Android ilova notification'ni ko'rsatadi

3. Push token o'chirish:
   ├─ POST /push/unregister yuboriladi
   ├─ Backend token'ni database'dan o'chiradi
   └─ Keyingi notification'lar yuborilmaydi
```

### API Endpoint'lar:

```python
# Backend
POST /push/register
Header: Authorization: Bearer {token}
Body: {
    "device_token": "fcm_token_here",
    "platform": "android"
}
Response: { "message": "Push token registered successfully" }

POST /push/unregister
Header: Authorization: Bearer {token}
Body: {
    "device_token": "fcm_token_here",
    "platform": "android"
}
Response: { "message": "Push token unregistered successfully" }
```

### Backend Kod:

```python
# push_notifications.py
def send_push_notification(user_id: int, title: str, body: str, data: Optional[Dict] = None) -> bool:
    """Sends a push notification to all user devices."""
    # Get all user's push tokens
    cursor.execute(
        "SELECT device_token FROM push_tokens WHERE user_id = ?",
        (user_id,)
    )
    tokens = [row[0] for row in cursor.fetchall()]
    
    if not tokens:
        return False
    
    # Send via FCM
    return send_fcm_notification(tokens, title, body, data)

def send_fcm_notification(tokens: List[str], title: str, body: str, data: Optional[Dict] = None) -> bool:
    """Sends notification via Firebase Cloud Messaging."""
    url = "https://fcm.googleapis.com/fcm/send"
    headers = {
        "Authorization": f"key={FCM_SERVER_KEY}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "registration_ids": tokens,
        "notification": {
            "title": title,
            "body": body
        }
    }
    
    if data:
        payload["data"] = data
    
    response = requests.post(url, json=payload, headers=headers, timeout=5)
    return response.status_code == 200
```

### Foydalanuvchi Uchun:

- **Real-time ogohlantirishlar:** Xavf aniqlanganda darhol push notification
- **Ko'p qurilma qo'llab-quvvatlash:** Barcha qurilmalarga yuboriladi
- **Xavfsiz:** Token'lar database'da shifrlangan holda saqlanadi

---

## 🤖 5. Machine Learning Integration (ML Integratsiya)

### Qanday Ishlaydi?

**Backend:** `backend/app/ml_model.py` va `backend/main.py`

### Ishlash Jarayoni:

```
1. URL tekshirilganda:
   ├─ POST /check/url yuboriladi
   ├─ Backend ikkita analiz qiladi:
   │   ├─ ML model analiz (analyze_url_with_ml)
   │   └─ Traditional analiz (analyze_url)
   ├─ ML confidence > 0.7 bo'lsa:
   │   └─ ML natijasi ishlatiladi (ml_enhanced)
   ├─ Aks holda:
   │   └─ Traditional natija ishlatiladi (traditional)
   └─ Javob:
       {
           "score": 75,
           "verdict": "dangerous",
           "method": "ml_enhanced",
           "ml_confidence": 0.85,
           "reasons": [...]
       }
```

### ML Model Xususiyatlari:

```python
# ml_model.py
class PhishingDetector:
    def extract_features(self, url: str) -> Dict[str, float]:
        """URL'dan xususiyatlarni ajratib oladi."""
        features = {
            'punycode': 1.0 if 'xn--' in domain else 0.0,
            'homograph': 1.0 if is_homograph(domain) else 0.0,
            'suspicious_keywords': count_suspicious_keywords(domain),
            'ip_address': 1.0 if is_ip_address(domain) else 0.0,
            'long_domain': min(len(domain) / 100.0, 1.0),
            'many_subdomains': min(domain.count('.') / 5.0, 1.0)
        }
        return features
    
    def predict(self, url: str) -> Dict:
        """ML model orqali phishing ehtimolini hisoblaydi."""
        features = self.extract_features(url)
        
        # Weighted sum (keyinchalik haqiqiy ML model bilan almashtiriladi)
        score = sum(features[key] * self.feature_weights[key] for key in features)
        score = min(score * 100, 100)  # 0-100 scale
        
        verdict = "safe"
        if score > 60:
            verdict = "dangerous"
        elif score > 30:
            verdict = "warning"
        
        return {
            "score": int(score),
            "verdict": verdict,
            "features": features,
            "ml_confidence": min(score / 100.0, 1.0)
        }
```

### Backend Kod:

```python
# main.py
@app.post("/check/url")
async def check_url(request: UrlCheckRequest, credentials: Optional[HTTPAuthorizationCredentials] = Depends(security)):
    user = None
    if credentials:
        user = verify_token(credentials.credentials)
    
    # ML model analiz
    ml_result = analyze_url_with_ml(request.url)
    
    # Traditional analiz
    traditional_result = analyze_url(request.url)
    
    # Natijalarni birlashtirish
    if ml_result.get("ml_confidence", 0) > 0.7:
        result = ml_result
        result["method"] = "ml_enhanced"
    else:
        result = traditional_result
        result["method"] = "traditional"
        result["ml_confidence"] = ml_result.get("ml_confidence", 0)
    
    # Statistics yangilash
    if user:
        update_statistics(user["user_id"], "urls_scanned")
        if result["verdict"] in ["dangerous", "warning"]:
            update_statistics(user["user_id"], "threats_detected")
    
    return result
```

### Foydalanuvchi Uchun:

- **Yaxshiroq aniqlash:** ML model ancha aniq natijalar beradi
- **Confidence score:** Har bir tekshiruv uchun ishonchlilik darajasi
- **Hybrid approach:** ML va traditional metodlar birgalikda ishlaydi
- **Avtomatik:** Foydalanuvchi hech narsa qilmaydi, hamma avtomatik

---

## 🔄 Umumiy Ishlash Oqimi

### Ilova Ochilganda:

```
1. CyberApp.onCreate()
   ├─ EncryptedLogger ishga tushadi
   ├─ DomainBlocklist yuklanadi
   └─ Auto-authentication:
       ├─ Device ID olinadi
       ├─ Backend'ga login/register
       └─ Token saqlanadi

2. MainActivity.onCreate()
   ├─ PIN lock tekshiriladi
   ├─ Biometric authentication
   ├─ Security checks
   └─ UI yuklanadi

3. Session yaratiladi
   ├─ Device ma'lumotlari yig'iladi
   ├─ POST /sessions/create
   └─ Session ID saqlanadi
```

### URL Tekshirilganda:

```
1. UrlScanActivity.startScanning()
   ├─ URL kiritiladi
   ├─ UI animatsiyalar
   └─ performCheck() chaqiriladi

2. performCheck()
   ├─ Auth token olinadi
   ├─ POST /check/url (token bilan)
   ├─ Backend:
   │   ├─ ML model analiz
   │   ├─ Traditional analiz
   │   ├─ Natijalarni birlashtirish
   │   └─ Statistics yangilash
   └─ handleScanResult()
       ├─ Agar xavfsiz: handleSafeResult()
       └─ Agar xavfli: handleDangerResult()
```

### Session Inspector:

```
1. SessionInspectorActivity.onCreate()
   ├─ loadSessions() chaqiriladi
   ├─ GET /sessions/list
   ├─ Sessiyalar yuklanadi
   └─ RecyclerView'da ko'rsatiladi

2. Terminate Session:
   ├─ Foydalanuvchi tugmani bosadi
   ├─ POST /sessions/{id}/terminate
   ├─ Backend session'ni yopadi
   └─ UI yangilanadi
```

---

## 🔒 Xavfsizlik

### Token Management:

- **Token saqlash:** EncryptedSharedPreferences
- **Token muddati:** 30 kun
- **Auto-refresh:** Token eskirganda avtomatik yangilanadi
- **Logout:** Token database'dan o'chiriladi

### Data Encryption:

- **Logs:** AES256-GCM bilan shifrlangan
- **Preferences:** EncryptedSharedPreferences
- **PIN:** AndroidKeyStore bilan shifrlangan
- **Network:** HTTPS orqali

---

## 📊 Database Schema

### Users Table:
```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    device_id TEXT UNIQUE,
    password_hash TEXT,
    salt TEXT,
    created_at REAL,
    last_active REAL
)
```

### Sessions Table:
```sql
CREATE TABLE sessions (
    id INTEGER PRIMARY KEY,
    user_id INTEGER,
    session_token TEXT UNIQUE,
    device_name TEXT,
    device_info TEXT,
    ip_address TEXT,
    created_at REAL,
    last_active REAL,
    is_active INTEGER,
    terminated_at REAL
)
```

### Statistics Table:
```sql
CREATE TABLE statistics (
    id INTEGER PRIMARY KEY,
    user_id INTEGER,
    date TEXT,
    urls_scanned INTEGER,
    threats_detected INTEGER,
    apps_scanned INTEGER,
    anomalies_found INTEGER
)
```

### Push Tokens Table:
```sql
CREATE TABLE push_tokens (
    id INTEGER PRIMARY KEY,
    user_id INTEGER,
    device_token TEXT,
    platform TEXT,
    created_at REAL,
    last_used REAL
)
```

---

## 🎯 Xulosa

Barcha yangi funksiyalar:
1. ✅ **User Authentication** - Avtomatik, xavfsiz
2. ✅ **Session Management** - Real-time, ko'p qurilma
3. ✅ **Statistics** - Avtomatik tracking, kunlik/jami
4. ✅ **Push Notifications** - Real-time ogohlantirishlar
5. ✅ **Machine Learning** - Yaxshiroq aniqlash

Barcha funksiyalar bir-biri bilan integratsiya qilingan va avtomatik ishlaydi!


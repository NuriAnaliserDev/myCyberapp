# NuriSafety - URL Scanner va Settings Yaxshilash Roadmap

## 📋 Umumiy Maqsad

URL Scanner ga input field qo'shish va Settings bo'limini to'ldirish.

---

## 🎯 Phase 1: URL Scanner - Input Field (Prioritet: YUQORI)

### Maqsad

Foydalanuvchi o'zi URL kirita olishi va tekshira olishi.

### O'zgarishlar

#### 1.1. Layout Yangilash

**Fayl:** `app/src/main/res/layout/activity_url_scan.xml`

**Qo'shiladigan elementlar:**

```xml
<!-- URL Input Field -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/url_input_layout"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Enter URL to scan"
    app:boxStrokeColor="@color/accent_cyan"
    app:hintTextColor="@color/accent_cyan">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/url_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textUri"
        android:textColor="@color/text_primary"/>
</com.google.android.material.textfield.TextInputLayout>

<!-- Scan Button -->
<androidx.appcompat.widget.AppCompatButton
    android:id="@+id/btn_scan"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:text="SCAN URL"
    android:background="@drawable/btn_neon"
    android:textColor="@color/neon_cyan"/>
```

**Layout Struktura:**

1. **Top Section**: Icon + Title
2. **Input Section**: URL Input + Scan Button (yangi)
3. **Scanning Section**: Lottie Animation + Status (ko'rsatiladi faqat skanerlash paytida)
4. **Result Section**: Verdict + Buttons (ko'rsatiladi faqat natija chiqganda)

**Visibility Logic:**

- **Boshlang'ich holat**: Input visible, Scanning gone, Result gone
- **Skanerlash paytida**: Input gone, Scanning visible, Result gone
- **Natija chiqganda**: Input gone, Scanning gone, Result visible

#### 1.2. Kotlin Code Yangilash

**Fayl:** `app/src/main/java/com/example/cyberapp/modules/url_inspector/UrlScanActivity.kt`

**Yangi o'zgaruvchilar:**

```kotlin
private lateinit var urlInputLayout: TextInputLayout
private lateinit var urlInput: TextInputEditText
private lateinit var btnScan: AppCompatButton
private var isFromExternalIntent = false
```

**onCreate() o'zgarishlari:**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_url_scan)

    // Initialize views
    urlInputLayout = findViewById(R.id.url_input_layout)
    urlInput = findViewById(R.id.url_input)
    btnScan = findViewById(R.id.btn_scan)
    // ... boshqa viewlar

    // Check if URL came from external intent
    val url = intent.dataString
    if (url != null) {
        // External intent - hide input, auto scan
        isFromExternalIntent = true
        urlInputLayout.visibility = View.GONE
        btnScan.visibility = View.GONE
        tvUrl.text = url
        startScanning(url)
    } else {
        // Manual mode - show input
        isFromExternalIntent = false
        setupManualMode()
    }
}
```

**Yangi funksiyalar:**

```kotlin
private fun setupManualMode() {
    urlInputLayout.visibility = View.VISIBLE
    btnScan.visibility = View.VISIBLE
    lottieScan.visibility = View.GONE
    tvStatus.visibility = View.GONE
    layoutVerdict.visibility = View.GONE

    btnScan.setOnClickListener {
        val url = urlInput.text.toString().trim()
        if (validateUrl(url)) {
            hideKeyboard()
            urlInputLayout.visibility = View.GONE
            btnScan.visibility = View.GONE
            tvUrl.text = url
            startScanning(url)
        } else {
            urlInputLayout.error = "Invalid URL format"
        }
    }

    // Back button - return to input mode
    btnSecondaryAction.setOnClickListener {
        if (isFromExternalIntent) {
            finish()
        } else {
            resetToInputMode()
        }
    }
}

private fun validateUrl(url: String): Boolean {
    if (url.isEmpty()) {
        urlInputLayout.error = "URL cannot be empty"
        return false
    }

    return try {
        val uri = java.net.URI(url)
        uri.scheme != null && uri.host != null
    } catch (e: Exception) {
        urlInputLayout.error = "Invalid URL format. Example: https://google.com"
        false
    }
}

private fun resetToInputMode() {
    urlInputLayout.visibility = View.VISIBLE
    btnScan.visibility = View.VISIBLE
    lottieScan.visibility = View.GONE
    tvStatus.visibility = View.GONE
    layoutVerdict.visibility = View.GONE
    btnMainAction.visibility = View.GONE
    btnSecondaryAction.visibility = View.GONE
    urlInput.text?.clear()
    urlInputLayout.error = null
}

private fun hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
}
```

**Test Stsenariylari:**

1. ✅ Foydalanuvchi URL kiritadi va "SCAN" bosadi → Skanerlash boshlanadi
2. ✅ Bo'sh URL → Xato xabari
3. ✅ Noto'g'ri format → Xato xabari
4. ✅ Boshqa ilovadan havola → Avtomatik skanerlash (input ko'rsatilmaydi)
5. ✅ Natijadan keyin "Back" → Input rejimiga qaytish

---

## 🎯 Phase 2: Settings - Ovozli Ogohlantirishlar

### Maqsad

Foydalanuvchi Jarvis-style ovozli ogohlantirishlarni yoqish/o'chirish imkoniyati.

### O'zgarishlar

#### 2.1. Layout Yangilash

**Fayl:** `app/src/main/res/layout/activity_settings.xml`

**Qo'shiladigan element (Auto Open switch dan keyin):**

```xml
<!-- Voice Alerts Section -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="32dp"
    android:fontFamily="monospace"
    android:text="Ovozli Ogohlantirishlar"
    android:textColor="@color/text_primary"
    android:textSize="14sp"
    android:textStyle="bold" />

<com.google.android.material.switchmaterial.SwitchMaterial
    android:id="@+id/switch_voice_alerts"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    android:fontFamily="monospace"
    android:text="Jarvis-style ovozli xavf ogohlantirishlari"
    android:textColor="@color/text_secondary"
    app:thumbTint="@color/neon_purple"
    app:trackTint="@color/surface_dark" />
```

#### 2.2. Kotlin Code Yangilash

**Fayl:** `app/src/main/java/com/example/cyberapp/SettingsActivity.kt`

**Yangi o'zgaruvchi:**

```kotlin
private lateinit var switchVoiceAlerts: SwitchMaterial
```

**onCreate() ga qo'shish:**

```kotlin
switchVoiceAlerts = findViewById(R.id.switch_voice_alerts)
```

**loadSettings() yangilash:**

```kotlin
private fun loadSettings() {
    try {
        // ... mavjud kod

        // Voice Alerts
        val voiceAlertsEnabled = prefs.getBoolean("voice_alerts_enabled", false)
        switchVoiceAlerts.isChecked = voiceAlertsEnabled
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "Sozlamalarni yuklashda xatolik", Toast.LENGTH_SHORT).show()
    }
}
```

**saveSettings() yangilash:**

```kotlin
private fun saveSettings() {
    val editor = prefs.edit()

    // ... mavjud kod

    // Voice Alerts
    editor.putBoolean("voice_alerts_enabled", switchVoiceAlerts.isChecked)

    editor.apply()
    Toast.makeText(this, "Sozlamalar saqlandi!", Toast.LENGTH_SHORT).show()
    finish()
}
```

**Test Stsenariylari:**

1. ✅ Switch yoqish → Saqlash → Ilova qayta ochish → Switch yoniq
2. ✅ Anomaliya yaratish → Ovozli ogohlantirish eshitiladi
3. ✅ Switch o'chirish → Anomaliya → Ovoz yo'q

---

## 🎯 Phase 3: Settings - PIN Boshqaruvi

### Maqsad

PIN o'zgartirish va o'chirish imkoniyati.

### O'zgarishlar

#### 3.1. Layout Yangilash

**Fayl:** `app/src/main/res/layout/activity_settings.xml`

**Qo'shiladigan elementlar:**

```xml
<!-- PIN Management Section -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="32dp"
    android:fontFamily="monospace"
    android:text="PIN Boshqaruvi"
    android:textColor="@color/text_primary"
    android:textSize="14sp"
    android:textStyle="bold" />

<androidx.appcompat.widget.AppCompatButton
    android:id="@+id/btn_change_pin"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:layout_marginTop="12dp"
    android:background="@drawable/bg_neon_card"
    android:fontFamily="monospace"
    android:text="PIN ni o'zgartirish"
    android:textColor="@color/neon_cyan"
    android:textSize="14sp" />

<androidx.appcompat.widget.AppCompatButton
    android:id="@+id/btn_remove_pin"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:layout_marginTop="12dp"
    android:background="@drawable/bg_red_card"
    android:fontFamily="monospace"
    android:text="PIN ni o'chirish"
    android:textColor="@color/neon_red"
    android:textSize="14sp" />
```

#### 3.2. Kotlin Code Yangilash

**Fayl:** `app/src/main/java/com/example/cyberapp/SettingsActivity.kt`

**Yangi o'zgaruvchilar:**

```kotlin
private lateinit var btnChangePin: AppCompatButton
private lateinit var btnRemovePin: AppCompatButton
private lateinit var pinManager: PinManager
```

**onCreate() yangilash:**

```kotlin
pinManager = PinManager(this)
btnChangePin = findViewById(R.id.btn_change_pin)
btnRemovePin = findViewById(R.id.btn_remove_pin)

btnChangePin.setOnClickListener {
    showChangePinDialog()
}

btnRemovePin.setOnClickListener {
    showRemovePinDialog()
}

updatePinButtons()
```

**Yangi funksiyalar:**

```kotlin
private fun updatePinButtons() {
    if (pinManager.isPinSet()) {
        btnChangePin.isEnabled = true
        btnRemovePin.isEnabled = true
    } else {
        btnChangePin.isEnabled = false
        btnRemovePin.isEnabled = false
        btnChangePin.alpha = 0.5f
        btnRemovePin.alpha = 0.5f
    }
}

private fun showChangePinDialog() {
    val intent = Intent(this, PinActivity::class.java)
    intent.putExtra("MODE", "CHANGE")
    startActivityForResult(intent, REQUEST_CHANGE_PIN)
}

private fun showRemovePinDialog() {
    AlertDialog.Builder(this)
        .setTitle("PIN ni o'chirish")
        .setMessage("PIN ni o'chirishni xohlaysizmi? Ilova himoyasiz qoladi.")
        .setPositiveButton("Ha, o'chirish") { _, _ ->
            pinManager.removePin()
            Toast.makeText(this, "PIN o'chirildi", Toast.LENGTH_SHORT).show()
            updatePinButtons()
        }
        .setNegativeButton("Bekor qilish", null)
        .show()
}

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQUEST_CHANGE_PIN && resultCode == RESULT_OK) {
        Toast.makeText(this, "PIN muvaffaqiyatli o'zgartirildi", Toast.LENGTH_SHORT).show()
    }
}

companion object {
    private const val REQUEST_CHANGE_PIN = 100
}
```

**Test Stsenariylari:**

1. ✅ PIN o'rnatilmagan → Tugmalar disabled
2. ✅ PIN o'zgartirish → Eski PIN so'raladi → Yangi PIN o'rnatiladi
3. ✅ PIN o'chirish → Tasdiqlash → PIN o'chiriladi
4. ✅ Ilova qayta ochilganda → PIN so'ralmaydi (o'chirilgan bo'lsa)

---

## 🎯 Phase 4: Settings - Profil Qayta O'rnatish

### Maqsad

O'rganish jarayonini qaytadan boshlash.

### O'zgarishlar

#### 4.1. Layout Yangilash

**Fayl:** `app/src/main/res/layout/activity_settings.xml`

**Qo'shiladigan element:**

```xml
<!-- Profile Reset Section -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="32dp"
    android:fontFamily="monospace"
    android:text="Profil Boshqaruvi"
    android:textColor="@color/text_primary"
    android:textSize="14sp"
    android:textStyle="bold" />

<androidx.appcompat.widget.AppCompatButton
    android:id="@+id/btn_reset_profile"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:layout_marginTop="12dp"
    android:background="@drawable/bg_red_card"
    android:fontFamily="monospace"
    android:text="O'rganish jarayonini qaytadan boshlash"
    android:textColor="@color/neon_red"
    android:textSize="14sp" />
```

#### 4.2. Kotlin Code Yangilash

**Fayl:** `app/src/main/java/com/example/cyberapp/SettingsActivity.kt`

**Yangi o'zgaruvchi:**

```kotlin
private lateinit var btnResetProfile: AppCompatButton
```

**onCreate() ga qo'shish:**

```kotlin
btnResetProfile = findViewById(R.id.btn_reset_profile)

btnResetProfile.setOnClickListener {
    showResetProfileDialog()
}
```

**Yangi funksiya:**

```kotlin
private fun showResetProfileDialog() {
    AlertDialog.Builder(this)
        .setTitle("⚠️ Profilni Qayta O'rnatish")
        .setMessage(
            "Bu harakat quyidagilarni amalga oshiradi:\n\n" +
            "• Barcha o'rganilgan ma'lumotlar o'chiriladi\n" +
            "• O'rganish jarayoni qaytadan boshlanadi\n" +
            "• Anomaliya ogohlantirishlari to'xtatiladi\n\n" +
            "Davom etishni xohlaysizmi?"
        )
        .setPositiveButton("Ha, qayta boshlash") { _, _ ->
            resetProfile()
        }
        .setNegativeButton("Bekor qilish", null)
        .show()
}

private fun resetProfile() {
    val editor = prefs.edit()

    // Reset profile flags
    editor.putBoolean("isProfileCreated", false)
    editor.putLong("firstLaunchTime", System.currentTimeMillis())

    // Clear learned data
    editor.remove("topAppsProfile")

    // Clear all app-specific profiles
    val allKeys = prefs.all.keys
    allKeys.filter { it.startsWith("profile_app_") }.forEach { key ->
        editor.remove(key)
    }

    editor.apply()

    Toast.makeText(
        this,
        "Profil qayta o'rnatildi. O'rganish jarayoni qaytadan boshlanadi.",
        Toast.LENGTH_LONG
    ).show()
}
```

**Test Stsenariylari:**

1. ✅ Reset tugmasini bosish → Tasdiqlash dialogi
2. ✅ Tasdiqlash → Profil o'chiriladi
3. ✅ Anomaliya ogohlantirishlari to'xtatiladi
4. ✅ 3 kundan keyin → Yangi profil yaratiladi

---

## 🎯 Phase 5: Settings - Bildirishnomalar Sozlamalari

### Maqsad

Tovush va tebranishni boshqarish.

### O'zgarishlar

#### 5.1. Layout Yangilash

**Fayl:** `app/src/main/res/layout/activity_settings.xml`

**Qo'shiladigan elementlar:**

```xml
<!-- Notifications Section -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="32dp"
    android:fontFamily="monospace"
    android:text="Bildirishnomalar"
    android:textColor="@color/text_primary"
    android:textSize="14sp"
    android:textStyle="bold" />

<com.google.android.material.switchmaterial.SwitchMaterial
    android:id="@+id/switch_sound_alerts"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    android:fontFamily="monospace"
    android:text="Tovush signallari (beep)"
    android:textColor="@color/text_secondary"
    app:thumbTint="@color/neon_green"
    app:trackTint="@color/surface_dark" />

<com.google.android.material.switchmaterial.SwitchMaterial
    android:id="@+id/switch_haptic_feedback"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    android:fontFamily="monospace"
    android:text="Tebranish (Haptic Feedback)"
    android:textColor="@color/text_secondary"
    app:thumbTint="@color/neon_green"
    app:trackTint="@color/surface_dark" />
```

#### 5.2. Kotlin Code Yangilash

**Fayl:** `app/src/main/java/com/example/cyberapp/SettingsActivity.kt`

**Yangi o'zgaruvchilar:**

```kotlin
private lateinit var switchSoundAlerts: SwitchMaterial
private lateinit var switchHapticFeedback: SwitchMaterial
```

**onCreate(), loadSettings(), saveSettings() yangilash:**

```kotlin
// onCreate()
switchSoundAlerts = findViewById(R.id.switch_sound_alerts)
switchHapticFeedback = findViewById(R.id.switch_haptic_feedback)

// loadSettings()
val soundAlertsEnabled = prefs.getBoolean("sound_alerts_enabled", true)
switchSoundAlerts.isChecked = soundAlertsEnabled

val hapticEnabled = prefs.getBoolean("haptic_feedback_enabled", true)
switchHapticFeedback.isChecked = hapticEnabled

// saveSettings()
editor.putBoolean("sound_alerts_enabled", switchSoundAlerts.isChecked)
editor.putBoolean("haptic_feedback_enabled", switchHapticFeedback.isChecked)
```

**Test Stsenariylari:**

1. ✅ Tovush o'chirish → Anomaliya → Beep eshitilmaydi
2. ✅ Tebranish o'chirish → Tugma bosish → Tebranish yo'q

---

## 🎯 Phase 6: Final Testing va Polish

### Test Checklist

#### URL Scanner

- [ ] Manual URL input (valid)
- [ ] Manual URL input (invalid)
- [ ] Manual URL input (empty)
- [ ] External intent (Chrome, Telegram, etc.)
- [ ] Safe URL result
- [ ] Dangerous URL result
- [ ] Back button (manual mode)
- [ ] Back button (external intent)

#### Settings - Voice Alerts

- [ ] Switch yoqish/o'chirish
- [ ] Sozlamani saqlash
- [ ] Ilova qayta ochilganda saqlangan holat
- [ ] Anomaliya + ovoz (yoqilgan)
- [ ] Anomaliya + ovoz yo'q (o'chirilgan)

#### Settings - PIN Management

- [ ] PIN o'zgartirish (PIN mavjud)
- [ ] PIN o'zgartirish (PIN yo'q - disabled)
- [ ] PIN o'chirish
- [ ] Ilova qayta ochilganda PIN so'ralmaydi

#### Settings - Profile Reset

- [ ] Reset tasdiqlash dialogi
- [ ] Profil o'chirilishi
- [ ] Anomaliya ogohlantirishlari to'xtatilishi
- [ ] Yangi profil yaratilishi (3 kundan keyin)

#### Settings - Notifications

- [ ] Tovush o'chirish
- [ ] Tebranish o'chirish
- [ ] Sozlamalar saqlanishi

#### General

- [ ] Hech qanday crash yo'q
- [ ] Barcha tugmalar ishlaydi
- [ ] UI/UX silliq
- [ ] Animatsiyalar to'g'ri

---

## 📝 Implementation Order

1. **Phase 1** (URL Scanner) - ENG MUHIM
2. **Phase 2** (Voice Alerts) - OSON
3. **Phase 3** (PIN Management) - O'RTA
4. **Phase 4** (Profile Reset) - OSON
5. **Phase 5** (Notifications) - OSON
6. **Phase 6** (Testing) - MAJBURIY

---

## ⚠️ Xavf Nuqtalari

1. **URL Validation** - Regex to'g'ri ishlashi kerak
2. **PIN Management** - PinActivity MODE parametrini qo'llab-quvvatlashi kerak
3. **Profile Reset** - Barcha ma'lumotlar to'g'ri o'chirilishi kerak
4. **Layout Changes** - Scroll qilish kerak bo'lishi mumkin (ScrollView ichida)

---

## ✅ Success Criteria

- [ ] Foydalanuvchi URL kirita oladi
- [ ] Barcha sozlamalar ishlaydi
- [ ] Hech qanday crash yo'q
- [ ] UI/UX professional ko'rinadi
- [ ] Barcha testlar o'tadi

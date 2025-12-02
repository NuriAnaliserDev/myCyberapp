# Nuri Safety v2.0 (PhishGuard)

**Nuri Safety** is a comprehensive mobile security suite designed to protect Android users from modern web threats, including Phishing, AiTM (Adversary-in-the-Middle) attacks, and malicious APKs.

## 🚀 Features

### 🛡️ PhishGuard (URL Inspector)

- **Real-time URL Analysis**: Intercepts links before they open in the browser.
- **Punycode Detection**: Identifies homograph attacks (e.g., `xn--google-....com`).
- **Heuristic Scanning**: Detects suspicious keywords, IP-based domains, and redirect chains.
- **Backend Verification**: Checks URLs against a reputation engine.

### 🌐 Safe WebView (Anti-AiTM)

- **Sandboxed Browsing**: Opens risky links in a secure, isolated environment.
- **JavaScript Control**: JS is disabled by default to prevent script injection and credential harvesting.
- **Cookie Isolation**: Prevents session hijacking by blocking third-party cookies and storage.

### 🔍 App Scanner (Malware Detection)

- **Permission Analysis**: Scans installed apps for dangerous permissions (SMS, Camera, Accessibility).
- **Integrity Check**: Calculates SHA-256 signatures of APKs and verifies them against a known malware database.
- **Risk Scoring**: Assigns a risk score to each app based on its behavior.

### 🔐 Core Security (v1.0 Features)

- **VPN Service**: Encrypts network traffic and blocks malicious IPs.
- **Root Detection**: Warns if the device is rooted.
- **Biometric Lock**: Secures the app with Fingerprint/Face ID.

## 🛠️ Architecture

- **Android (Kotlin)**: MVVM architecture with modular design (`url_inspector`, `safe_webview`, `apk_scanner`).
- **Backend (FastAPI)**: Python-based reputation service for URL and APK analysis.
- **Database (Room)**: Local storage for logs and whitelist/blacklist.

## 📦 Installation

1. Clone the repository.
2. Open in Android Studio.
3. Sync Gradle and Run on Device/Emulator.
4. (Optional) Run the backend server: `uvicorn backend.main:app --reload`.

## ⚠️ Disclaimer

This app is for educational and defensive purposes only. Do not use it to analyze or interfere with systems you do not own.

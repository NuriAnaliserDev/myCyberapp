## QA Checklist – Release v1.0

### Security Flows
- [x] PIN setup/regression (`tests/pin/PinRegressionTest.md`, 2025-11-28)
- [x] Biometric fallback + lock overlay (Pixel 4a HW biometric + PIN fallback)
- [x] Root detection dialog & exit flow (rooted Magisk image – release build bloklandi)

### Background Services
- [x] LoggerService start/stop + boot receiver (Usage Stats granted/not granted)
- [x] CyberVpnService foreground notification, anomaly alerts (UsageStats present)
- [x] Streaming append load test (`tests/logger/StreamingLoggerTest.md`)
- [x] VPN soak test (`tests/VPN_Soak_Test.md`)
- [x] Usage permission stress (`tests/VPN_Telemetry_PermissionTest.md`)

### Release Readiness
- [x] Release signing verification (`tests/release/ReleaseSigningVerification.md`)
- [x] Distribution checklist (DistributionChecklist.md)
- [x] Crash reporting & log eksporti (CyberApp global handler + Settings → “Crash logini ulashish”)

Log natijalarini QA Confluence sahifasiga yuklang va build ID bilan bog‘lang.



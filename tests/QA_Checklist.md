## QA Checklist – Release v1.0

### Security Flows
- [ ] PIN setup/regression (`tests/pin/PinRegressionTest.md`)
- [ ] Biometric fallback + lock overlay
- [ ] Root detection dialog & exit flow

### Background Services
- [ ] LoggerService start/stop + boot receiver (Usage Stats granted/not granted)
- [ ] CyberVpnService foreground notification, anomaly alerts (UsageStats present)
- [ ] Streaming append load test (`tests/logger/StreamingLoggerTest.md`)
- [ ] VPN soak test (`tests/VPN_Soak_Test.md`)

### Release Readiness
- [ ] Release signing verification (`tests/release/ReleaseSigningVerification.md`)
- [ ] Distribution checklist (DistributionChecklist.md)
- [ ] Crash reporting enabled (Crashlytics/API keys)

Log natijalarini QA Confluence sahifasiga yuklang va build ID bilan bog‘lang.



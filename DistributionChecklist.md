## CyberApp Release Distribution Checklist

1. **Signing**
   - Release build `apksigner verify --print-certs` bilan tasdiqlandi.
   - Keystore hash’i `RELEASE_SIGNING_GUIDE.md` dagi referens bilan mos.
2. **Variantlar**
   - `assembleRelease` va `bundleRelease` ikkalasi ham bajarilgan.
   - `BuildConfig.VERSION_NAME` va changelog mos.
3. **Security toggles**
   - `android:debuggable=false`, `allowBackup=false`, `networkSecurityConfig` final.
4. **Store assets**
   - `fastlane/metadata` (yoki Play Console) dagi screenshots va privacy policy yangilangan.
5. **QA sign-off**
   - PIN regressiya testi (tests/pin/PinRegressionTest.md).
   - Logger/VPN soak test loglari biriktirilgan.
6. **Monitoring**
   - Crashlytics release tag yaratilgan.
   - Log retention siyosati yangilangan.







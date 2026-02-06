# TapAPDU

Android NFC APDU console for testing and learning. Tap an NFC card/tag (IsoDep) to send APDUs and view responses.

## ⚠️ Disclaimer

**FOR EDUCATIONAL AND AUTHORIZED TESTING ONLY**

- Use this tool only for learning, authorized security research, or testing your own devices
- Do not access payment cards or devices without explicit authorization
- Never capture, store, or share sensitive card data
- Users are solely responsible for compliance with applicable laws (CFAA, GDPR, PCI DSS, etc.)
- Developers disclaim all liability for misuse or illegal activities
- This software is provided "AS IS" without warranties

**By using this application, you agree to these terms and assume all legal risks.**

---

## Features

- Manual APDU send (hex)
- Auto scan common payment AIDs (PPSE/Visa/Mastercard/Amex/UnionPay/Discover/JCB)
- Trigger mobile wallet activation on target devices for NFC HCE testing
- On-screen log: Tx/Rx + status word (SW)

## Requirements

- Android Studio + JDK 17
- Android 9.0+ (minSdk 28)
- NFC-capable device (NFC is required by the manifest)

## Build & Run

```windows
.\gradlew.bat :app:assembleDebug
```

## Usage

1. Keep the app in the foreground.
2. Choose Manual or Auto Scan.
3. Tap an NFC card/tag or target device ( phone, watch, etc.) to send APDUs.

## License

MIT License. See LICENSE.

# TapAPDU

Android NFC APDU console for testing and learning. Tap an NFC card/tag (IsoDep) to send APDUs and view responses.

> Disclaimer: This app may display data returned by a payment card. Use only in legal, authorized scenarios.

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

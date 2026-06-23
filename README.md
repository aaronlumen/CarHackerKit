# CarHackerKit 🚗🔓

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

**An Android automotive security testing framework inspired by "The Car Hacker's Handbook" by Craig Smith.**

> ⚠️ **DISCLAIMER**: For authorized security research and educational purposes only. Never use on vehicles without explicit written permission. Always test on isolated benches — never on vehicles in motion or on public roads.

---

## What's New

- **⚙️ Gear Icon Connection Selector** — tap the gear icon in the toolbar to open a bottom sheet interface selector (Bluetooth Classic, BLE, Wi-Fi, USB/CAN, Simulator). Each interface type shows relevant config fields and a device list inline.
- **BLE scanning** — tap "Scan BLE" to discover nearby BLE OBD adapters live.
- **USB/CAN auto-detect** — connected USB serial devices are detected automatically.
- **Simulator mode** — test without any hardware.

---

## Features

### OBD-II Security Testing
- **PID Enumeration** — discover supported PIDs using standard queries (modes 0x00, 0x20, 0x40…)
- **Brute Force Discovery** — find undocumented PIDs through systematic probing (0x01–0xFF)
- **Manufacturer Mode Discovery** — probe modes 0x21–0x3E for proprietary functions
- **DTC Management** — read stored and pending trouble codes, clear with confirmation
- **Vehicle Info Extraction** — VIN, ECU name, calibration ID

### CAN Bus Analysis
- **Traffic Capture** — monitor and log CAN bus traffic
- **Pattern Detection** — identify counters, sensors, and constants
- **Arbitration ID Analysis** — map active message IDs and frequencies
- **Replay Attack Preparation** — capture and replay CAN sequences
- **Fuzzing Engine** — random, sequential, boundary, and bit-flip payloads

### Security Assessment
- **ECU Discovery** — enumerate connected ECUs via UDS
- **Authentication Testing** — test seed-key mechanisms
- **UDS Service Probing** — discover available diagnostic services
- **Vulnerability Reporting** — findings with severity, evidence, and remediation

---

## Architecture

```
CarHackerKit/
├── app/src/main/java/com/carhacker/kit/
│   ├── ui/
│   │   ├── MainActivity.kt              # Toolbar + gear icon wiring
│   │   ├── ConnectionSheetFragment.kt   # Bottom sheet: BT/BLE/WiFi/USB/Sim
│   │   └── LogAdapter.kt               # Console log recycler
│   ├── obd/
│   │   ├── OBDConnection.kt            # Interface + USB/BT/WiFi/Sim impls
│   │   ├── OBDProtocol.kt              # ELM327 command layer
│   │   └── PIDDefinitions.kt           # SAE J1979 PID database
│   ├── can/
│   │   └── CANProtocol.kt              # CAN frame capture/replay/fuzz
│   └── security/
│       └── SecurityTester.kt           # Full assessment orchestration
├── security/scripts/
│   ├── can_analyzer.py                 # Desktop CAN traffic analysis
│   └── obd_fuzzer.py                  # OBD-II fuzz case generator
└── app/src/main/res/
    ├── layout/
    │   ├── activity_main.xml            # Toolbar with gear icon
    │   └── fragment_connection_sheet.xml # Bottom sheet UI
    └── drawable/ic_gear.xml
```

---

## Quick Start

### Prerequisites

- Android Studio Hedgehog or later
- Android device with USB OTG support (API 26+)
- OBD-II adapter — USB, Bluetooth Classic, BLE, or Wi-Fi ELM327

### Build

```bash
git clone https://github.com/aaronlumen/CarHackerKit.git
cd CarHackerKit
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Connecting to a Vehicle

Tap the **⚙ gear icon** in the top-right of the toolbar to open the connection sheet.

| Interface | When to use |
|-----------|-------------|
| **Bluetooth** | ELM327 Bluetooth Classic (SPP) adapters |
| **BLE** | Bluetooth Low Energy OBD adapters |
| **Wi-Fi** | ELM327 Wi-Fi adapters (default 192.168.0.10:35000) |
| **USB/CAN** | CANtact, PCAN-USB, OBDLink SX via USB OTG |
| **Simulator** | Test without hardware |

Select an interface → configure → tap **Connect**.

---

## Supported Hardware

### OBD-II Adapters
| Adapter | Chip | Interface |
|---------|------|-----------|
| ELM327 USB | FTDI / CH340 / CP2102 | USB |
| ELM327 Bluetooth | — | Bluetooth Classic |
| ELM327 Wi-Fi | — | Wi-Fi TCP |
| OBDLink SX/MX | STN1110 | USB |

### CAN Analyzers
| Device | Notes |
|--------|-------|
| CANtact | Open source, SLCAN protocol |
| PEAK PCAN-USB | Professional |
| Kvaser Leaf | Professional |

---

## Security Testing Techniques

Based on methodologies from **"The Car Hacker's Handbook"** (Craig Smith):

- **Chapter 4** — OBD-II PID enumeration, service mode discovery
- **Chapter 5** — CAN traffic monitoring, pattern recognition, frequency analysis
- **Chapter 6** — Replay attacks, fuzzing, UDS authentication testing

---

## Python Tools

```bash
# Analyze a captured CAN log
python3 security/scripts/can_analyzer.py capture.log -o json

# Generate OBD-II fuzz cases
python3 security/scripts/obd_fuzzer.py --strategy boundary -o fuzz_cases.csv
```

---

## Safety Guidelines

1. Never test on moving vehicles
2. Never inject messages affecting brakes, steering, or throttle
3. Use isolated test benches only
4. Obtain explicit written permission before testing
5. Follow responsible disclosure practices

---

## References

- [The Car Hacker's Handbook](http://opengarages.org/handbook/) — Craig Smith
- [SAE J1979](https://www.sae.org/standards/content/j1979_202104/) — OBD-II PID Standard
- [ISO 14229](https://www.iso.org/standard/72439.html) — UDS Specification

## License

MIT — see [LICENSE](LICENSE)

**Author:** Aaron Surina · [github.com/aaronlumen](https://github.com/aaronlumen)

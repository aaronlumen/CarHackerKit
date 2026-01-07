# CarHackerKit
# CarHackerKit 🚗🔓

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

**An Android-based automotive security testing framework inspired by "The Car Hacker's Handbook" by Craig Smith.**

> ⚠️ **DISCLAIMER**: This tool is for authorized security research and educational purposes only. Never use on vehicles without explicit permission. Always test on isolated benches, never on vehicles in motion or public roads.

## Features

### OBD-II Security Testing
- 🔍 **PID Enumeration** - Discover supported PIDs using standard queries
- 💪 **Brute Force Discovery** - Find undocumented PIDs through systematic probing
- 📊 **Manufacturer Mode Discovery** - Probe modes 0x21-0x3E for proprietary functions
- 🔧 **DTC Management** - Read and clear diagnostic trouble codes
- 🚗 **Vehicle Info Extraction** - VIN, ECU names, calibration IDs

### CAN Bus Analysis
- 📡 **Traffic Capture** - Monitor and log CAN bus traffic
- 🔬 **Pattern Detection** - Identify counters, sensors, and constants
- 🎯 **Arbitration ID Analysis** - Map active message IDs and frequencies
- ⚡ **Replay Attack Preparation** - Capture and replay CAN sequences
- 🎲 **Fuzzing Engine** - Generate test payloads (random, sequential, boundary, bit-flip)

### Security Assessment
- 🛡️ **ECU Discovery** - Enumerate connected ECUs
- 🔐 **Authentication Testing** - Test seed-key mechanisms
- 📋 **UDS Service Probing** - Discover available diagnostic services
- 📝 **Vulnerability Reporting** - Generate comprehensive security reports

## Architecture

```
CarHackerKit/
├── app/                              # Android Application
│   ├── src/main/java/com/carhacker/kit/
│   │   ├── obd/                      # OBD-II Protocol Implementation
│   │   │   ├── OBDProtocol.kt        # Protocol handler & PID enumeration
│   │   │   ├── OBDConnection.kt      # USB/Bluetooth/WiFi connections
│   │   │   └── PIDDefinitions.kt     # SAE J1979 PID database
│   │   ├── can/                      # CAN Bus Implementation
│   │   │   └── CANProtocol.kt        # CAN frame parsing & analysis
│   │   ├── security/                 # Security Testing Framework
│   │   │   └── SecurityTester.kt     # Vulnerability assessment
│   │   └── ui/                       # User Interface
│   │       └── MainActivity.kt       # Main activity & controls
│   └── src/main/res/                 # Android Resources
├── security/                         # Python Security Tools
│   └── scripts/
│       ├── can_analyzer.py           # CAN traffic analysis
│       └── obd_fuzzer.py             # OBD-II fuzzing generator
└── docs/                             # Documentation
```

## Quick Start

### Prerequisites

- Android Studio Arctic Fox or later
- Android device with USB OTG support (API 26+)
- OBD-II adapter (USB, Bluetooth, or WiFi)
- **For CAN analysis**: CAN-capable adapter (e.g., CANtact, PCAN-USB)

### Building the APK

```bash
# Clone the repository
git clone https://github.com/aaronlumen/CarHackerKit.git
cd CarHackerKit

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### Installation

```bash
# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

### 1. Connect to Vehicle

Choose your connection method:
- **USB**: Connect OBD-II adapter via USB OTG
- **Bluetooth**: Pair with Bluetooth OBD-II adapter
- **WiFi**: Connect to WiFi OBD-II adapter (default: 192.168.0.10:35000)
- **Simulator**: Test without hardware

### 2. Basic Operations

```kotlin
// Enumerate supported PIDs
val supportedPIDs = obdProtocol.enumerateSupportedPIDs(0x01)

// Brute force undocumented PIDs
val foundPIDs = obdProtocol.bruteForcePIDs(0x01, 0x01, 0xFF)

// Read vehicle info
val vin = obdProtocol.getVIN()
val ecuName = obdProtocol.getECUName()

// Read/Clear DTCs
val dtcs = obdProtocol.readDTCs(0x03)
obdProtocol.clearDTCs()
```

### 3. Security Assessment

```kotlin
// Run full security scan
val securityTester = SecurityTester(obdProtocol, canProtocol)
val report = securityTester.runFullAssessment()

// Export report
val markdown = report.toMarkdown()
val json = report.toJSON()
```

### 4. Python Tools

```bash
# Analyze CAN traffic
python3 security/scripts/can_analyzer.py capture.log -o json

# Generate fuzz cases
python3 security/scripts/obd_fuzzer.py --strategy boundary -o fuzz_cases.csv
```

## Supported Hardware

### OBD-II Adapters
| Adapter Type | Chip | Connection |
|--------------|------|------------|
| ELM327 USB | FTDI/CH340/CP2102 | USB |
| ELM327 Bluetooth | - | Bluetooth SPP |
| ELM327 WiFi | - | TCP Socket |
| OBDLink SX/MX | STN1110 | USB |

### CAN Analyzers
| Device | Interface | Notes |
|--------|-----------|-------|
| CANtact | USB | Open source |
| PEAK PCAN-USB | USB | Professional |
| Kvaser Leaf | USB | Professional |

## Security Testing Techniques

Based on methodologies from "The Car Hacker's Handbook":

### Chapter 4: OBD-II Diagnostics
- PID support enumeration via 0x00, 0x20, 0x40... PIDs
- Service discovery across modes 0x01-0x0A
- Manufacturer-specific mode probing (0x21-0x3E)

### Chapter 5: CAN Bus Analysis
- Traffic monitoring and ID discovery
- Pattern recognition (counters, sensors, checksums)
- Frequency and timing analysis

### Chapter 6: Vehicle Attacks
- Replay attack preparation
- Fuzzing strategies
- UDS authentication testing

## Report Format

Security reports include:
- **Executive Summary**: Overall risk assessment
- **ECU Inventory**: Discovered control units
- **Service Map**: Available diagnostic services
- **Vulnerability Findings**: Categorized by severity (Critical/High/Medium/Low/Info)
- **Recommendations**: Remediation guidance

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Safety Guidelines

1. **Never test on moving vehicles**
2. **Never inject messages affecting brakes, steering, or throttle**
3. **Always use isolated test benches**
4. **Obtain explicit written permission before testing**
5. **Follow responsible disclosure practices**

## Legal Notice

This software is provided for educational and authorized security research purposes only. Users are responsible for ensuring compliance with all applicable laws and regulations. The authors assume no liability for misuse of this tool.

## References

- [The Car Hacker's Handbook](http://opengarages.org/handbook/) by Craig Smith
- [SAE J1979](https://www.sae.org/standards/content/j1979_202104/) - OBD-II PID Standards
- [ISO 15765](https://www.iso.org/standard/66574.html) - CAN Diagnostics
- [ISO 14229](https://www.iso.org/standard/72439.html) - UDS Specification

## License

MIT License - see [LICENSE](LICENSE) for details.

## Author

**Aaron Surina** - [github.com/aaronlumen](https://github.com/aaronlumen)

---

*Built with ❤️ for the automotive security research community*

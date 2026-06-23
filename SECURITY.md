# Security Policy

## Responsible Use

CarHackerKit is designed for **authorized security research and educational purposes only**. 

### Before Using This Tool

1. **Obtain Written Authorization** - Always get explicit permission from the vehicle owner before testing
2. **Use Isolated Test Benches** - Never test on vehicles that may be driven
3. **Avoid Safety-Critical Systems** - Never inject messages affecting brakes, steering, or throttle
4. **Follow Local Laws** - Ensure compliance with computer fraud and vehicle tampering laws

## Reporting Security Issues

If you discover a security vulnerability in CarHackerKit itself:

1. **Do NOT** open a public issue
2. Email security concerns to the maintainer
3. Include detailed steps to reproduce
4. Allow 90 days for a fix before public disclosure

## Reporting Vehicle Vulnerabilities

If you discover vulnerabilities in vehicle systems using this tool:

1. **Follow responsible disclosure** - Contact the manufacturer first
2. **Use coordinated disclosure** - Work with CERT/CC or Auto-ISAC
3. **Do not publish exploits** for safety-critical systems without coordination
4. **Consider public safety** - Some issues may warrant longer embargo periods

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |

## Security Features

This tool includes safety measures:
- Rate limiting on OBD-II queries to prevent ECU flooding
- Warnings before potentially dangerous operations
- Logging of all diagnostic commands
- Simulation mode for testing without hardware

## Disclaimer

The authors of CarHackerKit are not responsible for:
- Damage to vehicles or property
- Injury resulting from misuse
- Legal consequences of unauthorized testing
- Warranty voiding on tested vehicles

Use this tool responsibly and ethically.

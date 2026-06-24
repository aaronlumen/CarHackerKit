package com.carhacker.kit.obd

/**
 * OBD-II PID Definitions
 * Comprehensive database of standard PIDs
 * 
 * Reference: SAE J1979, ISO 15031-5
 */
object PIDDefinitions {
    
    // Mode 01 - Current Data
    val MODE_01_PIDS = mapOf(
        0x00 to PIDInfo("PIDs supported [01-20]", "Bitmap", 4, ::decodeBitmap),
        0x01 to PIDInfo("Monitor status since DTCs cleared", "Status", 4, ::decodeMonitorStatus),
        0x02 to PIDInfo("Freeze DTC", "DTC", 2, ::decodeFreezeDTC),
        0x03 to PIDInfo("Fuel system status", "Status", 2, ::decodeFuelSystemStatus),
        0x04 to PIDInfo("Calculated engine load", "%", 1, ::decodePercentage),
        0x05 to PIDInfo("Engine coolant temperature", "°C", 1, ::decodeTempA),
        0x06 to PIDInfo("Short term fuel trim - Bank 1", "%", 1, ::decodeFuelTrim),
        0x07 to PIDInfo("Long term fuel trim - Bank 1", "%", 1, ::decodeFuelTrim),
        0x08 to PIDInfo("Short term fuel trim - Bank 2", "%", 1, ::decodeFuelTrim),
        0x09 to PIDInfo("Long term fuel trim - Bank 2", "%", 1, ::decodeFuelTrim),
        0x0A to PIDInfo("Fuel pressure", "kPa", 1, ::decodeFuelPressure),
        0x0B to PIDInfo("Intake manifold absolute pressure", "kPa", 1, ::decodeIntakeMAP),
        0x0C to PIDInfo("Engine RPM", "rpm", 2, ::decodeRPM),
        0x0D to PIDInfo("Vehicle speed", "km/h", 1, ::decodeSpeed),
        0x0E to PIDInfo("Timing advance", "°", 1, ::decodeTimingAdvance),
        0x0F to PIDInfo("Intake air temperature", "°C", 1, ::decodeTempA),
        0x10 to PIDInfo("MAF air flow rate", "g/s", 2, ::decodeMAF),
        0x11 to PIDInfo("Throttle position", "%", 1, ::decodePercentage),
        0x12 to PIDInfo("Commanded secondary air status", "Status", 1, ::decodeAirStatus),
        0x13 to PIDInfo("Oxygen sensors present (2 banks)", "Bitmap", 1, ::decodeBitmap),
        0x14 to PIDInfo("O2 Sensor 1 - Voltage, Short term fuel trim", "V, %", 2, ::decodeO2Sensor),
        0x15 to PIDInfo("O2 Sensor 2 - Voltage, Short term fuel trim", "V, %", 2, ::decodeO2Sensor),
        0x16 to PIDInfo("O2 Sensor 3 - Voltage, Short term fuel trim", "V, %", 2, ::decodeO2Sensor),
        0x17 to PIDInfo("O2 Sensor 4 - Voltage, Short term fuel trim", "V, %", 2, ::decodeO2Sensor),
        0x18 to PIDInfo("O2 Sensor 5 - Voltage, Short term fuel trim", "V, %", 2, ::decodeO2Sensor),
        0x19 to PIDInfo("O2 Sensor 6 - Voltage, Short term fuel trim", "V, %", 2, ::decodeO2Sensor),
        0x1A to PIDInfo("O2 Sensor 7 - Voltage, Short term fuel trim", "V, %", 2, ::decodeO2Sensor),
        0x1B to PIDInfo("O2 Sensor 8 - Voltage, Short term fuel trim", "V, %", 2, ::decodeO2Sensor),
        0x1C to PIDInfo("OBD standards compliance", "Type", 1, ::decodeOBDStandard),
        0x1D to PIDInfo("Oxygen sensors present (4 banks)", "Bitmap", 1, ::decodeBitmap),
        0x1E to PIDInfo("Auxiliary input status", "Status", 1, ::decodeAuxInput),
        0x1F to PIDInfo("Run time since engine start", "seconds", 2, ::decodeRuntime),
        
        0x20 to PIDInfo("PIDs supported [21-40]", "Bitmap", 4, ::decodeBitmap),
        0x21 to PIDInfo("Distance traveled with MIL on", "km", 2, ::decodeDistance),
        0x22 to PIDInfo("Fuel rail pressure (relative to manifold vacuum)", "kPa", 2, ::decodeFuelRailPressure),
        0x23 to PIDInfo("Fuel rail gauge pressure (diesel)", "kPa", 2, ::decodeFuelRailGaugePressure),
        0x24 to PIDInfo("O2 Sensor 1 - Fuel-air equivalence ratio, Voltage", "ratio, V", 4, ::decodeO2SensorWideband),
        0x25 to PIDInfo("O2 Sensor 2 - Fuel-air equivalence ratio, Voltage", "ratio, V", 4, ::decodeO2SensorWideband),
        0x26 to PIDInfo("O2 Sensor 3 - Fuel-air equivalence ratio, Voltage", "ratio, V", 4, ::decodeO2SensorWideband),
        0x27 to PIDInfo("O2 Sensor 4 - Fuel-air equivalence ratio, Voltage", "ratio, V", 4, ::decodeO2SensorWideband),
        0x28 to PIDInfo("O2 Sensor 5 - Fuel-air equivalence ratio, Voltage", "ratio, V", 4, ::decodeO2SensorWideband),
        0x29 to PIDInfo("O2 Sensor 6 - Fuel-air equivalence ratio, Voltage", "ratio, V", 4, ::decodeO2SensorWideband),
        0x2A to PIDInfo("O2 Sensor 7 - Fuel-air equivalence ratio, Voltage", "ratio, V", 4, ::decodeO2SensorWideband),
        0x2B to PIDInfo("O2 Sensor 8 - Fuel-air equivalence ratio, Voltage", "ratio, V", 4, ::decodeO2SensorWideband),
        0x2C to PIDInfo("Commanded EGR", "%", 1, ::decodePercentage),
        0x2D to PIDInfo("EGR error", "%", 1, ::decodeEGRError),
        0x2E to PIDInfo("Commanded evaporative purge", "%", 1, ::decodePercentage),
        0x2F to PIDInfo("Fuel tank level input", "%", 1, ::decodePercentage),
        0x30 to PIDInfo("Warm-ups since codes cleared", "count", 1, ::decodeCount),
        0x31 to PIDInfo("Distance traveled since codes cleared", "km", 2, ::decodeDistance),
        0x32 to PIDInfo("Evap system vapor pressure", "Pa", 2, ::decodeEvapPressure),
        0x33 to PIDInfo("Absolute barometric pressure", "kPa", 1, ::decodeBarometricPressure),
        0x34 to PIDInfo("O2 Sensor 1 - Fuel-air equivalence ratio, Current", "ratio, mA", 4, ::decodeO2SensorCurrent),
        0x35 to PIDInfo("O2 Sensor 2 - Fuel-air equivalence ratio, Current", "ratio, mA", 4, ::decodeO2SensorCurrent),
        0x36 to PIDInfo("O2 Sensor 3 - Fuel-air equivalence ratio, Current", "ratio, mA", 4, ::decodeO2SensorCurrent),
        0x37 to PIDInfo("O2 Sensor 4 - Fuel-air equivalence ratio, Current", "ratio, mA", 4, ::decodeO2SensorCurrent),
        0x38 to PIDInfo("O2 Sensor 5 - Fuel-air equivalence ratio, Current", "ratio, mA", 4, ::decodeO2SensorCurrent),
        0x39 to PIDInfo("O2 Sensor 6 - Fuel-air equivalence ratio, Current", "ratio, mA", 4, ::decodeO2SensorCurrent),
        0x3A to PIDInfo("O2 Sensor 7 - Fuel-air equivalence ratio, Current", "ratio, mA", 4, ::decodeO2SensorCurrent),
        0x3B to PIDInfo("O2 Sensor 8 - Fuel-air equivalence ratio, Current", "ratio, mA", 4, ::decodeO2SensorCurrent),
        0x3C to PIDInfo("Catalyst temperature: Bank 1, Sensor 1", "°C", 2, ::decodeCatalystTemp),
        0x3D to PIDInfo("Catalyst temperature: Bank 2, Sensor 1", "°C", 2, ::decodeCatalystTemp),
        0x3E to PIDInfo("Catalyst temperature: Bank 1, Sensor 2", "°C", 2, ::decodeCatalystTemp),
        0x3F to PIDInfo("Catalyst temperature: Bank 2, Sensor 2", "°C", 2, ::decodeCatalystTemp),
        
        0x40 to PIDInfo("PIDs supported [41-60]", "Bitmap", 4, ::decodeBitmap),
        0x41 to PIDInfo("Monitor status this drive cycle", "Status", 4, ::decodeMonitorStatus),
        0x42 to PIDInfo("Control module voltage", "V", 2, ::decodeControlModuleVoltage),
        0x43 to PIDInfo("Absolute load value", "%", 2, ::decodeAbsoluteLoad),
        0x44 to PIDInfo("Fuel-air commanded equivalence ratio", "ratio", 2, ::decodeFuelAirRatio),
        0x45 to PIDInfo("Relative throttle position", "%", 1, ::decodePercentage),
        0x46 to PIDInfo("Ambient air temperature", "°C", 1, ::decodeTempA),
        0x47 to PIDInfo("Absolute throttle position B", "%", 1, ::decodePercentage),
        0x48 to PIDInfo("Absolute throttle position C", "%", 1, ::decodePercentage),
        0x49 to PIDInfo("Accelerator pedal position D", "%", 1, ::decodePercentage),
        0x4A to PIDInfo("Accelerator pedal position E", "%", 1, ::decodePercentage),
        0x4B to PIDInfo("Accelerator pedal position F", "%", 1, ::decodePercentage),
        0x4C to PIDInfo("Commanded throttle actuator", "%", 1, ::decodePercentage),
        0x4D to PIDInfo("Time run with MIL on", "minutes", 2, ::decodeMinutes),
        0x4E to PIDInfo("Time since trouble codes cleared", "minutes", 2, ::decodeMinutes),
        0x4F to PIDInfo("Maximum values", "Various", 4, ::decodeMaxValues),
        0x50 to PIDInfo("Maximum MAF", "g/s", 4, ::decodeMaxMAF),
        0x51 to PIDInfo("Fuel type", "Type", 1, ::decodeFuelType),
        0x52 to PIDInfo("Ethanol fuel %", "%", 1, ::decodePercentage),
        0x53 to PIDInfo("Absolute evap system vapor pressure", "kPa", 2, ::decodeAbsEvapPressure),
        0x54 to PIDInfo("Evap system vapor pressure", "Pa", 2, ::decodeEvapPressure2),
        0x55 to PIDInfo("Short term secondary O2 trim - Bank 1 & 3", "%", 2, ::decodeSecondaryO2Trim),
        0x56 to PIDInfo("Long term secondary O2 trim - Bank 1 & 3", "%", 2, ::decodeSecondaryO2Trim),
        0x57 to PIDInfo("Short term secondary O2 trim - Bank 2 & 4", "%", 2, ::decodeSecondaryO2Trim),
        0x58 to PIDInfo("Long term secondary O2 trim - Bank 2 & 4", "%", 2, ::decodeSecondaryO2Trim),
        0x59 to PIDInfo("Fuel rail absolute pressure", "kPa", 2, ::decodeFuelRailAbsPressure),
        0x5A to PIDInfo("Relative accelerator pedal position", "%", 1, ::decodePercentage),
        0x5B to PIDInfo("Hybrid battery pack remaining life", "%", 1, ::decodePercentage),
        0x5C to PIDInfo("Engine oil temperature", "°C", 1, ::decodeTempA),
        0x5D to PIDInfo("Fuel injection timing", "°", 2, ::decodeInjectionTiming),
        0x5E to PIDInfo("Engine fuel rate", "L/h", 2, ::decodeFuelRate),
        0x5F to PIDInfo("Emission requirements", "Type", 1, ::decodeEmissionReq),
        
        0x60 to PIDInfo("PIDs supported [61-80]", "Bitmap", 4, ::decodeBitmap),
        0x61 to PIDInfo("Driver's demand engine - percent torque", "%", 1, ::decodeTorque),
        0x62 to PIDInfo("Actual engine - percent torque", "%", 1, ::decodeTorque),
        0x63 to PIDInfo("Engine reference torque", "Nm", 2, ::decodeReferenceTorque),
        0x64 to PIDInfo("Engine percent torque data", "%", 5, ::decodeTorqueData),
        0x65 to PIDInfo("Auxiliary input / output supported", "Bitmap", 2, ::decodeBitmap),
        0x66 to PIDInfo("Mass air flow sensor", "g/s", 5, ::decodeMAFSensor),
        0x67 to PIDInfo("Engine coolant temperature", "°C", 3, ::decodeCoolantTemp2),
        0x68 to PIDInfo("Intake air temperature sensor", "°C", 7, ::decodeIntakeTemp2),
        
        0x80 to PIDInfo("PIDs supported [81-A0]", "Bitmap", 4, ::decodeBitmap),
        0xA0 to PIDInfo("PIDs supported [A1-C0]", "Bitmap", 4, ::decodeBitmap),
        0xC0 to PIDInfo("PIDs supported [C1-E0]", "Bitmap", 4, ::decodeBitmap)
    )
    
    // Mode 09 - Vehicle Information
    val MODE_09_PIDS = mapOf(
        0x00 to PIDInfo("Mode 09 supported PIDs", "Bitmap", 4, ::decodeBitmap),
        0x01 to PIDInfo("VIN Message Count", "count", 1, ::decodeCount),
        0x02 to PIDInfo("Vehicle Identification Number (VIN)", "VIN", 17, ::decodeVIN),
        0x03 to PIDInfo("Calibration ID Message Count", "count", 1, ::decodeCount),
        0x04 to PIDInfo("Calibration ID", "ID", 16, ::decodeASCII),
        0x05 to PIDInfo("Calibration Verification Numbers Count", "count", 1, ::decodeCount),
        0x06 to PIDInfo("Calibration Verification Numbers", "CVN", 4, ::decodeHex),
        0x07 to PIDInfo("In-use Performance Tracking Message Count", "count", 1, ::decodeCount),
        0x08 to PIDInfo("In-use Performance Tracking (spark ignition)", "Various", 4, ::decodeIPT),
        0x09 to PIDInfo("ECU Name Message Count", "count", 1, ::decodeCount),
        0x0A to PIDInfo("ECU Name", "Name", 20, ::decodeASCII),
        0x0B to PIDInfo("In-use Performance Tracking (compression ignition)", "Various", 4, ::decodeIPT)
    )
    
    // Decoding functions
    private fun decodeBitmap(data: ByteArray): String {
        return data.joinToString("") { String.format("%08d", it.toInt().and(0xFF).toString(2).toInt()) }
    }
    
    private fun decodeMonitorStatus(data: ByteArray): String = "Status: ${data.toHexString()}"
    
    private fun decodeFreezeDTC(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val dtcHigh = data[0].toInt() and 0xFF
        val dtcLow = data[1].toInt() and 0xFF
        return if (dtcHigh == 0 && dtcLow == 0) "No freeze frame DTC" else "DTC: ${formatDTC(dtcHigh, dtcLow)}"
    }
    
    private fun decodeFuelSystemStatus(data: ByteArray): String {
        val status = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return when (status) {
            0x01 -> "Open loop (insufficient temp)"
            0x02 -> "Closed loop (using O2)"
            0x04 -> "Open loop (engine load/fuel cut)"
            0x08 -> "Open loop (system failure)"
            0x10 -> "Closed loop (feedback fault)"
            else -> "Unknown ($status)"
        }
    }
    
    private fun decodePercentage(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return String.format("%.1f%%", a * 100.0 / 255.0)
    }
    
    private fun decodeTempA(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return "${a - 40}°C"
    }
    
    private fun decodeFuelTrim(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return String.format("%.1f%%", (a - 128) * 100.0 / 128.0)
    }
    
    private fun decodeFuelPressure(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return "${a * 3} kPa"
    }
    
    private fun decodeIntakeMAP(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return "$a kPa"
    }
    
    private fun decodeRPM(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val a = data[0].toInt() and 0xFF
        val b = data[1].toInt() and 0xFF
        return "${(256 * a + b) / 4} rpm"
    }
    
    private fun decodeSpeed(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return "$a km/h"
    }
    
    private fun decodeTimingAdvance(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return String.format("%.1f°", (a - 128) / 2.0)
    }
    
    private fun decodeMAF(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val a = data[0].toInt() and 0xFF
        val b = data[1].toInt() and 0xFF
        return String.format("%.2f g/s", (256 * a + b) / 100.0)
    }
    
    private fun decodeAirStatus(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return when (a) {
            0x01 -> "Upstream"
            0x02 -> "Downstream of catalytic converter"
            0x04 -> "From outside atmosphere or off"
            0x08 -> "Pump commanded on for diagnostics"
            else -> "Unknown ($a)"
        }
    }
    
    private fun decodeO2Sensor(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val voltage = (data[0].toInt() and 0xFF) / 200.0
        val trim = if (data[1].toInt() and 0xFF == 0xFF) "N/A" else String.format("%.1f%%", ((data[1].toInt() and 0xFF) - 128) * 100.0 / 128.0)
        return String.format("%.3fV, %s", voltage, trim)
    }
    
    private fun decodeOBDStandard(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return when (a) {
            1 -> "OBD-II (CARB)"
            2 -> "OBD (EPA)"
            3 -> "OBD and OBD-II"
            4 -> "OBD-I"
            5 -> "Not OBD compliant"
            6 -> "EOBD (Europe)"
            7 -> "EOBD and OBD-II"
            8 -> "EOBD and OBD"
            9 -> "EOBD, OBD, OBD-II"
            10 -> "JOBD (Japan)"
            11 -> "JOBD and OBD-II"
            12 -> "JOBD and EOBD"
            13 -> "JOBD, EOBD, OBD-II"
            else -> "Unknown ($a)"
        }
    }
    
    private fun decodeAuxInput(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return if (a and 0x01 != 0) "PTO active" else "PTO inactive"
    }
    
    private fun decodeRuntime(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val seconds = (data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)
        return "${seconds}s (${seconds / 60}m ${seconds % 60}s)"
    }
    
    private fun decodeDistance(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val km = (data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)
        return "$km km"
    }
    
    private fun decodeFuelRailPressure(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val kpa = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) * 0.079
        return String.format("%.1f kPa", kpa)
    }
    
    private fun decodeFuelRailGaugePressure(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val kpa = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) * 10
        return "$kpa kPa"
    }
    
    private fun decodeO2SensorWideband(data: ByteArray): String {
        if (data.size < 4) return "N/A"
        val ratio = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) * 2 / 65536.0
        val voltage = ((data[2].toInt() and 0xFF) * 256 + (data[3].toInt() and 0xFF)) * 8 / 65536.0
        return String.format("λ=%.3f, %.3fV", ratio, voltage)
    }
    
    private fun decodeEGRError(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return String.format("%.1f%%", (a - 128) * 100.0 / 128.0)
    }
    
    private fun decodeCount(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return "$a"
    }
    
    private fun decodeEvapPressure(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val a = data[0].toInt() // signed
        val b = data[1].toInt() and 0xFF
        val pa = (a * 256 + b) / 4.0
        return String.format("%.2f Pa", pa)
    }
    
    private fun decodeBarometricPressure(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return "$a kPa"
    }
    
    private fun decodeO2SensorCurrent(data: ByteArray): String {
        if (data.size < 4) return "N/A"
        val ratio = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) * 2 / 65536.0
        val current = ((data[2].toInt() and 0xFF) * 256 + (data[3].toInt() and 0xFF)) / 256.0 - 128
        return String.format("λ=%.3f, %.3fmA", ratio, current)
    }
    
    private fun decodeCatalystTemp(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val temp = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) / 10.0 - 40
        return String.format("%.1f°C", temp)
    }
    
    private fun decodeControlModuleVoltage(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val voltage = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) / 1000.0
        return String.format("%.3fV", voltage)
    }
    
    private fun decodeAbsoluteLoad(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val load = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) * 100.0 / 255.0
        return String.format("%.1f%%", load)
    }
    
    private fun decodeFuelAirRatio(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val ratio = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) * 2 / 65536.0
        return String.format("λ=%.4f", ratio)
    }
    
    private fun decodeMinutes(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val minutes = (data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)
        return "$minutes min (${minutes / 60}h ${minutes % 60}m)"
    }
    
    private fun decodeMaxValues(data: ByteArray): String = data.toHexString()
    private fun decodeMaxMAF(data: ByteArray): String = data.toHexString()
    
    private fun decodeFuelType(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return when (a) {
            0 -> "Not available"
            1 -> "Gasoline"
            2 -> "Methanol"
            3 -> "Ethanol"
            4 -> "Diesel"
            5 -> "LPG"
            6 -> "CNG"
            7 -> "Propane"
            8 -> "Electric"
            9 -> "Bifuel (Gasoline)"
            10 -> "Bifuel (Methanol)"
            11 -> "Bifuel (Ethanol)"
            12 -> "Bifuel (LPG)"
            13 -> "Bifuel (CNG)"
            14 -> "Bifuel (Propane)"
            15 -> "Bifuel (Electric)"
            16 -> "Bifuel (Electric/Combustion)"
            17 -> "Hybrid (Gasoline)"
            18 -> "Hybrid (Ethanol)"
            19 -> "Hybrid (Diesel)"
            20 -> "Hybrid (Electric)"
            21 -> "Hybrid (Mixed)"
            22 -> "Hybrid (Regenerative)"
            else -> "Unknown ($a)"
        }
    }
    
    private fun decodeAbsEvapPressure(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val kpa = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) / 200.0
        return String.format("%.3f kPa", kpa)
    }
    
    private fun decodeEvapPressure2(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val pa = (data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF) - 32767
        return "$pa Pa"
    }
    
    private fun decodeSecondaryO2Trim(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val a = (data[0].toInt() and 0xFF) - 128
        val b = (data[1].toInt() and 0xFF) - 128
        return String.format("%.1f%%, %.1f%%", a * 100.0 / 128.0, b * 100.0 / 128.0)
    }
    
    private fun decodeFuelRailAbsPressure(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val kpa = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) * 10
        return "$kpa kPa"
    }
    
    private fun decodeInjectionTiming(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val timing = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) / 128.0 - 210
        return String.format("%.2f°", timing)
    }
    
    private fun decodeFuelRate(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val rate = ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) / 20.0
        return String.format("%.2f L/h", rate)
    }
    
    private fun decodeEmissionReq(data: ByteArray): String = data.toHexString()
    private fun decodeTorque(data: ByteArray): String {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return "N/A"
        return "${a - 125}%"
    }
    
    private fun decodeReferenceTorque(data: ByteArray): String {
        if (data.size < 2) return "N/A"
        val nm = (data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)
        return "$nm Nm"
    }
    
    private fun decodeTorqueData(data: ByteArray): String = data.toHexString()
    private fun decodeMAFSensor(data: ByteArray): String = data.toHexString()
    private fun decodeCoolantTemp2(data: ByteArray): String = data.toHexString()
    private fun decodeIntakeTemp2(data: ByteArray): String = data.toHexString()
    private fun decodeVIN(data: ByteArray): String = data.map { it.toInt().toChar() }.filter { it.isLetterOrDigit() }.joinToString("")
    private fun decodeASCII(data: ByteArray): String = data.map { it.toInt().toChar() }.filter { it.code >= 32 }.joinToString("")
    private fun decodeHex(data: ByteArray): String = data.toHexString()
    private fun decodeIPT(data: ByteArray): String = data.toHexString()
    
    private fun formatDTC(high: Int, low: Int): String {
        val typeCode = (high shr 6) and 0x03
        val prefix = when (typeCode) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            3 -> "U"
            else -> "?"
        }
        val digit1 = (high shr 4) and 0x03
        val digit2 = high and 0x0F
        val digit3 = (low shr 4) and 0x0F
        val digit4 = low and 0x0F
        return "$prefix$digit1${String.format("%X", digit2)}${String.format("%X", digit3)}${String.format("%X", digit4)}"
    }
    
    private fun ByteArray.toHexString(): String = joinToString(" ") { String.format("%02X", it) }
}

data class PIDInfo(
    val name: String,
    val unit: String,
    val bytes: Int,
    val decoder: (ByteArray) -> String
)

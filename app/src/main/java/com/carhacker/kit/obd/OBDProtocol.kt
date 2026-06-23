package com.carhacker.kit.obd

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * OBD-II Protocol Handler
 * 
 * Implements techniques from "The Car Hacker's Handbook" Chapter 4:
 * - Standard OBD-II PID enumeration
 * - Mode discovery and brute forcing
 * - Response parsing and validation
 * - ECU identification
 */
class OBDProtocol(private val connection: OBDConnection) {
    
    companion object {
        // OBD-II Modes (Services)
        const val MODE_CURRENT_DATA = 0x01
        const val MODE_FREEZE_FRAME = 0x02
        const val MODE_DTC_STORED = 0x03
        const val MODE_DTC_CLEAR = 0x04
        const val MODE_O2_TEST = 0x05
        const val MODE_TEST_RESULTS = 0x06
        const val MODE_DTC_PENDING = 0x07
        const val MODE_CONTROL = 0x08
        const val MODE_VEHICLE_INFO = 0x09
        const val MODE_PERMANENT_DTC = 0x0A
        
        // Extended/Manufacturer modes (0x21-0x3E)
        const val MODE_MANUFACTURER_START = 0x21
        const val MODE_MANUFACTURER_END = 0x3E
        
        // Response offset
        const val RESPONSE_MODE_OFFSET = 0x40
        
        // Timing
        const val QUERY_TIMEOUT_MS = 2000L
        const val INTER_QUERY_DELAY_MS = 50L
    }
    
    private val discoveredPIDs = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val ecuAddresses = mutableSetOf<Int>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Event flow for real-time updates
    private val _events = MutableSharedFlow<OBDEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<OBDEvent> = _events.asSharedFlow()
    
    /**
     * Initialize connection with ELM327 adapter
     * Sets up protocol auto-detection
     */
    suspend fun initialize(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Reset adapter
            val reset = sendCommand("ATZ")
            delay(1000)
            
            // Disable echo
            sendCommand("ATE0")
            
            // Disable line feeds  
            sendCommand("ATL0")
            
            // Disable spaces in responses
            sendCommand("ATS0")
            
            // Set protocol to auto
            sendCommand("ATSP0")
            
            // Enable headers (shows ECU addresses)
            sendCommand("ATH1")
            
            // Get adapter info
            val info = sendCommand("ATI")
            
            _events.emit(OBDEvent.Connected(info))
            info
        }
    }
    
    /**
     * Enumerate supported PIDs for a mode
     * Uses the PID support PIDs (0x00, 0x20, 0x40, etc.)
     * 
     * From Car Hacker's Handbook: "PIDs 0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0
     * are special PIDs that tell you which PIDs the vehicle supports"
     */
    suspend fun enumerateSupportedPIDs(mode: Int): Set<Int> = withContext(Dispatchers.IO) {
        val supported = mutableSetOf<Int>()
        
        // Support PIDs are at 0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0
        val supportPIDs = listOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0)
        
        for (supportPID in supportPIDs) {
            val response = queryPID(mode, supportPID)
            if (response.isSuccess) {
                val data = response.getOrNull()?.data ?: continue
                val pidOffset = supportPID + 1
                
                // Each bit represents support for next 32 PIDs
                for (byteIndex in data.indices) {
                    val byte = data[byteIndex]
                    for (bitIndex in 0..7) {
                        if ((byte.toInt() and (0x80 shr bitIndex)) != 0) {
                            val pid = pidOffset + (byteIndex * 8) + bitIndex
                            supported.add(pid)
                        }
                    }
                }
                
                // Check if next support PID is supported
                val nextSupportPID = supportPID + 0x20
                if (nextSupportPID !in supported && supportPID != 0xE0) {
                    break // No more PIDs to check
                }
            } else {
                if (supportPID == 0x00) {
                    // Mode not supported at all
                    break
                }
            }
            
            delay(INTER_QUERY_DELAY_MS)
        }
        
        discoveredPIDs[mode] = supported
        _events.emit(OBDEvent.PIDsEnumerated(mode, supported))
        supported
    }
    
    /**
     * Brute force PID discovery
     * Tests each PID individually - slower but finds undocumented PIDs
     * 
     * WARNING: Use with caution - can trigger unexpected behavior
     */
    suspend fun bruteForcePIDs(
        mode: Int,
        startPID: Int = 0x01,
        endPID: Int = 0xFF,
        onProgress: (Int, Int, Boolean) -> Unit = { _, _, _ -> }
    ): Set<Int> = withContext(Dispatchers.IO) {
        val supported = mutableSetOf<Int>()
        
        for (pid in startPID..endPID) {
            // Skip known support PIDs in brute force
            if (pid % 0x20 == 0) continue
            
            val response = queryPID(mode, pid)
            val isSupported = response.isSuccess && response.getOrNull()?.data?.isNotEmpty() == true
            
            if (isSupported) {
                supported.add(pid)
            }
            
            onProgress(pid, endPID - startPID + 1, isSupported)
            delay(INTER_QUERY_DELAY_MS)
        }
        
        _events.emit(OBDEvent.BruteForcComplete(mode, supported))
        supported
    }
    
    /**
     * Discover manufacturer-specific modes
     * Probes modes 0x21-0x3E for valid responses
     */
    suspend fun discoverManufacturerModes(): Map<Int, Set<Int>> = withContext(Dispatchers.IO) {
        val manufacturerModes = mutableMapOf<Int, Set<Int>>()
        
        for (mode in MODE_MANUFACTURER_START..MODE_MANUFACTURER_END) {
            // Try PID 0x00 first
            val response = queryPID(mode, 0x00)
            if (response.isSuccess) {
                // Found a valid mode, enumerate its PIDs
                val pids = enumerateSupportedPIDs(mode)
                if (pids.isNotEmpty()) {
                    manufacturerModes[mode] = pids
                }
            }
            delay(INTER_QUERY_DELAY_MS)
        }
        
        _events.emit(OBDEvent.ManufacturerModesDiscovered(manufacturerModes))
        manufacturerModes
    }
    
    /**
     * Query a specific PID
     */
    suspend fun queryPID(mode: Int, pid: Int): Result<OBDResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val command = String.format("%02X%02X", mode, pid)
            val rawResponse = sendCommand(command)
            parseResponse(mode, pid, rawResponse)
        }
    }
    
    /**
     * Query multiple PIDs in rapid succession
     * More efficient than individual queries
     */
    suspend fun queryPIDs(mode: Int, pids: List<Int>): List<Result<OBDResponse>> {
        return pids.map { pid ->
            val result = queryPID(mode, pid)
            delay(INTER_QUERY_DELAY_MS)
            result
        }
    }
    
    /**
     * Get Vehicle Identification Number (VIN)
     * Mode 09, PID 02
     */
    suspend fun getVIN(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = sendCommand("0902")
            parseVIN(response)
        }
    }
    
    /**
     * Get ECU Name
     * Mode 09, PID 0A
     */
    suspend fun getECUName(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = sendCommand("090A")
            parseASCII(response)
        }
    }
    
    /**
     * Get Calibration ID
     * Mode 09, PID 04
     */
    suspend fun getCalibrationID(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = sendCommand("0904")
            parseASCII(response)
        }
    }
    
    /**
     * Read Diagnostic Trouble Codes (DTCs)
     * Mode 03 for stored, Mode 07 for pending, Mode 0A for permanent
     */
    suspend fun readDTCs(mode: Int = MODE_DTC_STORED): Result<List<DTC>> = withContext(Dispatchers.IO) {
        runCatching {
            val command = String.format("%02X", mode)
            val response = sendCommand(command)
            parseDTCs(response)
        }
    }
    
    /**
     * Clear DTCs
     * Mode 04
     */
    suspend fun clearDTCs(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = sendCommand("04")
            response.contains("44") || response.contains("OK")
        }
    }
    
    /**
     * Send raw command to adapter
     */
    suspend fun sendCommand(command: String): String = withContext(Dispatchers.IO) {
        connection.send("$command\r")
        val response = connection.receive(QUERY_TIMEOUT_MS)
        
        _events.emit(OBDEvent.CommandSent(command, response))
        response
    }
    
    /**
     * Parse OBD response
     */
    private fun parseResponse(mode: Int, pid: Int, raw: String): OBDResponse {
        // Clean response
        val cleaned = raw.replace("\\s".toRegex(), "")
            .replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
        
        // Check for errors
        if (cleaned.contains("NODATA") || cleaned.contains("ERROR") || 
            cleaned.contains("?") || cleaned.contains("UNABLE")) {
            throw OBDException("No data for mode $mode PID $pid")
        }
        
        // Parse response - look for mode+0x40 response
        val expectedResponse = String.format("%02X%02X", mode + RESPONSE_MODE_OFFSET, pid)
        
        // Handle multi-line responses (multiple ECUs)
        val lines = cleaned.split("(?<=\\G.{${if (cleaned.contains(expectedResponse)) cleaned.indexOf(expectedResponse) else 0}})".toRegex())
            .filter { it.contains(expectedResponse) }
        
        val data = mutableListOf<Byte>()
        var ecuAddress: Int? = null
        
        // Extract data bytes after mode+pid
        val startIndex = cleaned.indexOf(expectedResponse)
        if (startIndex >= 0) {
            // Check for ECU address prefix (3 bytes before response if headers enabled)
            if (startIndex >= 6) {
                ecuAddress = cleaned.substring(startIndex - 6, startIndex - 3).toIntOrNull(16)
                ecuAddress?.let { ecuAddresses.add(it) }
            }
            
            val dataStart = startIndex + 4
            var i = dataStart
            while (i + 1 < cleaned.length) {
                val byteStr = cleaned.substring(i, i + 2)
                byteStr.toIntOrNull(16)?.let { data.add(it.toByte()) }
                i += 2
            }
        }
        
        return OBDResponse(
            mode = mode,
            pid = pid,
            data = data.toByteArray(),
            raw = raw,
            ecuAddress = ecuAddress,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Parse VIN from Mode 09 PID 02 response
     */
    private fun parseVIN(response: String): String {
        val cleaned = response.replace("\\s".toRegex(), "")
            .replace(">", "")
            .replace("4902", "")
            .replace("014902", "")  // Multi-frame prefix
            
        val vinBytes = cleaned.chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toChar() }
            .filter { it.isLetterOrDigit() }
            .joinToString("")
        
        return vinBytes.take(17)
    }
    
    /**
     * Parse ASCII string response
     */
    private fun parseASCII(response: String): String {
        val cleaned = response.replace("\\s".toRegex(), "")
            .replace(">", "")
            .replace("49[0-9A-F]{2}".toRegex(), "")
            
        return cleaned.chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toChar() }
            .filter { it.code >= 32 }
            .joinToString("")
            .trim()
    }
    
    /**
     * Parse Diagnostic Trouble Codes
     */
    private fun parseDTCs(response: String): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        val cleaned = response.replace("\\s".toRegex(), "")
            .replace(">", "")
            .replace("43", "")  // Mode 03 response
            .replace("47", "")  // Mode 07 response
            .replace("4A", "")  // Mode 0A response
        
        // DTCs are 2 bytes each
        var i = 0
        while (i + 3 < cleaned.length) {
            val dtcHigh = cleaned.substring(i, i + 2).toIntOrNull(16) ?: break
            val dtcLow = cleaned.substring(i + 2, i + 4).toIntOrNull(16) ?: break
            
            if (dtcHigh == 0 && dtcLow == 0) {
                i += 4
                continue
            }
            
            val dtc = decodeDTC(dtcHigh, dtcLow)
            dtcs.add(dtc)
            i += 4
        }
        
        return dtcs
    }
    
    /**
     * Decode DTC from bytes
     */
    private fun decodeDTC(high: Int, low: Int): DTC {
        // First nibble determines type
        val typeCode = (high shr 6) and 0x03
        val type = when (typeCode) {
            0 -> DTCType.POWERTRAIN
            1 -> DTCType.CHASSIS
            2 -> DTCType.BODY
            3 -> DTCType.NETWORK
            else -> DTCType.UNKNOWN
        }
        
        val prefix = when (type) {
            DTCType.POWERTRAIN -> "P"
            DTCType.CHASSIS -> "C"
            DTCType.BODY -> "B"
            DTCType.NETWORK -> "U"
            DTCType.UNKNOWN -> "?"
        }
        
        val digit1 = (high shr 4) and 0x03
        val digit2 = high and 0x0F
        val digit3 = (low shr 4) and 0x0F
        val digit4 = low and 0x0F
        
        val code = "$prefix${digit1}${String.format("%X", digit2)}${String.format("%X", digit3)}${String.format("%X", digit4)}"
        
        return DTC(code, type)
    }
    
    fun getDiscoveredECUs(): Set<Int> = ecuAddresses.toSet()
    
    fun getDiscoveredPIDs(mode: Int): Set<Int> = discoveredPIDs[mode]?.toSet() ?: emptySet()
    
    fun shutdown() {
        scope.cancel()
    }
}

// Data classes

data class OBDResponse(
    val mode: Int,
    val pid: Int,
    val data: ByteArray,
    val raw: String,
    val ecuAddress: Int?,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OBDResponse
        return mode == other.mode && pid == other.pid && data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = mode
        result = 31 * result + pid
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class DTC(
    val code: String,
    val type: DTCType,
    val description: String? = null
)

enum class DTCType {
    POWERTRAIN, CHASSIS, BODY, NETWORK, UNKNOWN
}

sealed class OBDEvent {
    data class Connected(val adapterInfo: String) : OBDEvent()
    data class Disconnected(val reason: String) : OBDEvent()
    data class CommandSent(val command: String, val response: String) : OBDEvent()
    data class PIDsEnumerated(val mode: Int, val pids: Set<Int>) : OBDEvent()
    data class BruteForcComplete(val mode: Int, val pids: Set<Int>) : OBDEvent()
    data class ManufacturerModesDiscovered(val modes: Map<Int, Set<Int>>) : OBDEvent()
    data class Error(val message: String, val throwable: Throwable?) : OBDEvent()
}

class OBDException(message: String) : Exception(message)

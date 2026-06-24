package com.carhacker.kit.can

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * CAN Bus Protocol Handler
 * 
 * Implements techniques from "The Car Hacker's Handbook" Chapters 2, 5, 6:
 * - CAN frame parsing and generation
 * - Bus traffic analysis
 * - Arbitration ID discovery
 * - Replay attacks
 * - Fuzzing
 * - Reverse engineering
 * 
 * WARNING: For security research on isolated test benches only.
 * NEVER use on vehicles in motion or on public roads.
 */
class CANProtocol {
    
    companion object {
        const val STANDARD_ID_BITS = 11
        const val EXTENDED_ID_BITS = 29
        const val MAX_DATA_LENGTH = 8
        
        // Common CAN Bus Speeds
        const val SPEED_500KBPS = 500000
        const val SPEED_250KBPS = 250000
        const val SPEED_125KBPS = 125000
        
        // ISO-TP Frame Types
        const val ISOTP_SINGLE = 0x00
        const val ISOTP_FIRST = 0x10
        const val ISOTP_CONSECUTIVE = 0x20
        const val ISOTP_FLOW_CONTROL = 0x30
        
        // UDS Service IDs
        const val UDS_DIAG_SESSION = 0x10
        const val UDS_ECU_RESET = 0x11
        const val UDS_CLEAR_DTC = 0x14
        const val UDS_READ_DTC = 0x19
        const val UDS_READ_DATA = 0x22
        const val UDS_READ_MEMORY = 0x23
        const val UDS_SECURITY_ACCESS = 0x27
        const val UDS_WRITE_DATA = 0x2E
        const val UDS_IO_CONTROL = 0x2F
        const val UDS_ROUTINE_CTRL = 0x31
        const val UDS_TESTER_PRESENT = 0x3E
    }
    
    private val discoveredIDs = ConcurrentHashMap<Int, ArbitrationIDInfo>()
    private val trafficStats = ConcurrentHashMap<Int, TrafficStats>()
    
    private val _events = MutableSharedFlow<CANEvent>(replay = 0, extraBufferCapacity = 256)
    val events: SharedFlow<CANEvent> = _events.asSharedFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Parse raw CAN frame
     */
    fun parseFrame(raw: ByteArray): CANFrame? {
        if (raw.size < 5) return null
        
        val arbId = ((raw[0].toInt() and 0x7F) shl 8) or (raw[1].toInt() and 0xFF)
        val isExtended = (raw[0].toInt() and 0x80) != 0
        val dlc = raw[2].toInt() and 0x0F
        val data = raw.copyOfRange(3, minOf(3 + dlc, raw.size))
        
        return CANFrame(
            arbitrationId = arbId,
            isExtended = isExtended,
            data = data,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Generate CAN frame bytes
     */
    fun generateFrame(arbId: Int, data: ByteArray, extended: Boolean = false): ByteArray {
        val frame = ByteArray(3 + minOf(data.size, MAX_DATA_LENGTH))
        frame[0] = ((arbId shr 8) and 0x7F or (if (extended) 0x80 else 0)).toByte()
        frame[1] = (arbId and 0xFF).toByte()
        frame[2] = minOf(data.size, MAX_DATA_LENGTH).toByte()
        data.copyInto(frame, 3, 0, minOf(data.size, MAX_DATA_LENGTH))
        return frame
    }
    
    /**
     * Discover active arbitration IDs by analyzing traffic
     */
    fun analyzeTraffic(frames: List<CANFrame>): Map<Int, ArbitrationIDInfo> {
        frames.forEach { frame ->
            val info = discoveredIDs.getOrPut(frame.arbitrationId) {
                ArbitrationIDInfo(frame.arbitrationId)
            }
            info.addSample(frame)
            
            trafficStats.getOrPut(frame.arbitrationId) { TrafficStats() }.apply {
                count++
                lastSeen = frame.timestamp
            }
        }
        return discoveredIDs.toMap()
    }
    
    /**
     * Find patterns in CAN data (counters, sensors, constants)
     */
    fun findPatterns(frames: List<CANFrame>): List<PatternMatch> {
        val patterns = mutableListOf<PatternMatch>()
        val grouped = frames.groupBy { it.arbitrationId }
        
        grouped.forEach { (arbId, frameList) ->
            if (frameList.size < 10) return@forEach
            
            for (bytePos in 0 until MAX_DATA_LENGTH) {
                val values = frameList.mapNotNull { 
                    it.data.getOrNull(bytePos)?.toInt()?.and(0xFF) 
                }
                if (values.size < 10) continue
                
                val diffs = values.zipWithNext { a, b -> b - a }
                val avgDiff = diffs.average()
                
                when {
                    avgDiff in 0.8..1.2 && diffs.count { it == 1 } > diffs.size * 0.8 ->
                        patterns.add(PatternMatch(arbId, bytePos, PatternType.INCREMENTING, avgDiff))
                    avgDiff in -1.2..-0.8 && diffs.count { it == -1 } > diffs.size * 0.8 ->
                        patterns.add(PatternMatch(arbId, bytePos, PatternType.DECREMENTING, avgDiff))
                    values.distinct().size == 1 ->
                        patterns.add(PatternMatch(arbId, bytePos, PatternType.CONSTANT, values.first().toDouble()))
                }
            }
        }
        return patterns
    }
    
    /**
     * Prepare frames for replay attack
     */
    fun prepareReplay(frames: List<CANFrame>, preserveTiming: Boolean = true): List<ReplayFrame> {
        if (frames.isEmpty()) return emptyList()
        val baseTime = frames.first().timestamp
        
        return frames.map { frame ->
            ReplayFrame(
                frame = frame,
                delayMs = if (preserveTiming) frame.timestamp - baseTime else 0
            )
        }
    }
    
    /**
     * Generate fuzzing payloads for a target arbitration ID
     */
    fun generateFuzzPayloads(
        arbId: Int,
        baseData: ByteArray? = null,
        strategy: FuzzStrategy = FuzzStrategy.RANDOM
    ): Sequence<CANFrame> = sequence {
        var counter = 0
        
        while (true) {
            val data = when (strategy) {
                FuzzStrategy.RANDOM -> ByteArray(8) { Random.nextBytes(1)[0] }
                
                FuzzStrategy.SEQUENTIAL -> {
                    val d = ByteArray(8) { ((counter shr (it * 8)) and 0xFF).toByte() }
                    counter++
                    d
                }
                
                FuzzStrategy.BIT_FLIP -> {
                    val d = baseData?.copyOf() ?: ByteArray(8)
                    val byteIdx = counter / 8 % d.size
                    val bitIdx = counter % 8
                    d[byteIdx] = (d[byteIdx].toInt() xor (1 shl bitIdx)).toByte()
                    counter++
                    d
                }
                
                FuzzStrategy.BOUNDARY -> {
                    when (counter % 6) {
                        0 -> ByteArray(8) { 0x00 }
                        1 -> ByteArray(8) { 0xFF.toByte() }
                        2 -> ByteArray(8) { 0x7F }
                        3 -> ByteArray(8) { 0x80.toByte() }
                        4 -> ByteArray(8) { if (it == 0) 0x01 else 0x00 }
                        else -> ByteArray(8) { if (it == 7) 0x01 else 0x00 }
                    }.also { counter++ }
                }
                
                FuzzStrategy.INCREMENTAL -> {
                    val d = baseData?.copyOf() ?: ByteArray(8)
                    val byteIdx = counter / 256 % d.size
                    d[byteIdx] = (counter % 256).toByte()
                    counter++
                    d
                }
            }
            
            yield(CANFrame(arbId, false, data, System.currentTimeMillis()))
        }
    }
    
    /**
     * Brute force arbitration ID discovery
     */
    suspend fun bruteForceArbIDs(
        startId: Int = 0x000,
        endId: Int = 0x7FF,
        testData: ByteArray = byteArrayOf(0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        onProgress: (Int, Int, Boolean) -> Unit
    ): Set<Int> = withContext(Dispatchers.Default) {
        val responding = mutableSetOf<Int>()
        
        for (id in startId..endId) {
            // Generate test frame
            val frame = CANFrame(id, false, testData, System.currentTimeMillis())
            
            // In real implementation, send and check for response
            // This is a placeholder for the actual CAN interface
            val hasResponse = false // Would come from CAN interface
            
            if (hasResponse) {
                responding.add(id)
            }
            
            onProgress(id - startId, endId - startId + 1, hasResponse)
            delay(10) // Rate limiting
        }
        
        responding
    }
    
    /**
     * Decode ISO-TP (ISO 15765-2) multi-frame messages
     */
    fun decodeISOTP(frames: List<CANFrame>): ByteArray? {
        if (frames.isEmpty()) return null
        
        val first = frames.first()
        val frameType = (first.data.getOrNull(0)?.toInt()?.and(0xF0)) ?: return null
        
        return when (frameType) {
            ISOTP_SINGLE -> {
                val length = first.data[0].toInt() and 0x0F
                first.data.copyOfRange(1, 1 + length)
            }
            
            ISOTP_FIRST -> {
                val length = ((first.data[0].toInt() and 0x0F) shl 8) or 
                            (first.data[1].toInt() and 0xFF)
                val result = ByteArray(length)
                var offset = 0
                
                // Copy first frame data (bytes 2-7)
                val firstData = first.data.copyOfRange(2, first.data.size)
                firstData.copyInto(result, 0)
                offset += firstData.size
                
                // Process consecutive frames
                frames.drop(1).forEach { frame ->
                    if ((frame.data[0].toInt() and 0xF0) == ISOTP_CONSECUTIVE) {
                        val data = frame.data.copyOfRange(1, frame.data.size)
                        val copyLen = minOf(data.size, length - offset)
                        data.copyInto(result, offset, 0, copyLen)
                        offset += copyLen
                    }
                }
                
                result
            }
            
            else -> null
        }
    }
    
    /**
     * Encode data into ISO-TP frames
     */
    fun encodeISOTP(arbId: Int, data: ByteArray): List<CANFrame> {
        val frames = mutableListOf<CANFrame>()
        
        if (data.size <= 7) {
            // Single frame
            val frameData = ByteArray(8)
            frameData[0] = data.size.toByte()
            data.copyInto(frameData, 1)
            frames.add(CANFrame(arbId, false, frameData, System.currentTimeMillis()))
        } else {
            // Multi-frame
            // First frame
            val firstData = ByteArray(8)
            firstData[0] = (ISOTP_FIRST or ((data.size shr 8) and 0x0F)).toByte()
            firstData[1] = (data.size and 0xFF).toByte()
            data.copyInto(firstData, 2, 0, 6)
            frames.add(CANFrame(arbId, false, firstData, System.currentTimeMillis()))
            
            // Consecutive frames
            var offset = 6
            var seqNum = 1
            while (offset < data.size) {
                val cfData = ByteArray(8)
                cfData[0] = (ISOTP_CONSECUTIVE or (seqNum and 0x0F)).toByte()
                val copyLen = minOf(7, data.size - offset)
                data.copyInto(cfData, 1, offset, offset + copyLen)
                frames.add(CANFrame(arbId, false, cfData, System.currentTimeMillis()))
                offset += 7
                seqNum = (seqNum + 1) % 16
            }
        }
        
        return frames
    }
    
    /**
     * Identify potential security-critical message IDs
     * Based on common automotive patterns
     */
    fun identifySecurityCritical(discoveredIds: Set<Int>): Map<Int, String> {
        val critical = mutableMapOf<Int, String>()
        
        // Common diagnostic ranges
        discoveredIds.filter { it in 0x700..0x7FF }.forEach {
            critical[it] = "Diagnostic (ISO 15765)"
        }
        
        // OBD-II standard
        if (0x7DF in discoveredIds) critical[0x7DF] = "OBD-II Broadcast"
        if (0x7E0 in discoveredIds) critical[0x7E0] = "ECU Request"
        if (0x7E8 in discoveredIds) critical[0x7E8] = "ECU Response"
        
        // Common safety-critical ranges (varies by manufacturer)
        discoveredIds.filter { it in 0x000..0x0FF }.forEach {
            critical[it] = "High Priority (possibly safety)"
        }
        
        return critical
    }
    
    /**
     * Calculate message frequency/timing
     */
    fun calculateTiming(frames: List<CANFrame>): Map<Int, MessageTiming> {
        val timing = mutableMapOf<Int, MessageTiming>()
        
        frames.groupBy { it.arbitrationId }.forEach { (arbId, list) ->
            if (list.size < 2) return@forEach
            
            val intervals = list.zipWithNext { a, b -> b.timestamp - a.timestamp }
            timing[arbId] = MessageTiming(
                averageIntervalMs = intervals.average(),
                minIntervalMs = intervals.minOrNull() ?: 0,
                maxIntervalMs = intervals.maxOrNull() ?: 0,
                messageCount = list.size,
                totalDurationMs = list.last().timestamp - list.first().timestamp
            )
        }
        
        return timing
    }
    
    fun shutdown() {
        scope.cancel()
    }
}

// Data Classes

data class CANFrame(
    val arbitrationId: Int,
    val isExtended: Boolean,
    val data: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CANFrame) return false
        return arbitrationId == other.arbitrationId && 
               isExtended == other.isExtended && 
               data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = arbitrationId
        result = 31 * result + isExtended.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
    
    fun toHexString(): String {
        val idStr = String.format("%03X", arbitrationId)
        val dataStr = data.joinToString(" ") { String.format("%02X", it) }
        return "$idStr [$${data.size}] $dataStr"
    }
}

class ArbitrationIDInfo(val id: Int) {
    var firstSeen: Long = 0
    var lastSeen: Long = 0
    var messageCount: Int = 0
    val samples = mutableListOf<ByteArray>()
    private val maxSamples = 100
    
    fun addSample(frame: CANFrame) {
        if (firstSeen == 0L) firstSeen = frame.timestamp
        lastSeen = frame.timestamp
        messageCount++
        
        if (samples.size < maxSamples) {
            samples.add(frame.data.copyOf())
        }
    }
    
    fun getByteRanges(): List<IntRange> {
        if (samples.isEmpty()) return emptyList()
        return (0 until samples.first().size).map { pos ->
            val values = samples.mapNotNull { it.getOrNull(pos)?.toInt()?.and(0xFF) }
            (values.minOrNull() ?: 0)..(values.maxOrNull() ?: 255)
        }
    }
}

data class TrafficStats(
    var count: Int = 0,
    var lastSeen: Long = 0
)

data class PatternMatch(
    val arbitrationId: Int,
    val bytePosition: Int,
    val type: PatternType,
    val value: Double
)

enum class PatternType {
    INCREMENTING,
    DECREMENTING,
    CONSTANT,
    COUNTER_4BIT,
    COUNTER_8BIT,
    SENSOR_16BIT,
    CHECKSUM,
    UNKNOWN
}

data class ReplayFrame(
    val frame: CANFrame,
    val delayMs: Long
)

enum class FuzzStrategy {
    RANDOM,
    SEQUENTIAL,
    BIT_FLIP,
    BOUNDARY,
    INCREMENTAL
}

data class MessageTiming(
    val averageIntervalMs: Double,
    val minIntervalMs: Long,
    val maxIntervalMs: Long,
    val messageCount: Int,
    val totalDurationMs: Long
) {
    val frequencyHz: Double get() = if (averageIntervalMs > 0) 1000.0 / averageIntervalMs else 0.0
}

sealed class CANEvent {
    data class FrameReceived(val frame: CANFrame) : CANEvent()
    data class FrameSent(val frame: CANFrame) : CANEvent()
    data class Error(val message: String) : CANEvent()
    data class PatternFound(val pattern: PatternMatch) : CANEvent()
    data class BusError(val type: String) : CANEvent()
}

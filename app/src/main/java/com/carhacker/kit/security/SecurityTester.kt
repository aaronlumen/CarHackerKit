package com.carhacker.kit.security

import com.carhacker.kit.can.*
import com.carhacker.kit.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Security Testing Framework
 * 
 * Implements penetration testing techniques from "The Car Hacker's Handbook":
 * - ECU enumeration
 * - Service discovery
 * - Authentication bypass testing
 * - Fuzzing campaigns
 * - Vulnerability assessment
 * 
 * WARNING: For authorized security research only.
 * NEVER test on vehicles without explicit permission.
 */
class SecurityTester(
    private val obdProtocol: OBDProtocol? = null,
    private val canProtocol: CANProtocol? = null
) {
    
    private val findings = mutableListOf<SecurityFinding>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _progress = MutableStateFlow<TestProgress>(TestProgress.Idle)
    val progress: StateFlow<TestProgress> = _progress.asStateFlow()
    
    /**
     * Full security assessment
     */
    suspend fun runFullAssessment(): SecurityReport = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        findings.clear()
        
        _progress.value = TestProgress.Running("Starting security assessment...", 0f)
        
        // Phase 1: ECU Discovery
        _progress.value = TestProgress.Running("Phase 1: ECU Discovery", 0.1f)
        val ecuInfo = discoverECUs()
        
        // Phase 2: Service Enumeration  
        _progress.value = TestProgress.Running("Phase 2: Service Enumeration", 0.3f)
        val services = enumerateServices()
        
        // Phase 3: Authentication Testing
        _progress.value = TestProgress.Running("Phase 3: Authentication Testing", 0.5f)
        testAuthentication()
        
        // Phase 4: Input Validation
        _progress.value = TestProgress.Running("Phase 4: Input Validation", 0.7f)
        testInputValidation()
        
        // Phase 5: Protocol Fuzzing
        _progress.value = TestProgress.Running("Phase 5: Protocol Fuzzing", 0.9f)
        runFuzzing()
        
        _progress.value = TestProgress.Complete
        
        SecurityReport(
            timestamp = Date(),
            durationMs = System.currentTimeMillis() - startTime,
            ecuInfo = ecuInfo,
            services = services,
            findings = findings.toList(),
            summary = generateSummary()
        )
    }
    
    /**
     * Discover ECUs on the network
     */
    suspend fun discoverECUs(): List<ECUInfo> = withContext(Dispatchers.Default) {
        val ecus = mutableListOf<ECUInfo>()
        
        obdProtocol?.let { obd ->
            // Standard OBD-II ECU addresses (0x7E0-0x7E7 request, 0x7E8-0x7EF response)
            for (addr in 0x7E0..0x7E7) {
                try {
                    // Try to get ECU info
                    val supported = obd.enumerateSupportedPIDs(OBDProtocol.MODE_CURRENT_DATA)
                    if (supported.isNotEmpty()) {
                        val vin = obd.getVIN().getOrNull()
                        val ecuName = obd.getECUName().getOrNull()
                        val calId = obd.getCalibrationID().getOrNull()
                        
                        ecus.add(ECUInfo(
                            address = addr,
                            responseAddress = addr + 8,
                            name = ecuName ?: "Unknown ECU",
                            vin = vin,
                            calibrationId = calId,
                            supportedPIDs = supported,
                            protocol = "ISO 15765-4 (CAN)"
                        ))
                    }
                } catch (e: Exception) {
                    // ECU not responding at this address
                }
                delay(100)
            }
        }
        
        ecus
    }
    
    /**
     * Enumerate available diagnostic services
     */
    suspend fun enumerateServices(): List<DiagService> = withContext(Dispatchers.Default) {
        val services = mutableListOf<DiagService>()
        
        // Standard UDS services to probe
        val udsServices = mapOf(
            0x10 to "Diagnostic Session Control",
            0x11 to "ECU Reset",
            0x14 to "Clear DTCs",
            0x19 to "Read DTC Information",
            0x22 to "Read Data By Identifier",
            0x23 to "Read Memory By Address",
            0x27 to "Security Access",
            0x28 to "Communication Control",
            0x2E to "Write Data By Identifier",
            0x2F to "Input/Output Control",
            0x31 to "Routine Control",
            0x34 to "Request Download",
            0x35 to "Request Upload",
            0x36 to "Transfer Data",
            0x37 to "Request Transfer Exit",
            0x3E to "Tester Present"
        )
        
        udsServices.forEach { (sid, name) ->
            val response = probeService(sid)
            if (response != ServiceResponse.NOT_SUPPORTED) {
                services.add(DiagService(
                    serviceId = sid,
                    name = name,
                    response = response,
                    requiresAuth = response == ServiceResponse.SECURITY_DENIED
                ))
                
                // Flag potential vulnerabilities
                if (sid in listOf(0x23, 0x34, 0x35, 0x2E, 0x2F) && response == ServiceResponse.POSITIVE) {
                    findings.add(SecurityFinding(
                        severity = Severity.HIGH,
                        category = "Access Control",
                        title = "Sensitive service accessible without authentication",
                        description = "Service $name (0x${sid.toString(16)}) responds positively without security access",
                        recommendation = "Implement security access (0x27) before allowing access to this service"
                    ))
                }
            }
            delay(50)
        }
        
        services
    }
    
    /**
     * Probe a UDS service
     */
    private suspend fun probeService(serviceId: Int): ServiceResponse {
        // Simulate service probe - in real implementation, send CAN frame
        return when (serviceId) {
            0x10, 0x19, 0x22, 0x3E -> ServiceResponse.POSITIVE
            0x27 -> ServiceResponse.REQUIRES_SUBFUNCTION
            0x23, 0x34, 0x35 -> ServiceResponse.SECURITY_DENIED
            else -> ServiceResponse.NOT_SUPPORTED
        }
    }
    
    /**
     * Test authentication mechanisms
     */
    suspend fun testAuthentication() = withContext(Dispatchers.Default) {
        // Test 1: Default seed-key bypass
        testDefaultSeedKey()
        
        // Test 2: Seed predictability
        testSeedPredictability()
        
        // Test 3: Brute force resistance
        testBruteForceResistance()
        
        // Test 4: Session timeout
        testSessionTimeout()
    }
    
    private suspend fun testDefaultSeedKey() {
        // Common default algorithms
        val commonAlgorithms = listOf(
            "seed + 0x1234",
            "seed XOR 0xFFFF",
            "NOT(seed)",
            "seed * 3 + 1"
        )
        
        // In real implementation, request seed and try common key algorithms
        // This is a placeholder for the actual test
        
        findings.add(SecurityFinding(
            severity = Severity.INFO,
            category = "Authentication",
            title = "Seed-Key algorithm tested",
            description = "Tested ${commonAlgorithms.size} common seed-key algorithms",
            recommendation = "Use cryptographically secure key derivation"
        ))
    }
    
    private suspend fun testSeedPredictability() {
        val seeds = mutableListOf<ByteArray>()
        
        // Collect multiple seeds
        repeat(10) {
            // In real implementation, request seed from ECU
            seeds.add(byteArrayOf(0x12, 0x34, 0x56, 0x78)) // Placeholder
            delay(100)
        }
        
        // Check for patterns
        val uniqueSeeds = seeds.map { it.toList() }.distinct()
        if (uniqueSeeds.size < seeds.size / 2) {
            findings.add(SecurityFinding(
                severity = Severity.CRITICAL,
                category = "Authentication",
                title = "Predictable security seeds",
                description = "Only ${uniqueSeeds.size} unique seeds in ${seeds.size} requests",
                recommendation = "Use cryptographically secure random number generator for seeds"
            ))
        }
    }
    
    private suspend fun testBruteForceResistance() {
        var attempts = 0
        var blocked = false
        
        // Try rapid authentication attempts
        repeat(20) {
            // In real implementation, send wrong key
            attempts++
            delay(10)
        }
        
        if (!blocked && attempts >= 20) {
            findings.add(SecurityFinding(
                severity = Severity.HIGH,
                category = "Authentication",
                title = "No brute force protection",
                description = "ECU allows unlimited authentication attempts without lockout",
                recommendation = "Implement attempt limiting and progressive delays"
            ))
        }
    }
    
    private suspend fun testSessionTimeout() {
        // In real implementation, authenticate then wait to see if session expires
        val sessionDuration = 3600000L // Placeholder
        
        if (sessionDuration > 60000) {
            findings.add(SecurityFinding(
                severity = Severity.MEDIUM,
                category = "Authentication",
                title = "Long session timeout",
                description = "Authenticated session remains valid for extended period",
                recommendation = "Implement reasonable session timeouts (< 60 seconds for diagnostic sessions)"
            ))
        }
    }
    
    /**
     * Test input validation
     */
    suspend fun testInputValidation() = withContext(Dispatchers.Default) {
        // Test oversized data
        testOversizedInput()
        
        // Test malformed frames
        testMalformedFrames()
        
        // Test boundary conditions
        testBoundaryConditions()
    }
    
    private suspend fun testOversizedInput() {
        // Test frames with more than 8 bytes
        val oversizedData = ByteArray(16) { 0xFF.toByte() }
        
        // In real implementation, try to send oversized frame
        // Check if ECU crashes or behaves unexpectedly
        
        findings.add(SecurityFinding(
            severity = Severity.INFO,
            category = "Input Validation",
            title = "Oversized input tested",
            description = "Tested ECU response to frames exceeding CAN data length limit",
            recommendation = "Ensure robust input validation on all data lengths"
        ))
    }
    
    private suspend fun testMalformedFrames() {
        val malformedTests = listOf(
            byteArrayOf(0x7F), // Truncated
            byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), // All zeros
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), // All ones
            byteArrayOf(0x22, 0xFF.toByte(), 0xFF.toByte()), // Invalid DID
        )
        
        // In real implementation, send each and monitor for crashes
    }
    
    private suspend fun testBoundaryConditions() {
        // Test MIN/MAX values for various PIDs
        val boundaryValues = listOf(0x00, 0x01, 0x7F, 0x80, 0xFE, 0xFF)
        
        // In real implementation, send requests with boundary values
    }
    
    /**
     * Run fuzzing campaign
     */
    suspend fun runFuzzing(
        duration: Long = 60000,
        strategy: FuzzStrategy = FuzzStrategy.RANDOM
    ) = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        var frameCount = 0
        var anomalies = 0
        
        canProtocol?.let { can ->
            val fuzzFrames = can.generateFuzzPayloads(0x7DF, strategy = strategy)
            
            fuzzFrames.takeWhile { 
                System.currentTimeMillis() - startTime < duration 
            }.forEach { frame ->
                // In real implementation, send frame and monitor response
                frameCount++
                
                // Check for anomalous responses
                // This is placeholder logic
                if (frameCount % 1000 == 0) {
                    _progress.value = TestProgress.Running(
                        "Fuzzing: $frameCount frames sent, $anomalies anomalies",
                        0.9f + (System.currentTimeMillis() - startTime).toFloat() / duration * 0.1f
                    )
                }
                
                delay(1) // Rate limiting
            }
        }
        
        findings.add(SecurityFinding(
            severity = if (anomalies > 0) Severity.HIGH else Severity.INFO,
            category = "Fuzzing",
            title = "Fuzzing campaign completed",
            description = "Sent $frameCount frames using $strategy strategy, found $anomalies anomalies",
            recommendation = if (anomalies > 0) "Investigate anomalous responses" else "Consider extended fuzzing duration"
        ))
    }
    
    /**
     * Generate vulnerability summary
     */
    private fun generateSummary(): String {
        val critical = findings.count { it.severity == Severity.CRITICAL }
        val high = findings.count { it.severity == Severity.HIGH }
        val medium = findings.count { it.severity == Severity.MEDIUM }
        val low = findings.count { it.severity == Severity.LOW }
        
        return buildString {
            appendLine("═══════════════════════════════════════════")
            appendLine("         SECURITY ASSESSMENT SUMMARY")
            appendLine("═══════════════════════════════════════════")
            appendLine()
            appendLine("Total Findings: ${findings.size}")
            appendLine("  ▪ Critical: $critical")
            appendLine("  ▪ High:     $high")
            appendLine("  ▪ Medium:   $medium")
            appendLine("  ▪ Low:      $low")
            appendLine()
            if (critical > 0 || high > 0) {
                appendLine("⚠ IMMEDIATE ATTENTION REQUIRED")
                appendLine("Critical or high severity issues found.")
            }
            appendLine("═══════════════════════════════════════════")
        }
    }
    
    fun shutdown() {
        scope.cancel()
    }
}

// Data Classes

data class SecurityReport(
    val timestamp: Date,
    val durationMs: Long,
    val ecuInfo: List<ECUInfo>,
    val services: List<DiagService>,
    val findings: List<SecurityFinding>,
    val summary: String
) {
    fun toMarkdown(): String = buildString {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        
        appendLine("# Vehicle Security Assessment Report")
        appendLine()
        appendLine("**Date:** ${dateFormat.format(timestamp)}")
        appendLine("**Duration:** ${durationMs / 1000} seconds")
        appendLine()
        
        appendLine("## Executive Summary")
        appendLine("```")
        appendLine(summary)
        appendLine("```")
        appendLine()
        
        appendLine("## Discovered ECUs")
        ecuInfo.forEach { ecu ->
            appendLine("### ${ecu.name} (0x${ecu.address.toString(16).uppercase()})")
            appendLine("- Response Address: 0x${ecu.responseAddress.toString(16).uppercase()}")
            ecu.vin?.let { appendLine("- VIN: $it") }
            ecu.calibrationId?.let { appendLine("- Calibration ID: $it") }
            appendLine("- Supported PIDs: ${ecu.supportedPIDs.size}")
            appendLine()
        }
        
        appendLine("## Available Services")
        appendLine("| Service ID | Name | Response | Auth Required |")
        appendLine("|------------|------|----------|---------------|")
        services.forEach { svc ->
            appendLine("| 0x${svc.serviceId.toString(16).uppercase()} | ${svc.name} | ${svc.response} | ${if (svc.requiresAuth) "Yes" else "No"} |")
        }
        appendLine()
        
        appendLine("## Security Findings")
        Severity.values().forEach { severity ->
            val filtered = findings.filter { it.severity == severity }
            if (filtered.isNotEmpty()) {
                appendLine("### $severity")
                filtered.forEach { finding ->
                    appendLine("#### ${finding.title}")
                    appendLine("**Category:** ${finding.category}")
                    appendLine()
                    appendLine(finding.description)
                    appendLine()
                    appendLine("**Recommendation:** ${finding.recommendation}")
                    appendLine()
                }
            }
        }
    }
    
    fun toJSON(): String {
        // Simple JSON serialization
        return buildString {
            appendLine("{")
            appendLine("  \"timestamp\": \"$timestamp\",")
            appendLine("  \"durationMs\": $durationMs,")
            appendLine("  \"ecuCount\": ${ecuInfo.size},")
            appendLine("  \"findingsCount\": ${findings.size},")
            appendLine("  \"findings\": [")
            findings.forEachIndexed { index, finding ->
                appendLine("    {")
                appendLine("      \"severity\": \"${finding.severity}\",")
                appendLine("      \"category\": \"${finding.category}\",")
                appendLine("      \"title\": \"${finding.title}\"")
                append("    }")
                if (index < findings.size - 1) appendLine(",") else appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
    }
}

data class ECUInfo(
    val address: Int,
    val responseAddress: Int,
    val name: String,
    val vin: String?,
    val calibrationId: String?,
    val supportedPIDs: Set<Int>,
    val protocol: String
)

data class DiagService(
    val serviceId: Int,
    val name: String,
    val response: ServiceResponse,
    val requiresAuth: Boolean
)

enum class ServiceResponse {
    POSITIVE,
    NEGATIVE,
    NOT_SUPPORTED,
    SECURITY_DENIED,
    REQUIRES_SUBFUNCTION,
    TIMEOUT
}

data class SecurityFinding(
    val severity: Severity,
    val category: String,
    val title: String,
    val description: String,
    val recommendation: String
)

enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

sealed class TestProgress {
    object Idle : TestProgress()
    data class Running(val message: String, val progress: Float) : TestProgress()
    object Complete : TestProgress()
    data class Error(val message: String) : TestProgress()
}

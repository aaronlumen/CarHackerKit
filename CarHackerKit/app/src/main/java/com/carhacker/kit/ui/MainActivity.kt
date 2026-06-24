package com.carhacker.kit.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.carhacker.kit.R
import com.carhacker.kit.databinding.ActivityMainBinding
import com.carhacker.kit.can.CANProtocol
import com.carhacker.kit.obd.*
import com.carhacker.kit.security.SecurityTester
import com.carhacker.kit.security.TestProgress
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var obdConnection: OBDConnection? = null
    private var obdProtocol: OBDProtocol? = null
    private var canProtocol: CANProtocol? = null
    private var securityTester: SecurityTester? = null
    
    private val logAdapter = LogAdapter()
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        // Log recycler view
        binding.rvLog.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }
        
        // Connection buttons
        binding.btnConnectUsb.setOnClickListener { connectUSB() }
        binding.btnConnectBluetooth.setOnClickListener { connectBluetooth() }
        binding.btnConnectWifi.setOnClickListener { connectWiFi() }
        binding.btnSimulator.setOnClickListener { connectSimulator() }
        binding.btnDisconnect.setOnClickListener { disconnect() }
        
        // Feature buttons
        binding.btnEnumeratePids.setOnClickListener { enumeratePIDs() }
        binding.btnBruteForcePids.setOnClickListener { bruteForcePIDs() }
        binding.btnReadDtcs.setOnClickListener { readDTCs() }
        binding.btnClearDtcs.setOnClickListener { clearDTCs() }
        binding.btnGetVehicleInfo.setOnClickListener { getVehicleInfo() }
        binding.btnSecurityScan.setOnClickListener { runSecurityScan() }
        binding.btnExportLog.setOnClickListener { exportLog() }
        binding.btnClearLog.setOnClickListener { clearLog() }
        
        updateConnectionStatus(false)
        log("CarHackerKit initialized. Ready to connect.")
        log("⚠️ WARNING: For security research on isolated test benches only.")
    }
    
    private fun checkPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun connectUSB() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList.values.toList()
        
        if (devices.isEmpty()) {
            log("❌ No USB devices found")
            Toast.makeText(this, "No USB devices found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show device selection dialog
        val deviceNames = devices.map { "${it.productName ?: "Unknown"} (${it.vendorId}:${it.productId})" }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select USB Device")
            .setItems(deviceNames.toTypedArray()) { _, which ->
                val device = devices[which]
                connectToUSBDevice(device)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun connectToUSBDevice(device: UsbDevice) {
        lifecycleScope.launch {
            log("Connecting to USB: ${device.productName}...")
            
            obdConnection = USBOBDConnection(this@MainActivity, device)
            if (obdConnection?.connect() == true) {
                obdProtocol = OBDProtocol(obdConnection!!)
                val result = obdProtocol?.initialize()
                
                if (result?.isSuccess == true) {
                    log("✓ Connected: ${result.getOrNull()}")
                    updateConnectionStatus(true)
                    setupProtocolListeners()
                } else {
                    log("❌ Initialization failed: ${result?.exceptionOrNull()?.message}")
                    disconnect()
                }
            } else {
                log("❌ USB connection failed")
            }
        }
    }
    
    private fun connectBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            log("❌ Bluetooth not available")
            return
        }
        
        if (!adapter.isEnabled) {
            log("❌ Bluetooth is disabled")
            return
        }
        
        try {
            val pairedDevices = adapter.bondedDevices?.toList() ?: emptyList()
            val obdDevices = pairedDevices.filter { 
                it.name?.contains("OBD", ignoreCase = true) == true ||
                it.name?.contains("ELM", ignoreCase = true) == true ||
                it.name?.contains("OBDII", ignoreCase = true) == true
            }
            
            if (obdDevices.isEmpty()) {
                log("No paired OBD devices found. Showing all paired devices...")
                showBluetoothDeviceSelector(pairedDevices)
            } else {
                showBluetoothDeviceSelector(obdDevices)
            }
        } catch (e: SecurityException) {
            log("❌ Bluetooth permission denied")
        }
    }
    
    private fun showBluetoothDeviceSelector(devices: List<BluetoothDevice>) {
        if (devices.isEmpty()) {
            log("❌ No paired Bluetooth devices")
            return
        }
        
        try {
            val deviceNames = devices.map { "${it.name ?: "Unknown"} (${it.address})" }
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Bluetooth Device")
                .setItems(deviceNames.toTypedArray()) { _, which ->
                    connectToBluetoothDevice(devices[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: SecurityException) {
            log("❌ Bluetooth permission denied")
        }
    }
    
    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        lifecycleScope.launch {
            try {
                log("Connecting to Bluetooth: ${device.name}...")
                
                obdConnection = BluetoothOBDConnection(device)
                if (obdConnection?.connect() == true) {
                    obdProtocol = OBDProtocol(obdConnection!!)
                    val result = obdProtocol?.initialize()
                    
                    if (result?.isSuccess == true) {
                        log("✓ Connected via Bluetooth: ${result.getOrNull()}")
                        updateConnectionStatus(true)
                        setupProtocolListeners()
                    } else {
                        log("❌ Initialization failed")
                        disconnect()
                    }
                } else {
                    log("❌ Bluetooth connection failed")
                }
            } catch (e: SecurityException) {
                log("❌ Bluetooth permission denied")
            }
        }
    }
    
    private fun connectWiFi() {
        // Show dialog to enter WiFi address
        val input = android.widget.EditText(this).apply {
            hint = "192.168.0.10:35000"
            setText("192.168.0.10:35000")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enter WiFi OBD Address")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val address = input.text.toString()
                val parts = address.split(":")
                val host = parts.getOrNull(0) ?: "192.168.0.10"
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 35000
                connectToWiFiDevice(host, port)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun connectToWiFiDevice(host: String, port: Int) {
        lifecycleScope.launch {
            log("Connecting to WiFi: $host:$port...")
            
            obdConnection = WiFiOBDConnection(host, port)
            if (obdConnection?.connect() == true) {
                obdProtocol = OBDProtocol(obdConnection!!)
                val result = obdProtocol?.initialize()
                
                if (result?.isSuccess == true) {
                    log("✓ Connected via WiFi: ${result.getOrNull()}")
                    updateConnectionStatus(true)
                    setupProtocolListeners()
                } else {
                    log("❌ Initialization failed")
                    disconnect()
                }
            } else {
                log("❌ WiFi connection failed")
            }
        }
    }
    
    private fun connectSimulator() {
        lifecycleScope.launch {
            log("Starting OBD-II Simulator...")
            
            obdConnection = SimulatedOBDConnection()
            obdConnection?.connect()
            
            obdProtocol = OBDProtocol(obdConnection!!)
            obdProtocol?.initialize()
            
            log("✓ Simulator connected (no hardware required)")
            updateConnectionStatus(true)
            setupProtocolListeners()
        }
    }
    
    private fun disconnect() {
        lifecycleScope.launch {
            obdProtocol?.shutdown()
            obdConnection?.disconnect()
            canProtocol?.shutdown()
            securityTester?.shutdown()
            
            obdProtocol = null
            obdConnection = null
            canProtocol = null
            securityTester = null
            
            log("Disconnected")
            updateConnectionStatus(false)
        }
    }
    
    private fun setupProtocolListeners() {
        obdProtocol?.let { protocol ->
            lifecycleScope.launch {
                protocol.events.collectLatest { event ->
                    when (event) {
                        is OBDEvent.CommandSent -> {
                            log("TX: ${event.command}")
                            log("RX: ${event.response.take(100)}${if (event.response.length > 100) "..." else ""}")
                        }
                        is OBDEvent.PIDsEnumerated -> {
                            log("Mode 0x${event.mode.toString(16)}: ${event.pids.size} PIDs supported")
                        }
                        is OBDEvent.BruteForcComplete -> {
                            log("Brute force complete: ${event.pids.size} PIDs found")
                        }
                        is OBDEvent.ManufacturerModesDiscovered -> {
                            log("Manufacturer modes: ${event.modes.keys.size} found")
                        }
                        is OBDEvent.Error -> {
                            log("❌ Error: ${event.message}")
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    
    private fun updateConnectionStatus(connected: Boolean) {
        runOnUiThread {
            binding.tvConnectionStatus.text = if (connected) "Connected" else "Disconnected"
            binding.tvConnectionStatus.setTextColor(
                ContextCompat.getColor(this, if (connected) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
            )
            
            binding.btnDisconnect.isEnabled = connected
            binding.layoutFeatures.visibility = if (connected) View.VISIBLE else View.GONE
        }
    }
    
    private fun enumeratePIDs() {
        lifecycleScope.launch {
            log("═══ Enumerating Supported PIDs ═══")
            
            obdProtocol?.let { protocol ->
                // Mode 01 - Current Data
                log("Mode 01 (Current Data):")
                val mode01 = protocol.enumerateSupportedPIDs(0x01)
                mode01.forEach { pid ->
                    val info = PIDDefinitions.MODE_01_PIDS[pid]
                    log("  PID 0x${pid.toString(16).padStart(2, '0')}: ${info?.name ?: "Unknown"}")
                }
                
                // Mode 09 - Vehicle Info
                log("\nMode 09 (Vehicle Info):")
                val mode09 = protocol.enumerateSupportedPIDs(0x09)
                mode09.forEach { pid ->
                    val info = PIDDefinitions.MODE_09_PIDS[pid]
                    log("  PID 0x${pid.toString(16).padStart(2, '0')}: ${info?.name ?: "Unknown"}")
                }
                
                log("═══════════════════════════════════")
            }
        }
    }
    
    private fun bruteForcePIDs() {
        lifecycleScope.launch {
            log("═══ Brute Force PID Discovery ═══")
            log("Testing Mode 01 PIDs 0x01-0xFF...")
            
            obdProtocol?.let { protocol ->
                val found = protocol.bruteForcePIDs(0x01, 0x01, 0xFF) { pid, total, supported ->
                    if (supported) {
                        log("  Found PID 0x${pid.toString(16).padStart(2, '0')}")
                    }
                    if (pid % 32 == 0) {
                        log("  Progress: $pid / $total")
                    }
                }
                
                log("\nBrute force complete. Found ${found.size} responding PIDs.")
                log("═══════════════════════════════════")
            }
        }
    }
    
    private fun readDTCs() {
        lifecycleScope.launch {
            log("═══ Reading Diagnostic Trouble Codes ═══")
            
            obdProtocol?.let { protocol ->
                // Stored DTCs
                val stored = protocol.readDTCs(0x03)
                if (stored.isSuccess) {
                    val dtcs = stored.getOrNull() ?: emptyList()
                    log("Stored DTCs: ${dtcs.size}")
                    dtcs.forEach { log("  ${it.code} (${it.type})") }
                }
                
                // Pending DTCs
                val pending = protocol.readDTCs(0x07)
                if (pending.isSuccess) {
                    val dtcs = pending.getOrNull() ?: emptyList()
                    log("Pending DTCs: ${dtcs.size}")
                    dtcs.forEach { log("  ${it.code} (${it.type})") }
                }
                
                log("═══════════════════════════════════")
            }
        }
    }
    
    private fun clearDTCs() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear DTCs")
            .setMessage("Are you sure you want to clear all diagnostic trouble codes?\n\nThis will also reset readiness monitors.")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    val result = obdProtocol?.clearDTCs()
                    if (result?.isSuccess == true && result.getOrNull() == true) {
                        log("✓ DTCs cleared successfully")
                    } else {
                        log("❌ Failed to clear DTCs")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getVehicleInfo() {
        lifecycleScope.launch {
            log("═══ Vehicle Information ═══")
            
            obdProtocol?.let { protocol ->
                val vin = protocol.getVIN()
                log("VIN: ${vin.getOrNull() ?: "N/A"}")
                
                val ecuName = protocol.getECUName()
                log("ECU Name: ${ecuName.getOrNull() ?: "N/A"}")
                
                val calId = protocol.getCalibrationID()
                log("Calibration ID: ${calId.getOrNull() ?: "N/A"}")
                
                log("═══════════════════════════════════")
            }
        }
    }
    
    private fun runSecurityScan() {
        lifecycleScope.launch {
            log("═══ Starting Security Assessment ═══")
            log("⚠️ WARNING: Ensure you have authorization to test this vehicle")
            
            canProtocol = CANProtocol()
            securityTester = SecurityTester(obdProtocol, canProtocol)
            
            securityTester?.progress?.collectLatest { progress ->
                when (progress) {
                    is TestProgress.Running -> {
                        log("[${(progress.progress * 100).toInt()}%] ${progress.message}")
                    }
                    is TestProgress.Complete -> {
                        log("✓ Security assessment complete")
                    }
                    is TestProgress.Error -> {
                        log("❌ Error: ${progress.message}")
                    }
                    else -> {}
                }
            }
            
            val report = securityTester?.runFullAssessment()
            report?.let {
                log("\n${it.summary}")
                log("\nFull report generated with ${it.findings.size} findings.")
            }
        }
    }
    
    private fun exportLog() {
        val log = logAdapter.getFullLog()
        
        // Copy to clipboard
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("CarHackerKit Log", log)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
        log("Log exported to clipboard (${log.length} chars)")
    }
    
    private fun clearLog() {
        logAdapter.clear()
    }
    
    private fun log(message: String) {
        runOnUiThread {
            logAdapter.add(message)
            binding.rvLog.scrollToPosition(logAdapter.itemCount - 1)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}

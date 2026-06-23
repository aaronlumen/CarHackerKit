package com.carhacker.kit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.carhacker.kit.databinding.ActivityMainBinding
import com.carhacker.kit.can.CANProtocol
import com.carhacker.kit.obd.*
import com.carhacker.kit.security.SecurityTester
import com.carhacker.kit.security.TestProgress
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), ConnectionSheetFragment.Listener {

    private lateinit var binding: ActivityMainBinding
    private var obdConnection: OBDConnection? = null
    private var obdProtocol: OBDProtocol? = null
    private var canProtocol: CANProtocol? = null
    private var securityTester: SecurityTester? = null

    private val logAdapter = LogAdapter()

    // ConnectionSheetFragment.Listener
    override val isConnected: Boolean get() = obdConnection?.isConnected() == true
    override val connectedLabel: String get() = _connectedLabel
    private var _connectedLabel = ""

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
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
        binding.rvLog.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }

        // Gear icon → open connection sheet
        binding.btnGear.setOnClickListener {
            ConnectionSheetFragment()
                .show(supportFragmentManager, "connection_sheet")
        }

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
        log("CarHackerKit initialized. Tap ⚙ to connect.")
        log("⚠️  For authorized security research on isolated benches only.")
    }

    // ── ConnectionSheetFragment.Listener ─────────────────────────────────────

    override fun onConnectRequested(connection: OBDConnection, label: String) {
        lifecycleScope.launch {
            log("Connecting: $label…")
            obdConnection = connection
            if (connection.connect()) {
                obdProtocol = OBDProtocol(connection)
                val result = obdProtocol?.initialize()
                if (result?.isSuccess == true) {
                    _connectedLabel = label
                    log("✓ Connected — $label")
                    updateConnectionStatus(true)
                    setupProtocolListeners()
                } else {
                    log("❌ Init failed: ${result?.exceptionOrNull()?.message}")
                    doDisconnect()
                }
            } else {
                log("❌ Connection failed")
                obdConnection = null
            }
        }
    }

    override fun onDisconnectRequested() = doDisconnect()

    // ── internal helpers ──────────────────────────────────────────────────────

    private fun doDisconnect() {
        lifecycleScope.launch {
            obdProtocol?.shutdown()
            obdConnection?.disconnect()
            canProtocol?.shutdown()
            securityTester?.shutdown()
            obdProtocol = null
            obdConnection = null
            canProtocol = null
            securityTester = null
            _connectedLabel = ""
            log("Disconnected")
            updateConnectionStatus(false)
        }
    }

    private fun checkPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun setupProtocolListeners() {
        obdProtocol?.let { protocol ->
            lifecycleScope.launch {
                protocol.events.collectLatest { event ->
                    when (event) {
                        is OBDEvent.CommandSent -> {
                            log("TX: ${event.command}")
                            val rx = event.response
                            log("RX: ${rx.take(100)}${if (rx.length > 100) "…" else ""}")
                        }
                        is OBDEvent.PIDsEnumerated ->
                            log("Mode 0x${event.mode.toString(16).uppercase()}: ${event.pids.size} PIDs")
                        is OBDEvent.BruteForcComplete ->
                            log("Brute force done: ${event.pids.size} PIDs found")
                        is OBDEvent.ManufacturerModesDiscovered ->
                            log("Manufacturer modes: ${event.modes.keys.size} found")
                        is OBDEvent.Error ->
                            log("❌ ${event.message}")
                        else -> {}
                    }
                }
            }
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        runOnUiThread {
            binding.tvConnectionStatus.text = if (connected) _connectedLabel.ifEmpty { "Connected" } else "Disconnected"
            binding.tvConnectionStatus.setTextColor(
                ContextCompat.getColor(this, if (connected) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
            )
            binding.btnGear.setColorFilter(
                ContextCompat.getColor(this, if (connected) android.R.color.holo_green_dark else android.R.color.darker_gray)
            )
            binding.layoutFeatures.visibility = if (connected) View.VISIBLE else View.GONE
        }
    }

    // ── features ──────────────────────────────────────────────────────────────

    private fun enumeratePIDs() {
        lifecycleScope.launch {
            log("═══ Enumerating Supported PIDs ═══")
            obdProtocol?.let { protocol ->
                log("Mode 01 (Current Data):")
                protocol.enumerateSupportedPIDs(0x01).forEach { pid ->
                    val info = PIDDefinitions.MODE_01_PIDS[pid]
                    log("  PID 0x${pid.toString(16).padStart(2, '0').uppercase()}: ${info?.name ?: "Unknown"}")
                }
                log("Mode 09 (Vehicle Info):")
                protocol.enumerateSupportedPIDs(0x09).forEach { pid ->
                    val info = PIDDefinitions.MODE_09_PIDS[pid]
                    log("  PID 0x${pid.toString(16).padStart(2, '0').uppercase()}: ${info?.name ?: "Unknown"}")
                }
            }
            log("═══════════════════════════════════")
        }
    }

    private fun bruteForcePIDs() {
        lifecycleScope.launch {
            log("═══ Brute Force PID Discovery ═══")
            log("Testing Mode 01 PIDs 0x01–0xFF…")
            obdProtocol?.let { protocol ->
                val found = protocol.bruteForcePIDs(0x01, 0x01, 0xFF) { pid, total, supported ->
                    if (supported) log("  Found PID 0x${pid.toString(16).padStart(2, '0').uppercase()}")
                    if (pid % 32 == 0) log("  Progress: $pid / $total")
                }
                log("Done. ${found.size} responding PIDs.")
            }
            log("═══════════════════════════════════")
        }
    }

    private fun readDTCs() {
        lifecycleScope.launch {
            log("═══ Diagnostic Trouble Codes ═══")
            obdProtocol?.let { protocol ->
                val stored = protocol.readDTCs(0x03)
                if (stored.isSuccess) {
                    val list = stored.getOrNull() ?: emptyList()
                    log("Stored (${list.size}): ${if (list.isEmpty()) "none" else ""}")
                    list.forEach { log("  ${it.code} (${it.type})") }
                }
                val pending = protocol.readDTCs(0x07)
                if (pending.isSuccess) {
                    val list = pending.getOrNull() ?: emptyList()
                    log("Pending (${list.size}): ${if (list.isEmpty()) "none" else ""}")
                    list.forEach { log("  ${it.code} (${it.type})") }
                }
            }
            log("═══════════════════════════════════")
        }
    }

    private fun clearDTCs() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear DTCs")
            .setMessage("Clear all diagnostic trouble codes? This resets readiness monitors.")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    val ok = obdProtocol?.clearDTCs()?.getOrNull() == true
                    log(if (ok) "✓ DTCs cleared" else "❌ Failed to clear DTCs")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getVehicleInfo() {
        lifecycleScope.launch {
            log("═══ Vehicle Information ═══")
            obdProtocol?.let { protocol ->
                log("VIN: ${protocol.getVIN().getOrNull() ?: "N/A"}")
                log("ECU Name: ${protocol.getECUName().getOrNull() ?: "N/A"}")
                log("Calibration ID: ${protocol.getCalibrationID().getOrNull() ?: "N/A"}")
            }
            log("═══════════════════════════════════")
        }
    }

    private fun runSecurityScan() {
        lifecycleScope.launch {
            log("═══ Security Assessment ═══")
            log("⚠️  Ensure you have authorization to test this vehicle")
            canProtocol = CANProtocol()
            securityTester = SecurityTester(obdProtocol, canProtocol)
            securityTester?.progress?.collectLatest { progress ->
                when (progress) {
                    is TestProgress.Running  -> log("[${(progress.progress * 100).toInt()}%] ${progress.message}")
                    is TestProgress.Complete -> log("✓ Assessment complete")
                    is TestProgress.Error    -> log("❌ ${progress.message}")
                    else                     -> {}
                }
            }
            securityTester?.runFullAssessment()?.let { report ->
                log("\n${report.summary}")
                log("${report.findings.size} finding(s) — see log above.")
            }
            log("═══════════════════════════════════")
        }
    }

    private fun exportLog() {
        val text = logAdapter.getFullLog()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CarHackerKit Log", text))
        Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun clearLog() = logAdapter.clear()

    private fun log(message: String) {
        runOnUiThread {
            logAdapter.add(message)
            binding.rvLog.scrollToPosition(logAdapter.itemCount - 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        doDisconnect()
    }
}

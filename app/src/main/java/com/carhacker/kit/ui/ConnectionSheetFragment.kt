package com.carhacker.kit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.carhacker.kit.R
import com.carhacker.kit.databinding.FragmentConnectionSheetBinding
import com.carhacker.kit.obd.BluetoothOBDConnection
import com.carhacker.kit.obd.OBDConnection
import com.carhacker.kit.obd.SimulatedOBDConnection
import com.carhacker.kit.obd.USBOBDConnection
import com.carhacker.kit.obd.WiFiOBDConnection
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

enum class InterfaceType { BLUETOOTH, BLE, WIFI, USB_CAN, SIMULATOR }

class ConnectionSheetFragment : BottomSheetDialogFragment() {

    interface Listener {
        fun onConnectRequested(connection: OBDConnection, label: String)
        fun onDisconnectRequested()
        val isConnected: Boolean
        val connectedLabel: String
    }

    private var _binding: FragmentConnectionSheetBinding? = null
    private val binding get() = _binding!!

    private var selectedType: InterfaceType? = null
    private val btAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }

    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val bleDevices = mutableListOf<BluetoothDevice>()

    private lateinit var btDeviceAdapter: DeviceListAdapter
    private lateinit var bleDeviceAdapter: DeviceListAdapter

    private var selectedBtDevice: BluetoothDevice? = null
    private var selectedBleDevice: BluetoothDevice? = null
    private var detectedUsbDevice: UsbDevice? = null

    private var bleScanning = false
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (bleDevices.none { it.address == device.address }) {
                bleDevices.add(device)
                bleDeviceAdapter.notifyItemInserted(bleDevices.size - 1)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConnectionSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listener = activity as? Listener

        // Update status chip
        if (listener?.isConnected == true) {
            binding.tvConnectionStatus.text = listener.connectedLabel
            binding.tvConnectionStatus.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            )
            binding.btnDisconnect.isEnabled = true
        }

        // BT device list
        btDeviceAdapter = DeviceListAdapter(pairedDevices) { device ->
            selectedBtDevice = device
            binding.btnConnect.isEnabled = true
        }
        binding.rvBtDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBtDevices.adapter = btDeviceAdapter

        // BLE device list
        bleDeviceAdapter = DeviceListAdapter(bleDevices) { device ->
            selectedBleDevice = device
            binding.btnConnect.isEnabled = true
        }
        binding.rvBleDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBleDevices.adapter = bleDeviceAdapter

        // Interface card clicks
        listOf(
            binding.cardBluetooth to InterfaceType.BLUETOOTH,
            binding.cardBle        to InterfaceType.BLE,
            binding.cardWifi       to InterfaceType.WIFI,
            binding.cardUsb        to InterfaceType.USB_CAN,
            binding.cardSimulator  to InterfaceType.SIMULATOR,
        ).forEach { (card, type) ->
            card.setOnClickListener { selectInterface(type) }
        }

        // BT scan
        binding.btnBtScan.setOnClickListener { loadPairedDevices() }

        // BLE scan
        binding.btnBleScan.setOnClickListener {
            if (bleScanning) stopBleScan() else startBleScan()
        }

        // Connect
        binding.btnConnect.setOnClickListener {
            val conn = buildConnection() ?: return@setOnClickListener
            listener?.onConnectRequested(conn, connectionLabel())
            dismiss()
        }

        // Disconnect
        binding.btnDisconnect.setOnClickListener {
            listener?.onDisconnectRequested()
            dismiss()
        }
    }

    private fun selectInterface(type: InterfaceType) {
        selectedType = type
        selectedBtDevice = null
        selectedBleDevice = null

        // Highlight selected card, dim others
        val allCards = listOf(
            binding.cardBluetooth to InterfaceType.BLUETOOTH,
            binding.cardBle        to InterfaceType.BLE,
            binding.cardWifi       to InterfaceType.WIFI,
            binding.cardUsb        to InterfaceType.USB_CAN,
            binding.cardSimulator  to InterfaceType.SIMULATOR,
        )
        allCards.forEach { (card, cardType) ->
            val selected = cardType == type
            card.setCardBackgroundColor(
                if (selected) 0xFF2D1A3A.toInt() else 0xFF252540.toInt()
            )
            card.strokeWidth = if (selected) 2 else 0
            card.strokeColor = if (selected) 0xFFE94560.toInt() else 0
            // tint icons/labels inside
            card.getChildAt(0)?.let { inner ->
                if (inner is ViewGroup) {
                    for (i in 0 until inner.childCount) {
                        (inner.getChildAt(i) as? TextView)?.setTextColor(
                            if (selected) 0xFFE94560.toInt() else 0xFFA0A0B0.toInt()
                        )
                    }
                }
            }
        }

        // Show/hide config sections
        binding.layoutConfig.visibility = if (type == InterfaceType.SIMULATOR) View.GONE else View.VISIBLE
        binding.configBluetooth.visibility = if (type == InterfaceType.BLUETOOTH) View.VISIBLE else View.GONE
        binding.configBle.visibility       = if (type == InterfaceType.BLE)       View.VISIBLE else View.GONE
        binding.configWifi.visibility      = if (type == InterfaceType.WIFI)      View.VISIBLE else View.GONE
        binding.configUsb.visibility       = if (type == InterfaceType.USB_CAN)   View.VISIBLE else View.GONE

        // Auto-populate on selection
        when (type) {
            InterfaceType.BLUETOOTH  -> loadPairedDevices()
            InterfaceType.USB_CAN    -> detectUsbDevice()
            InterfaceType.SIMULATOR  -> binding.btnConnect.isEnabled = true
            InterfaceType.WIFI       -> binding.btnConnect.isEnabled = true
            else                     -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        if (!hasBluetoothPermission()) return
        pairedDevices.clear()
        btAdapter?.bondedDevices?.let { pairedDevices.addAll(it) }
        btDeviceAdapter.notifyDataSetChanged()
        if (pairedDevices.isNotEmpty()) binding.btnConnect.isEnabled = false // wait for selection
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasBluetoothPermission()) return
        bleDevices.clear()
        bleDeviceAdapter.notifyDataSetChanged()
        btAdapter?.bluetoothLeScanner?.startScan(bleScanCallback)
        bleScanning = true
        binding.btnBleScan.text = "Stop Scan"
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        btAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        bleScanning = false
        binding.btnBleScan.text = "Scan BLE"
    }

    private fun detectUsbDevice() {
        val usbManager = requireContext().getSystemService(UsbManager::class.java)
        detectedUsbDevice = usbManager.deviceList.values.firstOrNull()
        binding.tvUsbDevice.text = detectedUsbDevice?.let {
            "${it.productName ?: "USB Device"} (${it.vendorId}:${it.productId})"
        } ?: "No USB device detected"
        binding.btnConnect.isEnabled = detectedUsbDevice != null
    }

    private fun buildConnection(): OBDConnection? = when (selectedType) {
        InterfaceType.BLUETOOTH -> selectedBtDevice?.let { BluetoothOBDConnection(it) }
        InterfaceType.BLE       -> selectedBleDevice?.let { BluetoothOBDConnection(it) } // BLE via SPP fallback
        InterfaceType.WIFI      -> {
            val host = binding.etWifiHost.text?.toString()?.trim() ?: "192.168.0.10"
            val port = binding.etWifiPort.text?.toString()?.toIntOrNull() ?: 35000
            WiFiOBDConnection(host, port)
        }
        InterfaceType.USB_CAN   -> detectedUsbDevice?.let {
            USBOBDConnection(requireContext(), it)
        }
        InterfaceType.SIMULATOR -> SimulatedOBDConnection()
        null                    -> null
    }

    private fun connectionLabel(): String = when (selectedType) {
        InterfaceType.BLUETOOTH -> "BT: ${selectedBtDevice?.name ?: "device"}"
        InterfaceType.BLE       -> "BLE: ${selectedBleDevice?.name ?: "device"}"
        InterfaceType.WIFI      -> "WiFi: ${binding.etWifiHost.text}:${binding.etWifiPort.text}"
        InterfaceType.USB_CAN   -> "USB: ${detectedUsbDevice?.productName ?: "device"}"
        InterfaceType.SIMULATOR -> "Simulator"
        null                    -> "Unknown"
    }

    private fun hasBluetoothPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        if (bleScanning) stopBleScan()
        super.onDestroyView()
        _binding = null
    }
}

// Minimal RecyclerView adapter for BT/BLE device lists
class DeviceListAdapter(
    private val devices: List<BluetoothDevice>,
    private val onSelect: (BluetoothDevice) -> Unit,
) : RecyclerView.Adapter<DeviceListAdapter.VH>() {

    private var selectedPos = -1

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 20, 16, 20)
            textSize = 13f
            setTextColor(0xFFA0A0B0.toInt())
        }
        return VH(tv)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val device = devices[position]
        holder.tv.text = "${device.name ?: "Unknown"}  ${device.address}"
        val selected = position == selectedPos
        holder.tv.setTextColor(if (selected) 0xFFE94560.toInt() else 0xFFA0A0B0.toInt())
        holder.tv.setBackgroundColor(if (selected) 0x22E94560.toInt() else android.graphics.Color.TRANSPARENT)
        holder.tv.setOnClickListener {
            val prev = selectedPos
            selectedPos = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPos)
            onSelect(device)
        }
    }

    override fun getItemCount() = devices.size
}

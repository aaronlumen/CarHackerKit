package com.carhacker.kit.obd

import android.content.Context
import android.hardware.usb.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.hoho.android.usbserial.driver.*
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*
import java.util.UUID
import java.util.concurrent.Executors

/**
 * OBD Connection Interface
 * Abstracts USB and Bluetooth connections
 */
interface OBDConnection {
    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun send(data: String)
    suspend fun receive(timeoutMs: Long): String
    fun isConnected(): Boolean
}

/**
 * USB Serial Connection for OBD-II Adapters
 * Supports ELM327, STN, OBDLink USB adapters
 */
class USBOBDConnection(
    private val context: Context,
    private val usbDevice: UsbDevice
) : OBDConnection {
    
    private var usbManager: UsbManager? = null
    private var connection: UsbDeviceConnection? = null
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    
    private val receiveChannel = Channel<String>(Channel.BUFFERED)
    private val receiveBuffer = StringBuilder()
    private val executor = Executors.newSingleThreadExecutor()
    
    companion object {
        const val BAUD_RATE = 38400  // Standard ELM327 baud rate
        const val DATA_BITS = 8
        const val STOP_BITS = UsbSerialPort.STOPBITS_1
        const val PARITY = UsbSerialPort.PARITY_NONE
    }
    
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            
            // Get driver for device
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val driver = availableDrivers.find { it.device == usbDevice }
                ?: throw IOException("No driver found for device")
            
            // Open connection
            connection = usbManager?.openDevice(usbDevice)
                ?: throw IOException("Failed to open USB device")
            
            // Get first port
            serialPort = driver.ports.firstOrNull()
                ?: throw IOException("No serial ports available")
            
            serialPort?.open(connection)
            serialPort?.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY)
            
            // Start IO manager for async read
            ioManager = SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    val text = String(data)
                    synchronized(receiveBuffer) {
                        receiveBuffer.append(text)
                        // Check for complete response (ends with >)
                        if (receiveBuffer.contains(">")) {
                            val response = receiveBuffer.toString()
                            receiveBuffer.clear()
                            runBlocking { receiveChannel.send(response) }
                        }
                    }
                }
                
                override fun onRunError(e: Exception) {
                    runBlocking { receiveChannel.send("ERROR: ${e.message}") }
                }
            })
            ioManager?.start()
            
            true
        } catch (e: Exception) {
            disconnect()
            false
        }
    }
    
    override suspend fun disconnect() {
        ioManager?.stop()
        ioManager = null
        serialPort?.close()
        serialPort = null
        connection?.close()
        connection = null
    }
    
    override suspend fun send(data: String) {
        serialPort?.write(data.toByteArray(), 1000)
    }
    
    override suspend fun receive(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            receiveChannel.receive()
        } ?: "TIMEOUT"
    }
    
    override fun isConnected(): Boolean = serialPort != null && connection != null
}

/**
 * Bluetooth Classic Connection for OBD-II Adapters
 * Standard SPP profile (most ELM327 Bluetooth adapters)
 */
class BluetoothOBDConnection(
    private val device: BluetoothDevice
) : OBDConnection {
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    companion object {
        // Standard SPP UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Cancel any ongoing discovery
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            
            // Create socket
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            
            true
        } catch (e: Exception) {
            disconnect()
            false
        }
    }
    
    override suspend fun disconnect() {
        inputStream?.close()
        outputStream?.close()
        socket?.close()
        inputStream = null
        outputStream = null
        socket = null
    }
    
    override suspend fun send(data: String) = withContext(Dispatchers.IO) {
        outputStream?.write(data.toByteArray())
        outputStream?.flush()
    }
    
    override suspend fun receive(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val buffer = ByteArray(1024)
        val response = StringBuilder()
        val startTime = System.currentTimeMillis()
        
        try {
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (inputStream?.available() ?: 0 > 0) {
                    val bytes = inputStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        response.append(String(buffer, 0, bytes))
                        if (response.contains(">")) {
                            break
                        }
                    }
                }
                delay(10)
            }
        } catch (e: Exception) {
            response.append("ERROR: ${e.message}")
        }
        
        response.toString()
    }
    
    override fun isConnected(): Boolean = socket?.isConnected == true
}

/**
 * WiFi Connection for OBD-II Adapters
 * For WiFi-based ELM327 adapters (typically 192.168.0.10:35000)
 */
class WiFiOBDConnection(
    private val host: String = "192.168.0.10",
    private val port: Int = 35000
) : OBDConnection {
    
    private var socket: java.net.Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = java.net.Socket(host, port)
            socket?.soTimeout = 2000
            reader = BufferedReader(InputStreamReader(socket?.inputStream))
            writer = PrintWriter(socket?.outputStream, true)
            true
        } catch (e: Exception) {
            disconnect()
            false
        }
    }
    
    override suspend fun disconnect() {
        reader?.close()
        writer?.close()
        socket?.close()
        reader = null
        writer = null
        socket = null
    }
    
    override suspend fun send(data: String) = withContext(Dispatchers.IO) {
        writer?.println(data)
    }
    
    override suspend fun receive(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val response = StringBuilder()
        val startTime = System.currentTimeMillis()
        
        try {
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (reader?.ready() == true) {
                    val char = reader?.read()?.toChar()
                    if (char != null) {
                        response.append(char)
                        if (char == '>') break
                    }
                }
                delay(10)
            }
        } catch (e: Exception) {
            response.append("ERROR: ${e.message}")
        }
        
        response.toString()
    }
    
    override fun isConnected(): Boolean = socket?.isConnected == true && socket?.isClosed == false
}

/**
 * Simulated Connection for Testing
 * Returns realistic OBD-II responses without hardware
 */
class SimulatedOBDConnection : OBDConnection {
    
    private var connected = false
    private val supportedPIDs = mutableMapOf(
        0x01 to setOf(0x00, 0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x1C, 0x20, 0x21),
        0x09 to setOf(0x00, 0x02, 0x04, 0x06, 0x0A)
    )
    
    // Simulated sensor values
    private var rpm = 850
    private var speed = 0
    private var coolantTemp = 80
    private var throttle = 15
    
    override suspend fun connect(): Boolean {
        connected = true
        return true
    }
    
    override suspend fun disconnect() {
        connected = false
    }
    
    override suspend fun send(data: String) {
        // Simulate slight delay
        delay(50)
    }
    
    override suspend fun receive(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        // Simulate processing time
        delay(100)
        
        "SIMULATED>" // Placeholder - actual logic below
    }
    
    fun generateResponse(command: String): String {
        val cmd = command.replace("\r", "").replace(" ", "").uppercase()
        
        return when {
            // AT commands
            cmd == "ATZ" -> "ELM327 v1.5 (SIMULATED)>"
            cmd == "ATE0" -> "OK>"
            cmd == "ATL0" -> "OK>"
            cmd == "ATS0" -> "OK>"
            cmd == "ATSP0" -> "OK>"
            cmd == "ATH1" -> "OK>"
            cmd == "ATI" -> "ELM327 v1.5 SIMULATED>"
            
            // Mode 01 - Current Data
            cmd == "0100" -> {
                // PIDs 01-20 supported bitmap
                "41 00 BF 1F B8 10>"
            }
            cmd == "0105" -> String.format("41 05 %02X>", coolantTemp + 40) // Coolant temp
            cmd == "010C" -> {
                // RPM = value * 4
                val rpmValue = rpm * 4
                String.format("41 0C %02X %02X>", rpmValue shr 8, rpmValue and 0xFF)
            }
            cmd == "010D" -> String.format("41 0D %02X>", speed) // Vehicle speed
            cmd == "0111" -> String.format("41 11 %02X>", (throttle * 255 / 100)) // Throttle position
            
            // Mode 03 - DTCs
            cmd == "03" -> "43 01 33 00 00 00 00>" // P0133
            
            // Mode 09 - Vehicle Info
            cmd == "0902" -> "49 02 01 31 47 31 4A 43 35 34 34 34 52 37 32 35 32 33 36 37>" // VIN
            cmd == "090A" -> "49 0A 01 45 43 4D 00 2D 41 42 43>" // ECU Name
            
            // Unknown/unsupported
            else -> "NO DATA>"
        }
    }
    
    override fun isConnected(): Boolean = connected
    
    // Methods to simulate sensor changes
    fun setRPM(value: Int) { rpm = value.coerceIn(0, 8000) }
    fun setSpeed(value: Int) { speed = value.coerceIn(0, 255) }
    fun setCoolantTemp(value: Int) { coolantTemp = value.coerceIn(-40, 215) }
    fun setThrottle(value: Int) { throttle = value.coerceIn(0, 100) }
}

package com.lighttowers.ble.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

data class ScannedBarDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isKnownLed: Boolean
)

data class ConnectedBarDevice(
    val address: String,
    val displayName: String,
    val isReady: Boolean,
    val rgbOrder: RgbOrder
)

class BleManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val prefs = context.getSharedPreferences("light_towers_prefs", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private val gattMap = ConcurrentHashMap<String, BluetoothGatt>()
    private val writeCharMap = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedBarDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedBarDevice>> = _scannedDevices.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<ConnectedBarDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedBarDevice>> = _connectedDevices.asStateFlow()

    private val _lastHexPacket = MutableStateFlow("Brak wysłanych ramek")
    val lastHexPacket: StateFlow<String> = _lastHexPacket.asStateFlow()

    private val _statusMessage = MutableStateFlow("Gotowy do skanowania")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var scanStopRunnable: Runnable? = null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun getSavedOrder(address: String): RgbOrder {
        val saved = prefs.getString("order_$address", RgbOrder.RGB.name)
        return RgbOrder.entries.firstOrNull { it.name == saved } ?: RgbOrder.RGB
    }

    fun setDeviceRgbOrder(address: String, order: RgbOrder) {
        prefs.edit().putString("order_$address", order.name).apply()
        _connectedDevices.update { list ->
            list.map { if (it.address == address) it.copy(rgbOrder = order) else it }
        }
    }

    fun setAllRgbOrder(order: RgbOrder) {
        val editor = prefs.edit()
        _connectedDevices.value.forEach { editor.putString("order_${it.address}", order.name) }
        editor.apply()
        _connectedDevices.update { list -> list.map { it.copy(rgbOrder = order) } }
    }

    fun renameDevice(address: String, newName: String) {
        val clean = newName.trim().ifEmpty { "Light Tower" }
        prefs.edit().putString("name_$address", clean).apply()
        _connectedDevices.update { list ->
            list.map { if (it.address == address) it.copy(displayName = clean) else it }
        }
    }

    private fun getSavedName(address: String, fallback: String): String {
        return prefs.getString("name_$address", null) ?: fallback
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val rawName = device.name ?: result.scanRecord?.deviceName ?: "Nieznane BLE"
            val upper = rawName.uppercase()
            val isKnown = upper.contains("ELK") ||
                upper.contains("BLEDOM") ||
                upper.contains("MELK") ||
                upper.contains("LIGHT") ||
                upper.contains("TOWER") ||
                address.uppercase().startsWith("BE:16:46")

            val item = ScannedBarDevice(
                address = address,
                name = getSavedName(address, rawName),
                rssi = result.rssi,
                isKnownLed = isKnown
            )

            _scannedDevices.update { current ->
                val filtered = current.filterNot { it.address == address }
                (filtered + item).sortedWith(
                    compareByDescending<ScannedBarDevice> { it.isKnownLed }
                        .thenByDescending { it.rssi }
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _statusMessage.value = "Błąd skanowania BLE (kod: $errorCode)"
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothEnabled()) {
            _statusMessage.value = "Włącz Bluetooth w telefonie"
            return
        }
        stopScan()
        _scannedDevices.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
            _statusMessage.value = "Skanowanie urządzeń BLE..."
            scanStopRunnable = Runnable { stopScan() }
            handler.postDelayed(scanStopRunnable!!, 12_000L)
        } catch (e: Exception) {
            _isScanning.value = false
            _statusMessage.value = "Nie można rozpocząć skanowania: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanStopRunnable?.let { handler.removeCallbacks(it) }
        scanStopRunnable = null
        if (_isScanning.value) {
            try {
                adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: Exception) {
            }
            _isScanning.value = false
            _statusMessage.value = "Skanowanie zakończone"
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, fallbackName: String) {
        val btAdapter = adapter ?: return
        if (gattMap.containsKey(address)) return

        val device: BluetoothDevice = try {
            btAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            _statusMessage.value = "Nieprawidłowy adres MAC: $address"
            return
        }

        val displayName = getSavedName(address, fallbackName)
        val order = getSavedOrder(address)
        _statusMessage.value = "Łączenie z $displayName..."

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectedDevices.update { list ->
                            list.filterNot { it.address == address } + ConnectedBarDevice(
                                address = address,
                                displayName = displayName,
                                isReady = false,
                                rgbOrder = order
                            )
                        }
                        _statusMessage.value = "Połączono z $displayName, wykrywanie usług..."
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        gattMap.remove(address)
                        writeCharMap.remove(address)
                        _connectedDevices.update { list -> list.filterNot { it.address == address } }
                        _statusMessage.value = "Rozłączono z $displayName"
                        try {
                            gatt.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _statusMessage.value = "Błąd wykrywania usług GATT ($status)"
                    return
                }
                val service = gatt.getService(ElkBledomProtocol.SERVICE_UUID)
                var targetChar = service?.getCharacteristic(ElkBledomProtocol.WRITE_CHAR_UUID)

                if (targetChar == null) {
                    for (s in gatt.services) {
                        for (c in s.characteristics) {
                            val props = c.properties
                            val writable = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                            if (writable && c.uuid.toString().lowercase().contains("fff3")) {
                                targetChar = c
                                break
                            }
                        }
                        if (targetChar != null) break
                    }
                }

                if (targetChar == null && service != null) {
                    targetChar = service.characteristics.firstOrNull { c ->
                        val props = c.properties
                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                            (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                    }
                }

                if (targetChar != null) {
                    writeCharMap[address] = targetChar
                    _connectedDevices.update { list ->
                        list.map { if (it.address == address) it.copy(isReady = true) else it }
                    }
                    _statusMessage.value = "Gotowe do sterowania: $displayName"
                } else {
                    _statusMessage.value = "Nie znaleziono charakterystyki zapisu (FFF3) w $displayName"
                }
            }
        }

        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
        gattMap[address] = gatt
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        gattMap.remove(address)?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
            }
        }
        writeCharMap.remove(address)
        _connectedDevices.update { list -> list.filterNot { it.address == address } }
    }

    @SuppressLint("MissingPermission")
    fun sendRaw(targetAddress: String?, payload: ByteArray) {
        _lastHexPacket.value = ElkBledomProtocol.toHexString(payload)
        val targets = if (targetAddress == null) {
            writeCharMap.keys.toList()
        } else {
            listOf(targetAddress)
        }
        for (addr in targets) {
            writeToDevice(addr, payload)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendColor(targetAddress: String?, r: Int, g: Int, b: Int, fallbackOrder: RgbOrder) {
        val targets = if (targetAddress == null) {
            _connectedDevices.value
        } else {
            _connectedDevices.value.filter { it.address == targetAddress }
        }
        if (targets.isEmpty()) {
            val preview = ElkBledomProtocol.color(r, g, b, fallbackOrder)
            _lastHexPacket.value = ElkBledomProtocol.toHexString(preview)
            return
        }
        for (dev in targets) {
            val packet = ElkBledomProtocol.color(r, g, b, dev.rgbOrder)
            _lastHexPacket.value = ElkBledomProtocol.toHexString(packet)
            writeToDevice(dev.address, packet)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeToDevice(address: String, payload: ByteArray) {
        val gatt = gattMap[address] ?: return
        val characteristic = writeCharMap[address] ?: return
        val writeType =
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, payload, writeType)
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = writeType
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun closeAll() {
        stopScan()
        gattMap.values.forEach { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
            }
        }
        gattMap.clear()
        writeCharMap.clear()
        _connectedDevices.value = emptyList()
    }
}

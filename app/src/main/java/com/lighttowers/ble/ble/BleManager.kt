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

data class SavedBarDevice(
    val address: String,
    val name: String,
    val rgbOrder: RgbOrder,
    val autoConnect: Boolean
)

data class ConnectedBarDevice(
    val address: String,
    val displayName: String,
    val isReady: Boolean,
    val rgbOrder: RgbOrder
)

data class CustomColorPreset(
    val id: String,
    val name: String,
    val r: Int,
    val g: Int,
    val b: Int
)

class BleManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val prefs = context.getSharedPreferences("light_towers_prefs", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private val gattMap = ConcurrentHashMap<String, BluetoothGatt>()
    private val writeCharMap = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedBarDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedBarDevice>> = _scannedDevices.asStateFlow()

    private val _savedDevices = MutableStateFlow<List<SavedBarDevice>>(emptyList())
    val savedDevices: StateFlow<List<SavedBarDevice>> = _savedDevices.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<ConnectedBarDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedBarDevice>> = _connectedDevices.asStateFlow()

    private val _customPresets = MutableStateFlow<List<CustomColorPreset>>(emptyList())
    val customPresets: StateFlow<List<CustomColorPreset>> = _customPresets.asStateFlow()

    private val _lastHexPacket = MutableStateFlow("Brak wysłanych ramek")
    val lastHexPacket: StateFlow<String> = _lastHexPacket.asStateFlow()

    private val _statusMessage = MutableStateFlow("Gotowy do skanowania")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var scanStopRunnable: Runnable? = null

    init {
        loadSavedDevices()
        loadCustomPresets()
        refreshBluetoothState()
    }

    fun refreshBluetoothState(): Boolean {
        val enabled = adapter?.isEnabled == true
        _isBluetoothEnabled.value = enabled
        return enabled
    }

    // --- ZAPISYWANIE URZĄDZEŃ ---

    private fun loadSavedDevices() {
        val addresses = prefs.getStringSet("saved_addresses", emptySet()) ?: emptySet()
        val list = addresses.map { addr ->
            val name = prefs.getString("name_$addr", "Light Tower") ?: "Light Tower"
            val orderName = prefs.getString("order_$addr", RgbOrder.RGB.name)
            val order = RgbOrder.entries.firstOrNull { it.name == orderName } ?: RgbOrder.RGB
            val auto = prefs.getBoolean("auto_$addr", true)
            SavedBarDevice(address = addr, name = name, rgbOrder = order, autoConnect = auto)
        }.sortedBy { it.name }
        _savedDevices.value = list
    }

    fun saveDevice(address: String, name: String, autoConnect: Boolean = true) {
        val current = (prefs.getStringSet("saved_addresses", emptySet()) ?: emptySet()).toMutableSet()
        current.add(address)
        val cleanName = name.trim().ifEmpty { "Light Tower" }
        prefs.edit()
            .putStringSet("saved_addresses", current)
            .putString("name_$address", cleanName)
            .putBoolean("auto_$address", autoConnect)
            .apply()
        loadSavedDevices()
        _statusMessage.value = "Zapisano urządzenie: $cleanName"
    }

    fun removeSavedDevice(address: String) {
        val current = (prefs.getStringSet("saved_addresses", emptySet()) ?: emptySet()).toMutableSet()
        current.remove(address)
        prefs.edit()
            .putStringSet("saved_addresses", current)
            .remove("auto_$address")
            .apply()
        loadSavedDevices()
        _statusMessage.value = "Usunięto z zapisanych"
    }

    fun setAutoConnect(address: String, enabled: Boolean) {
        prefs.edit().putBoolean("auto_$address", enabled).apply()
        loadSavedDevices()
    }

    fun isDeviceSaved(address: String): Boolean =
        _savedDevices.value.any { it.address == address }

    fun getSavedOrder(address: String): RgbOrder {
        val saved = prefs.getString("order_$address", RgbOrder.RGB.name)
        return RgbOrder.entries.firstOrNull { it.name == saved } ?: RgbOrder.RGB
    }

    fun setDeviceRgbOrder(address: String, order: RgbOrder) {
        prefs.edit().putString("order_$address", order.name).apply()
        _connectedDevices.update { list ->
            list.map { if (it.address == address) it.copy(rgbOrder = order) else it }
        }
        loadSavedDevices()
    }

    fun setAllRgbOrder(order: RgbOrder) {
        val editor = prefs.edit()
        _connectedDevices.value.forEach { editor.putString("order_${it.address}", order.name) }
        _savedDevices.value.forEach { editor.putString("order_${it.address}", order.name) }
        editor.apply()
        _connectedDevices.update { list -> list.map { it.copy(rgbOrder = order) } }
        loadSavedDevices()
    }

    fun renameDevice(address: String, newName: String) {
        val clean = newName.trim().ifEmpty { "Light Tower" }
        prefs.edit().putString("name_$address", clean).apply()
        _connectedDevices.update { list ->
            list.map { if (it.address == address) it.copy(displayName = clean) else it }
        }
        _scannedDevices.update { list ->
            list.map { if (it.address == address) it.copy(name = clean) else it }
        }
        loadSavedDevices()
    }

    private fun getSavedName(address: String, fallback: String): String {
        return prefs.getString("name_$address", null) ?: fallback
    }

    // --- WŁASNE ULUBIONE KOLORY ---

    private fun loadCustomPresets() {
        val raw = prefs.getString("custom_color_presets", "") ?: ""
        if (raw.isBlank()) {
            _customPresets.value = emptyList()
            return
        }
        val parsed = raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 5) {
                val r = parts[2].toIntOrNull() ?: return@mapNotNull null
                val g = parts[3].toIntOrNull() ?: return@mapNotNull null
                val b = parts[4].toIntOrNull() ?: return@mapNotNull null
                CustomColorPreset(parts[0], parts[1], r, g, b)
            } else null
        }
        _customPresets.value = parsed
    }

    fun addCustomPreset(name: String, r: Int, g: Int, b: Int) {
        val cleanName = name.trim().replace("|", "").replace(";", "").ifEmpty { "Kolor" }
        val id = System.currentTimeMillis().toString()
        val updated = _customPresets.value + CustomColorPreset(id, cleanName, r, g, b)
        saveCustomPresets(updated)
    }

    fun removeCustomPreset(id: String) {
        val updated = _customPresets.value.filterNot { it.id == id }
        saveCustomPresets(updated)
    }

    private fun saveCustomPresets(list: List<CustomColorPreset>) {
        val serialized = list.joinToString(";") { "${it.id}|${it.name}|${it.r}|${it.g}|${it.b}" }
        prefs.edit().putString("custom_color_presets", serialized).apply()
        _customPresets.value = list
    }

    // --- SKANOWANIE I ŁĄCZENIE BLE ---

    @SuppressLint("MissingPermission")
    fun connectSavedAutoDevices() {
        if (!refreshBluetoothState()) return
        val toConnect = _savedDevices.value.filter { it.autoConnect }
        if (toConnect.isEmpty()) return
        _statusMessage.value = "Łączenie z zapisanymi barami (${toConnect.size})..."
        toConnect.forEachIndexed { idx, dev ->
            handler.postDelayed({
                connect(dev.address, dev.name)
            }, idx * 450L)
        }
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
                address.uppercase().startsWith("BE:16:46") ||
                isDeviceSaved(address)

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
        if (scanner == null || !refreshBluetoothState()) {
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
                    // Automatyczne zapamiętanie urządzenia po pierwszym udanym połączeniu
                    if (!isDeviceSaved(address)) {
                        saveDevice(address, displayName, autoConnect = true)
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
    fun sendColor(
        targetAddress: String?,
        r: Int,
        g: Int,
        b: Int,
        fallbackOrder: RgbOrder,
        boostPercent: Int = 100
    ) {
        val targets = if (targetAddress == null) {
            _connectedDevices.value
        } else {
            _connectedDevices.value.filter { it.address == targetAddress }
        }
        if (targets.isEmpty()) {
            val preview = ElkBledomProtocol.color(r, g, b, fallbackOrder, boostPercent)
            _lastHexPacket.value = ElkBledomProtocol.toHexString(preview)
            return
        }
        for (dev in targets) {
            val packet = ElkBledomProtocol.color(r, g, b, dev.rgbOrder, boostPercent)
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

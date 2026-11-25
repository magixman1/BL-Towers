package com.lighttowers.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lighttowers.ble.ble.BleManager
import com.lighttowers.ble.ble.ElkBledomProtocol
import com.lighttowers.ble.ble.LedEffect
import com.lighttowers.ble.ble.RgbOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UiState(
    val selectedTargetAddress: String? = null, // null = Wszystkie bary
    val isPowerOn: Boolean = true,
    val red: Int = 255,
    val green: Int = 40,
    val blue: Int = 40,
    val brightness: Int = 100,
    val speed: Int = 60,
    val activeEffectId: Int? = null,
    val globalRgbOrder: RgbOrder = RgbOrder.RGB,
    val showAllBleDevices: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val bleManager = BleManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isScanning = bleManager.isScanning
    val scannedDevices = bleManager.scannedDevices
    val connectedDevices = bleManager.connectedDevices
    val lastHexPacket = bleManager.lastHexPacket
    val statusMessage = bleManager.statusMessage

    private var lastColorSendMs = 0L

    fun selectTarget(address: String?) {
        _uiState.update { state ->
            val targetOrder = if (address != null) {
                bleManager.getSavedOrder(address)
            } else {
                state.globalRgbOrder
            }
            state.copy(selectedTargetAddress = address, globalRgbOrder = targetOrder)
        }
    }

    fun toggleShowAllBle(showAll: Boolean) {
        _uiState.update { it.copy(showAllBleDevices = showAll) }
    }

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(address: String, name: String) = bleManager.connect(address, name)
    fun disconnect(address: String) {
        bleManager.disconnect(address)
        if (_uiState.value.selectedTargetAddress == address) {
            _uiState.update { it.copy(selectedTargetAddress = null) }
        }
    }

    fun renameDevice(address: String, newName: String) = bleManager.renameDevice(address, newName)

    fun setPower(on: Boolean) {
        _uiState.update { it.copy(isPowerOn = on) }
        bleManager.sendRaw(_uiState.value.selectedTargetAddress, ElkBledomProtocol.power(on))
    }

    fun setRgb(r: Int, g: Int, b: Int, forceImmediate: Boolean = false) {
        val rr = r.coerceIn(0, 255)
        val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255)
        _uiState.update { it.copy(red = rr, green = gg, blue = bb, activeEffectId = null, isPowerOn = true) }

        val now = System.currentTimeMillis()
        if (forceImmediate || now - lastColorSendMs >= 45L) {
            lastColorSendMs = now
            bleManager.sendColor(
                targetAddress = _uiState.value.selectedTargetAddress,
                r = rr,
                g = gg,
                b = bb,
                fallbackOrder = _uiState.value.globalRgbOrder
            )
        }
    }

    fun setBrightness(percent: Int) {
        val value = percent.coerceIn(0, 100)
        _uiState.update { it.copy(brightness = value) }
        bleManager.sendRaw(_uiState.value.selectedTargetAddress, ElkBledomProtocol.brightness(value))
    }

    fun setEffect(effect: LedEffect) {
        _uiState.update { it.copy(activeEffectId = effect.id, isPowerOn = true) }
        bleManager.sendRaw(_uiState.value.selectedTargetAddress, ElkBledomProtocol.effect(effect.code))
    }

    fun setSpeed(percent: Int) {
        val value = percent.coerceIn(0, 100)
        _uiState.update { it.copy(speed = value) }
        bleManager.sendRaw(_uiState.value.selectedTargetAddress, ElkBledomProtocol.effectSpeed(value))
    }

    fun setRgbOrder(order: RgbOrder) {
        val target = _uiState.value.selectedTargetAddress
        if (target == null) {
            bleManager.setAllRgbOrder(order)
        } else {
            bleManager.setDeviceRgbOrder(target, order)
        }
        _uiState.update { it.copy(globalRgbOrder = order) }
        val s = _uiState.value
        bleManager.sendColor(target, s.red, s.green, s.blue, order)
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.closeAll()
    }
}

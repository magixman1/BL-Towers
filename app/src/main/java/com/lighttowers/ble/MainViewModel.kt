package com.lighttowers.ble

import android.app.Application
import android.graphics.Color as AndroidColor
import androidx.lifecycle.AndroidViewModel
import com.lighttowers.ble.ble.BleManager
import com.lighttowers.ble.ble.ElkBledomProtocol
import com.lighttowers.ble.ble.LedEffect
import com.lighttowers.ble.ble.RgbOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

data class UiState(
    val selectedTargetAddress: String? = null, // null = Wszystkie bary
    val isPowerOn: Boolean = true,
    val red: Int = 255,
    val green: Int = 40,
    val blue: Int = 40,
    val hue: Float = 0f,
    val saturation: Float = 0.84f,
    val kelvin: Int = 4000,
    val brightness: Int = 100, // 1..150% (101..150% = Turbo Boost)
    val speed: Int = 60,
    val activeEffectId: Int? = null,
    val globalRgbOrder: RgbOrder = RgbOrder.RGB,
    val showAllBleDevices: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val bleManager = BleManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isBluetoothEnabled = bleManager.isBluetoothEnabled
    val isScanning = bleManager.isScanning
    val scannedDevices = bleManager.scannedDevices
    val savedDevices = bleManager.savedDevices
    val connectedDevices = bleManager.connectedDevices
    val customPresets = bleManager.customPresets
    val lastHexPacket = bleManager.lastHexPacket
    val statusMessage = bleManager.statusMessage

    private var lastColorSendMs = 0L
    private var lastBrightnessSendMs = 0L

    fun refreshBluetoothState(): Boolean = bleManager.refreshBluetoothState()

    fun connectSavedAutoDevices() = bleManager.connectSavedAutoDevices()

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

    fun saveDevice(address: String, name: String) = bleManager.saveDevice(address, name, true)
    fun removeSavedDevice(address: String) = bleManager.removeSavedDevice(address)
    fun setAutoConnect(address: String, enabled: Boolean) = bleManager.setAutoConnect(address, enabled)
    fun renameDevice(address: String, newName: String) = bleManager.renameDevice(address, newName)

    fun addCustomPreset(name: String) {
        val s = _uiState.value
        bleManager.addCustomPreset(name, s.red, s.green, s.blue)
    }

    fun removeCustomPreset(id: String) = bleManager.removeCustomPreset(id)

    fun setPower(on: Boolean) {
        _uiState.update { it.copy(isPowerOn = on) }
        bleManager.sendRaw(_uiState.value.selectedTargetAddress, ElkBledomProtocol.power(on))
    }

    fun setRgb(r: Int, g: Int, b: Int, forceImmediate: Boolean = false) {
        val rr = r.coerceIn(0, 255)
        val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255)
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(rr, gg, bb, hsv)

        _uiState.update {
            it.copy(
                red = rr,
                green = gg,
                blue = bb,
                hue = hsv[0],
                saturation = hsv[1],
                activeEffectId = null,
                isPowerOn = true
            )
        }
        dispatchColor(rr, gg, bb, forceImmediate)
    }

    fun setHsv(hue: Float, saturation: Float, forceImmediate: Boolean = false) {
        val h = hue.coerceIn(0f, 360f)
        val s = saturation.coerceIn(0f, 1f)
        val colorInt = AndroidColor.HSVToColor(floatArrayOf(h, s, 1f))
        val rr = AndroidColor.red(colorInt)
        val gg = AndroidColor.green(colorInt)
        val bb = AndroidColor.blue(colorInt)

        _uiState.update {
            it.copy(
                red = rr,
                green = gg,
                blue = bb,
                hue = h,
                saturation = s,
                activeEffectId = null,
                isPowerOn = true
            )
        }
        dispatchColor(rr, gg, bb, forceImmediate)
    }

    fun setKelvin(kelvin: Int, forceImmediate: Boolean = false) {
        val k = kelvin.coerceIn(2000, 9000)
        val (rr, gg, bb) = ElkBledomProtocol.kelvinToRgb(k)
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(rr, gg, bb, hsv)

        _uiState.update {
            it.copy(
                red = rr,
                green = gg,
                blue = bb,
                hue = hsv[0],
                saturation = hsv[1],
                kelvin = k,
                activeEffectId = null,
                isPowerOn = true
            )
        }
        dispatchColor(rr, gg, bb, forceImmediate)
    }

    fun applyHexColor(hexInput: String): Boolean {
        val clean = hexInput.trim().removePrefix("#")
        if (clean.length != 6) return false
        val r = clean.substring(0, 2).toIntOrNull(16) ?: return false
        val g = clean.substring(2, 4).toIntOrNull(16) ?: return false
        val b = clean.substring(4, 6).toIntOrNull(16) ?: return false
        setRgb(r, g, b, forceImmediate = true)
        return true
    }

    private fun dispatchColor(r: Int, g: Int, b: Int, forceImmediate: Boolean) {
        val now = System.currentTimeMillis()
        if (forceImmediate || now - lastColorSendMs >= 45L) {
            lastColorSendMs = now
            val s = _uiState.value
            bleManager.sendColor(
                targetAddress = s.selectedTargetAddress,
                r = r,
                g = g,
                b = b,
                fallbackOrder = s.globalRgbOrder,
                boostPercent = s.brightness
            )
        }
    }

    fun setBrightness(percent: Int, forceImmediate: Boolean = false) {
        val value = percent.coerceIn(1, 150)
        _uiState.update { it.copy(brightness = value, isPowerOn = true) }

        val now = System.currentTimeMillis()
        if (forceImmediate || now - lastBrightnessSendMs >= 50L) {
            lastBrightnessSendMs = now
            val s = _uiState.value
            bleManager.sendRaw(s.selectedTargetAddress, ElkBledomProtocol.brightness(value))
            // W trybie > 100% dodatkowo podbijamy kanały RGB w ramce koloru
            if (value > 100 || forceImmediate) {
                bleManager.sendColor(
                    targetAddress = s.selectedTargetAddress,
                    r = s.red,
                    g = s.green,
                    b = s.blue,
                    fallbackOrder = s.globalRgbOrder,
                    boostPercent = value
                )
            }
        }
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
        bleManager.sendColor(target, s.red, s.green, s.blue, order, s.brightness)
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.closeAll()
    }
}

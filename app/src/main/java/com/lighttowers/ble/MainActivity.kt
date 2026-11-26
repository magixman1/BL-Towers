package com.lighttowers.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lighttowers.ble.ble.ConnectedBarDevice
import com.lighttowers.ble.ble.CustomColorPreset
import com.lighttowers.ble.ble.ElkBledomProtocol
import com.lighttowers.ble.ble.RgbOrder
import com.lighttowers.ble.ble.SavedBarDevice
import com.lighttowers.ble.ble.ScannedBarDevice

private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    secondary = Color(0xFFA78BFA),
    background = Color(0xFF0D0F14),
    surface = Color(0xFF161A23),
    surfaceVariant = Color(0xFF1F2533),
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFF3F4F6)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppDarkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LightTowersApp()
                }
            }
        }
    }
}

@Composable
fun LightTowersApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()
    val isBtEnabled by vm.isBluetoothEnabled.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val scannedDevices by vm.scannedDevices.collectAsState()
    val savedDevices by vm.savedDevices.collectAsState()
    val connectedDevices by vm.connectedDevices.collectAsState()
    val customPresets by vm.customPresets.collectAsState()
    val lastHex by vm.lastHexPacket.collectAsState()
    val statusMsg by vm.statusMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingActionAfterBt by remember { mutableStateOf<(() -> Unit)?>(null) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // Launcher systemowego okna włączania Bluetooth (ACTION_REQUEST_ENABLE)
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val enabled = result.resultCode == Activity.RESULT_OK || vm.refreshBluetoothState()
        if (enabled) {
            pendingActionAfterBt?.invoke()
            pendingActionAfterBt = null
        }
    }

    // Launcher uprawnień Bluetooth (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, LOCATION)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            if (!vm.refreshBluetoothState()) {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                pendingActionAfterBt?.invoke()
                pendingActionAfterBt = null
            }
        }
    }

    fun runWithBluetoothReady(action: () -> Unit) {
        if (!hasAllPermissions()) {
            pendingActionAfterBt = action
            permissionLauncher.launch(requiredPermissions)
            return
        }
        if (!vm.refreshBluetoothState()) {
            pendingActionAfterBt = action
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        action()
    }

    // Przy starcie aplikacji: sprawdź uprawnienia/Bluetooth i połącz z zapisanymi barami
    LaunchedEffect(Unit) {
        if (hasAllPermissions() && vm.refreshBluetoothState()) {
            vm.connectSavedAutoDevices()
        } else {
            runWithBluetoothReady {
                vm.connectSavedAutoDevices()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.ColorLens, contentDescription = "Kolor") },
                    label = { Text("Kolory") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = "Efekty") },
                    label = { Text("Efekty") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = "RGB") },
                    label = { Text("Zamiana RGB") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Rounded.Bluetooth, contentDescription = "BLE") },
                    label = { Text("Bary (${connectedDevices.size})") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            TopHeader(
                connectedCount = connectedDevices.size,
                savedCount = savedDevices.size,
                isBtEnabled = isBtEnabled,
                isPowerOn = uiState.isPowerOn,
                onEnableBluetooth = { runWithBluetoothReady { vm.connectSavedAutoDevices() } },
                onTogglePower = { vm.setPower(!uiState.isPowerOn) }
            )
            Spacer(Modifier.height(10.dp))

            if (connectedDevices.isNotEmpty()) {
                TargetSelectorRow(
                    connectedDevices = connectedDevices,
                    selectedAddress = uiState.selectedTargetAddress,
                    onSelect = vm::selectTarget
                )
                Spacer(Modifier.height(10.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ControlTab(
                        uiState = uiState,
                        customPresets = customPresets,
                        vm = vm
                    )
                    1 -> EffectsTab(uiState = uiState, vm = vm)
                    2 -> RgbSwapTab(uiState = uiState, lastHex = lastHex, vm = vm)
                    3 -> DevicesTab(
                        isBtEnabled = isBtEnabled,
                        isScanning = isScanning,
                        statusMsg = statusMsg,
                        showAll = uiState.showAllBleDevices,
                        savedDevices = savedDevices,
                        connectedDevices = connectedDevices,
                        scannedDevices = scannedDevices,
                        onEnableBluetooth = { runWithBluetoothReady { vm.startScan() } },
                        onToggleShowAll = vm::toggleShowAllBle,
                        onScan = {
                            if (isScanning) vm.stopScan()
                            else runWithBluetoothReady { vm.startScan() }
                        },
                        onConnect = { addr, name ->
                            runWithBluetoothReady { vm.connect(addr, name) }
                        },
                        onDisconnect = vm::disconnect,
                        onSaveDevice = vm::saveDevice,
                        onRemoveSaved = vm::removeSavedDevice,
                        onToggleAutoConnect = vm::setAutoConnect,
                        onRenameDevice = vm::renameDevice
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHeader(
    connectedCount: Int,
    savedCount: Int,
    isBtEnabled: Boolean,
    isPowerOn: Boolean,
    onEnableBluetooth: () -> Unit,
    onTogglePower: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Light Towers BLE",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = when {
                    !isBtEnabled -> "Bluetooth wyłączony"
                    connectedCount > 0 -> "Połączono: $connectedCount (zapisanych: $savedCount)"
                    savedCount > 0 -> "Zapisane bary: $savedCount (rozłączone)"
                    else -> "Brak połączonych barów"
                },
                fontSize = 13.sp,
                color = when {
                    !isBtEnabled -> Color(0xFFF87171)
                    connectedCount > 0 -> Color(0xFF4ADE80)
                    else -> Color(0xFF9CA3AF)
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isBtEnabled) {
                Button(
                    onClick = onEnableBluetooth,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Rounded.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Włącz BT", fontSize = 12.sp, color = Color.White)
                }
            }
            Button(
                onClick = onTogglePower,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPowerOn) Color(0xFFEF4444) else Color(0xFF22C55E)
                )
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Zasilanie")
                Spacer(Modifier.width(6.dp))
                Text(if (isPowerOn) "Wyłącz" else "Włącz", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetSelectorRow(
    connectedDevices: List<ConnectedBarDevice>,
    selectedAddress: String?,
    onSelect: (String?) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedAddress == null,
            onClick = { onSelect(null) },
            label = { Text("Wszystkie bary") }
        )
        connectedDevices.forEach { dev ->
            FilterChip(
                selected = selectedAddress == dev.address,
                onClick = { onSelect(dev.address) },
                label = { Text("${dev.displayName} (${dev.rgbOrder.label})") }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlTab(
    uiState: UiState,
    customPresets: List<CustomColorPreset>,
    vm: MainViewModel
) {
    var colorModeTab by remember { mutableIntStateOf(0) } // 0 = Paleta/HSV, 1 = Biel (Kelwiny), 2 = Suwaki RGB + HEX
    var showAddPresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var hexInput by remember(uiState.red, uiState.green, uiState.blue) {
        mutableStateOf("%02X%02X%02X".format(uiState.red, uiState.green, uiState.blue))
    }

    val (boostedR, boostedG, boostedB) = remember(uiState.red, uiState.green, uiState.blue, uiState.brightness) {
        ElkBledomProtocol.applyBrightnessBoost(uiState.red, uiState.green, uiState.blue, uiState.brightness)
    }

    val activeColor = if (uiState.isPowerOn) {
        Color(boostedR, boostedG, boostedB)
    } else {
        Color(0xFF252A36)
    }

    val builtInPresets = remember {
        listOf(
            "Czerwony" to Triple(255, 0, 0),
            "Pomarańcz" to Triple(255, 85, 0),
            "Złoty" to Triple(255, 195, 0),
            "Zielony" to Triple(0, 255, 0),
            "Mięta" to Triple(0, 255, 140),
            "Cyjan" to Triple(0, 235, 255),
            "Błękit" to Triple(0, 120, 255),
            "Niebieski" to Triple(0, 0, 255),
            "Fiolet" to Triple(165, 0, 255),
            "Magenta" to Triple(255, 0, 210),
            "Róż" to Triple(255, 30, 120),
            "Czysta Biel" to Triple(255, 255, 255)
        )
    }

    if (showAddPresetDialog) {
        AlertDialog(
            onDismissRequest = { showAddPresetDialog = false },
            title = { Text("Zapisz aktualny kolor") },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text("Nazwa koloru (np. Gaming Noc)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.addCustomPreset(presetNameInput)
                    presetNameInput = ""
                    showAddPresetDialog = false
                }) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPresetDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Podgląd barów + Pasek Jasności (1% - 150% Turbo Boost)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LightBarPreview("Bar Lewy", activeColor, uiState.brightness)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "#%02X%02X%02X".format(uiState.red, uiState.green, uiState.blue),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "RGB(${uiState.red}, ${uiState.green}, ${uiState.blue})",
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF)
                            )
                            if (uiState.brightness > 100) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "TURBO BOOST +${uiState.brightness - 100}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                        LightBarPreview("Bar Prawy", activeColor, uiState.brightness)
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.brightness > 100) "Jasność (Tryb Maksymalny)" else "Jasność",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${uiState.brightness}%",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.brightness > 100) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = uiState.brightness.toFloat(),
                        onValueChange = { vm.setBrightness(it.toInt()) },
                        onValueChangeFinished = { vm.setBrightness(uiState.brightness, forceImmediate = true) },
                        valueRange = 1f..150f,
                        colors = SliderDefaults.colors(
                            thumbColor = if (uiState.brightness > 100) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary,
                            activeTrackColor = if (uiState.brightness > 100) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(25, 50, 100, 125, 150).forEach { lvl ->
                            FilterChip(
                                selected = uiState.brightness == lvl,
                                onClick = { vm.setBrightness(lvl, forceImmediate = true) },
                                label = {
                                    Text(
                                        text = if (lvl > 100) "$lvl% MAX" else "$lvl%",
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Wybór trybu personalizacji koloru
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = colorModeTab == 0,
                    onClick = { colorModeTab = 0 },
                    label = { Text("Spektrum HSV") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = colorModeTab == 1,
                    onClick = { colorModeTab = 1 },
                    label = { Text("Biel (Kelwiny)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = colorModeTab == 2,
                    onClick = { colorModeTab = 2 },
                    label = { Text("RGB / HEX") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Panel personalizacji zależny od wybranej zakładki
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (colorModeTab) {
                        0 -> {
                            Text("Płynny wybór odcienia i nasycenia", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.height(12.dp))
                            HueGradientBar(
                                hue = uiState.hue,
                                onHueChanged = { newHue -> vm.setHsv(newHue, uiState.saturation) }
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Nasycenie (biel <-> pełny kolor)", fontSize = 13.sp, color = Color(0xFFD1D5DB))
                                Text("${(uiState.saturation * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = uiState.saturation,
                                onValueChange = { vm.setHsv(uiState.hue, it) },
                                valueRange = 0f..1f
                            )
                        }

                        1 -> {
                            Text("Temperatura barwowa bieli", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("2000K (Świeca) — 9000K (Chłodny dzień)", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                Text("${uiState.kelvin} K", fontWeight = FontWeight.Bold, color = Color(0xFFFDE68A))
                            }
                            Slider(
                                value = uiState.kelvin.toFloat(),
                                onValueChange = { vm.setKelvin(it.toInt()) },
                                valueRange = 2000f..9000f
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "2200K Relaks" to 2200,
                                    "3000K Ciepła" to 3000,
                                    "4500K Neutralna" to 4500,
                                    "6500K Dzienna" to 6500,
                                    "8500K Lód" to 8500
                                ).forEach { (label, kVal) ->
                                    OutlinedButton(onClick = { vm.setKelvin(kVal, forceImmediate = true) }) {
                                        Text(label, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        2 -> {
                            Text("Precyzyjne kanały RGB i kod HEX", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            RgbSliderRow("Czerwony (R)", uiState.red, Color(0xFFEF4444)) {
                                vm.setRgb(it, uiState.green, uiState.blue)
                            }
                            RgbSliderRow("Zielony (G)", uiState.green, Color(0xFF22C55E)) {
                                vm.setRgb(uiState.red, it, uiState.blue)
                            }
                            RgbSliderRow("Niebieski (B)", uiState.blue, Color(0xFF3B82F6)) {
                                vm.setRgb(uiState.red, uiState.green, it)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = hexInput,
                                    onValueChange = { if (it.length <= 7) hexInput = it.uppercase() },
                                    label = { Text("Kod HEX (np. FF2A6D)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = { vm.applyHexColor(hexInput) }) {
                                    Text("Ustaw")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Własne zapisane kolory użytkownika
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Moje zapisane kolory (${customPresets.size})", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        OutlinedButton(
                            onClick = { showAddPresetDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Zapisz obecny", fontSize = 12.sp)
                        }
                    }
                    if (customPresets.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ustaw dowolny kolor powyżej i kliknij 'Zapisz obecny', aby dodać go do ulubionych.",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            customPresets.forEach { preset ->
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1F2533),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable {
                                                vm.setRgb(preset.r, preset.g, preset.b, forceImmediate = true)
                                            }
                                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(preset.r, preset.g, preset.b))
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(preset.name, fontSize = 12.sp)
                                        IconButton(
                                            onClick = { vm.removeCustomPreset(preset.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Usuń",
                                                modifier = Modifier.size(14.dp),
                                                tint = Color(0xFF9CA3AF)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gotowa paleta 12 kolorów
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Paleta gotowych kolorów", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        builtInPresets.forEach { (name, rgb) ->
                            val c = Color(rgb.first, rgb.second, rgb.third)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    vm.setRgb(rgb.first, rgb.second, rgb.third, forceImmediate = true)
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(name, fontSize = 11.sp, color = Color(0xFFD1D5DB))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HueGradientBar(
    hue: Float,
    onHueChanged: (Float) -> Unit
) {
    val spectrum = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onHueChanged(ratio * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onHueChanged(ratio * 360f)
                }
            }
    ) {
        drawRect(brush = Brush.horizontalGradient(spectrum))
        val markerX = (hue.coerceIn(0f, 360f) / 360f) * size.width
        drawCircle(
            color = Color.White,
            radius = 14.dp.toPx(),
            center = Offset(markerX, size.height / 2f),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun LightBarPreview(label: String, color: Color, brightness: Int) {
    val alpha = (brightness / 100f).coerceIn(0.18f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            color.copy(alpha = alpha),
                            color.copy(alpha = alpha * 0.8f)
                        )
                    )
                )
                .border(
                    width = if (brightness > 100) 2.dp else 1.dp,
                    color = if (brightness > 100) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(13.dp)
                )
        )
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = Color(0xFF9CA3AF))
    }
}

@Composable
private fun RgbSliderRow(
    label: String,
    value: Int,
    tint: Color,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color(0xFFD1D5DB))
        Text(value.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = 0f..255f,
        colors = SliderDefaults.colors(
            thumbColor = tint,
            activeTrackColor = tint
        )
    )
}

@Composable
private fun EffectsTab(uiState: UiState, vm: MainViewModel) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Prędkość efektu", fontWeight = FontWeight.SemiBold)
                        Text("${uiState.speed}%", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = uiState.speed.toFloat(),
                        onValueChange = { vm.setSpeed(it.toInt()) },
                        valueRange = 1f..100f
                    )
                }
            }
        }

        items(ElkBledomProtocol.EFFECTS) { effect ->
            val selected = uiState.activeEffectId == effect.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) Color(0xFF263554) else MaterialTheme.colorScheme.surface
                ),
                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setEffect(effect) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(effect.name, fontWeight = FontWeight.Medium)
                    Text(
                        text = "0x%02X".format(effect.code),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
        }
    }
}

@Composable
private fun RgbSwapTab(uiState: UiState, lastHex: String, vm: MainViewModel) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Kalibracja kolejności kanałów RGB", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Jeśli po kliknięciu Czerwonego bar świeci na zielono lub niebiesko, zmień mapowanie poniżej. Ustawienie zapisuje się na stałe dla każdego baru.",
                        fontSize = 13.sp,
                        color = Color(0xFF9CA3AF)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { vm.setRgb(255, 0, 0, forceImmediate = true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) { Text("Test R", color = Color.White) }
                        Button(
                            onClick = { vm.setRgb(0, 255, 0, forceImmediate = true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) { Text("Test G", color = Color.White) }
                        Button(
                            onClick = { vm.setRgb(0, 0, 255, forceImmediate = true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) { Text("Test B", color = Color.White) }
                    }
                }
            }
        }

        items(RgbOrder.entries) { order ->
            val selected = uiState.globalRgbOrder == order
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) Color(0xFF263554) else MaterialTheme.colorScheme.surface
                ),
                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setRgbOrder(order) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(order.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(order.description, fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    }
                    if (selected) {
                        Text("AKTYWNE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11141C)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Ostatnia wysłana ramka HEX (ELK-BLEDOM):", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lastHex,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFF4ADE80)
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicesTab(
    isBtEnabled: Boolean,
    isScanning: Boolean,
    statusMsg: String,
    showAll: Boolean,
    savedDevices: List<SavedBarDevice>,
    connectedDevices: List<ConnectedBarDevice>,
    scannedDevices: List<ScannedBarDevice>,
    onEnableBluetooth: () -> Unit,
    onToggleShowAll: (Boolean) -> Unit,
    onScan: () -> Unit,
    onConnect: (String, String) -> Unit,
    onDisconnect: (String) -> Unit,
    onSaveDevice: (String, String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onToggleAutoConnect: (String, Boolean) -> Unit,
    onRenameDevice: (String, String) -> Unit
) {
    var renameDialogDevice by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (renameDialogDevice != null) {
        val (addr, _) = renameDialogDevice!!
        AlertDialog(
            onDismissRequest = { renameDialogDevice = null },
            title = { Text("Zmień nazwę baru") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Np. Bar Lewy / Biurko") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameDevice(addr, renameText)
                    renameDialogDevice = null
                }) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogDevice = null }) {
                    Text("Anuluj")
                }
            }
        )
    }

    val connectedAddresses = remember(connectedDevices) { connectedDevices.map { it.address }.toSet() }
    val savedAddresses = remember(savedDevices) { savedDevices.map { it.address }.toSet() }

    val visibleScanned = remember(scannedDevices, showAll, connectedAddresses) {
        scannedDevices.filter {
            it.address !in connectedAddresses && (showAll || it.isKnownLed)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Karta stanu Bluetooth i skanowania
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!isBtEnabled) {
                        Button(
                            onClick = onEnableBluetooth,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Włącz Bluetooth i nadaj uprawnienia", color = Color.White)
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Skaner Bluetooth LE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(statusMsg, fontSize = 12.sp, color = Color(0xFF9CA3AF))
                        }
                        Button(onClick = onScan) {
                            Icon(
                                if (isScanning) Icons.Rounded.Close else Icons.Rounded.BluetoothSearching,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isScanning) "Stop" else "Skanuj")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pokaż wszystkie urządzenia BLE", fontSize = 13.sp)
                        Switch(checked = showAll, onCheckedChange = onToggleShowAll)
                    }
                }
            }
        }

        // Połączone bary
        if (connectedDevices.isNotEmpty()) {
            item {
                Text(
                    "Połączone teraz (${connectedDevices.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF4ADE80)
                )
            }
            items(connectedDevices, key = { "conn_${it.address}" }) { dev ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF172620)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.BluetoothConnected, contentDescription = null, tint = Color(0xFF4ADE80))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(dev.displayName, fontWeight = FontWeight.Bold)
                                Text(
                                    "${dev.address} • ${dev.rgbOrder.label} • ${if (dev.isReady) "Gotowe" else "Łączenie..."}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                        }
                        IconButton(onClick = {
                            renameText = dev.displayName
                            renameDialogDevice = dev.address to dev.displayName
                        }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Zmień nazwę", tint = Color(0xFF93C5FD))
                        }
                        IconButton(onClick = { onDisconnect(dev.address) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Rozłącz", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }

        // Zapisane urządzenia (pamięć trwała)
        item {
            Text(
                "Zapisane urządzenia (${savedDevices.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFF60A5FA)
            )
        }

        if (savedDevices.isEmpty()) {
            item {
                Text(
                    "Po połączeniu z barem zostanie on automatycznie zapisany tutaj, aby łączyć się jednym kliknięciem.",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }
        } else {
            items(savedDevices, key = { "saved_${it.address}" }) { saved ->
                val isConnected = saved.address in connectedAddresses
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(saved.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${saved.address} • Kanały: ${saved.rgbOrder.label}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                            IconButton(onClick = {
                                renameText = saved.name
                                renameDialogDevice = saved.address to saved.name
                            }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Nazwa", tint = Color(0xFF93C5FD))
                            }
                            IconButton(onClick = { onRemoveSaved(saved.address) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Usuń z zapisanych", tint = Color(0xFFF87171))
                            }
                            if (!isConnected) {
                                Button(onClick = { onConnect(saved.address, saved.name) }) {
                                    Text("Połącz")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-łączenie przy starcie aplikacji", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                            Switch(
                                checked = saved.autoConnect,
                                onCheckedChange = { onToggleAutoConnect(saved.address, it) }
                            )
                        }
                    }
                }
            }
        }

        // Nowe urządzenia ze skanera
        item {
            Text(
                "Wykryte w pobliżu (${visibleScanned.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        items(visibleScanned, key = { "scan_${it.address}" }) { dev ->
            val alreadySaved = dev.address in savedAddresses
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dev.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${dev.address} • Sygnał: ${dev.rssi} dBm",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    IconButton(onClick = {
                        if (alreadySaved) onRemoveSaved(dev.address)
                        else onSaveDevice(dev.address, dev.name)
                    }) {
                        Icon(
                            if (alreadySaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = "Zapisz urządzenie",
                            tint = if (alreadySaved) Color(0xFF60A5FA) else Color(0xFF9CA3AF)
                        )
                    }
                    OutlinedButton(onClick = { onConnect(dev.address, dev.name) }) {
                        Text("Połącz")
                    }
                }
            }
        }
    }
}

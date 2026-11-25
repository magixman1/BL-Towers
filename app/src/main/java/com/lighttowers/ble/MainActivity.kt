package com.lighttowers.ble

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lighttowers.ble.ble.ConnectedBarDevice
import com.lighttowers.ble.ble.ElkBledomProtocol
import com.lighttowers.ble.ble.RgbOrder
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
    val isScanning by vm.isScanning.collectAsState()
    val scannedDevices by vm.scannedDevices.collectAsState()
    val connectedDevices by vm.connectedDevices.collectAsState()
    val lastHex by vm.lastHexPacket.collectAsState()
    val statusMsg by vm.statusMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            vm.startScan()
        }
    }

    fun requestScanWithPermissions() {
        val missing = requiredPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            vm.startScan()
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
                    label = { Text("Sterowanie") }
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
                isPowerOn = uiState.isPowerOn,
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
                    0 -> ControlTab(uiState = uiState, vm = vm)
                    1 -> EffectsTab(uiState = uiState, vm = vm)
                    2 -> RgbSwapTab(uiState = uiState, lastHex = lastHex, vm = vm)
                    3 -> DevicesTab(
                        isScanning = isScanning,
                        statusMsg = statusMsg,
                        showAll = uiState.showAllBleDevices,
                        connectedDevices = connectedDevices,
                        scannedDevices = scannedDevices,
                        onToggleShowAll = vm::toggleShowAllBle,
                        onScan = { if (isScanning) vm.stopScan() else requestScanWithPermissions() },
                        onConnect = vm::connect,
                        onDisconnect = vm::disconnect
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHeader(
    connectedCount: Int,
    isPowerOn: Boolean,
    onTogglePower: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Light Towers BLE",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (connectedCount == 0) "Brak połączonych barów" else "Połączono: $connectedCount",
                fontSize = 13.sp,
                color = if (connectedCount > 0) Color(0xFF4ADE80) else Color(0xFF9CA3AF)
            )
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
private fun ControlTab(uiState: UiState, vm: MainViewModel) {
    val activeColor = if (uiState.isPowerOn) {
        Color(uiState.red, uiState.green, uiState.blue)
    } else {
        Color(0xFF252A36)
    }

    val presets = remember {
        listOf(
            "Czerwony" to Triple(255, 0, 0),
            "Zielony" to Triple(0, 255, 0),
            "Niebieski" to Triple(0, 0, 255),
            "Ciepły" to Triple(255, 160, 50),
            "Zimny" to Triple(220, 240, 255),
            "Fiolet" to Triple(170, 0, 255),
            "Cyjan" to Triple(0, 240, 255),
            "Róż" to Triple(255, 20, 140)
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LightBarPreview(label = "Bar Lewy", color = activeColor, brightness = uiState.brightness)
                    LightBarPreview(label = "Bar Prawy", color = activeColor, brightness = uiState.brightness)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Szybkie kolory", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        presets.forEach { (name, rgb) ->
                            val c = Color(rgb.first, rgb.second, rgb.third)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    vm.setRgb(rgb.first, rgb.second, rgb.third, forceImmediate = true)
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
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

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Suwaki RGB i jasność", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Jasność", fontSize = 13.sp, color = Color(0xFFD1D5DB))
                        Text("${uiState.brightness}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = uiState.brightness.toFloat(),
                        onValueChange = { vm.setBrightness(it.toInt()) },
                        valueRange = 1f..100f
                    )
                }
            }
        }
    }
}

@Composable
private fun LightBarPreview(label: String, color: Color, brightness: Int) {
    val alpha = (brightness / 100f).coerceIn(0.15f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            color.copy(alpha = alpha),
                            color.copy(alpha = alpha * 0.75f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.height(8.dp))
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
                        "Jeśli po kliknięciu Czerwonego bar świeci na zielono lub niebiesko, zmień mapowanie poniżej. Ustawienie zapisuje się osobno dla każdego baru.",
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
    isScanning: Boolean,
    statusMsg: String,
    showAll: Boolean,
    connectedDevices: List<ConnectedBarDevice>,
    scannedDevices: List<ScannedBarDevice>,
    onToggleShowAll: (Boolean) -> Unit,
    onScan: () -> Unit,
    onConnect: (String, String) -> Unit,
    onDisconnect: (String) -> Unit
) {
    val visibleDevices = remember(scannedDevices, showAll, connectedDevices) {
        val connectedAddresses = connectedDevices.map { it.address }.toSet()
        scannedDevices.filter {
            it.address !in connectedAddresses && (showAll || it.isKnownLed)
        }
    }

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
                    Spacer(Modifier.height(10.dp))
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

        if (connectedDevices.isNotEmpty()) {
            item {
                Text(
                    "Połączone bary (${connectedDevices.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF4ADE80)
                )
            }
            items(connectedDevices, key = { it.address }) { dev ->
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
                                    "${dev.address} • ${dev.rgbOrder.label} • ${if (dev.isReady) "Gotowe" else "Inicjalizacja..."}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                        }
                        IconButton(onClick = { onDisconnect(dev.address) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Rozłącz", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Znalezione urządzenia (${visibleDevices.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        if (visibleDevices.isEmpty()) {
            item {
                Text(
                    "Kliknij Skanuj, aby wyszukać Light Towers / ELK-BLEDOM. Jeśli nie widać barów, włącz przełącznik 'Pokaż wszystkie urządzenia BLE'.",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }

        items(visibleDevices, key = { it.address }) { dev ->
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
                    OutlinedButton(onClick = { onConnect(dev.address, dev.name) }) {
                        Text("Połącz")
                    }
                }
            }
        }
    }
}

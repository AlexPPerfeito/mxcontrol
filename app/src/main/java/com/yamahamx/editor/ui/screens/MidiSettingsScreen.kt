package com.yamahamx.editor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.BleMidiDevice
import com.yamahamx.editor.data.model.MidiPortType
import com.yamahamx.editor.ui.theme.*
import com.yamahamx.editor.ui.viewmodel.PerformanceViewModel

@Composable
fun MidiSettingsScreen(
    viewModel: PerformanceViewModel,
    modifier: Modifier = Modifier
) {
    val activePortType by viewModel.activePortType.collectAsState()
    val isBleScanning by viewModel.isBleScanning.collectAsState()
    val bleDevices by viewModel.bleDiscoveredDevices.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isProUser by viewModel.isProUser.collectAsState()

    MidiSettingsScreen(
        activePortType = activePortType,
        connectedDeviceName = uiState.connectedDeviceName,
        isConnected = uiState.isConnected,
        isBleScanning = isBleScanning,
        bleDevices = bleDevices,
        isProUser = isProUser,
        onScanBleRequested = {
            if (!isProUser) {
                viewModel.openUpgradeDialog()
            } else {
                viewModel.startBleScan()
            }
        },
        onStopScanBleRequested = { viewModel.stopBleScan() },
        onConnectBleDevice = {
            if (!isProUser) {
                viewModel.openUpgradeDialog()
            } else {
                viewModel.connectBleDevice(it)
            }
        },
        onDisconnectBleDevice = { viewModel.disconnectBleDevice(it) },
        onUpgradeRequested = { viewModel.openUpgradeDialog() },
        modifier = modifier
    )
}

@Composable
fun MidiSettingsScreen(
    activePortType: MidiPortType,
    connectedDeviceName: String?,
    isConnected: Boolean,
    isBleScanning: Boolean,
    bleDevices: List<BleMidiDevice>,
    onScanBleRequested: () -> Unit,
    onStopScanBleRequested: () -> Unit,
    onConnectBleDevice: (BleMidiDevice) -> Unit,
    onDisconnectBleDevice: (BleMidiDevice) -> Unit,
    isProUser: Boolean = true,
    onUpgradeRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===================================================================
        // 1. INDICADOR DE PORTA ATIVA (USB vs Bluetooth)
        // ===================================================================
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = BorderStroke(
                1.5.dp,
                when (activePortType) {
                    MidiPortType.USB -> YamahaCyan.copy(alpha = 0.6f)
                    MidiPortType.BLUETOOTH -> Color(0xFF2979FF).copy(alpha = 0.7f)
                    MidiPortType.NONE -> SynthCardBorder
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.active_midi_port).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SynthMutedGray,
                        letterSpacing = 0.5.sp
                    )

                    // Active badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (activePortType) {
                            MidiPortType.USB -> SynthGreen.copy(alpha = 0.15f)
                            MidiPortType.BLUETOOTH -> Color(0xFF2979FF).copy(alpha = 0.20f)
                            MidiPortType.NONE -> SynthRed.copy(alpha = 0.15f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when (activePortType) {
                                MidiPortType.USB -> SynthGreen
                                MidiPortType.BLUETOOTH -> Color(0xFF2979FF)
                                MidiPortType.NONE -> SynthRed
                            }
                        )
                    ) {
                        Text(
                            text = when (activePortType) {
                                MidiPortType.USB -> "ATIVO (USB)"
                                MidiPortType.BLUETOOTH -> "ATIVO (BLE)"
                                MidiPortType.NONE -> "OFFLINE"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = when (activePortType) {
                                MidiPortType.USB -> SynthGreen
                                MidiPortType.BLUETOOTH -> Color(0xFF82B1FF)
                                MidiPortType.NONE -> SynthRed
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when (activePortType) {
                                    MidiPortType.USB -> YamahaCyan.copy(alpha = 0.15f)
                                    MidiPortType.BLUETOOTH -> Color(0xFF2979FF).copy(alpha = 0.20f)
                                    MidiPortType.NONE -> SynthPanelSurface
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (activePortType) {
                                MidiPortType.USB -> Icons.Default.Usb
                                MidiPortType.BLUETOOTH -> Icons.Default.BluetoothConnected
                                MidiPortType.NONE -> Icons.Default.UsbOff
                            },
                            contentDescription = null,
                            tint = when (activePortType) {
                                MidiPortType.USB -> YamahaCyan
                                MidiPortType.BLUETOOTH -> Color(0xFF82B1FF)
                                MidiPortType.NONE -> SynthMutedGray
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isConnected) {
                                connectedDeviceName ?: "Yamaha MX Synth"
                            } else {
                                stringResource(R.string.port_none)
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) SynthLightText else SynthMutedGray,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (activePortType) {
                                MidiPortType.USB -> stringResource(R.string.port_usb)
                                MidiPortType.BLUETOOTH -> stringResource(R.string.port_bluetooth)
                                MidiPortType.NONE -> "Nenhum sintetizador ou adaptador conectado"
                            },
                            fontSize = 12.sp,
                            color = SynthMutedGray
                        )
                    }
                }
            }
        }

        // ===================================================================
        // 2. SEÇÃO: DISPOSITIVOS BLUETOOTH MIDI (BLE)
        // ===================================================================
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color(0xFF2979FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.section_ble_devices),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SynthLightText
                        )
                    }

                    if (!isProUser) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = YamahaGold.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, YamahaGold.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable { onUpgradeRequested() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = YamahaGold,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "PRO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = YamahaGold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Conecte adaptadores sem fio (ex: Yamaha MD-BT01, UD-BT01, CME WIDI Master) para controlar o sintetizador sem fios via BLE.",
                    fontSize = 11.5.sp,
                    color = SynthMutedGray,
                    lineHeight = 16.sp
                )

                if (!isProUser) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E2638),
                        border = BorderStroke(1.dp, YamahaGold.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpgradeRequested() }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = YamahaGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.ble_pro_required_banner),
                                fontSize = 11.5.sp,
                                color = SynthLightText,
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botão "Escanear Dispositivos BLE"
                Button(
                    onClick = {
                        if (!isProUser) {
                            onUpgradeRequested()
                        } else if (isBleScanning) {
                            onStopScanBleRequested()
                        } else {
                            onScanBleRequested()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isProUser) Color(0xFF232B3E) else if (isBleScanning) Color(0xFF1E2638) else Color(0xFF2979FF),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isProUser) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = YamahaGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_scan_ble_pro_locked),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = YamahaGold
                        )
                    } else if (isBleScanning) {
                        CircularProgressIndicator(
                            color = Color(0xFF82B1FF),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_stop_scan_ble),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_scan_ble),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lista de Dispositivos BLE Encontrados
                if (bleDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SynthDarkBackground)
                            .border(1.dp, SynthCardBorder, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = SynthMutedGray,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.ble_no_devices_found),
                                fontSize = 11.5.sp,
                                color = SynthMutedGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bleDevices.forEach { dev ->
                            BleDeviceListItem(
                                device = dev,
                                onConnect = {
                                    if (!isProUser) {
                                        onUpgradeRequested()
                                    } else {
                                        onConnectBleDevice(dev)
                                    }
                                },
                                onDisconnect = { onDisconnectBleDevice(dev) }
                            )
                        }
                    }
                }
            }
        }

        // ===================================================================
        // 3. SEÇÃO: CONTROLE & MAPEAMENTO MIDI CC (Visão Geral)
        // ===================================================================
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = YamahaCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mapeamento MIDI CC Padrão",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SynthLightText
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SynthDarkBackground)
                        .border(1.dp, SynthCardBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CcRowItem(ccNumber = "CC 1", name = "Modulation Wheel / Rotary Speed")
                    CcRowItem(ccNumber = "CC 7", name = "Volume do Canal (0..127)")
                    CcRowItem(ccNumber = "CC 10", name = "Pan Estéreo (Centro = 64)")
                    CcRowItem(ccNumber = "CC 11", name = "Expression / Atenuação")
                    CcRowItem(ccNumber = "CC 64", name = "Sustain Pedal (Hold 1)")
                }
            }
        }
    }
}

@Composable
private fun BleDeviceListItem(
    device: BleMidiDevice,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SynthPanelSurface,
        border = BorderStroke(
            1.dp,
            if (device.isConnected) SynthGreen else if (device.isYamaha) YamahaGold.copy(alpha = 0.5f) else SynthCardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (device.isConnected) SynthGreen.copy(alpha = 0.2f)
                            else if (device.isYamaha) YamahaGold.copy(alpha = 0.15f)
                            else Color(0xFF1E2638)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (device.isConnected) SynthGreen else if (device.isYamaha) YamahaGold else Color(0xFF82B1FF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SynthLightText,
                            maxLines = 1
                        )
                        if (device.isYamaha) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = YamahaGold.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "★ YAMAHA",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = YamahaGold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.address,
                            fontSize = 10.5.sp,
                            color = SynthMutedGray
                        )
                        if (device.rssi != 0) {
                            Text(
                                text = " • ${device.rssi} dBm",
                                fontSize = 10.5.sp,
                                color = SynthMutedGray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botão Conectar / Desconectar
            if (device.isConnecting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = YamahaCyan,
                    modifier = Modifier.size(22.dp)
                )
            } else if (device.isConnected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A1518),
                        contentColor = SynthRed
                    ),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, SynthRed.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ble_disconnect),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YamahaCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ble_connect),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CcRowItem(ccNumber: String, name: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ccNumber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = YamahaCyan
        )
        Text(
            text = name,
            fontSize = 11.sp,
            color = SynthMutedGray
        )
    }
}

package com.yamahamx.editor.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.MidiDeviceInfoWrapper
import com.yamahamx.editor.ui.screens.ViewMode
import com.yamahamx.editor.ui.theme.*

@Composable
fun ConnectionHeader(
    isConnected: Boolean,
    connectedDeviceName: String?,
    availableDevices: List<MidiDeviceInfoWrapper>,
    statusMessage: String,
    part12Link: Boolean,
    activePolyphony: Int = 0,
    currentViewMode: ViewMode = ViewMode.DETAIL,
    onViewModeChange: (ViewMode) -> Unit = {},
    onPart12LinkToggle: (Boolean) -> Unit,
    onDeviceSelected: (MidiDeviceInfoWrapper) -> Unit,
    onScanRequested: () -> Unit,
    onPanicClicked: () -> Unit,
    onSyncRequested: () -> Unit = {},
    onSaveRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeviceMenu by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp

    val ledColor by animateColorAsState(
        targetValue = if (isConnected) SynthGreen else SynthRed,
        label = "led"
    )

    Surface(
        color = SynthPanelSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
        shape = RoundedCornerShape(bottomStart = if (isLandscape) 8.dp else 16.dp, bottomEnd = if (isLandscape) 8.dp else 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (isLandscape) {
            // Landscape Compact Single Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and MX Logo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(YamahaCyan)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "MX",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.header_brand_title),
                        color = SynthLightText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                // Center-Left: Mode Selector Switcher (Parte, Mixer, Setlist)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SynthCardBackground)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        color = if (currentViewMode == ViewMode.DETAIL) YamahaCyan else Color.Transparent,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { onViewModeChange(ViewMode.DETAIL) }
                    ) {
                        Text(
                            text = stringResource(R.string.nav_tab_part_caps),
                            color = if (currentViewMode == ViewMode.DETAIL) Color.Black else SynthMutedGray,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = if (currentViewMode == ViewMode.MIXER) YamahaCyan else Color.Transparent,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { onViewModeChange(ViewMode.MIXER) }
                    ) {
                        Text(
                            text = stringResource(R.string.nav_tab_mixer_caps),
                            color = if (currentViewMode == ViewMode.MIXER) Color.Black else SynthMutedGray,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = if (currentViewMode == ViewMode.SETLIST) YamahaGold else Color.Transparent,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { onViewModeChange(ViewMode.SETLIST) }
                    ) {
                        Text(
                            text = stringResource(R.string.nav_tab_setlist_caps),
                            color = if (currentViewMode == ViewMode.SETLIST) Color.Black else SynthMutedGray,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = if (currentViewMode == ViewMode.MIDI_CC) YamahaCyan else Color.Transparent,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { onViewModeChange(ViewMode.MIDI_CC) }
                    ) {
                        Text(
                            text = stringResource(R.string.nav_tab_midi_cc_caps),
                            color = if (currentViewMode == ViewMode.MIDI_CC) Color.Black else SynthMutedGray,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Middle: Device selection dropdown trigger
                Box {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 185.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SynthCardBackground)
                            .border(1.dp, SynthCardBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                try {
                                    showDeviceMenu = true
                                } catch (_: Throwable) {}
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ledColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = if (isConnected) YamahaCyan else SynthMutedGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isConnected) (connectedDeviceName ?: "Yamaha MX") else stringResource(R.string.status_no_connection),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isConnected) SynthLightText else SynthMutedGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    CompactMidiDeviceDropdown(
                        expanded = showDeviceMenu,
                        availableDevices = availableDevices,
                        onDismissRequest = {
                            try {
                                showDeviceMenu = false
                            } catch (_: Throwable) {}
                        },
                        onDeviceSelected = onDeviceSelected,
                        onScanRequested = onScanRequested,
                        onSyncRequested = onSyncRequested
                    )
                }

                // Right controls: Part 1-2 Link Switch, Sync Button & Panic Button
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (part12Link) LinkBlue.copy(alpha = 0.2f) else SynthCardBackground)
                            .border(1.dp, if (part12Link) LinkBlue else SynthCardBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.part_link_short),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (part12Link) YamahaCyan else SynthMutedGray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = part12Link,
                            onCheckedChange = onPart12LinkToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = LinkBlue,
                                checkedTrackColor = LinkBlue.copy(alpha = 0.5f),
                                uncheckedThumbColor = SynthMutedGray,
                                uncheckedTrackColor = SynthCardBorder
                            ),
                            modifier = Modifier.height(20.dp)
                        )
                    }

                    PolyphonyBadge(
                        polyphony = activePolyphony,
                        isLandscape = true
                    )

                    Button(
                        onClick = onSaveRequested,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E2638),
                            contentColor = YamahaGold
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = stringResource(R.string.cd_save_performance),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(R.string.btn_save_top),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onSyncRequested,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E2638),
                            contentColor = YamahaCyan
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.cd_sync),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(R.string.action_sync),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onPanicClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SynthRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.cd_panic),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.panic),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        } else {
            // Portrait Standard Layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Top row: Brand title + SALVAR & Action buttons (Polyphony, SYNC, PÂNICO)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Brand Title & SALVAR
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(YamahaCyan)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "MX",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "CONTROL",
                            color = SynthLightText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        Button(
                            onClick = onSaveRequested,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E2638),
                                contentColor = YamahaGold
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = stringResource(R.string.cd_save_performance),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = stringResource(R.string.btn_save_top),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Right side: Polyphony notes badge, SYNC & PÂNICO
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PolyphonyBadge(
                            polyphony = activePolyphony,
                            isLandscape = true
                        )

                        Button(
                            onClick = onSyncRequested,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E2638),
                                contentColor = YamahaCyan
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = stringResource(R.string.cd_sync),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = stringResource(R.string.action_sync),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = onPanicClicked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SynthRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = stringResource(R.string.cd_panic),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = stringResource(R.string.panic),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Middle row: Connection status pill and Link switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 230.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(SynthCardBackground)
                                .border(1.dp, SynthCardBorder, RoundedCornerShape(20.dp))
                                .clickable {
                                    try {
                                        showDeviceMenu = true
                                    } catch (_: Throwable) {}
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(ledColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                tint = if (isConnected) YamahaCyan else SynthMutedGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) (connectedDeviceName ?: stringResource(R.string.status_yamaha_connected)) else stringResource(R.string.status_no_usb_connection),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isConnected) SynthLightText else SynthMutedGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        CompactMidiDeviceDropdown(
                            expanded = showDeviceMenu,
                            availableDevices = availableDevices,
                            onDismissRequest = {
                                try {
                                    showDeviceMenu = false
                                } catch (_: Throwable) {}
                            },
                            onDeviceSelected = onDeviceSelected,
                            onScanRequested = onScanRequested,
                            onSyncRequested = onSyncRequested
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (part12Link) LinkBlue.copy(alpha = 0.2f) else SynthCardBackground)
                            .border(1.dp, if (part12Link) LinkBlue else SynthCardBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.part_link),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (part12Link) YamahaCyan else SynthMutedGray
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = part12Link,
                            onCheckedChange = onPart12LinkToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = LinkBlue,
                                checkedTrackColor = LinkBlue.copy(alpha = 0.5f),
                                uncheckedThumbColor = SynthMutedGray,
                                uncheckedTrackColor = SynthCardBorder
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        fontSize = 10.sp,
                        color = SynthMutedGray,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactMidiDeviceDropdown(
    expanded: Boolean,
    availableDevices: List<MidiDeviceInfoWrapper>,
    onDismissRequest: () -> Unit,
    onDeviceSelected: (MidiDeviceInfoWrapper) -> Unit,
    onScanRequested: () -> Unit,
    onSyncRequested: () -> Unit
) {
    val safeDevices: List<MidiDeviceInfoWrapper> = remember(availableDevices) {
        try {
            @Suppress("USELESS_CAST")
            (availableDevices as? List<MidiDeviceInfoWrapper?>)
                ?.filterNotNull()
                ?.toList()
                ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            try {
                onDismissRequest()
            } catch (_: Throwable) {}
        },
        modifier = Modifier
            .widthIn(min = 240.dp, max = 300.dp)
            .heightIn(max = 210.dp)
            .background(SynthCardBackground)
            .border(1.dp, SynthCardBorder, RoundedCornerShape(4.dp))
    ) {
        if (safeDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.no_midi_devices_found),
                    style = MaterialTheme.typography.labelSmall,
                    color = SynthMutedGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            for (rawDev in safeDevices) {
                val dev: MidiDeviceInfoWrapper? = rawDev
                val devName = try {
                    dev?.name?.takeIf { it.isNotBlank() } ?: "MIDI Device"
                } catch (_: Throwable) {
                    "MIDI Device"
                }
                val isYamaha = try { dev?.isYamaha == true } catch (_: Throwable) { false }
                val isBluetooth = try { dev?.isBluetooth == true } catch (_: Throwable) { false }
                val isConnectedDev = try { dev?.isConnected == true } catch (_: Throwable) { false }
                val devId = try { dev?.id ?: 0 } catch (_: Throwable) { 0 }
                val inPorts = try { dev?.inputPortCount ?: 0 } catch (_: Throwable) { 0 }
                val outPorts = try { dev?.outputPortCount ?: 0 } catch (_: Throwable) { 0 }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp, max = 40.dp)
                        .clickable {
                            try {
                                if (dev != null) {
                                    onDeviceSelected(dev)
                                }
                                onDismissRequest()
                            } catch (_: Throwable) {
                                try { onDismissRequest() } catch (_: Throwable) {}
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isBluetooth) Icons.Default.Bluetooth else Icons.Default.Usb,
                            contentDescription = null,
                            tint = if (isYamaha || isConnectedDev) YamahaCyan else SynthMutedGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = devName + if (isYamaha) " ★" else "",
                                fontSize = 11.5.sp,
                                lineHeight = 13.sp,
                                color = if (isYamaha) YamahaCyan else SynthLightText,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.device_info_format, devId, inPorts, outPorts),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                lineHeight = 11.sp,
                                color = SynthMutedGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isConnectedDev || isYamaha) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isConnectedDev) SynthGreen else YamahaCyan.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }

        Divider(
            color = SynthCardBorder,
            modifier = Modifier.padding(vertical = 2.dp)
        )

        // Compact footer row with side-by-side actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A2234))
                    .border(1.dp, SynthCardBorder, RoundedCornerShape(6.dp))
                    .clickable {
                        try {
                            onScanRequested()
                            onDismissRequest()
                        } catch (_: Throwable) {
                            try { onDismissRequest() } catch (_: Throwable) {}
                        }
                    }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = YamahaCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.action_scan_midi_devices),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YamahaCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A2234))
                    .border(1.dp, SynthCardBorder, RoundedCornerShape(6.dp))
                    .clickable {
                        try {
                            onSyncRequested()
                            onDismissRequest()
                        } catch (_: Throwable) {
                            try { onDismissRequest() } catch (_: Throwable) {}
                        }
                    }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = YamahaCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.action_sync_all_devices),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YamahaCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PolyphonyBadge(
    polyphony: Int,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val polyColor = when {
        polyphony <= 100 -> Color(0xFF4CAF50)
        polyphony <= 115 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    Surface(
        color = Color(0xFF131824),
        shape = RoundedCornerShape(if (isLandscape) 6.dp else 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, polyColor.copy(alpha = 0.45f)),
        modifier = modifier.height(if (isLandscape) 28.dp else 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = if (isLandscape) 6.dp else 8.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 6.dp else 7.dp)
                    .clip(CircleShape)
                    .background(polyColor)
            )
            Spacer(modifier = Modifier.width(if (isLandscape) 4.dp else 5.dp))
            Text(
                text = stringResource(R.string.badge_polyphony_notes),
                fontSize = if (isLandscape) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                color = SynthMutedGray,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.badge_polyphony_format, polyphony),
                fontSize = if (isLandscape) 10.sp else 11.5.sp,
                fontWeight = FontWeight.Black,
                color = polyColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}


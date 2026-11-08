package com.yamahamx.editor.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.YamahaVoice
import com.yamahamx.editor.data.repository.MidiLearnableParameter
import com.yamahamx.editor.ui.components.PartStripItem
import com.yamahamx.editor.ui.components.VoiceSelectorDialog
import com.yamahamx.editor.ui.theme.YamahaCyan
import com.yamahamx.editor.ui.theme.YamahaGold
import com.yamahamx.editor.ui.theme.YamahaNavyLight

@Composable
fun MixerOverviewScreen(
    parts: List<MultiPartState>,
    selectedPartIndex: Int,
    activeFocusedPartIndex: Int,
    partActivityStates: Map<Int, Boolean> = emptyMap(),
    isProUser: Boolean = true,
    onUpgradeRequested: () -> Unit = {},
    onSelectPart: (Int) -> Unit,
    onToggleLayerSelection: (Int) -> Unit,
    onVolumeChange: (Int, Int) -> Unit,
    onVoiceChange: (Int, YamahaVoice) -> Unit = { _, _ -> },
    onVoicePreview: (Int, YamahaVoice) -> Unit = { _, _ -> },
    onVoiceConfirm: (Int, YamahaVoice) -> Unit = onVoiceChange,
    onVoiceCancel: (Int) -> Unit = {},
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onReceiveChannelChange: (Int, Int) -> Unit,
    onOctaveShift: (Int, Int) -> Unit = { _, _ -> },
    onModulationChange: (Int, Int) -> Unit = { _, _ -> },
    onToggleModulationEnabled: (Int, Boolean) -> Unit = { _, _ -> },
    userVoices: List<YamahaVoice> = emptyList(),
    onRenameUserVoice: (Int, String) -> Unit = { _, _ -> },
    onStartMidiLearn: (MidiLearnableParameter, Int) -> Unit = { _, _ -> },
    anySoloActive: Boolean = parts.any { it.isSolo },
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val verticalScrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp
    var selectedPartForVoiceDialog by remember { mutableStateOf<Int?>(null) }

    val focusedPart = parts.getOrNull(activeFocusedPartIndex) ?: parts.first()
    val secondaryLayerParts = parts.filter { it.isLayerSelected && it.partIndex != activeFocusedPartIndex }
    val isAnyLayerActive = secondaryLayerParts.isNotEmpty()
    val fullLayerParts = if (isAnyLayerActive) {
        listOf(focusedPart) + secondaryLayerParts
    } else {
        emptyList()
    }

    // Auto-scroll Mixer horizontal strip strictly when the active focused part changes
    LaunchedEffect(activeFocusedPartIndex) {
        if (activeFocusedPartIndex in parts.indices) {
            lazyListState.animateScrollToItem(activeFocusedPartIndex)
        }
    }

    if (isLandscape) {
        // Landscape Mode: Stage Mixing Console with vertical scroll ensuring all channel controls & faders are accessible
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .padding(vertical = 2.dp)
                .padding(bottom = 16.dp)
        ) {
            // Live Focus & Multi-Layer status banner (Compact for Landscape)
            Surface(
                color = if (isAnyLayerActive) YamahaGold.copy(alpha = 0.15f) else YamahaCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isAnyLayerActive) YamahaGold.copy(alpha = 0.5f) else YamahaCyan.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = if (isAnyLayerActive) Icons.Default.Layers else Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = if (isAnyLayerActive) YamahaGold else YamahaCyan,
                        modifier = Modifier.size(13.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = if (isAnyLayerActive)
                            stringResource(R.string.mixer_banner_layer_active, fullLayerParts.joinToString(" + ") { "P${it.partNumber} [${it.voice.name}]" })
                        else
                            stringResource(R.string.mixer_banner_live_focus, focusedPart.partNumber, focusedPart.voice.name),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Horizontal scrollable channel strips in Landscape
            LazyRow(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(parts, key = { it.partIndex }) { part ->
                    val isPartLocked = !isProUser && part.partIndex >= 2
                    PartStripItem(
                        part = part,
                        isSelected = part.partIndex == selectedPartIndex,
                        isFocused = part.partIndex == activeFocusedPartIndex,
                        isMidiActive = partActivityStates[part.partIndex] == true,
                        isLocked = isPartLocked,
                        onLockClick = onUpgradeRequested,
                        onSelect = { onSelectPart(part.partIndex) },
                        onLongClick = { onToggleLayerSelection(part.partIndex) },
                        onVolumeChange = { vol -> onVolumeChange(part.partIndex, vol) },
                        onVoiceClick = { selectedPartForVoiceDialog = part.partIndex },
                        onToggleMute = { onToggleMute(part.partIndex) },
                        onToggleSolo = { onToggleSolo(part.partIndex) },
                        onReceiveChannelChange = { ch -> onReceiveChannelChange(part.partIndex, ch) },
                        onOctaveShift = { octDelta -> onOctaveShift(part.partIndex, octDelta) },
                        onModulationChange = { mod -> onModulationChange(part.partIndex, mod) },
                        onToggleModulationEnabled = { enabled -> onToggleModulationEnabled(part.partIndex, enabled) },
                        onVolumeMidiLearn = { onStartMidiLearn(MidiLearnableParameter.VOLUME, part.partIndex) },
                        onModulationMidiLearn = { onStartMidiLearn(MidiLearnableParameter.MODULATION, part.partIndex) },
                        anySoloActive = anySoloActive
                    )
                }
            }
        }
    } else {
        // Portrait Mode: Standard layout with live status banner, vertical scroll & channel strips
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .padding(vertical = 4.dp)
                .padding(bottom = 24.dp)
        ) {
            // Live Focus & Multi-Layer status banner
            Surface(
                color = if (isAnyLayerActive) YamahaGold.copy(alpha = 0.15f) else YamahaCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAnyLayerActive) YamahaGold.copy(alpha = 0.5f) else YamahaCyan.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isAnyLayerActive) Icons.Default.Layers else Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = if (isAnyLayerActive) YamahaGold else YamahaCyan,
                        modifier = Modifier.size(16.dp).padding(end = 6.dp)
                    )
                    Text(
                        text = if (isAnyLayerActive)
                            stringResource(R.string.mixer_banner_layer_active, fullLayerParts.joinToString(" + ") { "P${it.partNumber} [${it.voice.name}]" })
                        else
                            stringResource(R.string.mixer_banner_live_focus, focusedPart.partNumber, focusedPart.voice.name),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Horizontal scrollable channel strips
            LazyRow(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(parts, key = { it.partIndex }) { part ->
                    val isPartLocked = !isProUser && part.partIndex >= 2
                    PartStripItem(
                        part = part,
                        isSelected = part.partIndex == selectedPartIndex,
                        isFocused = part.partIndex == activeFocusedPartIndex,
                        isMidiActive = partActivityStates[part.partIndex] == true,
                        isLocked = isPartLocked,
                        onLockClick = onUpgradeRequested,
                        onSelect = { onSelectPart(part.partIndex) },
                        onLongClick = { onToggleLayerSelection(part.partIndex) },
                        onVolumeChange = { vol -> onVolumeChange(part.partIndex, vol) },
                        onVoiceClick = { selectedPartForVoiceDialog = part.partIndex },
                        onToggleMute = { onToggleMute(part.partIndex) },
                        onToggleSolo = { onToggleSolo(part.partIndex) },
                        onReceiveChannelChange = { ch -> onReceiveChannelChange(part.partIndex, ch) },
                        onOctaveShift = { octDelta -> onOctaveShift(part.partIndex, octDelta) },
                        onModulationChange = { mod -> onModulationChange(part.partIndex, mod) },
                        onToggleModulationEnabled = { enabled -> onToggleModulationEnabled(part.partIndex, enabled) },
                        onVolumeMidiLearn = { onStartMidiLearn(MidiLearnableParameter.VOLUME, part.partIndex) },
                        onModulationMidiLearn = { onStartMidiLearn(MidiLearnableParameter.MODULATION, part.partIndex) },
                        anySoloActive = anySoloActive
                    )
                }
            }
        }
    }

    // Voice Selector Dialog when requested from a Part card
    selectedPartForVoiceDialog?.let { partIdx ->
        val targetPart = parts.getOrNull(partIdx)
        if (targetPart != null) {
            VoiceSelectorDialog(
                currentVoice = targetPart.voice,
                partNumber = targetPart.partNumber,
                userVoices = userVoices,
                onVoicePreview = { voice -> onVoicePreview(targetPart.partIndex, voice) },
                onVoiceConfirmed = { newVoice ->
                    onVoiceConfirm(targetPart.partIndex, newVoice)
                    selectedPartForVoiceDialog = null
                },
                onVoiceCancel = {
                    onVoiceCancel(targetPart.partIndex)
                },
                onRenameUserVoice = onRenameUserVoice,
                onDismissRequest = {
                    selectedPartForVoiceDialog = null
                }
            )
        }
    }
}

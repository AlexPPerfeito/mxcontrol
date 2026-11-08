package com.yamahamx.editor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.NoteHelper
import com.yamahamx.editor.data.model.YamahaVoice
import com.yamahamx.editor.data.repository.MidiLearnableParameter
import com.yamahamx.editor.ui.components.CompressorGraphDialog
import com.yamahamx.editor.ui.components.FaderControl
import com.yamahamx.editor.data.midi.YamahaSysExEncoder
import com.yamahamx.editor.ui.components.FilterXyPadDialog
import com.yamahamx.editor.ui.components.MasterEqGraphDialog
import com.yamahamx.editor.ui.components.NoteRangeSelector
import com.yamahamx.editor.ui.components.PanControl
import com.yamahamx.editor.ui.components.VoiceSelectorDialog
import com.yamahamx.editor.ui.components.sliderLongPressDetector
import com.yamahamx.editor.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PartDetailScreen(
    part: MultiPartState,
    isFocused: Boolean,
    isMidiActive: Boolean = false,
    onPartIndexChange: (Int) -> Unit,
    onFocusPart: () -> Unit,
    onVoiceChange: (YamahaVoice) -> Unit,
    onVoicePreview: (YamahaVoice) -> Unit = {},
    onVoiceConfirm: (YamahaVoice) -> Unit = onVoiceChange,
    onVoiceCancel: () -> Unit = {},
    onVolumeChange: (Int) -> Unit,
    onPanChange: (Int) -> Unit,
    onReverbSendChange: (Int) -> Unit,
    onChorusSendChange: (Int) -> Unit,
    onNoteShiftChange: (Int) -> Unit,
    onNoteRangeChange: (Int, Int) -> Unit,
    onCutoffChange: (Int) -> Unit,
    onResonanceChange: (Int) -> Unit,
    onAttackChange: (Int) -> Unit = {},
    onDecayChange: (Int) -> Unit = {},
    onReleaseChange: (Int) -> Unit = {},
    masterEqGains: List<Int> = List(5) { 64 },
    onMasterEqBandGainChange: (Int, Int) -> Unit = { _, _ -> },
    onResetMasterEq: () -> Unit = {},
    onToggleCompressorEnabled: (Boolean) -> Unit = {},
    onCompressorThresholdChange: (Int) -> Unit = {},
    onCompressorOutputGainChange: (Int) -> Unit = {},
    onCompressorRatioChange: (Int) -> Unit = {},
    onCompressorAttackChange: (Int) -> Unit = {},
    onCompressorReleaseChange: (Int) -> Unit = {},
    onResetCompressor: () -> Unit = {},
    onModulationChange: (Int) -> Unit = {},
    onToggleModulationEnabled: (Boolean) -> Unit = {},
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onReceiveChannelChange: (Int) -> Unit,
    onToggleLayer: () -> Unit,
    userVoices: List<YamahaVoice> = emptyList(),
    onRenameUserVoice: (Int, String) -> Unit = { _, _ -> },
    onStartMidiLearn: (MidiLearnableParameter) -> Unit = {},
    anySoloActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp
    var showVoiceSelectorDialog by remember { mutableStateOf(false) }
    var showFilterXyPadDialog by remember { mutableStateOf(false) }
    var showMasterEqGraphDialog by remember { mutableStateOf(false) }
    var showCompressorGraphDialog by remember { mutableStateOf(false) }
    var showChannelMenu by remember { mutableStateOf(false) }

    // Independent Collapsible Card states remembered across navigation/recompositions
    var isMixExpanded by rememberSaveable { mutableStateOf(true) }
    var isEqExpanded by rememberSaveable { mutableStateOf(true) }
    var isCompressorExpanded by rememberSaveable { mutableStateOf(true) }
    var isFilterExpanded by rememberSaveable { mutableStateOf(true) }
    var isModulationExpanded by rememberSaveable { mutableStateOf(true) }
    var isNoteLimitExpanded by rememberSaveable { mutableStateOf(true) }

    val mixArrowRotation by animateFloatAsState(
        targetValue = if (isMixExpanded) 180f else 0f,
        label = "mix_arrow_rotation"
    )
    val filterArrowRotation by animateFloatAsState(
        targetValue = if (isFilterExpanded) 180f else 0f,
        label = "filter_arrow_rotation"
    )
    val modArrowRotation by animateFloatAsState(
        targetValue = if (isModulationExpanded) 180f else 0f,
        label = "mod_arrow_rotation"
    )

    val isLayerActive = part.isLayerActive

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = if (isLandscape) 8.dp else 12.dp, vertical = if (isLandscape) 4.dp else 8.dp)
            .padding(bottom = if (isLandscape) 16.dp else 36.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
    ) {
        // Part Navigator & Mute / Solo Bar
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFocused) SynthPanelSurface else SynthCardBackground
            ),
            border = androidx.compose.foundation.BorderStroke(
                if (isFocused) 1.5.dp else 1.dp,
                if (isFocused) YamahaCyan else SynthCardBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev part button
                IconButton(
                    onClick = { if (part.partIndex > 0) onPartIndexChange(part.partIndex - 1) },
                    enabled = part.partIndex > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = stringResource(R.string.nav_prev_part),
                        tint = if (part.partIndex > 0) YamahaCyan else SynthMutedGray
                    )
                }

                // Part Title Badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 4.dp)
                ) {
                    val ledColor by animateColorAsState(
                        targetValue = if (isMidiActive) Color(0xFF00E5FF) else Color(0xFF181E2A),
                        label = "detail_part_midi_led"
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Blinking MIDI Activity LED
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(ledColor)
                                .border(
                                    width = 1.dp,
                                    color = if (isMidiActive) Color.White else Color(0xFF384358),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        if (isFocused) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Ativa",
                                tint = YamahaCyan,
                                modifier = Modifier.size(16.dp).padding(end = 2.dp)
                            )
                        }
                        Text(
                            text = if (isFocused) stringResource(R.string.part_detail_title_active, part.partNumber) else stringResource(R.string.part_detail_title_default, part.partNumber),
                            color = when {
                                isFocused -> YamahaCyan
                                isLayerActive -> YamahaGold
                                else -> Color.White
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Receive Channel Picker Dropdown Trigger
                    Box {
                        Surface(
                            color = when {
                                isFocused -> YamahaCyan.copy(alpha = 0.2f)
                                part.receiveChannel == 0 -> YamahaGold.copy(alpha = 0.2f)
                                else -> Color(0xFF1E2638)
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when {
                                    isFocused -> YamahaCyan
                                    part.receiveChannel == 0 -> YamahaGold
                                    else -> Color.DarkGray
                                }
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showChannelMenu = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.midi_rx_display_format, part.receiveChannelDisplay),
                                    color = when {
                                        isFocused -> YamahaCyan
                                        part.receiveChannel == 0 -> YamahaGold
                                        else -> Color.White
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showChannelMenu,
                            onDismissRequest = { showChannelMenu = false },
                            modifier = Modifier.background(YamahaNavyDark)
                        ) {
                            Text(
                                text = stringResource(R.string.midi_rx_channel_title),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            (0..15).forEach { ch ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.midi_rx_channel_format, ch + 1) + if (ch == 0) stringResource(R.string.midi_rx_ch1_physical_suffix) else "",
                                            color = if (part.receiveChannel == ch) YamahaCyan else Color.White,
                                            fontWeight = if (part.receiveChannel == ch) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onReceiveChannelChange(ch)
                                        showChannelMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.channel_off),
                                        color = if (part.receiveChannel == 127) YamahaGold else Color(0xFFFF5252)
                                    )
                                },
                                onClick = {
                                    onReceiveChannelChange(127)
                                    showChannelMenu = false
                                }
                            )
                        }
                    }
                }

                // Next part button
                IconButton(
                    onClick = { if (part.partIndex < 15) onPartIndexChange(part.partIndex + 1) },
                    enabled = part.partIndex < 15,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.nav_next_part),
                        tint = if (part.partIndex < 15) YamahaCyan else SynthMutedGray
                    )
                }

                // Mute / Solo Toggles
                val isSoloMuted = anySoloActive && !part.isSolo
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    // Mute
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    part.isMuted -> MuteRed
                                    isSoloMuted -> MuteRed.copy(alpha = 0.35f)
                                    else -> SynthDarkBackground
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    part.isMuted -> MuteRed
                                    isSoloMuted -> MuteRed.copy(alpha = 0.6f)
                                    else -> SynthCardBorder
                                },
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onToggleMute() }
                            .defaultMinSize(minWidth = 48.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.btn_mute),
                            color = when {
                                part.isMuted -> Color.White
                                isSoloMuted -> Color.White.copy(alpha = 0.7f)
                                else -> SynthMutedGray
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Solo
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (part.isSolo) SoloYellow else SynthDarkBackground)
                            .border(1.dp, if (part.isSolo) SoloYellow else SynthCardBorder, RoundedCornerShape(6.dp))
                            .clickable { onToggleSolo() }
                            .defaultMinSize(minWidth = 48.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.btn_solo),
                            color = if (part.isSolo) Color.Black else SynthMutedGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Voice Selection Banner Card & Quick Layer / Live Focus Action
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                when {
                    isFocused -> YamahaCyan
                    isLayerActive -> YamahaGold
                    else -> YamahaCyan.copy(alpha = 0.6f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showVoiceSelectorDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLayerActive) YamahaGold else YamahaCyan)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = part.voice.category,
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = part.voice.name,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${part.voice.bankName} | PC ${part.voice.displayProgramNumber}${if (part.voice.subCategory.isNotBlank()) " - ${part.voice.subCategory}" else ""}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Live Focus button (if not already focused)
                    if (!isFocused) {
                        Button(
                            onClick = onFocusPart,
                            colors = ButtonDefaults.buttonColors(containerColor = YamahaCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp).padding(end = 2.dp)
                            )
                            Text(
                                text = stringResource(R.string.btn_focus),
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Quick Layer Toggle Button (only on non-focused parts to layer with active voice)
                    if (!isFocused) {
                        Button(
                            onClick = onToggleLayer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLayerActive) YamahaGold else Color(0xFF1E2638)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isLayerActive) Color.Black else YamahaGold,
                                modifier = Modifier.size(13.dp).padding(end = 2.dp)
                            )
                            Text(
                                text = if (isLayerActive) stringResource(R.string.btn_layer_on) else stringResource(R.string.btn_layer_add),
                                color = if (isLayerActive) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Search / Change Voice Icon
                    IconButton(onClick = { showVoiceSelectorDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_change_voice),
                            tint = YamahaCyan
                        )
                    }
                }
            }
        }

        // ===================================================================
        // 1. COLLAPSIBLE CARD: MIX & EFEITOS
        // ===================================================================
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 8.dp else 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isMixExpanded = !isMixExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.section_mix_effects),
                            color = SynthLightText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = YamahaCyan,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(mixArrowRotation)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isMixExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isLandscape) 8.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
                    ) {
                        // Volume & Pan Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Volume Fader
                            FaderControl(
                                title = stringResource(R.string.param_volume),
                                value = part.volume,
                                range = 0f..127f,
                                onValueChange = onVolumeChange,
                                accentColor = YamahaCyan,
                                onLongClick = { onStartMidiLearn(MidiLearnableParameter.VOLUME) },
                                modifier = Modifier.weight(1f)
                            )

                            // Pan Knob / Fader
                            PanControl(
                                panValue = part.pan,
                                onPanChange = onPanChange,
                                onLongClick = { onStartMidiLearn(MidiLearnableParameter.PAN) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Note Shift (Transpose)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FaderControl(
                                title = stringResource(R.string.param_transpose),
                                value = part.noteShift,
                                range = 40f..88f,
                                onValueChange = onNoteShiftChange,
                                valueFormatter = { NoteHelper.formatNoteShift(it) },
                                accentColor = SynthAmber,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Quick Octave Shift Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onNoteShiftChange((part.noteShift - 12).coerceIn(40, 88)) },
                                    enabled = part.noteShift > 40,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(stringResource(R.string.btn_octave_down_full), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { onNoteShiftChange(64) },
                                    enabled = part.noteShift != 64,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(stringResource(R.string.btn_octave_reset), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { onNoteShiftChange((part.noteShift + 12).coerceIn(40, 88)) },
                                    enabled = part.noteShift < 88,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(stringResource(R.string.btn_octave_up_full), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Reverb & Chorus Sends
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FaderControl(
                                title = stringResource(R.string.param_reverb_send),
                                value = part.reverbSend,
                                range = 0f..127f,
                                onValueChange = onReverbSendChange,
                                accentColor = YamahaCyanDim,
                                onLongClick = { onStartMidiLearn(MidiLearnableParameter.REVERB_SEND) },
                                modifier = Modifier.weight(1f)
                            )

                            FaderControl(
                                title = stringResource(R.string.param_chorus_send),
                                value = part.chorusSend,
                                range = 0f..127f,
                                onValueChange = onChorusSendChange,
                                accentColor = YamahaCyanDim,
                                onLongClick = { onStartMidiLearn(MidiLearnableParameter.CHORUS_SEND) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ===================================================================
        // ===================================================================
        // 1B. COLLAPSIBLE CARD: EQ PARAMÉTRICO (5 BANDAS MASTER EQ + GRÁFICO)
        // ===================================================================
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 8.dp else 12.dp)
            ) {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.section_parametric_eq),
                    expanded = isEqExpanded,
                    onToggleExpand = { isEqExpanded = !isEqExpanded },
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = YamahaCyan.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, YamahaCyan.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showMasterEqGraphDialog = true }
                        ) {
                            Text(
                                text = stringResource(R.string.btn_eq_graph),
                                color = YamahaCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                )

                AnimatedVisibility(
                    visible = isEqExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val eqGains = remember(masterEqGains) {
                        List(5) { idx -> masterEqGains.getOrElse(idx) { 64 }.coerceIn(0, 127) }
                    }
                    val isEqModified = eqGains.any { it != 64 }
                    val isCompactPortraitEq = !isLandscape || configuration.screenWidthDp < 600

                    if (isCompactPortraitEq) {
                        // Portrait / Narrow Screens (< 600dp): 100% Full-Width Single-Line Horizontal Rows per Band
                        val bandTitles = listOf(
                            stringResource(R.string.eq_band_1_low),
                            stringResource(R.string.eq_band_2_low_mid),
                            stringResource(R.string.eq_band_3_mid),
                            stringResource(R.string.eq_band_4_high_mid),
                            stringResource(R.string.eq_band_5_high)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (bandIdx in 0..4) {
                                CompactEqBandHorizontalRow(
                                    title = bandTitles[bandIdx],
                                    value = eqGains[bandIdx],
                                    onValueChange = { onMasterEqBandGainChange(bandIdx, it) }
                                )
                            }

                            OutlinedButton(
                                onClick = onResetMasterEq,
                                enabled = isEqModified,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = YamahaCyan,
                                    disabledContentColor = SynthMutedGray.copy(alpha = 0.4f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isEqModified) YamahaCyan.copy(alpha = 0.6f) else SynthCardBorder
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_reset_eq_flat),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        // Landscape (>= 600dp): 2-Column Grid Layout
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Band 1 (Low) & Band 2 (Low-Mid)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FaderControl(
                                    title = stringResource(R.string.eq_band_1_low),
                                    value = eqGains[0],
                                    range = 0f..127f,
                                    onValueChange = { onMasterEqBandGainChange(0, it) },
                                    valueFormatter = { YamahaSysExEncoder.formatEqGainDb(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )

                                FaderControl(
                                    title = stringResource(R.string.eq_band_2_low_mid),
                                    value = eqGains[1],
                                    range = 0f..127f,
                                    onValueChange = { onMasterEqBandGainChange(1, it) },
                                    valueFormatter = { YamahaSysExEncoder.formatEqGainDb(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Row 2: Band 3 (Mid) & Band 4 (High-Mid)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FaderControl(
                                    title = stringResource(R.string.eq_band_3_mid),
                                    value = eqGains[2],
                                    range = 0f..127f,
                                    onValueChange = { onMasterEqBandGainChange(2, it) },
                                    valueFormatter = { YamahaSysExEncoder.formatEqGainDb(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )

                                FaderControl(
                                    title = stringResource(R.string.eq_band_4_high_mid),
                                    value = eqGains[3],
                                    range = 0f..127f,
                                    onValueChange = { onMasterEqBandGainChange(3, it) },
                                    valueFormatter = { YamahaSysExEncoder.formatEqGainDb(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Row 3: Band 5 (High) & Quick Reset EQ (Flat) Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                FaderControl(
                                    title = stringResource(R.string.eq_band_5_high),
                                    value = eqGains[4],
                                    range = 0f..127f,
                                    onValueChange = { onMasterEqBandGainChange(4, it) },
                                    valueFormatter = { YamahaSysExEncoder.formatEqGainDb(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = onResetMasterEq,
                                    enabled = isEqModified,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = YamahaCyan,
                                        disabledContentColor = SynthMutedGray.copy(alpha = 0.4f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isEqModified) YamahaCyan.copy(alpha = 0.6f) else SynthCardBorder
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_reset_eq_flat),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===================================================================
        // 1C. COLLAPSIBLE CARD: COMPRESSOR / INSERT FX (VCM / CLASSIC COMP + GRÁFICO)
        // ===================================================================
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 8.dp else 12.dp)
            ) {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.section_compressor_insert_fx),
                    expanded = isCompressorExpanded,
                    onToggleExpand = { isCompressorExpanded = !isCompressorExpanded },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = part.isCompressorEnabled,
                                onCheckedChange = { onToggleCompressorEnabled(it) },
                                modifier = Modifier.scale(0.8f),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = YamahaCyan,
                                    uncheckedThumbColor = SynthMutedGray,
                                    uncheckedTrackColor = SynthDarkBackground
                                )
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = YamahaCyan.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, YamahaCyan.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showCompressorGraphDialog = true }
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_comp_graph),
                                    color = YamahaCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                )

                AnimatedVisibility(
                    visible = isCompressorExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val isCompModified = part.compressorThreshold != 72 ||
                            part.compressorOutputGain != 64 ||
                            part.compressorRatio != 3 ||
                            part.compressorAttack != 19 ||
                            part.compressorRelease != 4
                    val isCompactPortraitComp = !isLandscape || configuration.screenWidthDp < 600

                    if (isCompactPortraitComp) {
                        // Portrait / Narrow Screens (< 600dp): 100% Full-Width Single-Line Horizontal Rows per Control
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactFaderRow(
                                title = stringResource(R.string.comp_param_threshold),
                                value = part.compressorThreshold,
                                valueRange = 0f..127f,
                                valueFormatter = { YamahaSysExEncoder.formatCompressorThresholdDb(it) },
                                onValueChange = onCompressorThresholdChange
                            )

                            CompactFaderRow(
                                title = stringResource(R.string.comp_param_output),
                                value = part.compressorOutputGain,
                                valueRange = 0f..127f,
                                valueFormatter = { YamahaSysExEncoder.formatCompressorOutputGainDb(it) },
                                onValueChange = onCompressorOutputGainChange
                            )

                            CompactFaderRow(
                                title = stringResource(R.string.comp_ratio_title),
                                value = part.compressorRatio,
                                valueRange = 0f..(YamahaSysExEncoder.COMPRESSOR_RATIO_LABELS.size - 1).toFloat(),
                                valueFormatter = { YamahaSysExEncoder.formatCompressorRatio(it) },
                                onValueChange = onCompressorRatioChange
                            )

                            CompactFaderRow(
                                title = stringResource(R.string.comp_attack_title),
                                value = part.compressorAttack,
                                valueRange = 0f..(YamahaSysExEncoder.COMPRESSOR_ATTACK_VALUES.size - 1).toFloat(),
                                valueFormatter = { YamahaSysExEncoder.formatCompressorAttackMs(it) },
                                onValueChange = onCompressorAttackChange
                            )

                            CompactFaderRow(
                                title = stringResource(R.string.comp_release_title),
                                value = part.compressorRelease,
                                valueRange = 0f..(YamahaSysExEncoder.COMPRESSOR_RELEASE_VALUES.size - 1).toFloat(),
                                valueFormatter = { YamahaSysExEncoder.formatCompressorReleaseMs(it) },
                                onValueChange = onCompressorReleaseChange
                            )

                            OutlinedButton(
                                onClick = onResetCompressor,
                                enabled = isCompModified,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = YamahaCyan,
                                    disabledContentColor = SynthMutedGray.copy(alpha = 0.4f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCompModified) YamahaCyan.copy(alpha = 0.6f) else SynthCardBorder
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_reset_comp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        // Landscape (>= 600dp): 2-Column Grid Layout
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Input / Threshold & Output Gain
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FaderControl(
                                    title = stringResource(R.string.comp_param_threshold),
                                    value = part.compressorThreshold,
                                    range = 0f..127f,
                                    onValueChange = onCompressorThresholdChange,
                                    valueFormatter = { YamahaSysExEncoder.formatCompressorThresholdDb(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )

                                FaderControl(
                                    title = stringResource(R.string.comp_param_output),
                                    value = part.compressorOutputGain,
                                    range = 0f..127f,
                                    onValueChange = onCompressorOutputGainChange,
                                    valueFormatter = { YamahaSysExEncoder.formatCompressorOutputGainDb(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Row 2: Ratio & Attack Time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FaderControl(
                                    title = stringResource(R.string.comp_ratio_title),
                                    value = part.compressorRatio,
                                    range = 0f..(YamahaSysExEncoder.COMPRESSOR_RATIO_LABELS.size - 1).toFloat(),
                                    onValueChange = onCompressorRatioChange,
                                    valueFormatter = { YamahaSysExEncoder.formatCompressorRatio(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )

                                FaderControl(
                                    title = stringResource(R.string.comp_attack_title),
                                    value = part.compressorAttack,
                                    range = 0f..(YamahaSysExEncoder.COMPRESSOR_ATTACK_VALUES.size - 1).toFloat(),
                                    onValueChange = onCompressorAttackChange,
                                    valueFormatter = { YamahaSysExEncoder.formatCompressorAttackMs(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Row 3: Release Time & Reset Comp Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                FaderControl(
                                    title = stringResource(R.string.comp_release_title),
                                    value = part.compressorRelease,
                                    range = 0f..(YamahaSysExEncoder.COMPRESSOR_RELEASE_VALUES.size - 1).toFloat(),
                                    onValueChange = onCompressorReleaseChange,
                                    valueFormatter = { YamahaSysExEncoder.formatCompressorReleaseMs(it) },
                                    accentColor = YamahaCyan,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = onResetCompressor,
                                    enabled = isCompModified,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = YamahaCyan,
                                        disabledContentColor = SynthMutedGray.copy(alpha = 0.4f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isCompModified) YamahaCyan.copy(alpha = 0.6f) else SynthCardBorder
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_reset_comp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===================================================================
        // 2. COLLAPSIBLE CARD: FILTRO (TONE GENERATOR) + PAD X/Y + ENVOLTÓRIA (AEG)
        // ===================================================================
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 8.dp else 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isFilterExpanded = !isFilterExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = stringResource(R.string.section_filter),
                            color = SynthLightText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = YamahaCyan,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(filterArrowRotation)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = YamahaCyan.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, YamahaCyan.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showFilterXyPadDialog = true }
                    ) {
                        Text(
                            text = stringResource(R.string.btn_filter_xy_pad),
                            color = YamahaCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isFilterExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isLandscape) 8.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
                    ) {
                        // Filter Cutoff & Resonance Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FaderControl(
                                title = stringResource(R.string.param_cutoff),
                                value = part.cutoff,
                                range = 0f..127f,
                                onValueChange = onCutoffChange,
                                accentColor = YamahaCyan,
                                onLongClick = { onStartMidiLearn(MidiLearnableParameter.CUTOFF) },
                                modifier = Modifier.weight(1f)
                            )

                            FaderControl(
                                title = stringResource(R.string.param_resonance),
                                value = part.resonance,
                                range = 0f..127f,
                                onValueChange = onResonanceChange,
                                accentColor = YamahaCyan,
                                onLongClick = { onStartMidiLearn(MidiLearnableParameter.RESONANCE) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = SynthCardBorder, thickness = 1.dp)

                        // Envelope (AEG: Attack, Decay, Release) Sub-header & Quick Reset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.section_envelope_aeg),
                                color = SynthMutedGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )

                            val isAegModified = part.attack != 64 || part.decay != 64 || part.release != 64
                            OutlinedButton(
                                onClick = {
                                    onAttackChange(64)
                                    onDecayChange(64)
                                    onReleaseChange(64)
                                },
                                enabled = isAegModified,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = YamahaCyan,
                                    disabledContentColor = SynthMutedGray.copy(alpha = 0.4f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isAegModified) YamahaCyan.copy(alpha = 0.6f) else SynthCardBorder
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_reset_aeg),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Attack (CC 73) & Decay (CC 75) Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FaderControl(
                                title = stringResource(R.string.param_attack),
                                value = part.attack,
                                range = 0f..127f,
                                onValueChange = onAttackChange,
                                accentColor = YamahaCyan,
                                onLongClick = { onStartMidiLearn(MidiLearnableParameter.ATTACK) },
                                modifier = Modifier.weight(1f)
                            )

                            FaderControl(
                                title = stringResource(R.string.param_decay),
                                value = part.decay,
                                range = 0f..127f,
                                onValueChange = onDecayChange,
                                accentColor = YamahaCyan,
                                onLongClick = { onStartMidiLearn(MidiLearnableParameter.DECAY) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Release (CC 72) Fader
                        FaderControl(
                            title = stringResource(R.string.param_release),
                            value = part.release,
                            range = 0f..127f,
                            onValueChange = onReleaseChange,
                            accentColor = YamahaCyan,
                            onLongClick = { onStartMidiLearn(MidiLearnableParameter.RELEASE) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ===================================================================
        // 3. COLLAPSIBLE CARD: MODULATION / ROTARY (MIDI CC 1)
        // ===================================================================
        val triggerModLearn = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onStartMidiLearn(MidiLearnableParameter.MODULATION)
        }
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = triggerModLearn
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isLandscape) 8.dp else 12.dp)
            ) {
                // Header Row: Title + Rotating Arrow + On/Bypass Switch & Continuous Digital MOD Value Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isModulationExpanded = !isModulationExpanded }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.section_modulation),
                            color = SynthLightText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = YamahaCyan,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(modArrowRotation)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // On / Bypass toggle badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (part.isModulationEnabled) YamahaCyan.copy(alpha = 0.2f) else Color(0xFF2E1A1E),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (part.isModulationEnabled) YamahaCyan else SynthRed.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onToggleModulationEnabled(!part.isModulationEnabled) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (part.isModulationEnabled) stringResource(R.string.mod_rcv_on) else stringResource(R.string.mod_bypass),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (part.isModulationEnabled) YamahaCyan else SynthRed,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Continuous digital LED-style indicator box (Same format as Mixer: MOD %d / BYPASS)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                !part.isModulationEnabled -> Color(0xFF2E171A)
                                part.modulation > 0 -> Color(0xFF241C12)
                                else -> Color(0xFF141924)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when {
                                    !part.isModulationEnabled -> SynthRed
                                    part.modulation > 0 -> SynthAmber.copy(alpha = 0.85f)
                                    else -> YamahaCyan.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = part.isModulationEnabled) { onModulationChange(0) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (part.isModulationEnabled) (if (part.modulation > 0) SynthAmber else YamahaCyan) else SynthRed)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (part.isModulationEnabled) stringResource(R.string.mod_value_format, part.modulation) else stringResource(R.string.mod_bypass),
                                    color = if (part.isModulationEnabled && part.modulation > 0) SynthAmber else if (part.isModulationEnabled) SynthLightText else SynthRed,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Quick Reset Button [0] if modulation > 0
                        if (part.isModulationEnabled && part.modulation > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E2638),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onModulationChange(0) }
                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "0",
                                    color = YamahaCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isModulationExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isLandscape) 6.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
                    ) {
                        Text(
                            text = if (part.isModulationEnabled)
                                stringResource(R.string.mod_desc_enabled)
                            else
                                stringResource(R.string.mod_desc_bypass),
                            color = if (part.isModulationEnabled) SynthMutedGray else SynthRed.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )

                        // Slider with fine-tuning buttons (- / +)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onModulationChange((part.modulation - 1).coerceIn(0, 127)) },
                                enabled = part.isModulationEnabled && part.modulation > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = stringResource(R.string.cd_mod_decrease),
                                    tint = if (part.isModulationEnabled && part.modulation > 0) SynthAmber else SynthMutedGray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Slider(
                                value = part.modulation.toFloat(),
                                onValueChange = { onModulationChange(it.toInt()) },
                                valueRange = 0f..127f,
                                enabled = part.isModulationEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = if (part.isModulationEnabled) SynthLightText else SynthMutedGray.copy(alpha = 0.4f),
                                    activeTrackColor = if (part.isModulationEnabled) SynthAmber else Color(0xFF2A241E),
                                    inactiveTrackColor = SynthFaderTrack,
                                    disabledThumbColor = Color(0xFF384358),
                                    disabledActiveTrackColor = Color(0xFF222838),
                                    disabledInactiveTrackColor = Color(0xFF141924)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .sliderLongPressDetector(onLongPress = triggerModLearn)
                            )

                            IconButton(
                                onClick = { onModulationChange((part.modulation + 1).coerceIn(0, 127)) },
                                enabled = part.isModulationEnabled && part.modulation < 127,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.cd_mod_increase),
                                    tint = if (part.isModulationEnabled && part.modulation < 127) SynthAmber else SynthMutedGray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ===================================================================
        // 4. COLLAPSIBLE CARD: NOTE LIMIT / KEYBOARD SPLIT
        // ===================================================================
        NoteRangeSelector(
            lowNote = part.noteLimitLow,
            highNote = part.noteLimitHigh,
            onRangeChange = onNoteRangeChange,
            expanded = isNoteLimitExpanded,
            onToggleExpand = { isNoteLimitExpanded = !isNoteLimitExpanded },
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Filter X/Y Pad Modal Dialog
    if (showFilterXyPadDialog) {
        FilterXyPadDialog(
            partNumber = part.partNumber,
            voiceName = part.voice.name,
            cutoff = part.cutoff,
            resonance = part.resonance,
            onCutoffChange = onCutoffChange,
            onResonanceChange = onResonanceChange,
            onDismiss = { showFilterXyPadDialog = false }
        )
    }

    // Master EQ 5-Band Interactive Curve Modal Dialog
    if (showMasterEqGraphDialog) {
        MasterEqGraphDialog(
            masterEqGains = masterEqGains,
            onBandGainChange = onMasterEqBandGainChange,
            onResetFlat = onResetMasterEq,
            onDismiss = { showMasterEqGraphDialog = false }
        )
    }

    // Compressor Dynamic Curve Modal Dialog
    if (showCompressorGraphDialog) {
        CompressorGraphDialog(
            partNumber = part.partNumber,
            threshold = part.compressorThreshold,
            outputGain = part.compressorOutputGain,
            ratio = part.compressorRatio,
            attack = part.compressorAttack,
            release = part.compressorRelease,
            onThresholdChange = onCompressorThresholdChange,
            onOutputGainChange = onCompressorOutputGainChange,
            onRatioChange = onCompressorRatioChange,
            onAttackChange = onCompressorAttackChange,
            onReleaseChange = onCompressorReleaseChange,
            onResetComp = onResetCompressor,
            onDismiss = { showCompressorGraphDialog = false }
        )
    }

    // Voice Selector Modal Dialog
    if (showVoiceSelectorDialog) {
        VoiceSelectorDialog(
            currentVoice = part.voice,
            partNumber = part.partNumber,
            userVoices = userVoices,
            onVoicePreview = onVoicePreview,
            onVoiceConfirmed = { voice ->
                onVoiceConfirm(voice)
                showVoiceSelectorDialog = false
            },
            onVoiceCancel = onVoiceCancel,
            onRenameUserVoice = onRenameUserVoice,
            onDismissRequest = { showVoiceSelectorDialog = false }
        )
    }
}


@Composable
fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "section_arrow_rotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggleExpand() }
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = title,
                color = SynthLightText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = YamahaCyan,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation)
            )
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}


/**
 * Full-width (100%) compact horizontal EQ band strip for Portrait / Narrow screens:
 * Band Label on the left, Slider in the middle, and non-wrapping digital dB box ("0.0 dB") on the right.
 */
@Composable
private fun CompactEqBandHorizontalRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SynthDarkBackground.copy(alpha = 0.65f))
            .border(1.dp, SynthCardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left: Band Label
        Text(
            text = title.uppercase(),
            color = SynthMutedGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(114.dp)
        )

        // Middle: Slider
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..127f,
            colors = SliderDefaults.colors(
                thumbColor = SynthLightText,
                activeTrackColor = YamahaCyan,
                inactiveTrackColor = SynthFaderTrack
            ),
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
        )

        // Right: Digital dB Readout Box ("0.0 dB", "+2.5 dB", "-4.0 dB") — never wraps vertically
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 68.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SynthDarkBackground)
                .border(
                    width = 1.dp,
                    color = if (value != 64) YamahaCyan.copy(alpha = 0.65f) else YamahaCyan.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable(enabled = value != 64) { onValueChange(64) }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = YamahaSysExEncoder.formatEqGainDb(value),
                color = if (value != 64) YamahaCyan else SynthLightText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Full-width (100%) compact horizontal fader row for Compressor and Part controls:
 * Label on the left, Slider in the middle, and formatted readout box on the right.
 */
@Composable
private fun CompactFaderRow(
    title: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Int) -> String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SynthDarkBackground.copy(alpha = 0.65f))
            .border(1.dp, SynthCardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left: Label
        Text(
            text = title.uppercase(),
            color = SynthMutedGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(114.dp)
        )

        // Middle: Slider
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = SynthLightText,
                activeTrackColor = YamahaCyan,
                inactiveTrackColor = SynthFaderTrack
            ),
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
        )

        // Right: Formatted Value Readout Box
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 68.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SynthDarkBackground)
                .border(
                    width = 1.dp,
                    color = YamahaCyan.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = valueFormatter(value),
                color = YamahaCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

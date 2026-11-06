package com.yamahamx.editor.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.yamahamx.editor.ui.components.FaderControl
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
    var showChannelMenu by remember { mutableStateOf(false) }

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

                    // Quick Layer Toggle Button
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

        // Faders Section: Volume, Pan, Transpose, Reverb, Chorus
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.section_mix_effects),
                    color = SynthLightText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

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

        // Filter Section: Cutoff & Resonance
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.section_filter),
                    color = SynthLightText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

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
            }
        }

        // Modulation / Rotary Section: MIDI CC 1
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
                    .padding(if (isLandscape) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
            ) {
                // Header Row: Title, On/Bypass Switch & Continuous Digital MOD Value Display (Tap to Reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.section_modulation),
                                color = SynthLightText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                softWrap = false
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

        // Keyboard Split Range Selector
        NoteRangeSelector(
            lowNote = part.noteLimitLow,
            highNote = part.noteLimitHigh,
            onRangeChange = onNoteRangeChange,
            modifier = Modifier.fillMaxWidth()
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

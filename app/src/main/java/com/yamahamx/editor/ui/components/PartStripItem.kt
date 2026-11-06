package com.yamahamx.editor.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.NoteHelper
import com.yamahamx.editor.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PartStripItem(
    part: MultiPartState,
    isSelected: Boolean,
    isFocused: Boolean,
    isMidiActive: Boolean = false,
    isLocked: Boolean = false,
    onLockClick: () -> Unit = {},
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVoiceClick: () -> Unit = {},
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onReceiveChannelChange: (Int) -> Unit,
    onOctaveShift: (Int) -> Unit = {},
    onModulationChange: (Int) -> Unit = {},
    onToggleModulationEnabled: (Boolean) -> Unit = {},
    onVolumeMidiLearn: () -> Unit = {},
    onModulationMidiLearn: () -> Unit = {},
    anySoloActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp
    var showChannelDropdown by remember { mutableStateOf(false) }

    val isLayerActive = part.isLayerActive
    val isSoloMuted = anySoloActive && !part.isSolo

    val ledColor by animateColorAsState(
        targetValue = if (isMidiActive) Color(0xFF00E5FF) else Color(0xFF181E2A),
        label = "part_midi_led"
    )

    val borderColor = when {
        isLocked -> YamahaGold.copy(alpha = 0.4f)
        isFocused -> YamahaCyan
        isLayerActive -> YamahaGold
        isSelected -> YamahaCyan.copy(alpha = 0.5f)
        else -> SynthCardBorder
    }

    val borderWidth = when {
        isFocused || isLayerActive -> 2.dp
        isSelected -> 1.5.dp
        else -> 1.dp
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isLocked -> Color(0xFF121620)
                isFocused -> SynthPanelSurface
                isSelected -> Color(0xFF1E283C)
                else -> SynthCardBackground
            }
        ),
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .width(96.dp)
            .graphicsLayer { alpha = if (isSoloMuted || part.isMuted) 0.72f else 1.0f }
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isLocked) {
                    Modifier.clickable(onClick = onLockClick)
                } else {
                    Modifier.combinedClickable(
                        onClick = onSelect,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    )
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = if (isLandscape) 4.dp else 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Part Number Header & Live Focus / Layer Badge with MIDI Activity LED
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isFocused -> YamahaCyan
                            isLayerActive -> YamahaGold
                            isSelected -> Color(0xFF2B3A55)
                            else -> SynthDarkBackground
                        }
                    )
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    // Blinking MIDI Activity LED
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ledColor)
                            .border(
                                width = 0.5.dp,
                                color = if (isMidiActive) Color.White else Color(0xFF384358),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.cd_pro_feature),
                            tint = YamahaGold,
                            modifier = Modifier.size(10.dp).padding(end = 2.dp)
                        )
                    } else if (isFocused) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Live Active",
                            tint = Color.Black,
                            modifier = Modifier.size(11.dp).padding(end = 1.dp)
                        )
                    } else if (isLayerActive) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Layer",
                            tint = Color.Black,
                            modifier = Modifier.size(10.dp).padding(end = 2.dp)
                        )
                    }
                    Text(
                        text = when {
                            isLocked -> stringResource(R.string.part_strip_locked, part.partNumber)
                            isFocused -> stringResource(R.string.part_strip_active, part.partNumber)
                            isLayerActive -> stringResource(R.string.part_strip_layer, part.partNumber)
                            else -> stringResource(R.string.part_strip_default, part.partNumber)
                        },
                        color = when {
                            isLocked -> YamahaGold
                            isFocused || isLayerActive -> Color.Black
                            else -> SynthLightText
                        },
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Voice Name & Category Pill (Tap to open Voice Selector)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when {
                    isLocked -> Color(0xFF161B26)
                    isFocused -> YamahaCyan.copy(alpha = 0.15f)
                    isLayerActive -> YamahaGold.copy(alpha = 0.15f)
                    else -> Color(0xFF141A28)
                },
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    when {
                        isLocked -> YamahaGold.copy(alpha = 0.3f)
                        isFocused -> YamahaCyan.copy(alpha = 0.6f)
                        isLayerActive -> YamahaGold.copy(alpha = 0.6f)
                        else -> SynthCardBorder
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { if (isLocked) onLockClick() else onVoiceClick() }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isLocked) stringResource(R.string.voice_locked) else part.voice.name,
                        color = when {
                            isLocked -> YamahaGold
                            isFocused -> YamahaCyan
                            isLayerActive -> YamahaGold
                            isSelected -> Color.White
                            else -> SynthLightText
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val isUserVoice = part.voice.msb == 63 && part.voice.lsb == 8
                    Text(
                        text = if (isLocked) stringResource(R.string.tag_pro) else if (isUserVoice) stringResource(R.string.tag_user) else "[${part.voice.category}]",
                        color = when {
                            isLocked -> YamahaGold.copy(alpha = 0.8f)
                            isUserVoice -> YamahaCyan
                            else -> SynthMutedGray
                        },
                        fontSize = 8.5.sp,
                        fontWeight = if (isUserVoice) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Receive Channel Selector Pill (Tap to change MIDI In Channel)
            Box {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        isLocked -> Color(0xFF161B26)
                        isFocused -> YamahaCyan.copy(alpha = 0.2f)
                        part.receiveChannel == 0 -> YamahaGold.copy(alpha = 0.25f)
                        else -> Color(0xFF1E2638)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        when {
                            isLocked -> YamahaGold.copy(alpha = 0.3f)
                            isFocused -> YamahaCyan
                            part.receiveChannel == 0 -> YamahaGold
                            else -> Color.DarkGray
                        }
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (isLocked) {
                                Modifier.clickable(onClick = onLockClick)
                            } else {
                                Modifier.combinedClickable(
                                    onClick = { showChannelDropdown = true },
                                    onLongClick = onLongClick
                                )
                            }
                        )
                ) {
                    Text(
                        text = if (isLocked) stringResource(R.string.channel_locked_pro) else part.receiveChannelDisplay,
                        color = when {
                            isLocked -> YamahaGold
                            isFocused -> YamahaCyan
                            part.receiveChannel == 0 -> YamahaGold
                            part.receiveChannel !in 0..15 -> SynthMutedGray
                            else -> Color.White
                        },
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                DropdownMenu(
                    expanded = showChannelDropdown,
                    onDismissRequest = { showChannelDropdown = false },
                    modifier = Modifier.background(SynthCardBackground)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.channel_keyboard_physical),
                                color = YamahaCyan,
                                fontWeight = if (part.receiveChannel == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onReceiveChannelChange(0)
                            showChannelDropdown = false
                        }
                    )
                    HorizontalDivider(color = SynthCardBorder)
                    (1..15).forEach { ch ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.channel_format, ch + 1),
                                    color = if (part.receiveChannel == ch) YamahaCyan else SynthLightText,
                                    fontWeight = if (part.receiveChannel == ch) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onReceiveChannelChange(ch)
                                showChannelDropdown = false
                            }
                        )
                    }
                    HorizontalDivider(color = SynthCardBorder)
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.channel_off),
                                color = SynthRed,
                                fontWeight = if (part.receiveChannel !in 0..15) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onReceiveChannelChange(127)
                            showChannelDropdown = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Mute & Solo Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Mute
                Button(
                    onClick = { if (isLocked) onLockClick() else onToggleMute() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            part.isMuted -> SynthRed
                            isSoloMuted -> SynthRed.copy(alpha = 0.35f)
                            else -> Color(0xFF1E2638)
                        },
                        contentColor = when {
                            part.isMuted -> Color.White
                            isSoloMuted -> Color.White.copy(alpha = 0.7f)
                            else -> SynthMutedGray
                        }
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_mute_short),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Solo
                Button(
                    onClick = { if (isLocked) onLockClick() else onToggleSolo() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (part.isSolo) SynthAmber else Color(0xFF1E2638),
                        contentColor = if (part.isSolo) Color.Black else SynthMutedGray
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_solo_short),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Octave Shift Control (-1 Oct / +1 Oct / Reset)
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Octave Down (-1 Oct / -12 semitones)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        isLocked -> Color(0xFF161B26)
                        part.noteShift < 64 -> YamahaCyan.copy(alpha = 0.25f)
                        else -> Color(0xFF1C2434)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        when {
                            isLocked -> YamahaGold.copy(alpha = 0.3f)
                            part.noteShift < 64 -> YamahaCyan
                            else -> Color(0xFF2C384E)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = !isLocked && part.noteShift > 40) {
                            if (isLocked) onLockClick() else onOctaveShift(-1)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.octave_down_short),
                            color = when {
                                isLocked -> YamahaGold.copy(alpha = 0.5f)
                                part.noteShift <= 40 -> SynthMutedGray.copy(alpha = 0.4f)
                                part.noteShift < 64 -> YamahaCyan
                                else -> SynthLightText
                            },
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Octave / Transpose State Indicator (Tap to Reset to 0 Oct)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        isLocked -> Color(0xFF161B26)
                        part.noteShift != 64 -> YamahaCyan.copy(alpha = 0.15f)
                        else -> Color(0xFF141924)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        when {
                            isLocked -> YamahaGold.copy(alpha = 0.3f)
                            part.noteShift != 64 -> YamahaCyan.copy(alpha = 0.6f)
                            else -> Color(0xFF263042)
                        }
                    ),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = !isLocked && part.noteShift != 64) {
                            if (isLocked) onLockClick() else onOctaveShift(0)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val semitones = part.noteShift - 64
                        val octText = when {
                            semitones == 0 -> stringResource(R.string.octave_zero)
                            semitones == 12 -> stringResource(R.string.octave_plus_1)
                            semitones == -12 -> stringResource(R.string.octave_minus_1)
                            semitones == 24 -> stringResource(R.string.octave_plus_2)
                            semitones == -24 -> stringResource(R.string.octave_minus_2)
                            semitones > 0 -> stringResource(R.string.octave_plus_semitones, semitones)
                            else -> stringResource(R.string.octave_minus_semitones, semitones)
                        }
                        Text(
                            text = if (isLocked) "-" else octText,
                            color = when {
                                isLocked -> YamahaGold
                                semitones != 0 -> YamahaCyan
                                else -> SynthMutedGray
                            },
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Octave Up (+1 Oct / +12 semitones)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        isLocked -> Color(0xFF161B26)
                        part.noteShift > 64 -> YamahaCyan.copy(alpha = 0.25f)
                        else -> Color(0xFF1C2434)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        when {
                            isLocked -> YamahaGold.copy(alpha = 0.3f)
                            part.noteShift > 64 -> YamahaCyan
                            else -> Color(0xFF2C384E)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = !isLocked && part.noteShift < 88) {
                            if (isLocked) onLockClick() else onOctaveShift(1)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.octave_up_short),
                            color = when {
                                isLocked -> YamahaGold.copy(alpha = 0.5f)
                                part.noteShift >= 88 -> SynthMutedGray.copy(alpha = 0.4f)
                                part.noteShift > 64 -> YamahaCyan
                                else -> SynthLightText
                            },
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Mini Horizontal Modulation Fader & Display
            MiniModulationControl(
                modulation = part.modulation,
                isModulationEnabled = part.isModulationEnabled,
                isLocked = isLocked,
                onModulationChange = onModulationChange,
                onToggleModulationEnabled = onToggleModulationEnabled,
                onLockClick = onLockClick,
                onMidiLearn = onModulationMidiLearn
            )

            // Volume Fader & Indicator (Vertical mixing console fader with fixed height for tactile sliding from 0 to 127)
            val faderHeight = if (isLandscape) 115.dp else 120.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(faderHeight),
                contentAlignment = Alignment.Center
            ) {
                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(faderHeight)
                            .clickable(onClick = onLockClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.cd_pro_feature),
                                tint = YamahaGold.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "PRO",
                                color = YamahaGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    VerticalFader(
                        value = part.volume,
                        valueRange = 0..127,
                        onValueChange = onVolumeChange,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVolumeMidiLearn()
                        },
                        accentColor = when {
                            isFocused -> YamahaCyan
                            isLayerActive -> YamahaGold
                            part.isMuted -> SynthRed.copy(alpha = 0.5f)
                            else -> YamahaCyan
                        },
                        modifier = Modifier.height(faderHeight)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Volume Value Display (Long-click triggers Volume MIDI Learn)
            Text(
                text = "${part.volume}",
                color = if (part.isMuted) SynthRed else SynthLightText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.then(
                    if (!isLocked) {
                        Modifier.combinedClickable(
                            onClick = onSelect,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onVolumeMidiLearn()
                            }
                        )
                    } else {
                        Modifier
                    }
                )
            )

            // Pan & Note Range summary
            Text(
                text = stringResource(
                    R.string.strip_summary_format,
                    part.pan.toString(),
                    NoteHelper.noteNumberToName(part.noteLimitLow),
                    NoteHelper.noteNumberToName(part.noteLimitHigh)
                ),
                color = SynthMutedGray,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalFader(
    value: Int,
    valueRange: IntRange = 0..127,
    onValueChange: (Int) -> Unit,
    onLongClick: (() -> Unit)? = null,
    accentColor: Color = YamahaCyan,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(36.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            )
            .sliderLongPressDetector(onLongPress = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = SynthLightText,
                activeTrackColor = accentColor,
                inactiveTrackColor = SynthFaderTrack
            ),
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(
                            -((placeable.width - placeable.height) / 2),
                            -((placeable.height - placeable.width) / 2)
                        )
                    }
                }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniModulationControl(
    modulation: Int,
    isModulationEnabled: Boolean = true,
    isLocked: Boolean,
    onModulationChange: (Int) -> Unit,
    onToggleModulationEnabled: (Boolean) -> Unit = {},
    onLockClick: () -> Unit,
    onMidiLearn: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var lastSliderTapTime by remember { mutableStateOf(0L) }
    val triggerMidiLearn: (() -> Unit)? = if (onMidiLearn != null && !isLocked) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onMidiLearn()
        }
    } else null

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Mini horizontal Slider with double-tap detection to reset to 0 & stationary long-press for MIDI Learn
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sliderLongPressDetector(onLongPress = triggerMidiLearn)
                .then(
                    if (isLocked) {
                        Modifier.clickable(onClick = onLockClick)
                    } else if (!isModulationEnabled) {
                        Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleModulationEnabled(true)
                        }
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastSliderTapTime < 350L) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onModulationChange(0)
                                            lastSliderTapTime = 0L
                                        } else {
                                            lastSliderTapTime = now
                                        }
                                    }
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = modulation.toFloat(),
                onValueChange = { onModulationChange(it.toInt()) },
                valueRange = 0f..127f,
                enabled = !isLocked && isModulationEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = if (isModulationEnabled && modulation > 0) SynthAmber else if (isModulationEnabled) SynthLightText else Color(0xFF384358),
                    activeTrackColor = if (isModulationEnabled) SynthAmber else Color(0xFF263042),
                    inactiveTrackColor = Color(0xFF1C2434),
                    disabledThumbColor = Color(0xFF384358),
                    disabledActiveTrackColor = Color(0xFF263042),
                    disabledInactiveTrackColor = Color(0xFF161B26)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )
        }

        // Digital LED-style indicator & toggle button:
        // - Single tap: Toggle Modulation On / Bypass (SysEx Receive Switch)
        // - Double tap or Long press (when active): Reset modulation to 0
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = when {
                isLocked -> Color(0xFF161B26)
                !isModulationEnabled -> Color(0xFF2E171A)
                modulation > 0 -> Color(0xFF241C12)
                else -> Color(0xFF141924)
            },
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                when {
                    isLocked -> YamahaGold.copy(alpha = 0.3f)
                    !isModulationEnabled -> SynthRed
                    modulation > 0 -> SynthAmber.copy(alpha = 0.85f)
                    else -> YamahaCyan.copy(alpha = 0.5f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .then(
                    if (isLocked) {
                        Modifier.clickable(onClick = onLockClick)
                    } else {
                        Modifier.combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleModulationEnabled(!isModulationEnabled)
                            },
                            onDoubleClick = {
                                if (isModulationEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onModulationChange(0)
                                }
                            },
                            onLongClick = {
                                if (triggerMidiLearn != null) {
                                    triggerMidiLearn()
                                } else if (isModulationEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onModulationChange(0)
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleModulationEnabled(true)
                                }
                            }
                        )
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLocked) {
                    Text(
                        text = stringResource(R.string.mod_locked),
                        color = YamahaGold,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (!isModulationEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(SynthRed)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.mod_bypass),
                            color = SynthRed,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(YamahaCyan)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.mod_rcv),
                                color = YamahaCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = stringResource(R.string.mod_value_format, modulation),
                            color = if (modulation > 0) SynthAmber else SynthLightText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

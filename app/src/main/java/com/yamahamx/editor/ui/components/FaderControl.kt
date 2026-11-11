package com.yamahamx.editor.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FaderControl(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..127f,
    accentColor: Color = YamahaCyan,
    valueFormatter: (Int) -> String = { it.toString() },
    onLongClick: (() -> Unit)? = null,
    isLocked: Boolean = false,
    onLockedClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val triggerLongClick: (() -> Unit)? = if (!isLocked && onLongClick != null) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        }
    } else null

    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLocked) SynthCardBorder.copy(alpha = 0.5f) else SynthCardBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (isLocked) 0.5f else 1.0f }
                .then(
                    if (!isLocked && triggerLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = triggerLongClick
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Header: Title and Digital Value Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = title.uppercase(),
                            color = if (isLocked) SynthMutedGray.copy(alpha = 0.6f) else SynthMutedGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Bloqueado",
                                tint = YamahaGold,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Digital LED-style value box
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SynthDarkBackground)
                            .border(
                                1.dp,
                                if (isLocked) accentColor.copy(alpha = 0.2f) else accentColor.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = valueFormatter(value),
                            color = if (isLocked) SynthMutedGray else accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Slider with fine-tuning buttons (- / +)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decrement button
                    IconButton(
                        onClick = {
                            val next = (value - 1).coerceIn(range.start.toInt(), range.endInclusive.toInt())
                            onValueChange(next)
                        },
                        enabled = !isLocked && value > range.start.toInt(),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Diminuir",
                            tint = if (!isLocked && value > range.start.toInt()) SynthMutedGray else SynthMutedGray.copy(alpha = 0.25f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Smooth Slider
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { onValueChange(it.toInt()) },
                        enabled = !isLocked,
                        valueRange = range,
                        colors = SliderDefaults.colors(
                            thumbColor = SynthLightText,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = SynthFaderTrack,
                            disabledThumbColor = SynthMutedGray.copy(alpha = 0.4f),
                            disabledActiveTrackColor = accentColor.copy(alpha = 0.25f),
                            disabledInactiveTrackColor = SynthFaderTrack.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .sliderLongPressDetector(onLongPress = if (!isLocked) triggerLongClick else null)
                    )

                    // Increment button
                    IconButton(
                        onClick = {
                            val next = (value + 1).coerceIn(range.start.toInt(), range.endInclusive.toInt())
                            onValueChange(next)
                        },
                        enabled = !isLocked && value < range.endInclusive.toInt(),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Aumentar",
                            tint = if (!isLocked && value < range.endInclusive.toInt()) SynthMutedGray else SynthMutedGray.copy(alpha = 0.25f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (isLocked && onLockedClick != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onLockedClick() }
            )
        }
    }
}

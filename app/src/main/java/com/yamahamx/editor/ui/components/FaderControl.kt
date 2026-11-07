package com.yamahamx.editor.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val triggerLongClick: (() -> Unit)? = if (onLongClick != null) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        }
    } else null

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (triggerLongClick != null) {
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
                Text(
                    text = title.uppercase(),
                    color = SynthMutedGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 6.dp)
                )

                // Digital LED-style value box
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SynthDarkBackground)
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = valueFormatter(value),
                        color = accentColor,
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
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Diminuir",
                        tint = SynthMutedGray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Smooth Slider
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValueChange(it.toInt()) },
                    valueRange = range,
                    colors = SliderDefaults.colors(
                        thumbColor = SynthLightText,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = SynthFaderTrack
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .sliderLongPressDetector(onLongPress = triggerLongClick)
                )

                // Increment button
                IconButton(
                    onClick = {
                        val next = (value + 1).coerceIn(range.start.toInt(), range.endInclusive.toInt())
                        onValueChange(next)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aumentar",
                        tint = SynthMutedGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

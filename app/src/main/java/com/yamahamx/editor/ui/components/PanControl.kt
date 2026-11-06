package com.yamahamx.editor.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.NoteHelper
import com.yamahamx.editor.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PanControl(
    panValue: Int,
    onPanChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.param_pan),
                    color = SynthMutedGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                // Pan text with Center click to reset, Long click for MIDI Learn
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SynthDarkBackground)
                        .border(1.dp, SynthAmber.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .combinedClickable(
                            onClick = { onPanChange(64) },
                            onLongClick = triggerLongClick
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = NoteHelper.formatPan(panValue),
                        color = SynthAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Pan Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "L",
                    color = SynthMutedGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Slider(
                    value = panValue.toFloat(),
                    onValueChange = { onPanChange(it.toInt()) },
                    valueRange = 1f..127f,
                    colors = SliderDefaults.colors(
                        thumbColor = SynthLightText,
                        activeTrackColor = SynthAmber,
                        inactiveTrackColor = SynthFaderTrack
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .sliderLongPressDetector(onLongPress = triggerLongClick)
                )

                Text(
                    text = "R",
                    color = SynthMutedGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

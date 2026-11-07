package com.yamahamx.editor.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yamahamx.editor.R
import com.yamahamx.editor.ui.theme.*
import kotlin.math.roundToInt

/**
 * Interactive 2D X/Y Pad Modal for real-time Filter Cutoff (X: 0..127) and Resonance (Y: 0..127) control.
 */
@Composable
fun FilterXyPadDialog(
    partNumber: Int,
    voiceName: String,
    cutoff: Int,
    resonance: Int,
    onCutoffChange: (Int) -> Unit,
    onResonanceChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp

    val clampedCutoff = cutoff.coerceIn(0, 127)
    val clampedResonance = resonance.coerceIn(0, 127)

    val currentCutoffState by rememberUpdatedState(clampedCutoff)
    val currentResonanceState by rememberUpdatedState(clampedResonance)
    val currentOnCutoffChange by rememberUpdatedState(onCutoffChange)
    val currentOnResonanceChange by rememberUpdatedState(onResonanceChange)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.65f else 0.92f)
                .widthIn(max = 420.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = BorderStroke(1.5.dp, YamahaCyan.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isLandscape) 14.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row: Badge & Part Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = YamahaCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, YamahaCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = stringResource(R.string.btn_filter_xy_pad),
                            color = YamahaCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = "P$partNumber • $voiceName",
                        color = SynthMutedGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title & Subtitle
                Text(
                    text = stringResource(R.string.dialog_filter_xy_title),
                    color = SynthLightText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center
                )

                if (!isLandscape) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.dialog_filter_xy_subtitle),
                        color = SynthMutedGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real-time Digital Coordinate Readout Row (Cutoff X & Resonance Y)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterValueReadoutBox(
                        label = stringResource(R.string.axis_cutoff_x),
                        value = clampedCutoff,
                        accentColor = YamahaCyan,
                        modifier = Modifier.weight(1f)
                    )
                    FilterValueReadoutBox(
                        label = stringResource(R.string.axis_resonance_y),
                        value = clampedResonance,
                        accentColor = SynthFaderActive,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Square X/Y Pad Area
                Box(
                    modifier = Modifier
                        .then(
                            if (isLandscape) {
                                Modifier.size(190.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            }
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(SynthDarkBackground)
                        .border(
                            width = 1.5.dp,
                            color = YamahaCyan.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .pointerInput(Unit) {
                            fun updateFromPosition(pos: Offset, boxSize: IntSize) {
                                if (boxSize.width <= 0 || boxSize.height <= 0) return
                                val normX = (pos.x / boxSize.width.toFloat()).coerceIn(0f, 1f)
                                val normY = (pos.y / boxSize.height.toFloat()).coerceIn(0f, 1f)

                                val newCutoff = (normX * 127f).roundToInt().coerceIn(0, 127)
                                val newResonance = ((1f - normY) * 127f).roundToInt().coerceIn(0, 127)

                                if (newCutoff != currentCutoffState) {
                                    currentOnCutoffChange(newCutoff)
                                }
                                if (newResonance != currentResonanceState) {
                                    currentOnResonanceChange(newResonance)
                                }
                            }

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                updateFromPosition(down.position, size)

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change != null && change.pressed) {
                                        change.consume()
                                        updateFromPosition(change.position, size)
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Subtle background radial ambient glow around current point
                        val pointX = (clampedCutoff / 127f) * w
                        val pointY = (1f - (clampedResonance / 127f)) * h
                        val indicatorCenter = Offset(pointX, pointY)

                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    YamahaCyan.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = indicatorCenter,
                                radius = (w * 0.55f).coerceAtLeast(1f)
                            )
                        )

                        // Minor Grid Lines (8x8 subdivisions)
                        val gridSteps = 8
                        val minorLineColor = SynthCardBorder.copy(alpha = 0.55f)
                        for (i in 1 until gridSteps) {
                            if (i == gridSteps / 2) continue // Center lines drawn separately
                            val frac = i.toFloat() / gridSteps
                            // Vertical line
                            drawLine(
                                color = minorLineColor,
                                start = Offset(w * frac, 0f),
                                end = Offset(w * frac, h),
                                strokeWidth = 1.dp.toPx()
                            )
                            // Horizontal line
                            drawLine(
                                color = minorLineColor,
                                start = Offset(0f, h * frac),
                                end = Offset(w, h * frac),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Center Reference Guide Lines (Cutoff = 64, Resonance = 64)
                        val centerRatio = 64f / 127f
                        val centerX = w * centerRatio
                        val centerY = h * (1f - centerRatio)
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

                        drawLine(
                            color = YamahaCyan.copy(alpha = 0.28f),
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, h),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = YamahaCyan.copy(alpha = 0.28f),
                            start = Offset(0f, centerY),
                            end = Offset(w, centerY),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = dashEffect
                        )

                        // Active Crosshair Lines through current coordinate
                        drawLine(
                            color = YamahaCyan.copy(alpha = 0.45f),
                            start = Offset(pointX, 0f),
                            end = Offset(pointX, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = SynthFaderActive.copy(alpha = 0.45f),
                            start = Offset(0f, pointY),
                            end = Offset(w, pointY),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Outer Glow Halo around active indicator point
                        val glowRadius = 26.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SynthFaderActive.copy(alpha = 0.55f),
                                    YamahaCyan.copy(alpha = 0.2f),
                                    Color.Transparent
                                ),
                                center = indicatorCenter,
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = indicatorCenter
                        )

                        // Outer Cyan Ring
                        drawCircle(
                            color = YamahaCyan,
                            radius = 11.dp.toPx(),
                            center = indicatorCenter,
                            style = Stroke(width = 2.2.dp.toPx())
                        )

                        // Inner Bright Core
                        drawCircle(
                            color = Color.White,
                            radius = 5.5.dp.toPx(),
                            center = indicatorCenter
                        )
                    }

                    // Subtle Corner / Axis Labels overlay
                    Text(
                        text = "RES 127",
                        color = SynthMutedGray.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    )

                    Text(
                        text = "RES 0 • CUT 0",
                        color = SynthMutedGray.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    )

                    Text(
                        text = "CUT 127",
                        color = SynthMutedGray.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Actions: Reset (64/64) and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onCutoffChange(64)
                            onResonanceChange(64)
                        },
                        enabled = clampedCutoff != 64 || clampedResonance != 64,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = YamahaCyan,
                            disabledContentColor = SynthMutedGray.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (clampedCutoff != 64 || clampedResonance != 64) YamahaCyan.copy(alpha = 0.6f)
                            else SynthCardBorder
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_filter_reset_center),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YamahaCyan,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_close),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterValueReadoutBox(
    label: String,
    value: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SynthDarkBackground)
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = SynthMutedGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value.toString(),
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

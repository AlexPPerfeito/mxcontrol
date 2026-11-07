package com.yamahamx.editor.ui.components

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.yamahamx.editor.data.midi.YamahaSysExEncoder
import com.yamahamx.editor.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

private val BAND_FREQ_LABELS = arrayOf("80Hz", "400Hz", "1kHz", "2.5kHz", "8kHz")

/**
 * Interactive 5-Band Parametric Master EQ Curve Modal Dialog.
 * - Equidistant X-Axis for the 5 fixed bands:
 *   paddingX = totalWidth * 0.1f, usableWidth = totalWidth - (paddingX * 2f),
 *   nodeX(i) = paddingX + usableWidth * (i / 4.0f) -> (10%, 30%, 50%, 70%, 90% of width)
 * - Amplitude Y-Axis: +12 dB (top), 0 dB (dashed center), -12 dB (bottom)
 * - 5 numbered glowing cyan circular nodes (1..5) with smooth end-to-end cubic Bézier curve
 * - Real-time vertical drag updates the ViewModel state and dispatches throttled SysEx
 */
@Composable
fun MasterEqGraphDialog(
    masterEqGains: List<Int>,
    onBandGainChange: (bandIndex: Int, newGain: Int) -> Unit,
    onResetFlat: () -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val density = LocalDensity.current

    val safeGains = remember(masterEqGains) {
        List(5) { idx -> masterEqGains.getOrElse(idx) { 64 }.coerceIn(0, 127) }
    }

    var activeDraggedBand by remember { mutableStateOf<Int?>(null) }

    val currentGainsState by rememberUpdatedState(safeGains)
    val currentOnBandGainChange by rememberUpdatedState(onBandGainChange)

    // Pre-allocated Paths and Native Paint for zero-allocation 60fps Canvas rendering
    val curvePath = remember { Path() }
    val fillPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) }

    val nodeNumberPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 10.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.78f else 0.95f)
                .widthIn(max = 520.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = BorderStroke(1.5.dp, YamahaCyan.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isLandscape) 12.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row: Badge & Frequency Range Info
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
                            text = stringResource(R.string.section_parametric_eq),
                            color = YamahaCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = "5 BANDAS • ±12 dB",
                        color = SynthMutedGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title & Subtitle
                Text(
                    text = stringResource(R.string.dialog_master_eq_title),
                    color = SynthLightText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center
                )

                if (!isLandscape) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.dialog_master_eq_subtitle),
                        color = SynthMutedGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5-Band Real-Time dB Readout Chips
                // Each column occupies 20% of totalWidth, so its center is at (10%, 30%, 50%, 70%, 90%)
                // coinciding with nodeX(i) = paddingX + usableWidth * (i / 4f) where paddingX = 0.1f * totalWidth!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (bandIdx in 0..4) {
                        val isBandActive = activeDraggedBand == bandIdx
                        val gainVal = safeGains[bandIdx]
                        val dbText = YamahaSysExEncoder.formatEqGainDb(gainVal)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isBandActive) YamahaCyan.copy(alpha = 0.15f) else SynthDarkBackground)
                                    .border(
                                        width = 1.dp,
                                        color = if (isBandActive) YamahaCyan else if (gainVal != 64) YamahaCyan.copy(alpha = 0.45f) else SynthCardBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${bandIdx + 1}·${BAND_FREQ_LABELS[bandIdx]}",
                                    color = if (isBandActive) YamahaCyan else SynthMutedGray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dbText,
                                    color = if (gainVal != 64 || isBandActive) YamahaCyan else SynthLightText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive EQ Curve Graph Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 165.dp else 225.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SynthDarkBackground)
                        .border(
                            width = 1.5.dp,
                            color = YamahaCyan.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .pointerInput(Unit) {
                            val padTopPx = 18.dp.toPx()
                            val padBottomPx = 22.dp.toPx()

                            fun findClosestBandIndex(touchX: Float, boxWidth: Int): Int {
                                val totalW = boxWidth.toFloat().coerceAtLeast(1f)
                                val paddingX = totalW * 0.1f
                                val usableW = (totalW - paddingX * 2f).coerceAtLeast(1f)
                                var bestIdx = 0
                                var bestDist = Float.MAX_VALUE
                                for (i in 0..4) {
                                    val nx = paddingX + usableW * (i / 4.0f)
                                    val dist = abs(touchX - nx)
                                    if (dist < bestDist) {
                                        bestDist = dist
                                        bestIdx = i
                                    }
                                }
                                return bestIdx
                            }

                            fun updateBandGainFromY(bandIdx: Int, touchY: Float, boxSize: IntSize) {
                                val usableH = (boxSize.height - padTopPx - padBottomPx).coerceAtLeast(1f)
                                val normY = ((touchY - padTopPx) / usableH).coerceIn(0f, 1f)
                                val newGain = ((1f - normY) * 127f).roundToInt().coerceIn(0, 127)
                                if (currentGainsState[bandIdx] != newGain) {
                                    currentOnBandGainChange(bandIdx, newGain)
                                }
                            }

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val targetBand = findClosestBandIndex(down.position.x, size.width)
                                activeDraggedBand = targetBand
                                updateBandGainFromY(targetBand, down.position.y, size)

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change != null && change.pressed) {
                                        change.consume()
                                        updateBandGainFromY(targetBand, change.position.y, size)
                                    }
                                } while (event.changes.any { it.pressed })

                                activeDraggedBand = null
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val totalWidth = size.width
                        val totalHeight = size.height
                        val padTop = 18.dp.toPx()
                        val padBottom = 22.dp.toPx()
                        val plotH = (totalHeight - padTop - padBottom).coerceAtLeast(1f)
                        val centerY = padTop + plotH * 0.5f

                        // Equidistant X-Axis Layout:
                        // paddingX = totalWidth * 0.1f
                        // usableWidth = totalWidth - (paddingX * 2f)
                        // nodeX[i] = paddingX + usableWidth * (i / 4.0f)
                        val paddingX = totalWidth * 0.1f
                        val usableWidth = (totalWidth - paddingX * 2f).coerceAtLeast(1f)

                        val nodeX = FloatArray(5)
                        val nodeY = FloatArray(5)
                        for (i in 0..4) {
                            nodeX[i] = paddingX + usableWidth * (i / 4.0f)
                            val normGain = safeGains[i] / 127f
                            nodeY[i] = padTop + (1f - normGain) * plotH
                        }

                        // 1. Horizontal Amplitude Grid Lines (+12dB, +6dB, 0dB dashed, -6dB, -12dB)
                        val minorGridColor = SynthCardBorder.copy(alpha = 0.5f)
                        val quarterTopY = padTop + plotH * 0.25f
                        val quarterBotY = padTop + plotH * 0.75f

                        drawLine(
                            color = minorGridColor,
                            start = Offset(0f, padTop),
                            end = Offset(totalWidth, padTop),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = minorGridColor,
                            start = Offset(0f, quarterTopY),
                            end = Offset(totalWidth, quarterTopY),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = minorGridColor,
                            start = Offset(0f, quarterBotY),
                            end = Offset(totalWidth, quarterBotY),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = minorGridColor,
                            start = Offset(0f, padTop + plotH),
                            end = Offset(totalWidth, padTop + plotH),
                            strokeWidth = 1.dp.toPx()
                        )

                        // 2. Vertical Grid Lines coinciding with the 5 Equidistant Node X Coordinates (plus midpoint subdivisions)
                        for (i in 0..4) {
                            val gx = nodeX[i]
                            val isActiveCol = activeDraggedBand == i
                            drawLine(
                                color = if (isActiveCol) YamahaCyan.copy(alpha = 0.45f) else SynthCardBorder.copy(alpha = 0.75f),
                                start = Offset(gx, padTop),
                                end = Offset(gx, padTop + plotH),
                                strokeWidth = if (isActiveCol || i == 2) 1.2.dp.toPx() else 1.dp.toPx()
                            )
                            if (i < 4) {
                                val midGx = (nodeX[i] + nodeX[i + 1]) * 0.5f
                                drawLine(
                                    color = minorGridColor.copy(alpha = 0.3f),
                                    start = Offset(midGx, padTop),
                                    end = Offset(midGx, padTop + plotH),
                                    strokeWidth = 0.8.dp.toPx()
                                )
                            }
                        }

                        // 3. Central 0 dB Dashed Reference Line
                        drawLine(
                            color = YamahaCyan.copy(alpha = 0.45f),
                            start = Offset(0f, centerY),
                            end = Offset(totalWidth, centerY),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = dashEffect
                        )

                        // 4. Build Smooth End-to-End Cubic Bézier Curve Path passing through all 5 Equidistant Nodes
                        curvePath.reset()
                        fillPath.reset()

                        val startX = 0f
                        val startY = centerY + (nodeY[0] - centerY) * 0.75f
                        val endX = totalWidth
                        val endY = centerY + (nodeY[4] - centerY) * 0.75f

                        curvePath.moveTo(startX, startY)

                        // Smooth entry segment from left border (0f) to Node 1 (nodeX[0])
                        val dxStart = nodeX[0] - startX
                        curvePath.cubicTo(
                            startX + dxStart * 0.5f, startY,
                            nodeX[0] - dxStart * 0.5f, nodeY[0],
                            nodeX[0], nodeY[0]
                        )

                        // Equidistant Cubic Bézier segments connecting Nodes 1 -> 2 -> 3 -> 4 -> 5
                        for (i in 0 until 4) {
                            val x0 = nodeX[i]
                            val y0 = nodeY[i]
                            val x1 = nodeX[i + 1]
                            val y1 = nodeY[i + 1]
                            val dx = x1 - x0
                            curvePath.cubicTo(
                                x0 + dx * 0.45f, y0,
                                x1 - dx * 0.45f, y1,
                                x1, y1
                            )
                        }

                        // Smooth exit segment from Node 5 (nodeX[4]) to right border (totalWidth)
                        val dxEnd = endX - nodeX[4]
                        curvePath.cubicTo(
                            nodeX[4] + dxEnd * 0.5f, nodeY[4],
                            endX - dxEnd * 0.5f, endY,
                            endX, endY
                        )

                        // Fill area between curve and 0 dB center line
                        fillPath.addPath(curvePath)
                        fillPath.lineTo(endX, centerY)
                        fillPath.lineTo(startX, centerY)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    YamahaCyan.copy(alpha = 0.24f),
                                    YamahaCyan.copy(alpha = 0.05f),
                                    YamahaCyan.copy(alpha = 0.24f)
                                ),
                                startY = padTop,
                                endY = padTop + plotH
                            )
                        )

                        // Outer soft glow on curve
                        drawPath(
                            path = curvePath,
                            color = YamahaCyan.copy(alpha = 0.28f),
                            style = Stroke(
                                width = 6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Crisp Cyan Parametric EQ Curve
                        drawPath(
                            path = curvePath,
                            color = YamahaCyan,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // 5. Draw 5 Numbered Circular Nodes (1..5) with Luminous Cyan Contour
                        val nodeRadius = 11.dp.toPx()
                        val glowRadius = 22.dp.toPx()
                        val fontOffset = (nodeNumberPaint.descent() + nodeNumberPaint.ascent()) / 2f

                        for (i in 0..4) {
                            val center = Offset(nodeX[i], nodeY[i])
                            val isDraggingThis = activeDraggedBand == i

                            // Active stem from node to 0 dB reference line
                            drawLine(
                                color = YamahaCyan.copy(alpha = if (isDraggingThis) 0.75f else 0.35f),
                                start = Offset(center.x, centerY),
                                end = center,
                                strokeWidth = 1.5.dp.toPx()
                            )

                            // Luminous radial glow halo
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        YamahaCyan.copy(alpha = if (isDraggingThis) 0.65f else 0.38f),
                                        YamahaCyan.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = if (isDraggingThis) glowRadius * 1.25f else glowRadius
                                ),
                                radius = if (isDraggingThis) glowRadius * 1.25f else glowRadius,
                                center = center
                            )

                            // Filled cyan circle core
                            drawCircle(
                                color = if (isDraggingThis) Color.White else YamahaCyan,
                                radius = nodeRadius,
                                center = center
                            )

                            // Luminous outer ring contour
                            drawCircle(
                                color = Color.White,
                                radius = nodeRadius,
                                center = center,
                                style = Stroke(width = if (isDraggingThis) 2.4.dp.toPx() else 1.5.dp.toPx())
                            )

                            // Node Number (1..5)
                            drawContext.canvas.nativeCanvas.drawText(
                                (i + 1).toString(),
                                center.x,
                                center.y - fontOffset,
                                nodeNumberPaint
                            )
                        }
                    }

                    // Y-Axis Amplitude Labels Overlay (+12 dB, 0 dB, -12 dB)
                    Text(
                        text = "+12 dB",
                        color = SynthMutedGray.copy(alpha = 0.75f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 5.dp, top = 3.dp)
                    )

                    Text(
                        text = "0 dB",
                        color = YamahaCyan.copy(alpha = 0.85f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 5.dp, bottom = 4.dp)
                    )

                    Text(
                        text = "-12 dB",
                        color = SynthMutedGray.copy(alpha = 0.75f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 5.dp, bottom = 18.dp)
                    )

                    // Bottom X-Axis Band Frequency Labels aligned directly under the 5 equidistant nodes
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (bandIdx in 0..4) {
                            val isActive = activeDraggedBand == bandIdx
                            Text(
                                text = BAND_FREQ_LABELS[bandIdx],
                                color = if (isActive || bandIdx == 2) YamahaCyan.copy(alpha = 0.9f) else SynthMutedGray.copy(alpha = 0.8f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isActive || bandIdx == 2) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Actions: Reset EQ (Flat) and Close
                val isEqModified = safeGains.any { it != 64 }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onResetFlat,
                        enabled = isEqModified,
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
                            if (isEqModified) YamahaCyan.copy(alpha = 0.6f) else SynthCardBorder
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_reset_eq_flat),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
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
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

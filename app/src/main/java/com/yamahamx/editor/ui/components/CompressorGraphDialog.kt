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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yamahamx.editor.R
import com.yamahamx.editor.data.midi.YamahaSysExEncoder
import com.yamahamx.editor.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

private val RATIO_NUMERIC_VALUES = floatArrayOf(1.0f, 1.5f, 2.0f, 3.0f, 5.0f, 7.0f, 10.0f, 20.0f)
private const val MIN_DB = -48f
private const val MAX_DB = 12f
private const val DB_SPAN = MAX_DB - MIN_DB // 60 dB

@Composable
fun CompressorGraphDialog(
    partNumber: Int,
    threshold: Int,
    outputGain: Int,
    ratio: Int,
    attack: Int,
    release: Int,
    onThresholdChange: (Int) -> Unit,
    onOutputGainChange: (Int) -> Unit,
    onRatioChange: (Int) -> Unit,
    onAttackChange: (Int) -> Unit,
    onReleaseChange: (Int) -> Unit,
    onResetComp: () -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp
    val density = LocalDensity.current

    val safeThreshold = threshold.coerceIn(0, 127)
    val safeGain = outputGain.coerceIn(0, 127)
    val safeRatio = ratio.coerceIn(0, YamahaSysExEncoder.COMPRESSOR_RATIO_LABELS.size - 1)
    val safeAttack = attack.coerceIn(0, YamahaSysExEncoder.COMPRESSOR_ATTACK_VALUES.size - 1)
    val safeRelease = release.coerceIn(0, YamahaSysExEncoder.COMPRESSOR_RELEASE_VALUES.size - 1)

    // Current dB conversions
    val threshDb = -48f + (safeThreshold / 127f) * 48f
    val gainDb = ((safeGain - 64) / 64f) * 12f
    val ratioValue = RATIO_NUMERIC_VALUES[safeRatio]

    val currentOnThresholdChange by rememberUpdatedState(onThresholdChange)
    val currentOnOutputGainChange by rememberUpdatedState(onOutputGainChange)

    val curvePath = remember { Path() }
    val fillPath = remember { Path() }
    val refPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }

    val labelPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(160, 160, 170, 185)
            textAlign = Paint.Align.RIGHT
            textSize = with(density) { 9.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.82f else 0.96f)
                .widthIn(max = 560.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = BorderStroke(1.5.dp, YamahaCyan.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(if (isLandscape) 12.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
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
                            text = "PARTE $partNumber • VCM COMP",
                            color = YamahaCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = "DYNAMIC CURVE",
                        color = SynthMutedGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.compressor_graph_title),
                    color = SynthLightText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Numerical Feedback Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompParamBadge(
                        label = stringResource(R.string.comp_param_thresh_short),
                        value = YamahaSysExEncoder.formatCompressorThresholdDb(safeThreshold),
                        modifier = Modifier.weight(1f),
                        isHighlighted = true
                    )
                    CompParamBadge(
                        label = stringResource(R.string.comp_param_ratio),
                        value = YamahaSysExEncoder.formatCompressorRatio(safeRatio),
                        modifier = Modifier.weight(1f)
                    )
                    CompParamBadge(
                        label = stringResource(R.string.comp_param_output_short),
                        value = YamahaSysExEncoder.formatCompressorOutputGainDb(safeGain),
                        modifier = Modifier.weight(1f),
                        isHighlighted = true
                    )
                    CompParamBadge(
                        label = stringResource(R.string.comp_param_attack_short),
                        value = YamahaSysExEncoder.formatCompressorAttackMs(safeAttack),
                        modifier = Modifier.weight(1f)
                    )
                    CompParamBadge(
                        label = stringResource(R.string.comp_param_release_short),
                        value = YamahaSysExEncoder.formatCompressorReleaseMs(safeRelease),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Dynamic Compression Canvas
                val canvasHeight = if (isLandscape) 180.dp else 220.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SynthDarkBackground)
                        .border(1.dp, SynthCardBorder, RoundedCornerShape(10.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val paddingLeft = 36f
                                    val paddingRight = 16f
                                    val paddingTop = 16f
                                    val paddingBottom = 26f
                                    val graphW = size.width - paddingLeft - paddingRight
                                    val graphH = size.height - paddingTop - paddingBottom

                                    if (graphW > 0 && graphH > 0) {
                                        // Update Threshold and Output Gain from touch position
                                        val touchX = down.position.x
                                        val touchY = down.position.y

                                        val normX = ((touchX - paddingLeft) / graphW).coerceIn(0f, 1f)
                                        val inputDb = MIN_DB + normX * DB_SPAN
                                        // Map -48..0 dB to 0..127 threshold
                                        val newThresh = ((inputDb - (-48f)) / 48f * 127f).roundToInt().coerceIn(0, 127)
                                        currentOnThresholdChange(newThresh)

                                        val normY = 1f - ((touchY - paddingTop) / graphH).coerceIn(0f, 1f)
                                        val outputDb = MIN_DB + normY * DB_SPAN
                                        val currentThreshDb = -48f + (newThresh / 127f) * 48f
                                        val desiredGainDb = (outputDb - currentThreshDb).coerceIn(-12f, 12f)
                                        val newGain = (64 + (desiredGainDb / 12f * 64f)).roundToInt().coerceIn(0, 127)
                                        currentOnOutputGainChange(newGain)
                                    }

                                    do {
                                        val event = awaitPointerEvent()
                                        val move = event.changes.firstOrNull() ?: break
                                        if (move.pressed) {
                                            if (graphW > 0 && graphH > 0) {
                                                val normX = ((move.position.x - paddingLeft) / graphW).coerceIn(0f, 1f)
                                                val inputDb = MIN_DB + normX * DB_SPAN
                                                val newThresh = ((inputDb - (-48f)) / 48f * 127f).roundToInt().coerceIn(0, 127)
                                                currentOnThresholdChange(newThresh)

                                                val normY = 1f - ((move.position.y - paddingTop) / graphH).coerceIn(0f, 1f)
                                                val outputDb = MIN_DB + normY * DB_SPAN
                                                val currentThreshDb = -48f + (newThresh / 127f) * 48f
                                                val desiredGainDb = (outputDb - currentThreshDb).coerceIn(-12f, 12f)
                                                val newGain = (64 + (desiredGainDb / 12f * 64f)).roundToInt().coerceIn(0, 127)
                                                currentOnOutputGainChange(newGain)
                                            }
                                            move.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    ) {
                        val paddingLeft = 36f
                        val paddingRight = 16f
                        val paddingTop = 16f
                        val paddingBottom = 26f
                        val graphW = size.width - paddingLeft - paddingRight
                        val graphH = size.height - paddingTop - paddingBottom

                        if (graphW <= 0 || graphH <= 0) return@Canvas

                        fun dbToPxX(db: Float): Float {
                            return paddingLeft + ((db - MIN_DB) / DB_SPAN) * graphW
                        }

                        fun dbToPxY(db: Float): Float {
                            val norm = (db - MIN_DB) / DB_SPAN
                            return paddingTop + (1f - norm.coerceIn(0f, 1f)) * graphH
                        }

                        // Draw Grid Lines & dB labels (-36, -24, -12, 0, +12)
                        val gridDbSteps = floatArrayOf(-36f, -24f, -12f, 0f, 12f)
                        gridDbSteps.forEach { db ->
                            val y = dbToPxY(db)
                            val x = dbToPxX(db)

                            // Horizontal Grid Line
                            drawLine(
                                color = if (db == 0f) SynthMutedGray.copy(alpha = 0.35f) else SynthCardBorder.copy(alpha = 0.4f),
                                start = Offset(paddingLeft, y),
                                end = Offset(paddingLeft + graphW, y),
                                strokeWidth = if (db == 0f) 1.5f else 1f
                            )

                            // Y axis label
                            drawContext.canvas.nativeCanvas.drawText(
                                "${db.toInt()}",
                                paddingLeft - 6f,
                                y + 3.5f,
                                labelPaint
                            )

                            // Vertical Grid Line
                            drawLine(
                                color = if (db == 0f) SynthMutedGray.copy(alpha = 0.35f) else SynthCardBorder.copy(alpha = 0.4f),
                                start = Offset(x, paddingTop),
                                end = Offset(x, paddingTop + graphH),
                                strokeWidth = if (db == 0f) 1.5f else 1f
                            )
                        }

                        // 1:1 Reference Line (Dashed)
                        refPath.reset()
                        refPath.moveTo(dbToPxX(MIN_DB), dbToPxY(MIN_DB))
                        refPath.lineTo(dbToPxX(MAX_DB), dbToPxY(MAX_DB))
                        drawPath(
                            path = refPath,
                            color = SynthMutedGray.copy(alpha = 0.30f),
                            style = Stroke(width = 1.5f, pathEffect = dashEffect)
                        )

                        // Compute Live Dynamic Compression Curve
                        val kneeWidth = 6.0f // dB soft knee
                        val halfKnee = kneeWidth / 2.0f

                        fun compCurveDb(inputDb: Float): Float {
                            val delta = inputDb - threshDb
                            val unshiftedOut = when {
                                delta <= -halfKnee -> inputDb
                                delta >= halfKnee -> threshDb + (delta / ratioValue)
                                else -> {
                                    val xKnee = delta + halfKnee
                                    inputDb + ((1f / ratioValue - 1f) * (xKnee * xKnee)) / (2f * kneeWidth)
                                }
                            }
                            return unshiftedOut + gainDb
                        }

                        curvePath.reset()
                        fillPath.reset()

                        val sampleSteps = 60
                        val startPxX = dbToPxX(MIN_DB)
                        val startPxY = dbToPxY(compCurveDb(MIN_DB))

                        curvePath.moveTo(startPxX, startPxY)
                        fillPath.moveTo(startPxX, paddingTop + graphH)
                        fillPath.lineTo(startPxX, startPxY)

                        for (i in 1..sampleSteps) {
                            val curDb = MIN_DB + (i.toFloat() / sampleSteps) * DB_SPAN
                            val outDb = compCurveDb(curDb)
                            val px = dbToPxX(curDb)
                            val py = dbToPxY(outDb)
                            curvePath.lineTo(px, py)
                            fillPath.lineTo(px, py)
                        }

                        fillPath.lineTo(paddingLeft + graphW, paddingTop + graphH)
                        fillPath.close()

                        // Gradient fill under curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    YamahaCyan.copy(alpha = 0.35f),
                                    YamahaCyan.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                startY = paddingTop,
                                endY = paddingTop + graphH
                            )
                        )

                        // Glowing curve stroke
                        drawPath(
                            path = curvePath,
                            color = YamahaCyan.copy(alpha = 0.3f),
                            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = curvePath,
                            color = YamahaCyan,
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // Inflection Point / Threshold Marker Node
                        val threshPxX = dbToPxX(threshDb)
                        val threshPxY = dbToPxY(threshDb + gainDb)

                        // Outer luminous aura
                        drawCircle(
                            color = YamahaCyan.copy(alpha = 0.25f),
                            radius = 16f,
                            center = Offset(threshPxX, threshPxY)
                        )
                        // Middle cyan circle
                        drawCircle(
                            color = YamahaCyan,
                            radius = 7f,
                            center = Offset(threshPxX, threshPxY)
                        )
                        // Center white core
                        drawCircle(
                            color = Color.White,
                            radius = 3.5f,
                            center = Offset(threshPxX, threshPxY)
                        )

                        // Dotted projection lines to axes
                        drawLine(
                            color = YamahaCyan.copy(alpha = 0.45f),
                            start = Offset(threshPxX, threshPxY),
                            end = Offset(threshPxX, paddingTop + graphH),
                            strokeWidth = 1f,
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = YamahaCyan.copy(alpha = 0.45f),
                            start = Offset(threshPxX, threshPxY),
                            end = Offset(paddingLeft, threshPxY),
                            strokeWidth = 1f,
                            pathEffect = dashEffect
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Compact Sliders: Ratio, Attack, Release
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Ratio Slider
                    CompInlineSliderRow(
                        title = stringResource(R.string.comp_ratio_title),
                        valueText = YamahaSysExEncoder.formatCompressorRatio(safeRatio),
                        value = safeRatio.toFloat(),
                        valueRange = 0f..(YamahaSysExEncoder.COMPRESSOR_RATIO_LABELS.size - 1).toFloat(),
                        steps = YamahaSysExEncoder.COMPRESSOR_RATIO_LABELS.size - 2,
                        onValueChange = { onRatioChange(it.roundToInt()) }
                    )

                    // Attack Slider
                    CompInlineSliderRow(
                        title = stringResource(R.string.comp_attack_title),
                        valueText = YamahaSysExEncoder.formatCompressorAttackMs(safeAttack),
                        value = safeAttack.toFloat(),
                        valueRange = 0f..(YamahaSysExEncoder.COMPRESSOR_ATTACK_VALUES.size - 1).toFloat(),
                        steps = YamahaSysExEncoder.COMPRESSOR_ATTACK_VALUES.size - 2,
                        onValueChange = { onAttackChange(it.roundToInt()) }
                    )

                    // Release Slider
                    CompInlineSliderRow(
                        title = stringResource(R.string.comp_release_title),
                        valueText = YamahaSysExEncoder.formatCompressorReleaseMs(safeRelease),
                        value = safeRelease.toFloat(),
                        valueRange = 0f..(YamahaSysExEncoder.COMPRESSOR_RELEASE_VALUES.size - 1).toFloat(),
                        steps = YamahaSysExEncoder.COMPRESSOR_RELEASE_VALUES.size - 2,
                        onValueChange = { onReleaseChange(it.roundToInt()) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Actions: Reset Comp & Close
                val isModified = safeThreshold != 72 || safeGain != 64 || safeRatio != 3 || safeAttack != 19 || safeRelease != 4
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onResetComp,
                        enabled = isModified,
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
                            if (isModified) YamahaCyan.copy(alpha = 0.6f) else SynthCardBorder
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_reset_comp),
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

@Composable
private fun CompParamBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (isHighlighted) YamahaCyan.copy(alpha = 0.12f) else SynthDarkBackground,
        border = BorderStroke(1.dp, if (isHighlighted) YamahaCyan.copy(alpha = 0.45f) else SynthCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = if (isHighlighted) YamahaCyan else SynthMutedGray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = value,
                color = SynthLightText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompInlineSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SynthDarkBackground.copy(alpha = 0.65f))
            .border(1.dp, SynthCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Line 1: Title & Value Box
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
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            // Right: Formatted Value Readout Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SynthDarkBackground)
                    .border(
                        width = 1.dp,
                        color = YamahaCyan.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = valueText,
                    color = YamahaCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Line 2: Fader Sandwich [-] | Slider | [+]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrement button [-]
            IconButton(
                onClick = {
                    val next = (value - 1f).coerceIn(minVal, maxVal)
                    if (next != value) onValueChange(next)
                },
                enabled = value > minVal,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.cd_decrease),
                    tint = if (value > minVal) SynthMutedGray else SynthMutedGray.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Slider
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = YamahaCyan,
                    activeTrackColor = YamahaCyan,
                    inactiveTrackColor = SynthCardBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .padding(horizontal = 4.dp)
            )

            // Increment button [+]
            IconButton(
                onClick = {
                    val next = (value + 1f).coerceIn(minVal, maxVal)
                    if (next != value) onValueChange(next)
                },
                enabled = value < maxVal,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_increase),
                    tint = if (value < maxVal) SynthMutedGray else SynthMutedGray.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

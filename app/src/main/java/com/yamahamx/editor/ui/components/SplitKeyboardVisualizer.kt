package com.yamahamx.editor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.ui.theme.*

private val PART_COLORS = listOf(
    YamahaCyan,
    SynthAmber,
    SynthGreen,
    Color(0xFFE040FB),
    Color(0xFFFF5252),
    Color(0xFF448AFF),
    Color(0xFFFFAB00),
    Color(0xFF69F0AE),
    Color(0xFFFF4081),
    Color(0xFF7C4DFF),
    Color(0xFF00E5FF),
    Color(0xFFEEFF41),
    Color(0xFFFF6E40),
    Color(0xFF18FFFF),
    Color(0xFFB388FF),
    Color(0xFFFFD740)
)

@Composable
fun SplitKeyboardVisualizer(
    parts: List<MultiPartState>,
    selectedPartIndex: Int,
    onPartSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val legendScrollState = rememberScrollState()
    var isExpanded by remember(isLandscape) { mutableStateOf(!isLandscape) }

    LaunchedEffect(isLandscape) {
        if (isLandscape) {
            isExpanded = false
        }
    }

    LaunchedEffect(selectedPartIndex) {
        // Smoothly auto-scroll when selectedPartIndex changes so active part is visible
        val targetPosition = ((selectedPartIndex - 2) * 110).coerceAtLeast(0)
        legendScrollState.animateScrollTo(targetPosition)
    }

    Card(
        shape = RoundedCornerShape(if (isLandscape) 6.dp else 8.dp),
        colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isLandscape && !isExpanded) {
                    Modifier.heightIn(max = 28.dp)
                } else if (isLandscape) {
                    Modifier.heightIn(max = 56.dp)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isLandscape) 8.dp else 10.dp,
                    vertical = if (isLandscape) 2.dp else 6.dp
                )
        ) {
            // Interactive Collapsible Header (starts strictly collapsed in landscape mode, max 24dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isLandscape) 24.dp else 32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = if (isLandscape) 1.dp else 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Piano,
                        contentDescription = null,
                        tint = if (isExpanded) YamahaCyan else SynthMutedGray,
                        modifier = Modifier.size(if (isLandscape) 12.dp else 15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.section_keyboard_overview),
                        color = if (isExpanded) SynthLightText else SynthMutedGray,
                        fontSize = if (isLandscape) 8.5.sp else 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isExpanded) {
                        Text(
                            text = "+",
                            color = YamahaCyan,
                            fontSize = if (isLandscape) 10.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 3.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Recolher" else "Expandir",
                        tint = YamahaCyan,
                        modifier = Modifier.size(if (isLandscape) 13.dp else 16.dp)
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isLandscape) Modifier.heightIn(max = 32.dp) else Modifier)
                ) {
                    Spacer(modifier = Modifier.height(if (isLandscape) 1.dp else 4.dp))

                    // Canvas rendering the keyboard zones for active/all parts (capped strictly to 24dp in landscape)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 24.dp else 46.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SynthDarkBackground)
                            .border(1.dp, SynthCardBorder, RoundedCornerShape(4.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height

                            // Draw octave guide lines (every 12 notes)
                            for (octaveNote in 0..127 step 12) {
                                val x = (octaveNote / 127f) * width
                                val isMiddleC = octaveNote == 60
                                drawLine(
                                    color = if (isMiddleC) YamahaCyan.copy(alpha = 0.6f) else Color.DarkGray.copy(alpha = 0.4f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, height),
                                    strokeWidth = if (isMiddleC) 2f else 1f
                                )
                            }

                            val anySoloActive = parts.any { it.isSolo }

                            // Render bars for each active part
                            val visibleParts = parts.filter { it.isAudible(anySoloActive) }
                            val rowHeight = if (visibleParts.isNotEmpty()) {
                                (height / visibleParts.size.coerceAtMost(16)).coerceIn(2.5f, 14f)
                            } else 10f

                            visibleParts.take(16).forEachIndexed { rowIndex, part ->
                                val startX = (part.noteLimitLow / 127f) * width
                                val endX = (part.noteLimitHigh / 127f) * width
                                val barWidth = (endX - startX).coerceAtLeast(4f)
                                val y = rowIndex * rowHeight + 1f

                                val color = PART_COLORS.getOrElse(part.partIndex) { YamahaCyan }
                                val isSelected = part.partIndex == selectedPartIndex

                                drawRoundRect(
                                    color = if (isSelected) color else color.copy(alpha = 0.65f),
                                    topLeft = Offset(startX, y),
                                    size = Size(barWidth, (rowHeight - 1f).coerceAtLeast(2f)),
                                    cornerRadius = CornerRadius(2f, 2f)
                                )
                            }
                        }
                    }

                    if (!isLandscape) {
                        Spacer(modifier = Modifier.height(5.dp))

                        // Legend indicators for all 16 parts with smooth horizontal scroll (Portrait only to stay <= 32dp in landscape)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(legendScrollState),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val anySoloActive = parts.any { it.isSolo }
                            parts.forEach { part ->
                                val color = PART_COLORS.getOrElse(part.partIndex) { YamahaCyan }
                                val isSelected = part.partIndex == selectedPartIndex
                                val isAudible = part.isAudible(anySoloActive)
                                val isSolo = part.isSolo

                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            when {
                                                isSelected -> color.copy(alpha = 0.25f)
                                                isSolo -> SynthAmber.copy(alpha = 0.20f)
                                                else -> SynthPanelSurface
                                            }
                                        )
                                        .border(
                                            width = if (isSelected || isSolo) 1.5.dp else 1.dp,
                                            color = when {
                                                isSelected -> color
                                                isSolo -> SynthAmber
                                                isAudible -> color.copy(alpha = 0.45f)
                                                else -> SynthCardBorder
                                            },
                                            shape = RoundedCornerShape(5.dp)
                                        )
                                        .clickable { onPartSelected(part.partIndex) }
                                        .padding(horizontal = 2.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.part_tab_format, part.partNumber),
                                        color = when {
                                            isSelected -> color
                                            isSolo -> SynthAmber
                                            isAudible -> SynthLightText
                                            else -> SynthMutedGray
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected || isSolo) FontWeight.Black else FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

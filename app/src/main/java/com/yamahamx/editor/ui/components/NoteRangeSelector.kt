package com.yamahamx.editor.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.KeyboardModel
import com.yamahamx.editor.data.model.NoteHelper
import com.yamahamx.editor.ui.theme.*

private const val PREFS_NAME = "yamaha_mx_prefs"
private const val PREFS_KEYBOARD_MODEL_KEY = "selected_keyboard_model"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteRangeSelector(
    lowNote: Int,
    highNote: Int,
    onRangeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    initialModel: KeyboardModel? = null,
    onModelChange: ((KeyboardModel) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var selectedModel by remember(initialModel) {
        if (initialModel != null) {
            mutableStateOf(initialModel)
        } else {
            val savedName = prefs.getString(PREFS_KEYBOARD_MODEL_KEY, KeyboardModel.MX61.name)
            val parsed = try {
                KeyboardModel.valueOf(savedName ?: KeyboardModel.MX61.name)
            } catch (_: Exception) {
                KeyboardModel.MX61
            }
            mutableStateOf(parsed)
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // Header: Range note names and fine-tuning step buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.section_note_limit),
                    color = SynthMutedGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Lower Note Limit (Display + Step Buttons)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SynthDarkBackground)
                                .border(1.dp, YamahaCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${NoteHelper.noteNumberToName(lowNote)} ($lowNote)",
                                color = YamahaCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NoteStepButton(
                                text = "-",
                                enabled = lowNote > 0,
                                onClick = {
                                    if (lowNote > 0) {
                                        onRangeChange(lowNote - 1, highNote)
                                    }
                                }
                            )
                            NoteStepButton(
                                text = "+",
                                enabled = lowNote < highNote,
                                onClick = {
                                    if (lowNote < highNote) {
                                        onRangeChange(lowNote + 1, highNote)
                                    }
                                }
                            )
                        }
                    }

                    // Arrow centered between Lower and Upper note blocks
                    Text(
                        text = "➔",
                        color = SynthMutedGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    // Upper Note Limit (Display + Step Buttons)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SynthDarkBackground)
                                .border(1.dp, YamahaCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${NoteHelper.noteNumberToName(highNote)} ($highNote)",
                                color = YamahaCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NoteStepButton(
                                text = "-",
                                enabled = highNote > lowNote,
                                onClick = {
                                    if (highNote > lowNote) {
                                        onRangeChange(lowNote, highNote - 1)
                                    }
                                }
                            )
                            NoteStepButton(
                                text = "+",
                                enabled = highNote < 127,
                                onClick = {
                                    if (highNote < 127) {
                                        onRangeChange(lowNote, highNote + 1)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RangeSlider for Note Limit Low & High
            RangeSlider(
                value = lowNote.toFloat()..highNote.toFloat(),
                onValueChange = { range ->
                    onRangeChange(range.start.toInt(), range.endInclusive.toInt())
                },
                valueRange = 0f..127f,
                steps = 126,
                colors = SliderDefaults.colors(
                    thumbColor = SynthLightText,
                    activeTrackColor = YamahaCyan,
                    inactiveTrackColor = SynthFaderTrack
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Linha 1: Seletor de Modelo de Teclado Físico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyboardModel.values().forEach { model ->
                    val isModelSelected = model == selectedModel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isModelSelected) YamahaCyan.copy(alpha = 0.22f)
                                else SynthDarkBackground.copy(alpha = 0.6f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isModelSelected) YamahaCyan else SynthCardBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                selectedModel = model
                                prefs.edit().putString(PREFS_KEYBOARD_MODEL_KEY, model.name).apply()
                                onModelChange?.invoke(model)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = model.displayName,
                            color = if (isModelSelected) YamahaCyan else SynthMutedGray,
                            fontSize = 11.sp,
                            fontWeight = if (isModelSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Linha 2: Ações Contextuais de Split (Full / Lower = 1ª metade / Upper = 2ª metade)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetSplitButton(
                    text = selectedModel.fullLabel,
                    isSelected = lowNote == selectedModel.fullLow && highNote == selectedModel.fullHigh,
                    onClick = { onRangeChange(selectedModel.fullLow, selectedModel.fullHigh) },
                    modifier = Modifier.weight(1f)
                )
                PresetSplitButton(
                    text = selectedModel.lowerLabel,
                    isSelected = lowNote == selectedModel.lowerLow && highNote == selectedModel.lowerHigh,
                    onClick = { onRangeChange(selectedModel.lowerLow, selectedModel.lowerHigh) },
                    modifier = Modifier.weight(1f)
                )
                PresetSplitButton(
                    text = selectedModel.upperLabel,
                    isSelected = lowNote == selectedModel.upperLow && highNote == selectedModel.upperHigh,
                    onClick = { onRangeChange(selectedModel.upperLow, selectedModel.upperHigh) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PresetSplitButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) YamahaCyanDim.copy(alpha = 0.3f) else Color.Transparent,
            contentColor = if (isSelected) YamahaCyan else SynthMutedGray
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) YamahaCyan else SynthCardBorder
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        modifier = modifier.height(30.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun NoteStepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 32.dp, height = 24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) SynthPanelSurface else SynthPanelSurface.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = if (enabled) YamahaCyan.copy(alpha = 0.4f) else SynthCardBorder.copy(alpha = 0.25f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) YamahaCyan else SynthMutedGray.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

package com.yamahamx.editor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yamahamx.editor.data.repository.MidiMappingConfig
import com.yamahamx.editor.data.repository.MidiTargetKey
import com.yamahamx.editor.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Interactive MIDI Learn capture dialog (PadLab-style).
 */
@Composable
fun MidiLearnDialog(
    target: MidiTargetKey,
    currentConfig: MidiMappingConfig?,
    onRestoreDefault: () -> Unit,
    onRemoveMapping: () -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "midi_learn_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val titleText = "Mapear ${target.parameter.displayName} - Parte ${target.partIndex + 1}"

    val currentMappingText = when {
        currentConfig == null ->
            "Padrão Yamaha (CC nativo ${target.parameter.defaultCc})"
        currentConfig.isDisabled ->
            "Mapeamento removido (Controle externo desativado)"
        else ->
            "Mapeado atualmente: CC ${currentConfig.ccNumber} (Canal ${currentConfig.channel + 1})"
    }

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SynthCardBackground),
            border = BorderStroke(1.5.dp, YamahaCyan.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = YamahaCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, YamahaCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "MIDI LEARN INTERATIVO",
                        color = YamahaCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title
                Text(
                    text = titleText,
                    color = SynthLightText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Highlighted Waiting Status Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SynthDarkBackground,
                    border = BorderStroke(1.dp, YamahaGold.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .alpha(pulseAlpha)
                                .background(YamahaGold, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Aguardando comando MIDI... Mexa o fader ou knob no seu controlador externo",
                            color = YamahaGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Current Configuration Info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = SynthDarkBackground.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, SynthCardBorder)
                ) {
                    Text(
                        text = currentMappingText,
                        color = if (currentConfig != null && !currentConfig.isDisabled) YamahaCyan else SynthMutedGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action 1: Restaurar Padrão Yamaha
                Button(
                    onClick = onRestoreDefault,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YamahaCyan.copy(alpha = 0.2f),
                        contentColor = YamahaCyan
                    ),
                    border = BorderStroke(1.dp, YamahaCyan.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "Restaurar Padrão Yamaha",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action 2: Remover Mapeamento
                OutlinedButton(
                    onClick = onRemoveMapping,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SynthRed
                    ),
                    border = BorderStroke(1.dp, SynthRed.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "Remover Mapeamento",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action 3: Cancelar
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancelar",
                        color = SynthMutedGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Non-consuming pointer detector that triggers [onLongPress] after holding stationary for [durationMs] (~750ms)
 * even when placed over interactive sliders/faders that normally consume pointer drag events.
 */
fun Modifier.sliderLongPressDetector(
    durationMs: Long = 750L,
    onLongPress: (() -> Unit)?
): Modifier {
    if (onLongPress == null) return this
    return this.pointerInput(onLongPress, durationMs) {
        coroutineScope {
            awaitPointerEventScope {
                val touchSlop = viewConfiguration.touchSlop
                while (true) {
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val firstDown = downEvent.changes.firstOrNull { it.pressed && !it.previousPressed }
                    if (firstDown != null) {
                        val pointerId = firstDown.id
                        val startPos = firstDown.position
                        var triggered = false
                        val timerJob = launch {
                            delay(durationMs)
                            triggered = true
                            onLongPress()
                        }

                        var tracking = true
                        while (tracking) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                timerJob.cancel()
                                tracking = false
                            } else {
                                val distance = (change.position - startPos).getDistance()
                                if (distance > touchSlop) {
                                    timerJob.cancel()
                                    tracking = false
                                } else if (triggered) {
                                    // Consume subsequent drag if long press already opened the modal
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.yamahamx.editor.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yamahamx.editor.R
import com.yamahamx.editor.data.midi.SaveToHardwareState
import com.yamahamx.editor.data.midi.YamahaMxSysExStorage
import com.yamahamx.editor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToKeyboardDialog(
    initialPerformanceName: String,
    isConnected: Boolean,
    saveState: SaveToHardwareState,
    onSyncEditBuffer: (name: String) -> Unit,
    onResetState: () -> Unit,
    onDismiss: () -> Unit
) {
    var rawName by remember { mutableStateOf(initialPerformanceName) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val screenHeight = configuration.screenHeightDp.dp

    val sanitizedName = remember(rawName) {
        YamahaMxSysExStorage.sanitizeMxPerformanceName(rawName)
    }

    Dialog(
        onDismissRequest = {
            if (saveState !is SaveToHardwareState.Progress) {
                onResetState()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SynthPanelSurface,
            border = BorderStroke(1.dp, SynthCardBorder),
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.88f else 0.95f)
                .heightIn(max = screenHeight * 0.92f)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(if (isLandscape) 14.dp else 20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(YamahaGold)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "MX",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = stringResource(R.string.dialog_save_keyboard_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isLandscape) 17.sp else 19.sp
                        )
                    }

                    Surface(
                        color = if (isConnected) SynthGreen.copy(alpha = 0.15f) else SynthRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isConnected) SynthGreen else SynthRed)
                    ) {
                        Text(
                            text = if (isConnected) stringResource(R.string.connected) else stringResource(R.string.disconnected),
                            color = if (isConnected) SynthGreen else SynthRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress View
                if (saveState is SaveToHardwareState.Progress) {
                    ProgressView(progress = saveState)
                } else if (saveState is SaveToHardwareState.Success) {
                    SuccessView(
                        success = saveState,
                        onDismiss = {
                            onResetState()
                            onDismiss()
                        }
                    )
                } else if (saveState is SaveToHardwareState.Error) {
                    ErrorView(
                        error = saveState,
                        onRetry = onResetState,
                        onDismiss = {
                            onResetState()
                            onDismiss()
                        }
                    )
                } else {
                    // Disconnected Warning
                    if (!isConnected) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SynthRed.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, SynthRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = SynthRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.save_keyboard_not_connected_alert),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // 1. MX LCD Name Input & Preview
                    Text(
                        text = stringResource(R.string.save_keyboard_name_label),
                        color = Color.LightGray,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = rawName,
                        onValueChange = { input ->
                            if (input.length <= 20) { // Allow typing, sanitization enforces 10 chars
                                rawName = input
                            }
                        },
                        placeholder = { Text("Nome da Performance", color = Color.Gray) },
                        singleLine = true,
                        trailingIcon = {
                            Text(
                                text = "${sanitizedName.trim().length}/${YamahaMxSysExStorage.MAX_NAME_LENGTH}",
                                color = if (sanitizedName.trim().length == YamahaMxSysExStorage.MAX_NAME_LENGTH) YamahaGold else SynthMutedGray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YamahaCyan,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // LCD Display Style Preview
                    LcdDisplayPreview(sanitizedName = sanitizedName)

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Hardware Instruction Steps
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D)),
                        border = BorderStroke(1.dp, YamahaCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.save_keyboard_instructions_title),
                                color = YamahaCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF090E17))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.save_keyboard_step_send),
                                    color = Color.White,
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = stringResource(R.string.save_keyboard_step_store),
                                    color = YamahaGold,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.save_keyboard_step_dial_enter),
                                    color = SynthGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Multi-Layer & Standalone Notice
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141926)),
                        border = BorderStroke(1.dp, SynthCardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = YamahaCyan,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Text(
                                text = stringResource(R.string.save_keyboard_parts_notice),
                                color = SynthMutedGray,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                        ) {
                            Text(stringResource(R.string.save_keyboard_btn_close), fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                onSyncEditBuffer(sanitizedName)
                            },
                            enabled = isConnected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YamahaCyan,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.DarkGray,
                                disabledContentColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.save_keyboard_btn_sync_buffer),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LcdDisplayPreview(sanitizedName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF071114))
            .border(1.dp, Color(0xFF13353A), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.save_keyboard_lcd_preview),
                color = Color(0xFF388B98),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "160x64 DOTS",
                color = Color(0xFF265F68),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[ ",
                color = Color(0xFF4EE2EC),
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sanitizedName,
                color = Color(0xFF4EE2EC),
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = " ]",
                color = Color(0xFF4EE2EC),
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProgressView(progress: SaveToHardwareState.Progress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(
            color = YamahaCyan,
            strokeWidth = 3.dp,
            modifier = Modifier.size(44.dp)
        )

        Text(
            text = "${progress.percentage}%",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp
        )

        LinearProgressIndicator(
            progress = { progress.percentage / 100f },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = YamahaCyan,
            trackColor = Color(0xFF1E2638)
        )

        Text(
            text = progress.currentStep,
            color = Color.LightGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SuccessView(
    success: SaveToHardwareState.Success,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SynthGreen,
            modifier = Modifier.size(52.dp)
        )

        Text(
            text = success.message,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A24)),
            border = BorderStroke(1.dp, YamahaCyan.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Passo Final no Teclado:",
                    color = YamahaCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "1. Pressione o botão físico [STORE] no painel do MX.\n2. Escolha o número User (001 a 128) no dial.\n3. Pressione [ENTER] para gravar permanentemente na memória.",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = SynthGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text(stringResource(R.string.save_keyboard_btn_close), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorView(
    error: SaveToHardwareState.Error,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = SynthRed,
            modifier = Modifier.size(52.dp)
        )

        Text(
            text = "Falha ao Enviar para o Teclado",
            color = SynthRed,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Text(
            text = error.message,
            color = Color.White,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
            ) {
                Text(stringResource(R.string.save_keyboard_btn_close))
            }

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = YamahaCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Tentar Novamente", fontWeight = FontWeight.Bold)
            }
        }
    }
}

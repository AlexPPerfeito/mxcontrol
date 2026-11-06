package com.yamahamx.editor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.ui.theme.YamahaCyan
import com.yamahamx.editor.ui.theme.YamahaGold
import com.yamahamx.editor.ui.theme.YamahaNavyDark
import com.yamahamx.editor.ui.theme.YamahaNavyLight

@Composable
fun SetlistImportExportDialog(
    exportJsonData: String,
    existingPerformancesCount: Int = 0,
    onDismiss: () -> Unit,
    onValidateJson: ((jsonString: String) -> Int?)? = null,
    onImportJson: (jsonString: String, replaceAll: Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var importInputText by remember { mutableStateOf("") }
    var showStrategyDialog by remember { mutableStateOf(false) }
    var pendingImportCount by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = YamahaNavyLight,
        title = {
            Text(
                text = stringResource(R.string.dialog_backup_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E2638),
                    contentColor = YamahaGold,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = YamahaGold
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text(stringResource(R.string.tab_export_json))
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text(stringResource(R.string.tab_import_json))
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Export Tab
                    Text(
                        text = stringResource(R.string.export_json_instructions),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(YamahaNavyDark, RoundedCornerShape(8.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = exportJsonData,
                            color = YamahaCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Yamaha MX Setlist", exportJsonData)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.toast_setlist_json_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = YamahaGold),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(YamahaGold))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.btn_copy_clipboard), fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Import Tab
                    Text(
                        text = stringResource(R.string.import_json_instructions),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        placeholder = { Text(stringResource(R.string.placeholder_paste_json), color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YamahaGold,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedContainerColor = YamahaNavyDark,
                            unfocusedContainerColor = YamahaNavyDark
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val trimmed = importInputText.trim()
                            if (trimmed.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.toast_import_empty_error), Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val validCount = onValidateJson?.invoke(trimmed)
                                ?: com.yamahamx.editor.data.repository.PerformanceStorageRepository.parsePerformancesJson(trimmed)?.size

                            if (validCount == null || validCount <= 0) {
                                Toast.makeText(context, context.getString(R.string.toast_import_invalid_error), Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            pendingImportCount = validCount

                            if (existingPerformancesCount == 0) {
                                // Cenário A — Banco Vazio (count == 0):
                                // Importa diretamente todas as performances do JSON.
                                onImportJson(trimmed, false)
                            } else {
                                // Cenário B — Banco com Dados Existentes (count > 0):
                                // Abre o diálogo de confirmação com opções Mesclar vs Substituir
                                showStrategyDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = YamahaGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.btn_import_performances), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close), color = Color.White)
            }
        }
    )

    if (showStrategyDialog) {
        AlertDialog(
            onDismissRequest = { showStrategyDialog = false },
            containerColor = YamahaNavyLight,
            title = {
                Text(
                    text = stringResource(R.string.dialog_strategy_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.dialog_strategy_message, existingPerformancesCount),
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Opção 1: Mesclar / Somar
                    Button(
                        onClick = {
                            showStrategyDialog = false
                            onImportJson(importInputText.trim(), false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = YamahaCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_strategy_merge),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Opção 2: Substituir Tudo
                    Button(
                        onClick = {
                            showStrategyDialog = false
                            onImportJson(importInputText.trim(), true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_strategy_replace_all),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Opção 3: Cancelar
                    OutlinedButton(
                        onClick = { showStrategyDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.Gray)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_cancel),
                            color = Color.White,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = null
        )
    }
}

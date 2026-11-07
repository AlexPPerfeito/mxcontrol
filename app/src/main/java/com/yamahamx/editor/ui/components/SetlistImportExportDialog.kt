package com.yamahamx.editor.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.yamahamx.editor.R
import com.yamahamx.editor.data.repository.PerformanceStorageRepository
import com.yamahamx.editor.ui.theme.YamahaCyan
import com.yamahamx.editor.ui.theme.YamahaGold
import com.yamahamx.editor.ui.theme.YamahaNavyDark
import com.yamahamx.editor.ui.theme.YamahaNavyLight
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun buildBackupFileName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    return "mxcontrol_backup_$timestamp.json"
}

private fun resolveUriFileName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment ?: "backup.json"
    } catch (_: Exception) {
        uri.lastPathSegment ?: "backup.json"
    }
}

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
    var importedJsonContent by remember { mutableStateOf("") }
    var loadedFileName by remember { mutableStateOf<String?>(null) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var showStrategyDialog by remember { mutableStateOf(false) }
    var pendingImportCount by remember { mutableIntStateOf(0) }

    val exportSizeKb = remember(exportJsonData) {
        val bytes = exportJsonData.toByteArray(Charsets.UTF_8).size
        String.format(Locale.US, "%.1f", bytes / 1024f)
    }

    // SAF Launcher for Exporting .json file
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportJsonData.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                } ?: throw IllegalStateException("OutputStream null")
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_export_file_success),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_export_file_error, e.localizedMessage ?: "I/O"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // SAF Launcher for Importing .json file
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fileName = resolveUriFileName(context, uri)
                val rawContent = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }

                if (rawContent.isNullOrBlank()) {
                    importErrorMessage = context.getString(R.string.toast_import_invalid_error)
                    loadedFileName = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_import_invalid_error),
                        Toast.LENGTH_LONG
                    ).show()
                    return@rememberLauncherForActivityResult
                }

                val trimmed = rawContent.trim().removePrefix("\uFEFF").trim()
                val validCount = onValidateJson?.invoke(trimmed)
                    ?: PerformanceStorageRepository.parsePerformancesJson(trimmed)?.size

                if (validCount == null || validCount <= 0) {
                    importErrorMessage = context.getString(R.string.toast_import_invalid_error)
                    loadedFileName = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_import_invalid_error),
                        Toast.LENGTH_LONG
                    ).show()
                    return@rememberLauncherForActivityResult
                }

                importErrorMessage = null
                importedJsonContent = trimmed
                loadedFileName = fileName
                pendingImportCount = validCount

                if (existingPerformancesCount == 0) {
                    onImportJson(trimmed, false)
                } else {
                    showStrategyDialog = true
                }
            } catch (e: Exception) {
                importErrorMessage = context.getString(R.string.toast_import_file_read_error)
                loadedFileName = null
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_import_file_read_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

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
                        text = stringResource(R.string.export_file_instructions),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Summary Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(YamahaNavyDark, RoundedCornerShape(8.dp))
                            .border(1.dp, YamahaCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = YamahaCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(
                                    R.string.export_summary_format,
                                    existingPerformancesCount,
                                    exportSizeKb
                                ),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "mxcontrol_backup_YYYYMMDD_HHmm.json",
                                color = YamahaCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Button: Export File (.json) via SAF CreateDocument
                    Button(
                        onClick = {
                            exportFileLauncher.launch(buildBackupFileName())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = YamahaCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = stringResource(R.string.btn_export_json_file),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Button: Share File (.json) via FileProvider + ACTION_SEND
                    OutlinedButton(
                        onClick = {
                            try {
                                val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
                                val backupFile = File(backupsDir, buildBackupFileName())
                                backupFile.writeText(exportJsonData, Charsets.UTF_8)

                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    backupFile
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    clipData = ClipData.newRawUri("MX Control Backup", contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                val chooser = Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.share_backup_chooser_title)
                                )
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_export_file_error, e.localizedMessage ?: "Share"),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = YamahaGold),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(YamahaGold)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = stringResource(R.string.btn_share_json_file),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Import Tab
                    Text(
                        text = stringResource(R.string.import_file_instructions),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Button: Select & Import .json file via SAF OpenDocument
                    Button(
                        onClick = {
                            importFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = YamahaGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = stringResource(R.string.btn_import_json_file),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (loadedFileName != null && pendingImportCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(YamahaNavyDark, RoundedCornerShape(8.dp))
                                .border(1.dp, YamahaCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = YamahaCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(
                                    R.string.import_file_loaded_status,
                                    loadedFileName ?: "backup.json",
                                    pendingImportCount
                                ),
                                color = YamahaCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (importErrorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2D1215), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = importErrorMessage ?: "",
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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
                            onImportJson(importedJsonContent, false)
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
                            onImportJson(importedJsonContent, true)
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
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.Gray)),
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

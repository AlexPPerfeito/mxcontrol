package com.yamahamx.editor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.repository.PerformanceStorageRepository
import com.yamahamx.editor.ui.theme.YamahaGold
import com.yamahamx.editor.ui.theme.YamahaNavyLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavePerformanceDialog(
    initialName: String = "",
    initialTag: String = "Geral",
    isEditing: Boolean = false,
    loadedPerformanceName: String? = null,
    availableTags: List<String> = PerformanceStorageRepository.DEFAULT_CATEGORIES,
    onDismiss: () -> Unit,
    onSave: (name: String, tag: String, saveAsNew: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedTag by remember { mutableStateOf(initialTag.ifBlank { availableTags.firstOrNull() ?: "Geral" }) }

    val tags = remember(availableTags) {
        availableTags.filter { it != "TODAS" }
    }

    val isExistingLoaded = loadedPerformanceName != null && !isEditing

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = YamahaNavyLight,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = if (isExistingLoaded) com.yamahamx.editor.ui.theme.YamahaCyan else YamahaGold
                )
                Text(
                    text = when {
                        isEditing -> stringResource(R.string.dialog_title_edit_performance)
                        isExistingLoaded -> stringResource(R.string.dialog_title_save_current_performance)
                        else -> stringResource(R.string.dialog_title_save_performance)
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.label_performance_name),
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.placeholder_performance_name), color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isExistingLoaded) com.yamahamx.editor.ui.theme.YamahaCyan else YamahaGold,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (isExistingLoaded) {
                    Text(
                        text = stringResource(R.string.dialog_loaded_performance_hint, loadedPerformanceName ?: ""),
                        color = com.yamahamx.editor.ui.theme.YamahaCyan,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.label_category_tag),
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        val isSelected = tag.equals(selectedTag, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTag = tag },
                            label = { Text(tag, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YamahaGold,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E2638),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isExistingLoaded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val defaultPerfName = stringResource(R.string.default_performance_my)
                    val copyName = stringResource(R.string.copy_suffix, loadedPerformanceName ?: defaultPerfName)
                    OutlinedButton(
                        onClick = {
                            val finalName = if (name.isBlank()) copyName else name.trim()
                            onSave(finalName, selectedTag, true)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = YamahaGold),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(YamahaGold)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.btn_save_as_new), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val finalName = if (name.isBlank()) (loadedPerformanceName ?: defaultPerfName) else name.trim()
                            onSave(finalName, selectedTag, false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.yamahamx.editor.ui.theme.YamahaCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.btn_update), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val fallbackGeneratedName = stringResource(R.string.default_performance_name, (System.currentTimeMillis() % 1000).toInt())
                Button(
                    onClick = {
                        val finalName = if (name.isBlank()) {
                            if (isEditing) initialName else fallbackGeneratedName
                        } else name.trim()
                        onSave(finalName, selectedTag, false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YamahaGold),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isEditing) stringResource(R.string.btn_update) else stringResource(R.string.btn_save),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color.Gray)
            }
        }
    )
}

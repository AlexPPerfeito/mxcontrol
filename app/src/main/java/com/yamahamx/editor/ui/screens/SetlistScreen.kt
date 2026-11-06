package com.yamahamx.editor.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.SavedPerformance
import com.yamahamx.editor.data.repository.PerformanceStorageRepository
import com.yamahamx.editor.ui.components.RenameCategoryDialog
import com.yamahamx.editor.ui.components.SavePerformanceDialog
import com.yamahamx.editor.ui.components.SetlistImportExportDialog
import com.yamahamx.editor.ui.theme.YamahaCyan
import com.yamahamx.editor.ui.theme.YamahaGold
import com.yamahamx.editor.ui.theme.YamahaGreen
import com.yamahamx.editor.ui.theme.YamahaNavyDark
import com.yamahamx.editor.ui.theme.YamahaNavyLight
import com.yamahamx.editor.ui.viewmodel.PerformanceViewModel

@Composable
fun SetlistScreen(
    viewModel: PerformanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val savedList by viewModel.savedPerformances.collectAsState()
    val currentLoadedId by viewModel.currentLoadedPerformanceId.collectAsState()
    val isProUser by viewModel.isProUser.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("TODAS") }
    val allTags = remember(categories) { listOf("TODAS") + categories }
    var categoryToRename by remember { mutableStateOf<String?>(null) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var editingPerformance by remember { mutableStateOf<SavedPerformance?>(null) }
    var showImportExportDialog by remember { mutableStateOf(false) }

    val filteredList = remember(savedList, searchQuery, selectedTag) {
        savedList.filter { item ->
            val matchesTag = selectedTag == "TODAS" || item.categoryTag.equals(selectedTag, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.categoryTag.contains(searchQuery, ignoreCase = true) ||
                    item.parts.any { it.voice.name.contains(searchQuery, ignoreCase = true) }
            matchesTag && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YamahaNavyDark)
            .padding(horizontal = if (isLandscape) 8.dp else 12.dp, vertical = if (isLandscape) 4.dp else 8.dp)
    ) {
        // --- Top Controls: Save & Import/Export ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLandscape) 4.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (!isProUser && savedList.size >= 2 && currentLoadedId == null) {
                        viewModel.openUpgradeDialog()
                    } else {
                        showSaveDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = YamahaGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 32.dp else 40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = if (isLandscape) 2.dp else 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(if (isLandscape) 15.dp else 18.dp).padding(end = 4.dp)
                )
                Text(
                    text = stringResource(R.string.action_save_current),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isLandscape) 11.5.sp else 13.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            OutlinedButton(
                onClick = { showImportExportDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = YamahaCyan),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(YamahaCyan)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(if (isLandscape) 32.dp else 40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = if (isLandscape) 2.dp else 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ImportExport,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 15.dp else 18.dp).padding(end = 4.dp)
                )
                Text(
                    text = stringResource(R.string.action_backup_json),
                    fontSize = if (isLandscape) 11.5.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // --- Scrollable Setlist Area (Search, Categories, Count & Performance Cards) ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp),
            contentPadding = PaddingValues(bottom = if (isLandscape) 16.dp else 24.dp)
        ) {
            item(key = "setlist_filters_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // --- Search Bar ---
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_setlist_placeholder), color = Color.Gray, fontSize = if (isLandscape) 11.sp else 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(if (isLandscape) 28.dp else 36.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search), tint = Color.Gray, modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YamahaGold,
                            unfocusedBorderColor = Color(0xFF2A344A),
                            focusedContainerColor = YamahaNavyLight,
                            unfocusedContainerColor = YamahaNavyLight,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

                    // --- Category Tag Filter Chips ---
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allTags) { tag ->
                            val isSelected = tag == selectedTag
                            val isAllFilter = tag == "TODAS"
                            CategoryFilterChip(
                                tag = tag,
                                isSelected = isSelected,
                                isAllFilter = isAllFilter,
                                onClick = { selectedTag = tag },
                                onLongClick = {
                                    categoryToRename = tag
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 2.dp else 8.dp))

                    // --- Presets Count Info ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isLandscape) 1.dp else 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.performances_count_format, filteredList.size),
                            color = Color.Gray,
                            fontSize = if (isLandscape) 10.sp else 12.sp
                        )
                    }
                }
            }

            if (filteredList.isEmpty()) {
                item(key = "setlist_empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isLandscape) 16.dp else 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedTag != "TODAS")
                                stringResource(R.string.empty_setlist_filtered)
                            else
                                stringResource(R.string.empty_setlist_placeholder),
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { performance ->
                    PerformanceCardItem(
                        performance = performance,
                        isLoaded = performance.id == currentLoadedId,
                        onLoad = {
                            if (performance.id == currentLoadedId) {
                                viewModel.recarregarPerformance(performance.id)
                                Toast.makeText(context, context.getString(R.string.toast_reloading_performance, performance.name), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.loadPerformance(performance)
                                Toast.makeText(context, context.getString(R.string.toast_loading_performance, performance.name), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onEdit = { editingPerformance = performance },
                        onDuplicate = {
                            viewModel.duplicatePerformance(performance)
                            Toast.makeText(context, context.getString(R.string.toast_performance_duplicated), Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            viewModel.deletePerformance(performance.id)
                            Toast.makeText(context, context.getString(R.string.toast_performance_deleted), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // --- Rename Category Dialog ---
    categoryToRename?.let { oldCategory ->
        RenameCategoryDialog(
            currentCategory = oldCategory,
            existingCategories = categories,
            onDismiss = { categoryToRename = null },
            onConfirm = { newName ->
                val success = viewModel.renameCategory(oldCategory, newName)
                if (success) {
                    if (selectedTag.equals(oldCategory, ignoreCase = true)) {
                        selectedTag = newName
                    }
                    Toast.makeText(context, context.getString(R.string.toast_category_renamed, newName), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_category_rename_failed), Toast.LENGTH_SHORT).show()
                }
                categoryToRename = null
            }
        )
    }

    // --- Save Current Dialog ---
    if (showSaveDialog) {
        val loadedPerf = savedList.firstOrNull { it.id == currentLoadedId }
        SavePerformanceDialog(
            initialName = loadedPerf?.name ?: "",
            initialTag = loadedPerf?.categoryTag ?: if (selectedTag != "TODAS") selectedTag else (categories.firstOrNull() ?: stringResource(R.string.default_category_fallback)),
            isEditing = false,
            loadedPerformanceName = loadedPerf?.name,
            availableTags = categories,
            onDismiss = { showSaveDialog = false },
            onSave = { name, tag, saveAsNew ->
                viewModel.saveCurrentPerformance(name, tag, saveAsNew = saveAsNew)
                showSaveDialog = false
                if (saveAsNew) {
                    Toast.makeText(context, context.getString(R.string.toast_performance_saved_new, name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_performance_updated, name), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- Edit Dialog ---
    editingPerformance?.let { perf ->
        SavePerformanceDialog(
            initialName = perf.name,
            initialTag = perf.categoryTag,
            isEditing = true,
            availableTags = categories,
            onDismiss = { editingPerformance = null },
            onSave = { newName, newTag, _ ->
                viewModel.renamePerformance(perf.id, newName, newTag)
                editingPerformance = null
                Toast.makeText(context, context.getString(R.string.toast_performance_updated_simple), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- Import / Export Dialog ---
    if (showImportExportDialog) {
        val exportJson = remember { viewModel.exportSetlistJson() }
        SetlistImportExportDialog(
            exportJsonData = exportJson,
            existingPerformancesCount = savedList.size,
            onDismiss = { showImportExportDialog = false },
            onValidateJson = { jsonStr ->
                viewModel.validateSetlistJson(jsonStr)?.size
            },
            onImportJson = { jsonStr, replaceAll ->
                val prevCount = savedList.size
                val importedCount = viewModel.importSetlistJson(jsonStr, replaceAll)
                showImportExportDialog = false
                if (importedCount > 0) {
                    val total = viewModel.savedPerformances.value.size
                    if (replaceAll) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_import_replaced_success, importedCount),
                            Toast.LENGTH_LONG
                        ).show()
                    } else if (prevCount == 0) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_import_restored_success, importedCount),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_import_merged_success, importedCount, total),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_import_failed_general), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun PerformanceCardItem(
    performance: SavedPerformance,
    isLoaded: Boolean,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLoaded) Modifier.border(1.5.dp, YamahaGreen, RoundedCornerShape(12.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = YamahaNavyLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category Tag Badge
                    Surface(
                        color = Color(0xFF223048),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = performance.categoryTag.uppercase(),
                            color = YamahaGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = performance.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status Badge & Menu
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoaded) {
                        Surface(
                            color = YamahaGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(YamahaGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.status_active_on_mx),
                                    color = YamahaGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_options), tint = Color.LightGray)
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(YamahaNavyDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_rename_tag), color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = YamahaGold) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_duplicate), color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = YamahaCyan) },
                                onClick = {
                                    menuExpanded = false
                                    onDuplicate()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_delete), color = Color(0xFFFF5252)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Summary of Primary Voices
            Text(
                text = performance.primaryVoicesSummary,
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Footer info & Load Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = performance.formattedDate,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    if (performance.part12Link) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = YamahaCyan, modifier = Modifier.size(12.dp))
                            Text(stringResource(R.string.label_link_1_2), color = YamahaCyan, fontSize = 10.sp)
                        }
                    }
                }

                Button(
                    onClick = onLoad,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLoaded) YamahaGreen else YamahaGold
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = if (isLoaded) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = if (isLoaded) stringResource(R.string.btn_reload) else stringResource(R.string.btn_load),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryFilterChip(
    tag: String,
    isSelected: Boolean,
    isAllFilter: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(8.dp)
    val backgroundColor = if (isSelected) YamahaGold else Color(0xFF1B2333)
    val textColor = if (isSelected) Color.Black else Color.LightGray
    val borderColor = if (isSelected) YamahaGold else Color(0xFF2A344A)

    Surface(
        shape = shape,
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (!isAllFilter) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                } else null
            )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tag,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

package com.yamahamx.editor.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.YamahaVoice
import com.yamahamx.editor.data.repository.VoiceRepository
import com.yamahamx.editor.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelectorDialog(
    currentVoice: YamahaVoice?,
    partNumber: Int,
    userVoices: List<YamahaVoice> = emptyList(),
    onVoicePreview: (YamahaVoice) -> Unit = {},
    onVoiceConfirmed: (YamahaVoice) -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    onVoiceSelected: ((YamahaVoice) -> Unit)? = null,
    onRenameUserVoice: (slotIndex: Int, newName: String) -> Unit = { _, _ -> },
    onDismissRequest: () -> Unit
) {
    val initialVoice = remember { currentVoice }
    var previewVoice by remember { mutableStateOf(currentVoice) }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    val initialCategory = remember(currentVoice) {
        when {
            currentVoice == null -> "ALL"
            currentVoice.category.equals("USR", ignoreCase = true) || (currentVoice.msb == 63 && currentVoice.lsb == 8) -> "USER"
            VoiceRepository.CATEGORIES.any { it.equals(currentVoice.category, ignoreCase = true) } -> {
                VoiceRepository.CATEGORIES.first { it.equals(currentVoice.category, ignoreCase = true) }
            }
            else -> "ALL"
        }
    }
    var selectedCategory by remember(currentVoice) { mutableStateOf(initialCategory) }

    var voiceToRename by remember { mutableStateOf<YamahaVoice?>(null) }

    val filteredVoices = remember(searchQuery, selectedCategory, userVoices) {
        VoiceRepository.searchVoices(searchQuery, selectedCategory, userVoices)
    }

    val lazyListState = rememberLazyListState()
    val categoryListState = rememberLazyListState()

    // Find index of previewVoice in the filtered list
    val currentFilteredIndex = remember(filteredVoices, previewVoice) {
        if (previewVoice != null) {
            filteredVoices.indexOfFirst {
                it.msb == previewVoice?.msb && it.lsb == previewVoice?.lsb && it.programChange == previewVoice?.programChange
            }
        } else -1
    }

    // Auto-scroll LazyColumn to the current voice on open
    LaunchedEffect(currentFilteredIndex, searchQuery) {
        if (searchQuery.isEmpty() && currentFilteredIndex >= 0) {
            lazyListState.scrollToItem(currentFilteredIndex)
        }
    }

    // Auto-scroll category row to make selected category chip visible
    LaunchedEffect(selectedCategory) {
        val catIdx = VoiceRepository.CATEGORIES.indexOfFirst { it.equals(selectedCategory, ignoreCase = true) }
        if (catIdx >= 0) {
            categoryListState.animateScrollToItem((catIdx - 1).coerceAtLeast(0))
        }
    }

    val canDecrement = currentFilteredIndex > 0
    val canIncrement = (currentFilteredIndex >= 0 && currentFilteredIndex < filteredVoices.lastIndex) ||
            (currentFilteredIndex == -1 && filteredVoices.isNotEmpty())

    fun selectVoiceAtIndex(newIndex: Int) {
        if (newIndex in filteredVoices.indices) {
            val selectedVoice = filteredVoices[newIndex]
            previewVoice = selectedVoice
            onVoicePreview(selectedVoice)
            coroutineScope.launch {
                lazyListState.animateScrollToItem(newIndex)
            }
        }
    }

    val handleDismiss = {
        if (previewVoice != initialVoice) {
            onVoiceCancel()
        }
        onDismissRequest()
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.96f else 0.95f)
                .fillMaxHeight(if (isLandscape) 0.95f else 0.90f)
                .clip(RoundedCornerShape(16.dp)),
            color = SynthDarkBackground,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SynthCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isLandscape) 8.dp else 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.voice_dialog_title),
                            color = YamahaCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = stringResource(R.string.voice_dialog_subtitle_format, partNumber, filteredVoices.size),
                            color = SynthMutedGray,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = handleDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close_revert),
                            tint = SynthLightText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(stringResource(R.string.search_voices_placeholder), color = SynthMutedGray, fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search), tint = YamahaCyan)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear), tint = SynthMutedGray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SynthLightText,
                        unfocusedTextColor = SynthLightText,
                        focusedBorderColor = YamahaCyan,
                        unfocusedBorderColor = SynthCardBorder,
                        focusedContainerColor = SynthCardBackground,
                        unfocusedContainerColor = SynthCardBackground
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Chips Scrollable Row
                LazyRow(
                    state = categoryListState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(VoiceRepository.CATEGORIES) { cat ->
                        val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                        val isUserChip = cat.equals("USER", ignoreCase = true) || cat.equals("USR", ignoreCase = true)

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = if (isUserChip) stringResource(R.string.category_user_count) else cat,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YamahaCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = SynthCardBackground,
                                labelColor = SynthMutedGray
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) YamahaCyan else SynthCardBorder
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = SynthCardBorder, thickness = 1.dp)

                // Voices List
                if (filteredVoices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.empty_voices_search),
                            color = SynthMutedGray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredVoices, key = { "${it.msb}_${it.lsb}_${it.programChange}_${it.id}" }) { voice ->
                            val isSelected = (previewVoice != null && voice.msb == previewVoice?.msb && voice.lsb == previewVoice?.lsb && voice.programChange == previewVoice?.programChange)
                            val isUserVoice = (voice.msb == 63 && voice.lsb == 8)

                            VoiceListItem(
                                voice = voice,
                                isSelected = isSelected,
                                isUserVoice = isUserVoice,
                                onClick = {
                                    previewVoice = voice
                                    onVoicePreview(voice)
                                },
                                onEditClick = if (isUserVoice) {
                                    { voiceToRename = voice }
                                } else null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                HorizontalDivider(color = SynthCardBorder, thickness = 1.dp)

                Spacer(modifier = Modifier.height(6.dp))

                // Fixed Navigation & Audition Footer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SynthPanelSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SynthCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = if (isLandscape) 4.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick Previous / Next Navigation Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { selectVoiceAtIndex(currentFilteredIndex - 1) },
                                enabled = canDecrement,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFF1E2638),
                                    contentColor = YamahaCyan,
                                    disabledContainerColor = Color(0xFF151922),
                                    disabledContentColor = Color.DarkGray
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .size(if (isLandscape) 38.dp else 42.dp)
                                    .border(
                                        1.dp,
                                        if (canDecrement) YamahaCyan.copy(alpha = 0.6f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = stringResource(R.string.cd_prev_voice),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    val nextIdx = if (currentFilteredIndex == -1) 0 else currentFilteredIndex + 1
                                    selectVoiceAtIndex(nextIdx)
                                },
                                enabled = canIncrement,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFF1E2638),
                                    contentColor = YamahaCyan,
                                    disabledContainerColor = Color(0xFF151922),
                                    disabledContentColor = Color.DarkGray
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .size(if (isLandscape) 38.dp else 42.dp)
                                    .border(
                                        1.dp,
                                        if (canIncrement) YamahaCyan.copy(alpha = 0.6f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.cd_next_voice),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Center: Preview / Audition Voice Indicator
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(YamahaCyan)
                                )
                                Text(
                                    text = stringResource(R.string.label_audition),
                                    color = YamahaCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp
                                )
                            }
                            Text(
                                text = previewVoice?.name ?: stringResource(R.string.no_voice_selected),
                                color = Color.White,
                                fontSize = if (isLandscape) 13.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (previewVoice != null) {
                                Text(
                                    text = "${previewVoice?.bankName} • PC:${previewVoice?.displayProgramNumber} (${previewVoice?.categoryDisplayName})",
                                    color = SynthMutedGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Primary Action Button: Confirm / OK
                        Button(
                            onClick = {
                                val finalVoice = previewVoice ?: initialVoice
                                if (finalVoice != null) {
                                    onVoiceConfirmed(finalVoice)
                                    onVoiceSelected?.invoke(finalVoice)
                                }
                                onDismissRequest()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = YamahaCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = if (isLandscape) 6.dp else 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.btn_ok),
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename User Voice Dialog
    voiceToRename?.let { voice ->
        var renameInput by remember(voice) { mutableStateOf(voice.name) }

        AlertDialog(
            onDismissRequest = { voiceToRename = null },
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.dialog_rename_user_voice_title),
                        color = YamahaCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.dialog_rename_user_voice_slot_format, voice.subCategory, voice.displayProgramNumber),
                        color = SynthMutedGray,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.dialog_rename_user_voice_prompt),
                        color = SynthLightText,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.placeholder_voice_name), color = SynthMutedGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SynthLightText,
                            unfocusedTextColor = SynthLightText,
                            focusedBorderColor = YamahaCyan,
                            unfocusedBorderColor = SynthCardBorder,
                            focusedContainerColor = SynthDarkBackground,
                            unfocusedContainerColor = SynthDarkBackground
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotBlank()) {
                            onRenameUserVoice(voice.programChange, trimmed)
                            voiceToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YamahaCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.btn_save), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { voiceToRename = null }) {
                    Text(stringResource(R.string.btn_cancel), color = SynthMutedGray)
                }
            },
            containerColor = SynthPanelSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceListItem(
    voice: YamahaVoice,
    isSelected: Boolean,
    isUserVoice: Boolean = false,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)? = null
) {
    Surface(
        color = when {
            isSelected -> YamahaCyanDim.copy(alpha = 0.35f)
            else -> SynthCardBackground
        },
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            when {
                isSelected -> YamahaCyan
                else -> SynthCardBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onEditClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Category Badge & Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                isSelected -> YamahaCyan
                                else -> SynthPanelSurface
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isSelected -> YamahaCyan
                                else -> SynthCardBorder
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isUserVoice) "USR" else voice.category,
                        color = when {
                            isSelected -> Color.Black
                            else -> YamahaCyan
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = voice.name,
                        color = when {
                            isSelected -> YamahaCyan
                            else -> SynthLightText
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isUserVoice)
                            stringResource(R.string.voice_user_slot_format, voice.subCategory, voice.programChange)
                        else
                            "${voice.categoryDisplayName} • ${voice.bankName}: ${voice.displayProgramNumber.toString().padStart(3, '0')}",
                        color = SynthMutedGray,
                        fontSize = 11.sp
                    )
                }
            }

            // Right: Edit pencil button (for USER voices), MSB/LSB info & Checkmark
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onEditClick != null) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.cd_rename_voice),
                            tint = YamahaCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = "PC:${voice.programChange}",
                    color = SynthMutedGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.cd_selected),
                        tint = YamahaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

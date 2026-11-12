package com.yamahamx.editor.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.MidiDeviceInfoWrapper
import com.yamahamx.editor.data.model.PerformanceState
import com.yamahamx.editor.data.model.YamahaVoice
import com.yamahamx.editor.ui.components.ConnectionHeader
import com.yamahamx.editor.ui.components.MidiLearnDialog
import com.yamahamx.editor.ui.components.SavePerformanceDialog
import com.yamahamx.editor.ui.components.SplitKeyboardVisualizer
import com.yamahamx.editor.ui.theme.*
import com.yamahamx.editor.ui.viewmodel.PerformanceViewModel

enum class ViewMode {
    DETAIL,
    MIXER,
    SETLIST,
    MIDI_CC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    state: PerformanceState,
    viewModel: PerformanceViewModel,
    onSelectPart: (Int) -> Unit,
    onPart12LinkToggle: (Boolean) -> Unit,
    onDeviceSelected: (MidiDeviceInfoWrapper) -> Unit,
    onScanRequested: () -> Unit,
    onPanicClicked: () -> Unit,
    onResetPerformance: () -> Unit,
    onVoiceChange: (Int, YamahaVoice) -> Unit,
    onVolumeChange: (Int, Int) -> Unit,
    onPanChange: (Int, Int) -> Unit,
    onReverbSendChange: (Int, Int) -> Unit,
    onChorusSendChange: (Int, Int) -> Unit,
    onNoteShiftChange: (Int, Int) -> Unit,
    onNoteRangeChange: (Int, Int, Int) -> Unit,
    onCutoffChange: (Int, Int) -> Unit,
    onResonanceChange: (Int, Int) -> Unit,
    onAttackChange: (Int, Int) -> Unit = { idx, att -> viewModel.setPartAttack(idx, att) },
    onDecayChange: (Int, Int) -> Unit = { idx, dec -> viewModel.setPartDecay(idx, dec) },
    onReleaseChange: (Int, Int) -> Unit = { idx, rel -> viewModel.setPartRelease(idx, rel) },
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onModulationChange: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val partActivityStates by viewModel.partActivityStates.collectAsState()
    val isProUser by viewModel.isProUser.collectAsState()
    val activePolyphony by viewModel.activePolyphony.collectAsState()
    val userVoiceEntities by viewModel.userVoices.collectAsState()
    val userVoices = remember(userVoiceEntities) { userVoiceEntities.map { it.toYamahaVoice() } }
    val midiMappings by viewModel.midiMappings.collectAsState()
    val isMidiLearnActive by viewModel.isMidiLearnActive.collectAsState()
    val midiLearnTarget by viewModel.midiLearnTarget.collectAsState()
    val midiLearnFeedback by viewModel.midiLearnFeedback.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()
    var currentViewMode by remember { mutableStateOf(ViewMode.DETAIL) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(midiLearnFeedback) {
        midiLearnFeedback?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMidiLearnFeedback()
        }
    }

    Scaffold(
        topBar = {
            ConnectionHeader(
                isConnected = state.isConnected,
                connectedDeviceName = state.connectedDeviceName,
                availableDevices = state.availableDevices,
                statusMessage = state.statusMessage,
                part12Link = state.common.part12Link,
                activePolyphony = activePolyphony,
                currentViewMode = currentViewMode,
                onViewModeChange = { currentViewMode = it },
                onPart12LinkToggle = onPart12LinkToggle,
                onDeviceSelected = onDeviceSelected,
                onScanRequested = onScanRequested,
                onPanicClicked = onPanicClicked,
                onSyncRequested = { viewModel.syncAllPartsToHardware() },
                onSaveRequested = {
                    if (!isProUser && viewModel.savedPerformances.value.size >= 2 && viewModel.currentLoadedPerformanceId.value == null) {
                        viewModel.openUpgradeDialog()
                    } else {
                        showSaveDialog = true
                    }
                }
            )
        },
        containerColor = SynthDarkBackground,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // View Mode Selector (Detail vs Mixer vs Setlist vs MIDI / CC) & Actions (ONLY in Portrait)
            if (!isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Container for Scrollable Tabs and Floating Navigation Indicators
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SynthCardBackground),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Scrollable Tabs Row with lateral padding of 36.dp so text is never covered by << or >>
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(tabScrollState)
                                .padding(horizontal = 36.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = currentViewMode == ViewMode.DETAIL,
                                onClick = { currentViewMode = ViewMode.DETAIL },
                                label = {
                                    Text(
                                        stringResource(R.string.nav_tab_part),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YamahaCyan,
                                    selectedLabelColor = Color.Black,
                                    selectedLeadingIconColor = Color.Black,
                                    containerColor = Color.Transparent,
                                    labelColor = SynthMutedGray,
                                    iconColor = SynthMutedGray
                                ),
                                border = null
                            )

                            FilterChip(
                                selected = currentViewMode == ViewMode.MIXER,
                                onClick = { currentViewMode = ViewMode.MIXER },
                                label = {
                                    Text(
                                        stringResource(R.string.nav_tab_mixer),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YamahaCyan,
                                    selectedLabelColor = Color.Black,
                                    selectedLeadingIconColor = Color.Black,
                                    containerColor = Color.Transparent,
                                    labelColor = SynthMutedGray,
                                    iconColor = SynthMutedGray
                                ),
                                border = null
                            )

                            FilterChip(
                                selected = currentViewMode == ViewMode.SETLIST,
                                onClick = { currentViewMode = ViewMode.SETLIST },
                                label = {
                                    Text(
                                        stringResource(R.string.nav_tab_setlist),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YamahaGold,
                                    selectedLabelColor = Color.Black,
                                    selectedLeadingIconColor = Color.Black,
                                    containerColor = Color.Transparent,
                                    labelColor = SynthMutedGray,
                                    iconColor = SynthMutedGray
                                ),
                                border = null
                            )

                            FilterChip(
                                selected = currentViewMode == ViewMode.MIDI_CC,
                                onClick = { currentViewMode = ViewMode.MIDI_CC },
                                label = {
                                    Text(
                                        stringResource(R.string.nav_tab_midi_cc),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YamahaCyan,
                                    selectedLabelColor = Color.Black,
                                    selectedLeadingIconColor = Color.Black,
                                    containerColor = Color.Transparent,
                                    labelColor = SynthMutedGray,
                                    iconColor = SynthMutedGray
                                ),
                                border = null
                            )
                        }

                        // Dynamic Left Scroll Indicator "<<"
                        androidx.compose.animation.AnimatedVisibility(
                            visible = tabScrollState.value > 0,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(width = 30.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2638))
                                    .clickable {
                                        coroutineScope.launch {
                                            tabScrollState.animateScrollTo((tabScrollState.value - 120).coerceAtLeast(0))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("<<", color = YamahaCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        // Dynamic Right Scroll Indicator ">>"
                        androidx.compose.animation.AnimatedVisibility(
                            visible = tabScrollState.maxValue > 0 && tabScrollState.value < tabScrollState.maxValue,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .size(width = 30.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2638))
                                    .clickable {
                                        coroutineScope.launch {
                                            tabScrollState.animateScrollTo((tabScrollState.value + 120).coerceAtMost(tabScrollState.maxValue))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(">>", color = YamahaCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.cd_reset_performance),
                            tint = SynthMutedGray
                        )
                    }
                }

                // Visualizer of Splits & Layers (shown in Detail and Mixer views in Portrait)
                if (currentViewMode == ViewMode.DETAIL || currentViewMode == ViewMode.MIXER) {
                    val currentPartIndex = if (currentViewMode == ViewMode.MIXER) state.activeFocusedPartIndex else state.selectedPartIndex

                    SplitKeyboardVisualizer(
                        parts = state.parts,
                        selectedPartIndex = currentPartIndex,
                        onPartSelected = { idx ->
                            if (!isProUser && idx >= 2) {
                                viewModel.openUpgradeDialog()
                            } else {
                                if (currentViewMode == ViewMode.MIXER) {
                                    viewModel.switchLiveFocusedPart(idx)
                                } else {
                                    onSelectPart(idx)
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    // 16 Part Quick Select Tabs
                    ScrollableTabRow(
                        selectedTabIndex = currentPartIndex,
                        containerColor = Color.Transparent,
                        contentColor = YamahaCyan,
                        edgePadding = 12.dp,
                        divider = {},
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        state.parts.forEach { part ->
                            val isPartMidiActive = partActivityStates[part.partIndex] == true
                            val isPartLocked = !isProUser && part.partIndex >= 2
                            val isTabSelected = currentPartIndex == part.partIndex
                            Tab(
                                selected = isTabSelected,
                                onClick = {
                                    if (isPartLocked) {
                                        viewModel.openUpgradeDialog()
                                    } else {
                                        if (currentViewMode == ViewMode.MIXER) {
                                            viewModel.switchLiveFocusedPart(part.partIndex)
                                        } else {
                                            onSelectPart(part.partIndex)
                                        }
                                    }
                                },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isPartLocked) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                                contentDescription = stringResource(R.string.cd_pro_feature),
                                                tint = YamahaGold,
                                                modifier = Modifier.size(11.dp).padding(end = 2.dp)
                                            )
                                        } else if (isPartMidiActive) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(SynthGreen)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = stringResource(R.string.part_tab_format, part.partNumber),
                                            fontWeight = if (isTabSelected) FontWeight.Black else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            color = if (isPartLocked) YamahaGold else Color.Unspecified
                                        )
                                    }
                                },
                                selectedContentColor = YamahaCyan,
                                unselectedContentColor = if (isPartLocked) YamahaGold else if (part.isMuted) MuteRed else SynthMutedGray
                            )
                        }
                    }

                    HorizontalDivider(color = SynthCardBorder, thickness = 1.dp)
                }
            } else {
                // In Landscape:
                // Only render SplitKeyboardVisualizer on DETAIL (Parte) and MIXER tabs; NEVER on SETLIST or MIDI_CC
                if (currentViewMode != ViewMode.SETLIST && currentViewMode != ViewMode.MIDI_CC) {
                    val currentPartIndex = if (currentViewMode == ViewMode.MIXER) state.activeFocusedPartIndex else state.selectedPartIndex

                    SplitKeyboardVisualizer(
                        parts = state.parts,
                        selectedPartIndex = currentPartIndex,
                        onPartSelected = { idx ->
                            if (!isProUser && idx >= 2) {
                                viewModel.openUpgradeDialog()
                            } else {
                                if (currentViewMode == ViewMode.MIXER) {
                                    viewModel.switchLiveFocusedPart(idx)
                                } else {
                                    onSelectPart(idx)
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )

                    // In DETAIL mode, show a ultra-compact 26.dp horizontal part selector row
                    if (currentViewMode == ViewMode.DETAIL) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.parts.forEach { part ->
                                val isPartMidiActive = partActivityStates[part.partIndex] == true
                                val isPartLocked = !isProUser && part.partIndex >= 2
                                val isTabSelected = state.selectedPartIndex == part.partIndex
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isTabSelected) YamahaCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    border = if (isTabSelected) androidx.compose.foundation.BorderStroke(1.dp, YamahaCyan) else null,
                                    modifier = Modifier
                                        .height(22.dp)
                                        .clickable {
                                            if (isPartLocked) {
                                                viewModel.openUpgradeDialog()
                                            } else {
                                                onSelectPart(part.partIndex)
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        if (isPartLocked) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                                contentDescription = stringResource(R.string.cd_pro_feature),
                                                tint = YamahaGold,
                                                modifier = Modifier.size(9.dp).padding(end = 2.dp)
                                            )
                                        } else if (isPartMidiActive) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(SynthGreen)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                        }
                                        Text(
                                            text = stringResource(R.string.part_tab_format, part.partNumber),
                                            fontWeight = if (isTabSelected) FontWeight.Black else FontWeight.Normal,
                                            fontSize = 10.sp,
                                            color = when {
                                                isTabSelected -> YamahaCyan
                                                isPartLocked -> YamahaGold
                                                part.isMuted -> MuteRed
                                                else -> SynthMutedGray
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = SynthCardBorder, thickness = 0.5.dp)
                }
            }

            // Main Content Area
            Crossfade(
                targetState = currentViewMode,
                label = "view_mode",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { mode ->
                when (mode) {
                    ViewMode.DETAIL -> {
                        val currentPart = state.selectedPart
                        PartDetailScreen(
                            modifier = Modifier.fillMaxSize(),
                            part = currentPart,
                            isFocused = currentPart.partIndex == state.activeFocusedPartIndex,
                            isMidiActive = partActivityStates[currentPart.partIndex] == true,
                            isProUser = isProUser,
                            onUpgradeRequested = { viewModel.openUpgradeDialog() },
                            anySoloActive = state.anySoloActive,
                            onPartIndexChange = onSelectPart,
                            onFocusPart = { viewModel.switchLiveFocusedPart(currentPart.partIndex) },
                            onVoiceChange = { voice -> onVoiceChange(currentPart.partIndex, voice) },
                            onVoicePreview = { voice -> viewModel.previewVoice(currentPart.partIndex, voice) },
                            onVoiceConfirm = { voice -> onVoiceChange(currentPart.partIndex, voice) },
                            onVoiceCancel = { viewModel.cancelVoiceSelection(currentPart.partIndex) },
                            onVolumeChange = { vol -> onVolumeChange(currentPart.partIndex, vol) },
                            onPanChange = { pan -> onPanChange(currentPart.partIndex, pan) },
                            onReverbSendChange = { rev -> onReverbSendChange(currentPart.partIndex, rev) },
                            onChorusSendChange = { cho -> onChorusSendChange(currentPart.partIndex, cho) },
                            onNoteShiftChange = { shift -> onNoteShiftChange(currentPart.partIndex, shift) },
                            onNoteRangeChange = { low, high -> onNoteRangeChange(currentPart.partIndex, low, high) },
                            onCutoffChange = { cut -> onCutoffChange(currentPart.partIndex, cut) },
                            onResonanceChange = { reso -> onResonanceChange(currentPart.partIndex, reso) },
                            onAttackChange = { att -> onAttackChange(currentPart.partIndex, att) },
                            onDecayChange = { dec -> onDecayChange(currentPart.partIndex, dec) },
                            onReleaseChange = { rel -> onReleaseChange(currentPart.partIndex, rel) },
                            onResetAeg = { viewModel.resetPartAeg(currentPart.partIndex) },
                            masterEqGains = state.masterEqGains,
                            onMasterEqBandGainChange = { bandIdx, gain -> viewModel.setMasterEqBandGain(bandIdx, gain) },
                            onResetMasterEq = { viewModel.resetMasterEq() },
                            onToggleCompressorEnabled = { enabled -> viewModel.togglePartCompressor(currentPart.partIndex, enabled) },
                            onCompressorThresholdChange = { thresh -> viewModel.setPartCompressorThreshold(currentPart.partIndex, thresh) },
                            onCompressorOutputGainChange = { gain -> viewModel.setPartCompressorOutputGain(currentPart.partIndex, gain) },
                            onCompressorRatioChange = { ratio -> viewModel.setPartCompressorRatio(currentPart.partIndex, ratio) },
                            onCompressorAttackChange = { attack -> viewModel.setPartCompressorAttack(currentPart.partIndex, attack) },
                            onCompressorReleaseChange = { release -> viewModel.setPartCompressorRelease(currentPart.partIndex, release) },
                            onResetCompressor = { viewModel.resetPartCompressor(currentPart.partIndex) },
                            onModulationChange = { mod -> onModulationChange(currentPart.partIndex, mod) },
                            onToggleModulationEnabled = { enabled -> viewModel.togglePartModulation(currentPart.partIndex, enabled) },
                            onToggleMute = { onToggleMute(currentPart.partIndex) },
                            onToggleSolo = { onToggleSolo(currentPart.partIndex) },
                            onReceiveChannelChange = { ch -> viewModel.setPartReceiveChannel(currentPart.partIndex, ch) },
                            onToggleLayer = { viewModel.togglePartLayerSelection(currentPart.partIndex) },
                            userVoices = userVoices,
                            onRenameUserVoice = { slotIdx, newName -> viewModel.updateUserVoiceName(slotIdx, newName) },
                            onStartMidiLearn = { param ->
                                if (!isProUser && currentPart.partIndex >= 2) {
                                    viewModel.openUpgradeDialog()
                                } else {
                                    viewModel.startMidiLearn(param, currentPart.partIndex)
                                }
                            }
                        )
                    }
                    ViewMode.MIXER -> {
                        MixerOverviewScreen(
                            modifier = Modifier.fillMaxSize(),
                            parts = state.parts,
                            selectedPartIndex = state.selectedPartIndex,
                            activeFocusedPartIndex = state.activeFocusedPartIndex,
                            partActivityStates = partActivityStates,
                            isProUser = isProUser,
                            anySoloActive = state.anySoloActive,
                            onUpgradeRequested = { viewModel.openUpgradeDialog() },
                            onSelectPart = { idx -> viewModel.switchLiveFocusedPart(idx) },
                            onToggleLayerSelection = { idx -> viewModel.togglePartLayerSelection(idx) },
                            onVolumeChange = onVolumeChange,
                            onVoiceChange = onVoiceChange,
                            onVoicePreview = { idx, voice -> viewModel.previewVoice(idx, voice) },
                            onVoiceConfirm = onVoiceChange,
                            onVoiceCancel = { idx -> viewModel.cancelVoiceSelection(idx) },
                            onToggleMute = onToggleMute,
                            onToggleSolo = onToggleSolo,
                            onReceiveChannelChange = { idx, ch -> viewModel.setPartReceiveChannel(idx, ch) },
                            onOctaveShift = { idx, delta -> viewModel.shiftPartOctave(idx, delta) },
                            onModulationChange = onModulationChange,
                            onToggleModulationEnabled = { idx, enabled -> viewModel.togglePartModulation(idx, enabled) },
                            userVoices = userVoices,
                            onRenameUserVoice = { slotIdx, newName -> viewModel.updateUserVoiceName(slotIdx, newName) },
                            onStartMidiLearn = { param, partIdx ->
                                if (!isProUser && partIdx >= 2) {
                                    viewModel.openUpgradeDialog()
                                } else {
                                    viewModel.startMidiLearn(param, partIdx)
                                }
                            }
                        )
                    }
                    ViewMode.SETLIST -> {
                        SetlistScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = viewModel
                        )
                    }
                    ViewMode.MIDI_CC -> {
                        MidiSettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // Save Performance Dialog
    if (showSaveDialog) {
        val loadedPerfId by viewModel.loadedPerformanceId.collectAsState()
        val savedList by viewModel.savedPerformances.collectAsState()
        val loadedPerf = savedList.firstOrNull { it.id == loadedPerfId }
        val categories by viewModel.categories.collectAsState()

        SavePerformanceDialog(
            initialName = loadedPerf?.name ?: state.common.performanceName.ifBlank { "" },
            initialTag = loadedPerf?.categoryTag ?: stringResource(R.string.default_category_fallback),
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

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.dialog_reset_title), color = SynthLightText) },
            text = {
                Text(
                    stringResource(R.string.dialog_reset_message),
                    color = SynthMutedGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetPerformance()
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_reset), color = SynthRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = SynthLightText)
                }
            },
            containerColor = SynthCardBackground
        )
    }

    // Interactive MIDI Learn Modal Dialog
    val activeLearnTarget = midiLearnTarget
    if (isMidiLearnActive && activeLearnTarget != null) {
        MidiLearnDialog(
            target = activeLearnTarget,
            currentConfig = midiMappings[activeLearnTarget],
            onRestoreDefault = { viewModel.restoreDefaultMidiMapping() },
            onRemoveMapping = { viewModel.removeMidiMapping() },
            onCancel = { viewModel.cancelMidiLearn() }
        )
    }
}

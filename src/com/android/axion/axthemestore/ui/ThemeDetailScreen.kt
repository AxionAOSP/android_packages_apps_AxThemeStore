/*
 * Copyright (C) 2025 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.android.axion.axthemestore.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.axion.axthemestore.R
import com.android.axion.axthemestore.data.model.Theme
import com.android.axion.axthemestore.data.model.ThemeInstallState
import com.android.axion.axthemestore.data.model.ThemeOverlay
import com.android.axion.axthemestore.data.model.formatFileSize
import com.android.axion.axthemestore.data.model.hasUpdate
import com.android.axion.axthemestore.ui.components.AsyncNetworkImage
import com.android.axion.axthemestore.ui.components.BackGesturePreview
import com.android.axion.axthemestore.ui.components.BatteryStylePreview
import com.android.axion.axthemestore.ui.components.ChargingAnimationBannerPreview
import com.android.axion.axthemestore.ui.components.UdfpsAnimationBannerPreview
import com.android.axion.axthemestore.ui.components.ImagePlaceholder
import com.android.axion.axthemestore.ui.components.ThemePackagePreview
import com.android.axion.axthemestore.viewmodel.ThemeStoreViewModel

@Composable
fun ThemeDetailScreen(
    theme: Theme,
    viewModel: ThemeStoreViewModel,
    onBackClick: () -> Unit
) {
    val themeStates by viewModel.themeStates.collectAsStateWithLifecycle()
    val installState = themeStates[theme.id] ?: ThemeInstallState.NotInstalled
    val pendingChanges by viewModel.pendingComponentChanges.collectAsStateWithLifecycle()
    val hasPendingChanges = pendingChanges.containsKey(theme.id)
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = theme.name,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 1.dp
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    InstallSection(
                        theme = theme,
                        installState = installState,
                        hasPendingChanges = hasPendingChanges,
                        onDownloadClick = { viewModel.downloadTheme(theme) },
                        onInstallClick = { viewModel.installTheme(theme) },
                        onApplyClick = { viewModel.applyTheme(theme) },
                        onApplyPendingClick = { viewModel.applyPendingChanges(theme) },
                        onDisableClick = { viewModel.disableTheme(theme) },
                        onUninstallClick = { 
                            viewModel.uninstallTheme(theme, onComplete = onBackClick)
                        },
                        onClearError = { viewModel.clearError(theme.id) }
                    )
                }
            }
        }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                DetailPreviewBox(theme = theme)
            }
            
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "by ${theme.author}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = theme.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (theme.isUnified && theme.overlays.isNotEmpty()) {
                    val overlay = theme.overlays.first()
                    val packageName = overlay.packageName
                    val isThisThemeActive = installState is ThemeInstallState.Installed
                    val isInstalled = installState is ThemeInstallState.Installed || 
                                      installState is ThemeInstallState.InstalledInactive
                    
                    val enabledComponents by viewModel.enabledComponents.collectAsStateWithLifecycle()
                    val categoryThemes by viewModel.categoryThemesState.collectAsStateWithLifecycle()
                    val pendingChanges by viewModel.pendingComponentChanges.collectAsStateWithLifecycle()
                    val hasPending = pendingChanges.containsKey(theme.id)
                    
                    if (overlay.targets.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.components),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (hasPending) {
                                Text(
                                    text = stringResource(R.string.pending_changes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        overlay.targets.forEach { target ->
                            val isFromThisTheme = categoryThemes[target] == packageName
                            val currentThemePkg = categoryThemes[target]
                            
                            val pendingSet = pendingChanges[theme.id]
                            val isEnabled = when {
                                pendingSet != null -> pendingSet.contains(target)
                                isFromThisTheme -> true
                                else -> false
                            }
                            
                            ComponentSelectionItem(
                                componentId = target,
                                isEnabled = isEnabled,
                                isToggleable = isInstalled,
                                currentSource = if (!isFromThisTheme && currentThemePkg != null) {
                                    currentThemePkg.substringAfterLast('.')
                                } else null,
                                onToggle = { enabled ->
                                    if (isInstalled) {
                                        viewModel.toggleComponent(theme, target, enabled)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                } else if (theme.overlays.isNotEmpty() && !theme.isUiStyle) {
                    Text(
                        text = stringResource(R.string.components),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    theme.overlays.forEach { overlay ->
                        ComponentOverlayItem(overlay = overlay)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (theme.tags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tags),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(theme.tags.size) { index ->
                            TagChip(tag = theme.tags[index])
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
            }
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TagChip(tag: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ComponentOverlayItem(overlay: ThemeOverlay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = overlay.componentId.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = overlay.targetPackage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = overlay.fileSize.formatFileSize(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnifiedComponentItem(
    componentId: String,
    isEnabled: Boolean,
    isToggleable: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getComponentDisplayName(componentId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = getComponentDescription(componentId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isToggleable) {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getComponentDisplayName(componentId: String): String {
    return when (componentId) {
        "statusbar_wifi", "wifi" -> "WiFi Icons"
        "statusbar_signal", "signal" -> "Signal Icons"
        "android" -> "Android Framework"
        "systemui", "systemui_icons" -> "System UI"
        "ui_qs" -> "QuickSettings Style"
        "ui_volume" -> "Volume Panel Style"
        "ui_style" -> "UI Style"
        else -> componentId.replaceFirstChar { it.uppercase() }
    }
}

private fun getComponentDescription(componentId: String): String {
    return when (componentId) {
        "statusbar_wifi", "wifi" -> "WiFi signal indicators in status bar"
        "statusbar_signal", "signal" -> "Mobile network indicators in status bar"
        "android" -> "Core Android framework icons"
        "systemui", "systemui_icons" -> "Status bar and quick settings icons"
        "ui_qs" -> "QuickSettings tiles and brightness slider style"
        "ui_volume" -> "Volume panel appearance"
        "ui_style" -> "Overall UI appearance"
        else -> "Theme component"
    }
}

@Composable
private fun ComponentSelectionItem(
    componentId: String,
    isEnabled: Boolean,
    isToggleable: Boolean,
    currentSource: String? = null,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getComponentDisplayName(componentId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (currentSource != null) {
                Text(
                    text = "Currently from: $currentSource",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Text(
                    text = getComponentDescription(componentId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (isToggleable) {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        } else {
            Icon(
                imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun InstallSection(
    theme: Theme,
    installState: ThemeInstallState,
    hasPendingChanges: Boolean,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onApplyClick: () -> Unit,
    onApplyPendingClick: () -> Unit,
    onDisableClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (theme.isUiStyle) {
            when (installState) {
                is ThemeInstallState.Installed -> {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.active))
                    }
                }
                is ThemeInstallState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.error_prefix, installState.message ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onClearError()
                                onApplyClick()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = onApplyClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
            return
        }
        
        when (installState) {
            is ThemeInstallState.NotInstalled -> {
                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.download))
                }
            }

            is ThemeInstallState.Downloaded -> {
                Button(
                    onClick = onInstallClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.install))
                }
            }
            
            is ThemeInstallState.InstalledInactive -> {
                val hasUpdate = theme.hasUpdate(installState.installedVersionCode)
                
                val hasSelection = if (theme.isUnified) {
                    hasPendingChanges
                } else {
                    true 
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (hasUpdate) {
                        Button(
                            onClick = onInstallClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.update_to, theme.version))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onApplyClick,
                            modifier = Modifier.weight(1f),
                            enabled = hasSelection
                        ) {
                            Text(stringResource(R.string.apply))
                        }

                        OutlinedButton(
                            onClick = onUninstallClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.uninstall))
                        }
                    }
                }
            }
            
            is ThemeInstallState.Installed -> {
                val hasUpdate = theme.hasUpdate(installState.installedVersionCode)
                
                if (hasUpdate) {
                    Button(
                        onClick = onInstallClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.update_to, theme.version))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hasPendingChanges) {
                            Button(
                                onClick = onApplyPendingClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.apply_changes))
                            }
                        } else {
                            Button(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.active))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDisableClick,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(stringResource(R.string.disable))
                            }
                            
                            OutlinedButton(
                                onClick = onUninstallClick,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.uninstall))
                            }
                        }
                    }
                }
            }
            
            is ThemeInstallState.Downloading -> {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.downloading, installState.currentOverlay),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${(installState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val motionScheme = MaterialTheme.motionScheme
                    val animatedProgress by animateFloatAsState(
                        targetValue = installState.progress,
                        animationSpec = motionScheme.defaultEffectsSpec(),
                        label = "download_progress"
                    )
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            is ThemeInstallState.Installing -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.installing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is ThemeInstallState.PartiallyInstalled -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.components_installed, installState.installedOverlays.size, installState.totalOverlays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onInstallClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.resume_installation))
                    }
                }
            }
            
            is ThemeInstallState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.error_prefix, installState.message ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onClearError()
                            onInstallClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPreviewBox(theme: Theme) {
    val packageName = theme.overlays.firstOrNull()?.packageName ?: ""
    val category = theme.category.ifEmpty { theme.overlays.firstOrNull()?.componentId ?: "" }
    val context = LocalContext.current
    val previewMap = remember {
        val map = mutableMapOf<String, String>()
        try {
            val entries = context.resources.getStringArray(R.array.overlay_preview_map)
            for (entry in entries) {
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2) map[parts[0]] = parts[1]
            }
        } catch (_: Exception) {}
        map
    }

    val prefix = previewMap[packageName] ?: ""
    val resIds = if (prefix.isNotEmpty()) {
        (1..4).mapNotNull { i ->
            val id = context.resources.getIdentifier("${prefix}_$i", "drawable", context.packageName)
            if (id != 0) id else null
        }
    } else emptyList()

    val isChargingAnim = packageName.contains("charging_animation") || category.contains("charging_animation")
    val isUdfpsAnim = packageName.contains("udfps_animation") || category.contains("udfps_animation")
    val isWaveform = packageName.contains("waveform") || category.contains("waveform")
    
    val bgModifier = if (isChargingAnim || isUdfpsAnim) {
        Modifier.background(Color.Black)
    } else {
        Modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.surfaceBright
                )
            )
        )
    }
    Box(
        modifier = Modifier.fillMaxSize().then(bgModifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            isChargingAnim -> {
                ChargingAnimationBannerPreview(
                    packageName = packageName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isUdfpsAnim -> {
                UdfpsAnimationBannerPreview(
                    packageName = packageName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isWaveform -> {
                if (theme.previewImages.isNotEmpty()) {
                    AsyncNetworkImage(
                        url = theme.previewImages.first(),
                        contentDescription = theme.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(120.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            packageName.contains("battery") || category.contains("battery") -> {
                BatteryStylePreview(
                    packageName = packageName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            packageName.contains("back_gesture") || category.contains("back_gesture") -> {
                BackGesturePreview(modifier = Modifier.fillMaxSize())
            }
            resIds.isNotEmpty() -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    resIds.forEach { resId ->
                        val drawable = remember(resId) {
                            ContextCompat.getDrawable(context, resId)
                        }
                        if (drawable != null) {
                            Image(
                                bitmap = remember(drawable) {
                                    drawable.toBitmap().asImageBitmap()
                                },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                colorFilter = ColorFilter.tint(
                                    MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

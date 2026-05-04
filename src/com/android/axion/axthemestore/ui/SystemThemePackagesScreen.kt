/*
 * Copyright (C) 2025-2026 AxionOS
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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.axion.axthemestore.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.axion.axthemestore.R
import com.android.axion.axthemestore.viewmodel.ThemeStoreViewModel

private const val CATEGORY_SIGNAL = "android.theme.customization.signal_icon"
private const val CATEGORY_WIFI = "android.theme.customization.wifi_icon"
private const val CATEGORY_UDFPS = "android.theme.customization.udfps_animation"
private const val CATEGORY_UDFPS_ICON = "android.theme.customization.udfps_icon"
private const val CATEGORY_QS_WAVEFORM = "android.theme.customization.qs_waveform"

data class OverlayPackItem(
    val packageName: String,
    val label: String,
    val isActive: Boolean,
    val icon: Drawable?,
    val previewResPrefix: String = ""
)

@Composable
fun SystemThemePackagesScreen(
    viewModel: ThemeStoreViewModel,
    onCreateTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val isUdfpsSupported = remember { viewModel.getThemeEngineProxy().isUdfpsSupported() }
    val tabs = remember {
        mutableListOf(
            context.getString(R.string.system_icons_title),
            context.getString(R.string.system_themes_title)
        ).apply {
            add(context.getString(R.string.qs_waveform_title))
            if (isUdfpsSupported) {
                add(context.getString(R.string.udfps_animation_title))
                add(context.getString(R.string.udfps_icon_title))
            }
        }.toList()
    }

    var signalPacks by remember { mutableStateOf<List<OverlayPackItem>>(emptyList()) }
    var wifiPacks by remember { mutableStateOf<List<OverlayPackItem>>(emptyList()) }
    var udfpsPacks by remember { mutableStateOf<List<OverlayPackItem>>(emptyList()) }
    var udfpsIconPacks by remember { mutableStateOf<List<OverlayPackItem>>(emptyList()) }
    var qsWaveformPacks by remember { mutableStateOf<List<OverlayPackItem>>(emptyList()) }

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

    fun refreshPacks() {
        val proxy = viewModel.getThemeEngineProxy()
        val pm = context.packageManager

        val activeSignal = proxy.getCategoryTheme(CATEGORY_SIGNAL)
        val activeWifi = proxy.getCategoryTheme(CATEGORY_WIFI)

        signalPacks = proxy.getAvailableOverlays(CATEGORY_SIGNAL).mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                OverlayPackItem(
                    packageName = pkg,
                    label = ai.loadLabel(pm).toString(),
                    isActive = pkg == activeSignal,
                    icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null },
                    previewResPrefix = previewMap[pkg] ?: ""
                )
            } catch (_: Exception) { null }
        }

        wifiPacks = proxy.getAvailableOverlays(CATEGORY_WIFI).mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                OverlayPackItem(
                    packageName = pkg,
                    label = ai.loadLabel(pm).toString(),
                    isActive = pkg == activeWifi,
                    icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null },
                    previewResPrefix = previewMap[pkg] ?: ""
                )
            } catch (_: Exception) { null }
        }

        val activeUdfps = proxy.getCategoryTheme(CATEGORY_UDFPS)
        udfpsPacks = proxy.getAvailableOverlays(CATEGORY_UDFPS).mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                OverlayPackItem(
                    packageName = pkg,
                    label = ai.loadLabel(pm).toString(),
                    isActive = pkg == activeUdfps,
                    icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null },
                    previewResPrefix = previewMap[pkg] ?: ""
                )
            } catch (_: Exception) { null }
        }

        val activeUdfpsIcon = proxy.getCategoryTheme(CATEGORY_UDFPS_ICON)
        udfpsIconPacks = proxy.getAvailableOverlays(CATEGORY_UDFPS_ICON).mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                OverlayPackItem(
                    packageName = pkg,
                    label = ai.loadLabel(pm).toString(),
                    isActive = pkg == activeUdfpsIcon,
                    icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null },
                    previewResPrefix = previewMap[pkg] ?: ""
                )
            } catch (_: Exception) { null }
        }

        val activeQsWaveform = proxy.getCategoryTheme(CATEGORY_QS_WAVEFORM)
        qsWaveformPacks = proxy.getAvailableOverlays(CATEGORY_QS_WAVEFORM).mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                OverlayPackItem(
                    packageName = pkg,
                    label = ai.loadLabel(pm).toString(),
                    isActive = pkg == activeQsWaveform,
                    icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null },
                    previewResPrefix = previewMap[pkg] ?: ""
                )
            } catch (_: Exception) { null }
        }
    }

    LaunchedEffect(Unit) { refreshPacks() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.system_themes_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val packs = when (selectedTab) {
                0 -> signalPacks
                1 -> wifiPacks
                2 -> qsWaveformPacks
                3 -> if (isUdfpsSupported) udfpsPacks else emptyList()
                4 -> if (isUdfpsSupported) udfpsIconPacks else emptyList()
                else -> emptyList()
            }
            val category = when (selectedTab) {
                0 -> CATEGORY_SIGNAL
                1 -> CATEGORY_WIFI
                2 -> CATEGORY_QS_WAVEFORM
                3 -> if (isUdfpsSupported) CATEGORY_UDFPS else ""
                4 -> if (isUdfpsSupported) CATEGORY_UDFPS_ICON else ""
                else -> ""
            }

            if (packs.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(packs, key = { it.packageName }) { item ->
                        OverlayPackCard(
                            item = item,
                            onApply = {
                                val proxy = viewModel.getThemeEngineProxy()
                                proxy.setCategoryTheme(category, item.packageName)
                                if (category == CATEGORY_SIGNAL || category == CATEGORY_WIFI) {
                                    proxy.setIconThemeWithTargets(
                                        item.packageName,
                                        proxy.getIconThemeTargets() + listOf(
                                            if (selectedTab == 0) "signal" else "wifi"
                                        )
                                    )
                                }
                                proxy.notifyThemeChanged()
                                refreshPacks()
                            },
                            onDisable = {
                                val proxy = viewModel.getThemeEngineProxy()
                                proxy.clearCategoryTheme(category)
                                proxy.notifyThemeChanged()
                                refreshPacks()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayPackCard(
    item: OverlayPackItem,
    onApply: () -> Unit,
    onDisable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.label, style = MaterialTheme.typography.titleMedium)
                    if (item.isActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.theme_applied),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (item.isActive) {
                    OutlinedButton(onClick = onDisable) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.disable))
                    }
                } else {
                    FilledTonalButton(onClick = onApply) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }

            if (item.previewResPrefix.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PreviewRow(prefix = item.previewResPrefix)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.no_theme_packages), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.no_theme_packages_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreviewRow(prefix: String) {
    val context = LocalContext.current
    val resIds = (1..4).mapNotNull { i ->
        val id = context.resources.getIdentifier("${prefix}_$i", "drawable", context.packageName)
        if (id != 0) id else null
    }
    if (resIds.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        resIds.forEach { resId ->
            Image(
                painter = androidx.compose.ui.res.painterResource(resId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

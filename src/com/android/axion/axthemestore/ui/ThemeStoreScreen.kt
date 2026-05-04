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

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalGlideComposeApi::class,
)

package com.android.axion.axthemestore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.axion.axthemestore.R
import com.android.axion.axthemestore.data.model.Theme
import com.android.axion.axthemestore.data.model.ThemeCategory
import com.android.axion.axthemestore.data.model.ThemeInstallState
import com.android.axion.axthemestore.engine.ThemeEngineProxy
import com.android.axion.axthemestore.ui.components.AsyncNetworkImage
import com.android.axion.axthemestore.ui.components.ChargingAnimationBannerPreview
import com.android.axion.axthemestore.ui.components.UdfpsAnimationBannerPreview
import com.android.axion.axthemestore.ui.components.BackGesturePreview
import com.android.axion.axthemestore.ui.components.BatteryStylePreview
import com.android.axion.axthemestore.ui.components.ImagePlaceholder
import com.android.axion.axthemestore.ui.components.ThemePackagePreview
import com.android.axion.axthemestore.viewmodel.ThemeStoreUiState
import com.android.axion.axthemestore.viewmodel.ThemeStoreViewModel
import com.android.axion.axthemestore.data.ThumbnailPreloader
import com.android.axion.compose.scaffold.AxionScaffold
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ThemeStoreScreen(
    viewModel: ThemeStoreViewModel,
    onThemeClick: (Theme) -> Unit,
    onNavigateToCategory: (String) -> Unit = {},
    onNavigateToInstalledComponents: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeStates by viewModel.themeStates.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isSearchActive) {
                SearchScreen(
                    viewModel = viewModel,
                    searchQuery = searchQuery,
                    onSearchChange = { 
                        searchQuery = it
                        viewModel.searchThemes(it)
                    },
                    onBack = { 
                        isSearchActive = false
                        searchQuery = ""
                        viewModel.searchThemes("")
                    },
                    themes = viewModel.getFilteredThemes(),
                    themeStates = themeStates,
                    onThemeClick = onThemeClick
                )
            } else {
                BrowseScreen(
                    uiState = uiState,
                    themeStates = themeStates,
                    onSearchClick = { isSearchActive = true },
                    onRefresh = { viewModel.loadThemes(forceRefresh = true) },
                    onThemeClick = onThemeClick,
                    onNavigateToCategory = onNavigateToCategory,

                    onNavigateToInstalledComponents = onNavigateToInstalledComponents
                )
            }
        }
    }
}

@Composable
fun CategoryThemesScreen(
    categoryId: String,
    viewModel: ThemeStoreViewModel,
    onThemeClick: (Theme) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeStates by viewModel.themeStates.collectAsStateWithLifecycle()
    
    val category = uiState.categories.find { it.id == categoryId }
    val categoryName = when (categoryId) {
        "installed" -> stringResource(R.string.installed)
        else -> category?.name ?: "Themes"
    }

    val drillData by produceState<DrillDownData?>(null, uiState.themes, themeStates, categoryId) {
        value = null
        val start = System.currentTimeMillis()
        val computed = withContext(Dispatchers.Default) {
            val source = when (categoryId) {
                "installed" -> uiState.themes.filter { theme ->
                    val s = themeStates[theme.id]
                    s is ThemeInstallState.Installed ||
                            s is ThemeInstallState.InstalledInactive ||
                            theme.isLocal
                }
                "local" -> uiState.themes.filter { it.isLocal }
                else -> uiState.themes.filter { it.category == categoryId }
            }
            val sorted = source.sortedBy { it.name.lowercase() }
            val grouped = sorted.groupBy { it.pack }
            DrillDownData(
                ungrouped = grouped[null].orEmpty(),
                packs = grouped.filterKeys { it != null }.toSortedMap(compareBy { it!!.lowercase() }),
            )
        }
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < 600) delay(600 - elapsed)
        value = computed
    }

    AxionScaffold(
        title = categoryName,
        onBackClick = onBackClick,
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(
                targetState = drillData,
                label = "drill_down_transition",
                modifier = Modifier.fillMaxSize(),
            ) { data ->
                when {
                    uiState.isLoading -> LoadingState()
                    uiState.error != null -> ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.loadThemes(forceRefresh = true) },
                    )
                    data == null -> LoadingState()
                    data.isEmpty -> EmptyState(isSearching = false)
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ThemeItemRows(
                            themes = data.ungrouped,
                            themeStates = themeStates,
                            onThemeClick = onThemeClick,
                        )
                        data.packs.forEach { (pack, packThemes) ->
                            PackHeader(pack!!)
                            ThemeItemRows(
                                themes = packThemes,
                                themeStates = themeStates,
                                onThemeClick = onThemeClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    viewModel: ThemeStoreViewModel,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    themes: List<Theme>,
    themeStates: Map<String, ThemeInstallState>,
    onThemeClick: (Theme) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            
            TextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { 
                    Text(
                        stringResource(R.string.search_themes),
                        style = MaterialTheme.typography.bodyLarge
                    ) 
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        if (themes.isEmpty() && searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_results_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (searchQuery.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(themes, key = { it.id }) { theme ->
                    ThemeListItem(
                        theme = theme,
                        installState = themeStates[theme.id] ?: ThemeInstallState.NotInstalled,
                        onClick = { onThemeClick(theme) }
                    )
                }
            }
        } else {
            val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
            
            if (searchHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = stringResource(R.string.no_recent_searches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.recent_searches),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(onClick = { viewModel.clearSearchHistory() }) {
                            Text(stringResource(R.string.clear_all))
                        }
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchHistory) { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSearchChange(query) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = query,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.removeSearchHistoryItem(query) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.remove),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun BrowseScreen(
    uiState: ThemeStoreUiState,
    themeStates: Map<String, ThemeInstallState>,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onThemeClick: (Theme) -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onNavigateToInstalledComponents: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.themes),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
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
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.error != null -> ErrorState(
                    message = uiState.error!!,
                    onRetry = onRefresh,
                )
                uiState.themes.isEmpty() -> EmptyState(isSearching = false)
                else -> DashboardContent(
                    uiState = uiState,
                    themeStates = themeStates,
                    onThemeClick = onThemeClick,
                    onNavigateToCategory = onNavigateToCategory,
                )
            }
        }
    }
}

private const val RAIL_LIMIT = 6
private const val SECTION_LIMIT = 9
private val RAIL_CARD_WIDTH = 120.dp

private data class DrillDownData(
    val ungrouped: List<Theme>,
    val packs: Map<String?, List<Theme>>,
) {
    val isEmpty: Boolean get() = ungrouped.isEmpty() && packs.isEmpty()
}

private data class ThemePreviewMeta(
    val packageName: String,
    val isBattery: Boolean,
    val isBackGesture: Boolean,
    val isChargingAnim: Boolean,
    val isUdfpsAnim: Boolean,
    val isUdfpsIcon: Boolean,
    val isWaveform: Boolean,
)

@Composable
private fun DashboardContent(
    uiState: ThemeStoreUiState,
    themeStates: Map<String, ThemeInstallState>,
    onThemeClick: (Theme) -> Unit,
    onNavigateToCategory: (String) -> Unit,
) {
    val themesByCategory = remember(uiState.themes) {
        uiState.themes.groupBy { it.category }
            .mapValues { (_, list) -> list.sortedBy { it.name.lowercase() } }
    }
    val installedThemes = remember(uiState.themes, themeStates) {
        uiState.themes.filter { theme ->
            val s = themeStates[theme.id]
            s is ThemeInstallState.Installed ||
                    s is ThemeInstallState.InstalledInactive ||
                    theme.isLocal
        }.sortedBy { it.name.lowercase() }
    }
    val featuredThemes = remember(uiState.themes) {
        uiState.themes.shuffled().take(RAIL_LIMIT)
    }
    val categoriesWithThemes = remember(uiState.categories, themesByCategory) {
        uiState.categories.filter { cat -> (themesByCategory[cat.id] ?: emptyList()).isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        if (featuredThemes.isNotEmpty()) {
            FeaturedCarousel(
                themes = featuredThemes,
                onThemeClick = onThemeClick,
            )
        }
        if (installedThemes.isNotEmpty()) {
            ThemeSection(
                title = stringResource(R.string.installed),
                themes = installedThemes.take(SECTION_LIMIT),
                themeStates = themeStates,
                onThemeClick = onThemeClick,
                onSeeAll = if (installedThemes.size > SECTION_LIMIT) {
                    { onNavigateToCategory("installed") }
                } else null,
            )
        }
        categoriesWithThemes.forEach { cat ->
            val catThemes = themesByCategory[cat.id].orEmpty()
            ThemeSection(
                title = cat.name,
                themes = catThemes.take(SECTION_LIMIT),
                themeStates = themeStates,
                onThemeClick = onThemeClick,
                onSeeAll = if (catThemes.size > SECTION_LIMIT) {
                    { onNavigateToCategory(cat.id) }
                } else null,
            )
        }
    }
}

@Composable
private fun ThemeSection(
    title: String,
    themes: List<Theme>,
    themeStates: Map<String, ThemeInstallState>,
    onThemeClick: (Theme) -> Unit,
    onSeeAll: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        SectionHeader(title = title, onSeeAll = onSeeAll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            themes.chunked(3).forEach { chunk ->
                Column(
                    modifier = Modifier.width(280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    chunk.forEach { theme ->
                        ThemeListItem(
                            theme = theme,
                            installState = themeStates[theme.id] ?: ThemeInstallState.NotInstalled,
                            onClick = { onThemeClick(theme) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedCarousel(
    themes: List<Theme>,
    onThemeClick: (Theme) -> Unit,
) {
    if (themes.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { themes.size })
    LaunchedEffect(themes) {
        while (themes.size > 1) {
            delay(5000)
            val next = (pagerState.currentPage + 1) % themes.size
            pagerState.animateScrollToPage(next)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            FeaturedCard(theme = themes[page], onClick = { onThemeClick(themes[page]) })
        }
        if (themes.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(themes.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedCard(theme: Theme, onClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        ThemePreviewBox(theme = theme, transparentBg = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (theme.author.isNotEmpty()) {
                    Text(
                        text = theme.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.see_all))
            }
        }
    }
}

@Composable
private fun RailThemeCard(
    theme: Theme,
    installState: ThemeInstallState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(RAIL_CARD_WIDTH),
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large),
        ) {
            ThemePreviewBox(theme = theme)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.name,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        val stateText = when (installState) {
            is ThemeInstallState.Installed -> stringResource(R.string.active)
            is ThemeInstallState.InstalledInactive -> stringResource(R.string.installed)
            else -> theme.author
        }
        Text(
            text = stateText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ThemePreviewBox(theme: Theme, transparentBg: Boolean = false) {
    val context = LocalContext.current
    val previewMeta = remember(theme.id) {
        val packageName = theme.overlays.firstOrNull()?.packageName ?: ""
        val category = theme.category.ifEmpty { theme.overlays.firstOrNull()?.componentId ?: "" }
        ThemePreviewMeta(
            packageName = packageName,
            isBattery = packageName.contains("battery") || category.contains("battery"),
            isBackGesture = packageName.contains("back_gesture") || category.contains("back_gesture"),
            isChargingAnim = packageName.contains("charging_animation") || category.contains("charging_animation"),
            isUdfpsAnim = packageName.contains("udfps_animation") || category.contains("udfps_animation"),
            isUdfpsIcon = packageName.contains("udfps_icon") || category.contains("udfps_icon"),
            isWaveform = packageName.contains("waveform") || category.contains("waveform"),
        )
    }
    val packageName = previewMeta.packageName
    val isBattery = previewMeta.isBattery
    val isBackGesture = previewMeta.isBackGesture
    val isChargingAnim = previewMeta.isChargingAnim
    val isUdfpsAnim = previewMeta.isUdfpsAnim
    val isUdfpsIcon = previewMeta.isUdfpsIcon
    val isWaveform = previewMeta.isWaveform
    val previewResIds = remember(packageName) { getLocalPreviewResIds(context, packageName) }

    val bgModifier = if (transparentBg) Modifier else Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            previewResIds.isNotEmpty() -> {
                Image(
                    painter = painterResource(previewResIds.first()),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )
            }
            isBattery -> BatteryStylePreview(packageName, Modifier.fillMaxSize())
            isBackGesture -> BackGesturePreview(Modifier.fillMaxSize())
            isChargingAnim -> ChargingAnimationBannerPreview(
                packageName = packageName,
                modifier = Modifier.fillMaxSize(),
                animate = false,
            )
            isUdfpsAnim -> UdfpsAnimationBannerPreview(
                packageName = packageName,
                modifier = Modifier.fillMaxSize(),
                animate = false,
            )
            isWaveform -> {
                if (theme.previewImages.isNotEmpty()) {
                    AsyncNetworkImage(
                        url = theme.previewImages.first(),
                        contentDescription = theme.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            isUdfpsIcon -> {
                if (theme.previewImages.isNotEmpty()) {
                    AsyncNetworkImage(
                        url = theme.previewImages.first(),
                        contentDescription = theme.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            theme.previewImages.isNotEmpty() -> AsyncNetworkImage(
                url = theme.previewImages.first(),
                contentDescription = theme.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeItemRows(
    themes: List<Theme>,
    themeStates: Map<String, ThemeInstallState>,
    onThemeClick: (Theme) -> Unit,
) {
    themes.chunked(3).forEach { rowThemes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowThemes.forEach { theme ->
                RailThemeCard(
                    theme = theme,
                    installState = themeStates[theme.id] ?: ThemeInstallState.NotInstalled,
                    onClick = { onThemeClick(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(3 - rowThemes.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PackHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ThemeListItem(
    theme: Theme,
    installState: ThemeInstallState,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
            ) {
                val isInstalled = installState is ThemeInstallState.Installed ||
                                  installState is ThemeInstallState.InstalledInactive
                val previewMeta = remember(theme.id) {
                    val pkg = theme.overlays.firstOrNull()?.packageName ?: ""
                    val cat = theme.category.ifEmpty { theme.overlays.firstOrNull()?.componentId ?: "" }
                    ThemePreviewMeta(
                        packageName = pkg,
                        isBattery = pkg.contains("battery") || cat.contains("battery"),
                        isBackGesture = pkg.contains("back_gesture") || cat.contains("back_gesture"),
                        isChargingAnim = pkg.contains("charging_animation") || cat.contains("charging_animation"),
                        isUdfpsAnim = pkg.contains("udfps_animation") || cat.contains("udfps_animation"),
                        isUdfpsIcon = pkg.contains("udfps_icon") || cat.contains("udfps_icon"),
                        isWaveform = pkg.contains("waveform") || cat.contains("waveform"),
                    )
                }
                val packageName = previewMeta.packageName
                val isBattery = previewMeta.isBattery
                val isBackGesture = previewMeta.isBackGesture
                val isChargingAnim = previewMeta.isChargingAnim
                val isUdfpsAnim = previewMeta.isUdfpsAnim
                val isUdfpsIcon = previewMeta.isUdfpsIcon
                val isWaveform = previewMeta.isWaveform
                val ctx = LocalContext.current
                val previewResIds = remember(packageName) { getLocalPreviewResIds(ctx, packageName) }

                run {
                    if (previewResIds.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(previewResIds.first()),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(
                                    MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    } else if (isBattery) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            BatteryStylePreview(
                                packageName = packageName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (isBackGesture) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            BackGesturePreview(modifier = Modifier.fillMaxSize())
                        }
                    } else if (isChargingAnim) {
                        ChargingAnimationBannerPreview(
                            packageName = packageName,
                            modifier = Modifier.fillMaxSize(),
                            animate = false,
                        )
                    } else if (isUdfpsAnim) {
                        UdfpsAnimationBannerPreview(
                            packageName = packageName,
                            modifier = Modifier.fillMaxSize(),
                            animate = false,
                        )
                    } else if (isWaveform) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (theme.previewImages.isNotEmpty()) {
                                AsyncNetworkImage(
                                    url = theme.previewImages.first(),
                                    contentDescription = theme.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(24.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Waves,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    } else if (isUdfpsIcon) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (theme.previewImages.isNotEmpty()) {
                                AsyncNetworkImage(
                                    url = theme.previewImages.first(),
                                    contentDescription = theme.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(24.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    } else if (theme.previewImages.isNotEmpty()) {
                        AsyncNetworkImage(
                            url = theme.previewImages.first(),
                            contentDescription = theme.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            errorContent = {
                                if (isInstalled && packageName.isNotEmpty()) {
                                    ThemePackagePreview(
                                        packageName = packageName,
                                        modifier = Modifier.fillMaxSize(),
                                        showSingleIcon = true
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )
                    } else if (isInstalled && packageName.isNotEmpty()) {
                        ThemePackagePreview(
                            packageName = packageName,
                            modifier = Modifier.fillMaxSize(),
                            showSingleIcon = true
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = theme.description.ifEmpty { theme.category },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val stateText = when (installState) {
                    is ThemeInstallState.Installed -> "Active"
                    is ThemeInstallState.InstalledInactive -> "Installed"
                    else -> null
                }
                stateText?.let {
                    Text(
                        text = "✓ $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ContainedLoadingIndicator(
                containerShape = CircleShape,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_themes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.failed_to_load_themes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun EmptyState(isSearching: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Default.SearchOff else Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(if (isSearching) R.string.no_themes_found else R.string.no_themes_available),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (isSearching) R.string.try_different_search
                    else R.string.check_back_later
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val sPreviewMapCache = mutableMapOf<String, String>()
private var sPreviewMapLoaded = false
private val sPreviewIdsCache = mutableMapOf<String, List<Int>>()

private fun getLocalPreviewResIds(context: android.content.Context, packageName: String): List<Int> {
    if (!sPreviewMapLoaded) {
        try {
            val entries = context.resources.getStringArray(R.array.overlay_preview_map)
            for (entry in entries) {
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2) sPreviewMapCache[parts[0]] = parts[1]
            }
        } catch (_: Exception) {}
        sPreviewMapLoaded = true
    }
    sPreviewIdsCache[packageName]?.let { return it }
    val prefix = sPreviewMapCache[packageName] ?: run {
        sPreviewIdsCache[packageName] = emptyList()
        return emptyList()
    }
    val ids = (1..4).mapNotNull { i ->
        val id = context.resources.getIdentifier("${prefix}_$i", "drawable", context.packageName)
        if (id != 0) id else null
    }
    sPreviewIdsCache[packageName] = ids
    return ids
}

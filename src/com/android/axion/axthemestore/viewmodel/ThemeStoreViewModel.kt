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

package com.android.axion.axthemestore.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.axion.axthemestore.data.model.IconPack
import com.android.axion.axthemestore.data.model.Theme
import com.android.axion.axthemestore.data.model.ThemeCategory
import com.android.axion.axthemestore.data.model.ThemeInstallState
import com.android.axion.axthemestore.data.model.ThemeOverlay
import com.android.axion.axthemestore.data.ThumbnailPreloader
import com.android.axion.axthemestore.data.repository.ThemeRepository
import com.android.axion.axthemestore.download.ThemeDownloadManager
import com.android.axion.axthemestore.engine.ThemeEngineProxy
import com.android.axion.axthemestore.install.ThemeInstaller
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import org.json.JSONArray

class ThemeStoreViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "ThemeStoreViewModel"
        private const val PREFS_NAME = "theme_store_prefs"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val MAX_SEARCH_HISTORY = 10
    }
    
    private val repository = ThemeRepository(application)
    private val downloadManager = ThemeDownloadManager(application)
    private val installer = ThemeInstaller(application)
    private val themeEngineProxy = ThemeEngineProxy(application)
    private val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(ThemeStoreUiState())
    val uiState: StateFlow<ThemeStoreUiState> = _uiState.asStateFlow()
    
    private val _themeStates = MutableStateFlow<Map<String, ThemeInstallState>>(emptyMap())
    val themeStates: StateFlow<Map<String, ThemeInstallState>> = _themeStates.asStateFlow()
    
    private val _enabledComponents = MutableStateFlow<Set<String>>(emptySet())
    val enabledComponents: StateFlow<Set<String>> = _enabledComponents.asStateFlow()
    
    private val _categoryThemes = MutableStateFlow<Map<String, String>>(emptyMap())
    val categoryThemesState: StateFlow<Map<String, String>> = _categoryThemes.asStateFlow()
    
    private val _pendingComponentChanges = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val pendingComponentChanges: StateFlow<Map<String, Set<String>>> = _pendingComponentChanges.asStateFlow()
    
    private val _initialComponentStates = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val initialComponentStates: StateFlow<Map<String, Set<String>>> = _initialComponentStates.asStateFlow()
    
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()
    
    init {
        loadThemes()
        loadIconPacks()
        loadThemedIconStyle()
        viewModelScope.launch { refreshComponentStates() }
        loadSearchHistory()
    }
    
    private suspend fun refreshComponentStates() {
        val (targets, cats) = withContext(Dispatchers.IO) {
            themeEngineProxy.getIconThemeTargets().toSet() to themeEngineProxy.getCategoryThemes()
        }
        _enabledComponents.value = targets
        _categoryThemes.value = cats
    }

    fun loadThemes(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val fetchResult = withContext(Dispatchers.IO) {
                repository.fetchThemes(forceRefresh)
            }
            fetchResult.fold(
                onSuccess = { response ->
                    val storePackages = response.themes
                        .flatMap { it.overlays }
                        .map { it.packageName }
                        .toSet()

                    val thirdPartyThemes = withContext(Dispatchers.IO) {
                        repository.getInstalledThirdPartyThemes(storePackages)
                    }

                    val allThemes = response.themes + thirdPartyThemes
                    
                    val baseCategories = response.categories

                    val extraCategories = thirdPartyThemes
                        .map { it.category }
                        .distinct()
                        .filter { cat -> cat != "local" && baseCategories.none { it.id == cat } }
                        .map { cat ->
                            ThemeCategory(
                                id = cat,
                                name = cat.replace('_', ' ')
                                    .split(' ')
                                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                                icon = "palette"
                            )
                        }

                    val categories = (baseCategories + extraCategories).let { cats ->
                        if (thirdPartyThemes.any { it.category == "local" }) {
                            cats + ThemeCategory(id = "local", name = "Installed", icon = "category")
                        } else cats
                    }
                    
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            themes = allThemes,
                            categories = categories,
                            error = null
                        )
                    }
                    updateInstallStates(allThemes)

                    refreshComponentStates()
                    ThumbnailPreloader.preload(getApplication(), allThemes)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load themes, entering offline mode", error)

                    val thirdPartyThemes = withContext(Dispatchers.IO) {
                        repository.getInstalledThirdPartyThemes(emptySet())
                    }

                    val offlineCategories = thirdPartyThemes
                        .map { it.category }
                        .distinct()
                        .map { cat ->
                            ThemeCategory(
                                id = cat,
                                name = cat.replace('_', ' ')
                                    .split(' ')
                                    .joinToString(" ") { part ->
                                        part.replaceFirstChar { c -> c.uppercase() }
                                    },
                                icon = "palette"
                            )
                        }

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            themes = thirdPartyThemes,
                            categories = offlineCategories,
                            error = if (thirdPartyThemes.isEmpty())
                                error.message ?: "Failed to load themes"
                            else null
                        )
                    }
                    updateInstallStates(thirdPartyThemes)
                    refreshComponentStates()
                    ThumbnailPreloader.preload(getApplication(), thirdPartyThemes)
                }
            )

        }
    }

    fun checkInstallStates() {
        viewModelScope.launch {
            refreshComponentStates()
            val themes = _uiState.value.themes
            if (themes.isNotEmpty()) {
                updateInstallStates(themes)
            }
        }
    }
    
    private suspend fun updateInstallStates(themes: List<Theme>) = withContext(Dispatchers.IO) {
        val enabledThemes = themeEngineProxy.getEnabledThemes()
        val categoryThemes = themeEngineProxy.getCategoryThemes()

        val states = themes.associate { theme ->
            val state = when {
                theme.isUnified && theme.overlays.isNotEmpty() -> {
                    val packageName = theme.overlays.first().packageName
                    val isInstalled = repository.isThemeInstalled(packageName)
                    val targets = theme.overlays.first().targets
                    val isAnyComponentActive = targets.any { target ->
                        categoryThemes[target] == packageName
                    }
                    
                    when {
                        isAnyComponentActive -> ThemeInstallState.Installed(theme.versionCode)
                        isInstalled -> ThemeInstallState.InstalledInactive(
                            repository.getInstalledVersionCode(packageName) ?: theme.versionCode
                        )
                        else -> ThemeInstallState.NotInstalled
                    }
                }
                else -> {
                    val installedOverlays = theme.overlays.filter { overlay ->
                        repository.isThemeInstalled(overlay.packageName)
                    }.map { it.componentId }.toSet()

                    val isAnyActive = theme.overlays.any { overlay ->
                        enabledThemes[overlay.componentId] == overlay.packageName
                                || categoryThemes[overlay.componentId] == overlay.packageName
                    }
                    
                    when {
                        installedOverlays.isEmpty() -> ThemeInstallState.NotInstalled
                        installedOverlays.size == theme.overlays.size -> {
                            val firstOverlay = theme.overlays.first()
                            val installedVersion = repository.getInstalledVersionCode(firstOverlay.packageName)
                                ?: theme.versionCode
                            
                            if (isAnyActive) {
                                ThemeInstallState.Installed(installedVersion)
                            } else {
                                ThemeInstallState.InstalledInactive(installedVersion)
                            }
                        }
                        else -> ThemeInstallState.PartiallyInstalled(installedOverlays, theme.overlays.size)
                    }
                }
            }
            theme.id to state
        }
        _themeStates.value = states
        
        val installedCount = states.values.count { 
            it is ThemeInstallState.Installed || it is ThemeInstallState.InstalledInactive 
        }
        Log.d("ThemeStoreViewModel", "Updated install states: $installedCount installed themes out of ${themes.size} total")
    }
    
    fun filterByCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategory = categoryId) }
    }
    
    fun searchThemes(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        if (query.trim().length >= 2) {
            saveSearchQuery(query.trim())
        }
    }
    
    fun getFilteredThemes(ignoreSearchQuery: Boolean = false): List<Theme> {
        val state = _uiState.value
        var themes = state.themes
        
        state.selectedCategory?.let { category ->
            themes = themes.filter { it.category == category }
        }
        
        if (!ignoreSearchQuery && state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            themes = themes.filter { theme ->
                theme.name.lowercase().contains(query) ||
                theme.description.lowercase().contains(query) ||
                theme.author.lowercase().contains(query)
            }
        }
        
        return themes
    }
    
    fun downloadTheme(theme: Theme) {
        if (theme.overlays.isEmpty()) {
            _themeStates.update { 
                it + (theme.id to ThemeInstallState.Error("No overlays available")) 
            }
            return
        }
        
        viewModelScope.launch {
            val overlaysToDownload = if (theme.isUnified) {
                listOf(theme.overlays.first())
            } else {
                theme.overlays.filter { it.enabled }
            }
            
            val downloadedFiles = mutableMapOf<String, java.io.File>()
            
            for ((index, overlay) in overlaysToDownload.withIndex()) {
                val progress = index.toFloat() / overlaysToDownload.size
                _themeStates.update { 
                    it + (theme.id to ThemeInstallState.Downloading(progress, overlay.componentId)) 
                }
                
                val file = downloadOverlay(theme.id, overlay)
                if (file == null) {
                    _themeStates.update { 
                        it + (theme.id to ThemeInstallState.Error("Failed to download ${overlay.componentId}")) 
                    }
                    return@launch
                }
                downloadedFiles[overlay.componentId] = file
            }
            
            _themeStates.update { 
                it + (theme.id to ThemeInstallState.Downloaded(downloadedFiles)) 
            }
            Log.d(TAG, "Successfully downloaded theme ${theme.name}")
        }
    }

    fun installTheme(theme: Theme) {
        val currentState = _themeStates.value[theme.id]
        if (currentState !is ThemeInstallState.Downloaded) {
            _themeStates.update { 
                it + (theme.id to ThemeInstallState.Error("Theme files not found. Please download again.")) 
            }
            return
        }

        val filesToInstall = currentState.files
        
        viewModelScope.launch {
            _themeStates.update { it + (theme.id to ThemeInstallState.Installing) }
            
            var installedCount = 0
            val overlays = if (theme.isUnified) {
                listOf(theme.overlays.first())
            } else {
                 theme.overlays.filter { it.enabled }
            }
            
            for (overlay in overlays) {
                val apkFile = filesToInstall[overlay.componentId]
                if (apkFile == null || !apkFile.exists()) {
                     _themeStates.update { 
                        it + (theme.id to ThemeInstallState.Error("File missing for ${overlay.componentId}")) 
                    }
                    return@launch
                }
                
                val installSuccess = installOverlay(overlay.packageName, apkFile)
                if (!installSuccess) {
                     _themeStates.update { 
                        it + (theme.id to ThemeInstallState.Error("Failed to install ${overlay.componentId}")) 
                    }
                    return@launch
                }
                
                installedCount++
            }

            // Delete files only after all successful installations
            filesToInstall.values.forEach { it.delete() }

            if (theme.isUnified && theme.overlays.isNotEmpty()) {
                val packageName = theme.overlays.first().packageName
                val actualTargets = readTargetsFromInstalledApk(packageName)
                
                if (actualTargets.isNotEmpty()) {
                    val updatedOverlays = theme.overlays.map { overlay ->
                        overlay.copy(targets = actualTargets)
                    }
                    
                    _uiState.update { state ->
                        val updatedThemes = state.themes.map { t ->
                            if (t.id == theme.id) {
                                t.copy(overlays = updatedOverlays)
                            } else t
                        }
                        state.copy(themes = updatedThemes)
                    }
                    
                    Log.d(TAG, "Updated ${theme.name} targets from APK: $actualTargets")
                }
            }
            
            val installedVersion = theme.versionCode
            _themeStates.update { 
                it + (theme.id to ThemeInstallState.InstalledInactive(installedVersion)) 
            }
            
            checkInstallStates()
            Log.d(TAG, "Successfully installed theme ${theme.name} - Ready to apply")
        }
    }

    private fun readTargetsFromInstalledApk(packageName: String): List<String> {
        val targets = mutableListOf<String>()
        
        try {
            val pm = getApplication<Application>().packageManager
            val resources = pm.getResourcesForApplication(packageName)
            
            val arrayToCategoryMap = mapOf(
                "target_android" to "android",
                "target_systemui" to "systemui",
                "target_wifi" to "wifi",
                "target_signal" to "signal"
            )
            
            arrayToCategoryMap.forEach { (arrayName, category) ->
                try {
                    val resId = resources.getIdentifier(arrayName, "array", packageName)
                    if (resId != 0) {
                        val array = resources.getStringArray(resId)
                        if (array.isNotEmpty()) {
                            targets.add(category)
                        }
                    }
                } catch (e: Exception) {
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read targets from $packageName", e)
        }
    
        val targetOrder = listOf("android", "systemui", "wifi", "signal")
        return targetOrder.filter { it in targets }
    }

    private suspend fun downloadOverlay(themeId: String, overlay: ThemeOverlay): java.io.File? {
        var resultFile: java.io.File? = null
        
        downloadManager.downloadTheme(
            "${themeId}_${overlay.componentId}", 
            overlay.downloadUrl
        ).collect { state ->
            when (state) {
                is ThemeDownloadManager.DownloadState.Success -> {
                    resultFile = state.file
                }
                is ThemeDownloadManager.DownloadState.Error -> {
                    Log.e(TAG, "Download failed for ${overlay.componentId}", state.exception)
                }
                else -> {}
            }
        }
        
        return resultFile
    }
        
    private suspend fun installOverlay(packageName: String, apkFile: java.io.File): Boolean {
        var success = false
        
        installer.installTheme(apkFile, packageName).collect { result ->
            success = result is ThemeInstaller.InstallResult.Success
        }
        
        return success
    }
        
    fun disableTheme(theme: Theme) {
        viewModelScope.launch {
            if (theme.isUnified) {
                val packageName = theme.overlays.first().packageName
                themeEngineProxy.clearIconTheme()
                themeEngineProxy.clearCategoryThemesForPackage(packageName)
            } else {
                for (overlay in theme.overlays) {
                    themeEngineProxy.clearCategoryTheme(overlay.componentId)
                }
                themeEngineProxy.notifyThemeChanged()
            }

            refreshComponentStates()
            updateInstallStates(_uiState.value.themes)
            Log.d(TAG, "Disabled theme: ${theme.name}")
        }
    }
        
    fun uninstallTheme(theme: Theme, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val overlaysToUninstall = if (theme.isUnified) {
                listOf(theme.overlays.first())
            } else {
                theme.overlays
            }

            for (overlay in overlaysToUninstall) {
                if (repository.isThemeInstalled(overlay.packageName)) {
                    installer.uninstallTheme(overlay.packageName).collect { result ->
                        if (result is ThemeInstaller.InstallResult.Failure) {
                            Log.e(TAG, "Failed to uninstall ${overlay.componentId}")
                        }
                    }
                }
            }
            
            if (theme.isUnified) {
                val packageName = theme.overlays.first().packageName
                themeEngineProxy.clearIconTheme()
                themeEngineProxy.clearCategoryThemesForPackage(packageName)
            } else {
                val categories = theme.overlays.map { it.componentId }
                themeEngineProxy.disableThemeOverlays(categories)
            }
            
            refreshComponentStates()
            
            delay(500)
            updateInstallStates(_uiState.value.themes)
            
            onComplete?.invoke()
        }
    }

    fun applyTheme(theme: Theme) {
        if (theme.overlays.isEmpty()) {
            _themeStates.update { 
                it + (theme.id to ThemeInstallState.Error("No overlays available")) 
            }
            return
        }
        
        viewModelScope.launch {
            if (theme.isUnified) {
                val overlay = theme.overlays.first()

                val targetsToApply = _pendingComponentChanges.value[theme.id] 
                    ?: emptySet()

                if (targetsToApply.isNotEmpty()) {
                    if (themeEngineProxy.applyThemeComponents(overlay.packageName, targetsToApply.toList())) {
                        _pendingComponentChanges.update { it - theme.id }
                        
                        refreshComponentStates()
                        updateInstallStates(_uiState.value.themes)
                        Log.d(TAG, "Applied unified theme components: ${theme.name}")
                    } else {
                        _themeStates.update { 
                            it + (theme.id to ThemeInstallState.Error("Failed to apply theme")) 
                        }
                    }
                } else {
                     Log.w(TAG, "Attempted to apply theme ${theme.name} with no targets selected")
                }
            } else {
                var success = true
                for (overlay in theme.overlays) {
                    val category = overlay.componentId
                    if (!themeEngineProxy.setCategoryTheme(category, overlay.packageName)) {
                        success = false
                    }
                }
                if (success) {
                    themeEngineProxy.notifyThemeChanged()
                    refreshComponentStates()
                    updateInstallStates(_uiState.value.themes)
                    Log.d(TAG, "Applied overlay theme: ${theme.name}")
                } else {
                    _themeStates.update {
                        it + (theme.id to ThemeInstallState.Error("Failed to apply theme"))
                    }
                }
            }
        }
    }
        
    fun toggleComponent(theme: Theme, componentId: String, enabled: Boolean) {
        if (!theme.isUnified) return
        
        val themeId = theme.id
        
        val isThemeActive = _themeStates.value[themeId] is ThemeInstallState.Installed
        
        val initialState = _initialComponentStates.value[themeId] ?: if (isThemeActive) {
            val themePackage = theme.overlays.firstOrNull()?.packageName
            val currentCategoryThemes = _categoryThemes.value
            
            _enabledComponents.value.filter { componentId ->
                currentCategoryThemes[componentId] == themePackage
            }.intersect(theme.overlays.firstOrNull()?.targets?.toSet() ?: emptySet())
        } else {
            emptySet()
        }.also { initial ->
            _initialComponentStates.update { it + (themeId to initial) }
        }
        
        val currentPending = _pendingComponentChanges.value[themeId] ?: initialState
        
        val newPending = if (enabled) {
            currentPending + componentId
        } else {
            currentPending - componentId
        }
        
        if (newPending == initialState) {
            _pendingComponentChanges.update { it - themeId }
            _initialComponentStates.update { it - themeId }
        } else {
            _pendingComponentChanges.update { it + (themeId to newPending) }
        }
        
        Log.d(TAG, "Pending component toggle: $componentId -> $enabled for ${theme.name}, has actual changes: ${newPending != initialState}")
    }
        
    fun hasPendingChanges(themeId: String): Boolean {
        return _pendingComponentChanges.value.containsKey(themeId)
    }
        
    fun getPendingComponents(themeId: String): Set<String> {
        return _pendingComponentChanges.value[themeId] ?: _enabledComponents.value
    }

    fun applyPendingChanges(theme: Theme) {
        if (!theme.isUnified) return
        
        val themeId = theme.id
        val pending = _pendingComponentChanges.value[themeId] ?: return
        
        viewModelScope.launch {
                val overlay = theme.overlays.first()
                val success = themeEngineProxy.applyThemeComponents(overlay.packageName, pending.toList())
                
                if (success) {
                    _pendingComponentChanges.update { it - themeId }
                    _initialComponentStates.update { it - themeId }
                    refreshComponentStates()
                    updateInstallStates(_uiState.value.themes)
                    Log.d(TAG, "Applied pending changes for ${theme.name}: $pending")
                } else {
                    Log.e(TAG, "Failed to apply pending changes for ${theme.name}")
                }
        }
    }
        
    fun clearPendingChanges(themeId: String) {
        _pendingComponentChanges.update { it - themeId }
        _initialComponentStates.update { it - themeId }
    }
        
    fun isComponentEnabled(componentId: String): Boolean {
        return _enabledComponents.value.contains(componentId)
    }
        
    fun getEnabledComponents(): List<String> {
        return themeEngineProxy.getIconThemeTargets()
    }
        
    fun applyThemeComponents(theme: Theme, selectedCategories: List<String>) {
        if (!theme.isUnified || theme.overlays.isEmpty()) return
        
        viewModelScope.launch {
            val packageName = theme.overlays.first().packageName
            
            val success = themeEngineProxy.applyThemeComponents(packageName, selectedCategories)
            if (success) {
                Log.d(TAG, "Applied ${selectedCategories.size} categories from ${theme.name}")
                refreshComponentStates()
                updateInstallStates(_uiState.value.themes)
            } else {
                Log.e(TAG, "Failed to apply theme components from ${theme.name}")
            }
        }
    }
        
    fun getCategoryThemes(): Map<String, String> {
        return _categoryThemes.value
    }
        
    fun isCategoryFromTheme(category: String, packageName: String): Boolean {
        return themeEngineProxy.getCategoryTheme(category) == packageName
    }

    fun getCategoryThemePackage(category: String): String? {
        return themeEngineProxy.getCategoryTheme(category)
    }
    
    fun clearCategoryTheme(category: String) {
        viewModelScope.launch {
            themeEngineProxy.clearCategoryTheme(category)
            refreshComponentStates()
            updateInstallStates(_uiState.value.themes)
        }
    }
    
    fun clearError(themeId: String) {
        val currentState = _themeStates.value[themeId]
        if (currentState is ThemeInstallState.Error) {
            val theme = _uiState.value.themes.find { it.id == themeId }
            theme?.let {
                val installedOverlays = it.overlays.filter { overlay ->
                    repository.isThemeInstalled(overlay.packageName)
                }.map { overlay -> overlay.componentId }.toSet()
                
                val newState = when {
                    installedOverlays.isEmpty() -> ThemeInstallState.NotInstalled
                    installedOverlays.size == it.overlays.size -> 
                        ThemeInstallState.Installed(it.versionCode)
                    else -> ThemeInstallState.PartiallyInstalled(installedOverlays, it.overlays.size)
                }
                _themeStates.update { states -> states + (themeId to newState) }
            }
        }
    }

    fun loadIconPacks() {
        viewModelScope.launch {
            val (packs, currentPack) = withContext(Dispatchers.IO) {
                repository.getInstalledIconPacks() to themeEngineProxy.getIconPack()
            }
            _uiState.update {
                it.copy(
                    iconPacks = packs,
                    currentIconPack = currentPack
                )
            }
        }
    }

    fun applyIconPack(packageName: String) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (packageName.isEmpty()) themeEngineProxy.clearIconPack()
                else themeEngineProxy.setIconPack(packageName)
            }
            if (success) {
                _uiState.update { it.copy(currentIconPack = if (packageName.isEmpty()) null else packageName) }
            }
        }
    }

    fun loadThemedIconStyle() {
        viewModelScope.launch {
            val (style, enabled) = withContext(Dispatchers.IO) {
                themeEngineProxy.getThemedIconStyle() to themeEngineProxy.isThemedIconsEnabled()
            }
            _uiState.update {
                it.copy(
                    themedIconStyle = style,
                    themedIconsEnabled = enabled
                )
            }
        }
    }

    fun setThemedIconStyle(style: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { themeEngineProxy.setThemedIconStyle(style) }
            _uiState.update { it.copy(themedIconStyle = style) }
        }
    }

    fun setThemedIconsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                themeEngineProxy.setThemedIconsEnabled(enabled)
                if (enabled) themeEngineProxy.setIconPack("")
            }
            _uiState.update { it.copy(themedIconsEnabled = enabled) }
            if (enabled) {
                _uiState.update { it.copy(currentIconPack = null) }
            }
        }
    }
    
    private fun loadSearchHistory() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                val historyJson = sharedPrefs.getString(KEY_SEARCH_HISTORY, null)
                    ?: return@withContext null
                runCatching {
                    val jsonArray = JSONArray(historyJson)
                    val list = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                    list
                }.onFailure { Log.e(TAG, "Failed to load search history", it) }
                    .getOrDefault(emptyList())
            }
            if (history != null) _searchHistory.value = history
        }
    }
    
    private fun saveSearchQuery(query: String) {
        val currentHistory = _searchHistory.value.toMutableList()
        
        currentHistory.remove(query)
        
        currentHistory.add(0, query)
        
        if (currentHistory.size > MAX_SEARCH_HISTORY) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _searchHistory.value = currentHistory
        
        try {
            val jsonArray = org.json.JSONArray(currentHistory)
            sharedPrefs.edit()
                .putString(KEY_SEARCH_HISTORY, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save search history", e)
        }
    }
    
    fun removeSearchHistoryItem(query: String) {
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(query)
        _searchHistory.value = currentHistory
        
        try {
            val jsonArray = org.json.JSONArray(currentHistory)
            sharedPrefs.edit()
                .putString(KEY_SEARCH_HISTORY, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update search history", e)
        }
    }
    
    fun getThemeEngineProxy(): ThemeEngineProxy = themeEngineProxy

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        sharedPrefs.edit()
            .remove(KEY_SEARCH_HISTORY)
            .apply()
    }
}

data class ThemeStoreUiState(
    val isLoading: Boolean = true,
    val themes: List<Theme> = emptyList(),
    val categories: List<ThemeCategory> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val error: String? = null,
    val iconPacks: List<IconPack> = emptyList(),
    val currentIconPack: String? = null,
    val themedIconStyle: String = ThemeEngineProxy.Companion.ThemedIconStyle.AXION,
    val themedIconsEnabled: Boolean = false
)

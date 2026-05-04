/*
 * Copyright (C) 2025-2026 AxionOS Project
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

package com.android.axion.axthemestore.engine

import android.content.Context
import android.content.om.OverlayManager
import android.content.res.ThemeEngine
import android.hardware.fingerprint.FingerprintManager
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

class ThemeEngineProxy(private val context: Context) {

    fun isUdfpsSupported(): Boolean {
        return try {
            val fm = context.getSystemService(FingerprintManager::class.java)
            val props = fm?.sensorPropertiesInternal
            props?.any { it.isAnyUdfpsType } == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check UDFPS support", e)
            false
        }
    }

    companion object {
        private const val TAG = "ThemeEngineProxy"

        private val workerThread: HandlerThread by lazy {
            HandlerThread("ThemeEngineProxy-worker").apply { start() }
        }
        private val workerHandler: Handler by lazy { Handler(workerThread.looper) }

        const val SETTINGS_THEME_ENGINE_DATA = "theme_engine_data"
        private const val OVERLAY_CATEGORY_PREFIX = "android.theme.customization."

        private val CATEGORY_ALIASES = mapOf(
            "back_gesture" to "android.theme.customization.back_gesture",
            "charging_animation" to "android.theme.customization.charging_animation",
            "battery_style" to "android.theme.customization.battery_style",
            "udfps_animation" to "android.theme.customization.udfps_animation",
            "udfps_icon" to "android.theme.customization.udfps_icon",
            "qs_waveform" to "android.theme.customization.qs_waveform",
        )

        private val SYNCED_OVERLAY_CATEGORIES = setOf(
            "android.theme.customization.icon_pack.android",
            "android.theme.customization.icon_pack.systemui",
            "android.theme.customization.back_gesture",
            "android.theme.customization.charging_animation",
            "android.theme.customization.battery_style",
            "android.theme.customization.udfps_animation",
            "android.theme.customization.udfps_icon",
            "android.theme.customization.qs_waveform",
        )

        object Category {
            const val STATUSBAR_WIFI = "statusbar_wifi"
            const val STATUSBAR_SIGNAL = "statusbar_signal"
            const val ANDROID = "android"
            const val SYSTEMUI = "systemui"
            const val UI_VOLUME = "ui_volume"
            const val ICON_PACK = "icon_pack"
        }

        object ThemedIconStyle {
            const val AXION = "axion"
            const val AOSP = "aosp"
        }

        const val THEMED_ICON_STYLE_SETTING = "themed_icon_style"
        const val THEMED_ICONS_ENABLED_SETTING = "themed_icons"
    }

    fun getThemeConfig(): ThemeEngineConfig {
        return try {
            val json = Settings.Secure.getString(context.contentResolver, SETTINGS_THEME_ENGINE_DATA)
            if (json.isNullOrBlank()) ThemeEngineConfig() else parseConfig(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse theme config", e)
            ThemeEngineConfig()
        }
    }

    private fun saveThemeConfig(config: ThemeEngineConfig): Boolean {
        return try {
            val json = serializeConfig(config)
            Settings.Secure.putString(context.contentResolver, SETTINGS_THEME_ENGINE_DATA, json)
            Log.d(TAG, "Saved theme config: $json")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save theme config", e)
            false
        }
    }

    private fun parseConfig(jsonStr: String): ThemeEngineConfig {
        return try {
            val json = JSONObject(jsonStr)
            val version = json.optInt("version", 1)
            val iconTheme = if (json.has("systemThemeIcons") && !json.isNull("systemThemeIcons"))
                json.getString("systemThemeIcons") else null
            val iconThemeTargets = mutableListOf<String>()
            val targetsArr = json.optJSONArray("systemThemeIconTargets")
            if (targetsArr != null) {
                for (i in 0 until targetsArr.length()) {
                    iconThemeTargets.add(targetsArr.getString(i))
                }
            }

            val categoryThemes = mutableMapOf<String, String>()
            val catThemesObj = json.optJSONObject("categoryThemes")
            catThemesObj?.keys()?.forEach { key ->
                val pkgName = catThemesObj.optString(key)
                if (pkgName.isNotBlank()) categoryThemes[normalizeOverlayCategory(key)] = pkgName
            }

            val themesMap = mutableMapOf<String, ThemeCategoryConfig>()
            val themesObj = json.optJSONObject("themes")
            themesObj?.keys()?.forEach { key ->
                val catObj = themesObj.getJSONObject(key)
                val enabled = catObj.optBoolean("enabled", false)
                val pkgName = if (catObj.has("packageName") && !catObj.isNull("packageName"))
                    catObj.getString("packageName") else null
                val styleId = if (catObj.has("styleId") && !catObj.isNull("styleId"))
                    catObj.getString("styleId") else null
                themesMap[key] = ThemeCategoryConfig(enabled, pkgName, styleId)
            }

            ThemeEngineConfig(version, themesMap, iconTheme, iconThemeTargets, categoryThemes)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config JSON", e)
            ThemeEngineConfig()
        }
    }

    private fun serializeConfig(config: ThemeEngineConfig): String {
        val json = JSONObject()
        json.put("version", config.version)
        json.put("systemThemeIcons", config.iconTheme)
        val targetsArr = org.json.JSONArray()
        config.iconThemeTargets.forEach { targetsArr.put(it) }
        json.put("systemThemeIconTargets", targetsArr)

        val catThemesObj = JSONObject()
        config.categoryThemes.forEach { (key, value) -> catThemesObj.put(key, value) }
        json.put("categoryThemes", catThemesObj)

        val themesObj = JSONObject()
        config.themes.forEach { (key, value) ->
            val catObj = JSONObject()
            catObj.put("enabled", value.enabled)
            catObj.put("packageName", value.packageName)
            catObj.put("styleId", value.styleId)
            themesObj.put(key, catObj)
        }
        json.put("themes", themesObj)

        return json.toString()
    }

    fun setIconPack(packageName: String): Boolean {
        val config = getThemeConfig()
        val updatedThemes = config.themes.toMutableMap().apply {
            put(Category.ICON_PACK, ThemeCategoryConfig(enabled = true, packageName = packageName))
        }
        return saveThemeConfig(config.copy(themes = updatedThemes))
    }

    fun getIconPack(): String? {
        val entry = getThemeConfig().themes[Category.ICON_PACK]
        return if (entry?.enabled == true) entry.packageName else null
    }

    fun clearIconPack(): Boolean {
        val config = getThemeConfig()
        val updatedThemes = config.themes.toMutableMap().apply {
            put(Category.ICON_PACK, ThemeCategoryConfig(enabled = false))
        }
        return saveThemeConfig(config.copy(themes = updatedThemes))
    }

    fun getEnabledThemes(): Map<String, String> {
        return getThemeConfig().themes
            .filter { it.value.enabled && it.value.packageName != null }
            .mapValues { it.value.packageName!! }
    }

    fun disableThemeOverlays(categories: List<String>): Boolean {
        val config = getThemeConfig()
        val updatedThemes = config.themes.toMutableMap().apply {
            categories.forEach { put(it, ThemeCategoryConfig(enabled = false)) }
        }
        return saveThemeConfig(config.copy(themes = updatedThemes))
    }

    fun setIconThemeWithTargets(packageName: String, targets: List<String>): Boolean {
        val config = getThemeConfig()
        val newCategoryThemes = config.categoryThemes.toMutableMap().apply {
            targets.forEach { put(it, packageName) }
        }
        val saved = saveThemeConfig(config.copy(
            iconTheme = packageName,
            iconThemeTargets = targets,
            categoryThemes = newCategoryThemes
        ))
        if (saved) syncOverlayPackagesSettings(newCategoryThemes)
        return saved
    }

    fun getIconTheme(): String? = getThemeConfig().iconTheme

    fun getIconThemeTargets(): List<String> = getThemeConfig().iconThemeTargets

    fun clearIconTheme(): Boolean {
        val config = getThemeConfig()
        return saveThemeConfig(config.copy(iconTheme = null, iconThemeTargets = emptyList()))
    }

    fun setCategoryTheme(category: String, packageName: String): Boolean {
        val overlayCategory = normalizeOverlayCategory(category)
        val config = getThemeConfig()
        val oldPackage = config.categoryThemes[overlayCategory]
        val updated = buildUpdatedCategoryConfig(config, config.categoryThemes + (overlayCategory to packageName))
        val saved = saveThemeConfig(updated)
        if (saved) {
            syncOverlayPackagesSettings(updated.categoryThemes)
            workerHandler.post {
                applyOverlays(packageName, setOfNotNull(oldPackage?.takeIf { it != packageName }))
            }
        }
        return saved
    }

    fun clearCategoryTheme(category: String): Boolean {
        val overlayCategory = normalizeOverlayCategory(category)
        val config = getThemeConfig()
        val oldPackage = config.categoryThemes[overlayCategory]
        val updated = buildUpdatedCategoryConfig(config, config.categoryThemes - overlayCategory)
        val saved = saveThemeConfig(updated)
        if (saved) {
            syncOverlayPackagesSettings(updated.categoryThemes)
            workerHandler.post {
                applyOverlays(null, setOfNotNull(oldPackage))
            }
        }
        return saved
    }

    fun applyThemeComponents(packageName: String, categories: List<String>): Boolean {
        val overlayCategories = categories.map(::normalizeOverlayCategory)
        val config = getThemeConfig()
        val newCategoryThemes = config.categoryThemes.toMutableMap().apply {
            entries.removeAll { it.value == packageName && it.key !in overlayCategories }
            overlayCategories.forEach { put(it, packageName) }
        }
        val oldPackages = overlayCategories
            .mapNotNull { config.categoryThemes[it] }
            .filter { it != packageName }
            .toSet()
        val updated = buildUpdatedCategoryConfig(config, newCategoryThemes)
        val saved = saveThemeConfig(updated)
        if (saved) {
            syncOverlayPackagesSettings(updated.categoryThemes)
            workerHandler.post { applyOverlays(packageName, oldPackages) }
        }
        return saved
    }

    fun clearCategoryThemesForPackage(packageName: String): Boolean {
        val config = getThemeConfig()
        val updated = buildUpdatedCategoryConfig(config, config.categoryThemes.filterValues { it != packageName })
        val saved = saveThemeConfig(updated)
        if (saved) {
            syncOverlayPackagesSettings(updated.categoryThemes)
            workerHandler.post {
                applyOverlays(null, setOf(packageName))
            }
        }
        return saved
    }

    fun getCategoryTheme(category: String): String? =
        getThemeConfig().categoryThemes[normalizeOverlayCategory(category)]

    fun getCategoryThemes(): Map<String, String> = getThemeConfig().categoryThemes.withAliases()

    private fun buildUpdatedCategoryConfig(config: ThemeEngineConfig, newCategoryThemes: Map<String, String>): ThemeEngineConfig {
        val uniquePackages = newCategoryThemes.values.toSet()
        return config.copy(
            iconTheme = if (uniquePackages.size == 1) uniquePackages.first() else null,
            iconThemeTargets = newCategoryThemes.keys.toList(),
            categoryThemes = newCategoryThemes
        )
    }

    private fun applyOverlays(packageName: String?, oldPackages: Set<String>) {
        try {
            val om = context.getSystemService(OverlayManager::class.java) ?: return
            for (oldPkg in oldPackages) {
                try {
                    om.setEnabled(oldPkg, false, UserHandle.SYSTEM)
                    Log.d(TAG, "disabled overlay: $oldPkg")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to disable overlay: $oldPkg", e)
                }
            }
            packageName ?: return
            workerHandler.postDelayed({
                try {
                    om.setEnabledExclusiveInCategory(packageName, UserHandle.SYSTEM)
                    Log.d(TAG, "enabled overlay: $packageName")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable overlay: $packageName", e)
                }
            }, 500)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply overlays for $packageName", e)
        }
    }

    private fun syncOverlayPackagesSettings(categoryThemes: Map<String, String>) {
        try {
            val resolver = context.contentResolver
            val current = Settings.Secure.getStringForUser(
                resolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.myUserId()
            )
            val json = if (current.isNullOrBlank()) JSONObject() else JSONObject(current)

            SYNCED_OVERLAY_CATEGORIES.forEach { category ->
                json.remove(category)
                json.remove(category.removePrefix(OVERLAY_CATEGORY_PREFIX))
            }
            categoryThemes.forEach { (category, packageName) ->
                val overlayCategory = normalizeOverlayCategory(category)
                if (overlayCategory in SYNCED_OVERLAY_CATEGORIES) {
                    json.put(overlayCategory, packageName)
                }
            }

            Settings.Secure.putStringForUser(
                resolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                json.toString(),
                UserHandle.myUserId()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync overlay packages settings", e)
        }
    }

    private fun normalizeOverlayCategory(category: String): String =
        if (category.startsWith(OVERLAY_CATEGORY_PREFIX)) category else CATEGORY_ALIASES[category] ?: category

    private fun Map<String, String>.withAliases(): Map<String, String> {
        val result = toMutableMap()
        CATEGORY_ALIASES.forEach { (alias, category) ->
            result[category]?.let { result[alias] = it }
        }
        return result
    }

    fun setThemedIconStyle(style: String) {
        Settings.Secure.putString(context.contentResolver, THEMED_ICON_STYLE_SETTING, style)
    }

    fun getThemedIconStyle(): String {
        return Settings.Secure.getString(context.contentResolver, THEMED_ICON_STYLE_SETTING)
            ?: ThemedIconStyle.AXION
    }

    fun setThemedIconsEnabled(enabled: Boolean) {
        Settings.Secure.putInt(context.contentResolver, THEMED_ICONS_ENABLED_SETTING, if (enabled) 1 else 0)
    }

    fun isThemedIconsEnabled(): Boolean {
        return Settings.Secure.getInt(context.contentResolver, THEMED_ICONS_ENABLED_SETTING, 0) == 1
    }

    fun getAvailableOverlays(category: String): List<String> {
        return try {
            ThemeEngine.getInstance(context)?.getAvailableOverlays(category) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available overlays", e)
            emptyList()
        }
    }

    fun notifyThemeChanged() {
        try {
            ThemeEngine.getInstance(context)?.notifyThemeChanged(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify theme changed", e)
        }
    }
}

data class ThemeEngineConfig(
    val version: Int = 1,
    val themes: Map<String, ThemeCategoryConfig> = emptyMap(),
    val iconTheme: String? = null,
    val iconThemeTargets: List<String> = emptyList(),
    val categoryThemes: Map<String, String> = emptyMap(),
)

data class ThemeCategoryConfig(
    val enabled: Boolean = false,
    val packageName: String? = null,
    val styleId: String? = null
)

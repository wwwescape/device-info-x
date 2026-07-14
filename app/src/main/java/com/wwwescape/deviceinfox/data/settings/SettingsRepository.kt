package com.wwwescape.deviceinfox.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Mirrors [ThemeMode] outside DataStore, which is Flow-only/async. [MainActivity] reads this
 * synchronously in `attachBaseContext`, before any Compose or DataStore code can run, so the
 * window's initial (pre-Compose) theme resolution honors the user's choice instead of always
 * following the raw system day/night setting. */
const val THEME_PREFS_NAME = "theme_prefs_sync"
const val THEME_MODE_PREF_KEY = "theme_mode"

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    private val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
    private val THEME_CONTRAST_KEY = stringPreferencesKey("theme_contrast")
    private val PURE_DARK_KEY = booleanPreferencesKey("pure_dark")
    private val ABSOLUTE_DARK_KEY = booleanPreferencesKey("absolute_dark")
    private val TEMPERATURE_UNIT_KEY = stringPreferencesKey("temperature_unit")
    private val REFRESH_INTERVAL_KEY = stringPreferencesKey("refresh_interval")

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[THEME_MODE_KEY].toEnumOrDefault(ThemeMode.SYSTEM),
            useDynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: true,
            colorTheme = prefs[COLOR_THEME_KEY].toEnumOrDefault(ColorTheme.DEFAULT),
            themeContrast = prefs[THEME_CONTRAST_KEY].toEnumOrDefault(ThemeContrast.STANDARD),
            pureDark = prefs[PURE_DARK_KEY] ?: false,
            absoluteDark = prefs[ABSOLUTE_DARK_KEY] ?: false,
            temperatureUnit = prefs[TEMPERATURE_UNIT_KEY].toEnumOrDefault(TemperatureUnit.CELSIUS),
            refreshInterval = prefs[REFRESH_INTERVAL_KEY].toEnumOrDefault(RefreshInterval.NORMAL),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE_KEY] = mode.name }
        context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(THEME_MODE_PREF_KEY, mode.name).apply()
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    suspend fun setColorTheme(theme: ColorTheme) {
        context.settingsDataStore.edit { it[COLOR_THEME_KEY] = theme.name }
    }

    suspend fun setThemeContrast(contrast: ThemeContrast) {
        context.settingsDataStore.edit { it[THEME_CONTRAST_KEY] = contrast.name }
    }

    suspend fun setPureDark(enabled: Boolean) {
        context.settingsDataStore.edit { it[PURE_DARK_KEY] = enabled }
    }

    suspend fun setAbsoluteDark(enabled: Boolean) {
        context.settingsDataStore.edit { it[ABSOLUTE_DARK_KEY] = enabled }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.settingsDataStore.edit { it[TEMPERATURE_UNIT_KEY] = unit.name }
    }

    suspend fun setRefreshInterval(interval: RefreshInterval) {
        context.settingsDataStore.edit { it[REFRESH_INTERVAL_KEY] = interval.name }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default
}

/** [DeviceInfoXWidget] is a Glance widget, not a Hilt-supported Android component (no
 * `@AndroidEntryPoint`), so it can't receive [SettingsRepository] via constructor/field
 * injection — this is Hilt's documented escape hatch for reaching the graph from such classes. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsRepositoryEntryPoint {
    fun settingsRepository(): SettingsRepository
}

fun Context.settingsRepositoryEntryPoint(): SettingsRepository =
    EntryPointAccessors.fromApplication(applicationContext, SettingsRepositoryEntryPoint::class.java)
        .settingsRepository()

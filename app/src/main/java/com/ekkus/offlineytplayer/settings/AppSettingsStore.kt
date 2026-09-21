package com.ekkus.offlineytplayer.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Typed, durable application settings persisted in Android SharedPreferences.
 *
 * SharedPreferences is intentionally used as the documented durable store for the first settings
 * slice because these values are small, synchronous defaults that must be available during app
 * bootstrap without introducing another storage dependency. Later settings slices can migrate keys
 * behind this interface without changing UI callers.
 */
interface AppSettingsStore : Closeable {
    fun snapshot(): AppSettingsSnapshot
    fun update(mutator: AppSettingsMutation.() -> Unit): AppSettingsSnapshot
    fun observe(observer: (AppSettingsSnapshot) -> Unit): SettingsSubscription
}

data class AppSettingsSnapshot(
    val schemaVersion: Int = APP_SETTINGS_SCHEMA_VERSION,
    val defaultQuality: String = AppSettingsDefaults.DefaultQuality,
    val wifiOnlyDownloads: Boolean = AppSettingsDefaults.WifiOnlyDownloads,
    val maxConcurrentDownloads: Int = AppSettingsDefaults.MaxConcurrentDownloads,
    val subtitleDefault: String = AppSettingsDefaults.SubtitleDefault,
    val rememberPlaybackPosition: Boolean = AppSettingsDefaults.RememberPlaybackPosition,
    val playbackSpeed: Float = AppSettingsDefaults.PlaybackSpeed,
    val appearance: AppearanceSetting = AppSettingsDefaults.Appearance,
    val libraryLayout: LibraryLayoutSetting = AppSettingsDefaults.LibraryLayout,
)

class AppSettingsMutation internal constructor(current: AppSettingsSnapshot) {
    var defaultQuality: String = current.defaultQuality
    var wifiOnlyDownloads: Boolean = current.wifiOnlyDownloads
    var maxConcurrentDownloads: Int = current.maxConcurrentDownloads
    var subtitleDefault: String = current.subtitleDefault
    var rememberPlaybackPosition: Boolean = current.rememberPlaybackPosition
    var playbackSpeed: Float = current.playbackSpeed
    var appearance: AppearanceSetting = current.appearance
    var libraryLayout: LibraryLayoutSetting = current.libraryLayout

    internal fun build(): AppSettingsSnapshot = AppSettingsSnapshot(
        defaultQuality = defaultQuality.trim().ifEmpty { AppSettingsDefaults.DefaultQuality },
        wifiOnlyDownloads = wifiOnlyDownloads,
        maxConcurrentDownloads = maxConcurrentDownloads.coerceIn(1, AppSettingsDefaults.MaxConcurrentDownloadsUpperBound),
        subtitleDefault = subtitleDefault.trim().ifEmpty { AppSettingsDefaults.SubtitleDefault },
        rememberPlaybackPosition = rememberPlaybackPosition,
        playbackSpeed = playbackSpeed.takeIf { it.isFinite() }?.coerceIn(0.25f, 3.0f) ?: AppSettingsDefaults.PlaybackSpeed,
        appearance = appearance,
        libraryLayout = libraryLayout,
    )
}

enum class AppearanceSetting { System, Light, Dark }

enum class LibraryLayoutSetting { List, Grid }

fun interface SettingsSubscription : Closeable {
    override fun close()
}

object AppSettingsDefaults {
    const val DefaultQuality = "Best compatible"
    const val WifiOnlyDownloads = true
    const val MaxConcurrentDownloads = 2
    const val MaxConcurrentDownloadsUpperBound = 8
    const val SubtitleDefault = "Preferred language"
    const val RememberPlaybackPosition = true
    const val PlaybackSpeed = 1.0f
    val Appearance = AppearanceSetting.System
    val LibraryLayout = LibraryLayoutSetting.List
}

const val APP_SETTINGS_SCHEMA_VERSION = 1

class SharedPreferencesAppSettingsStore private constructor(
    private val preferences: SharedPreferences,
) : AppSettingsStore {
    private val observers = CopyOnWriteArraySet<(AppSettingsSnapshot) -> Unit>()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        notifyObservers(snapshot())
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        ensureSchemaVersion()
    }

    override fun snapshot(): AppSettingsSnapshot = AppSettingsSnapshot(
        schemaVersion = preferences.getInt(Keys.SchemaVersion, APP_SETTINGS_SCHEMA_VERSION),
        defaultQuality = preferences.getString(Keys.DefaultQuality, AppSettingsDefaults.DefaultQuality)
            ?: AppSettingsDefaults.DefaultQuality,
        wifiOnlyDownloads = preferences.getBoolean(Keys.WifiOnlyDownloads, AppSettingsDefaults.WifiOnlyDownloads),
        maxConcurrentDownloads = preferences
            .getInt(Keys.MaxConcurrentDownloads, AppSettingsDefaults.MaxConcurrentDownloads)
            .coerceIn(1, AppSettingsDefaults.MaxConcurrentDownloadsUpperBound),
        subtitleDefault = preferences.getString(Keys.SubtitleDefault, AppSettingsDefaults.SubtitleDefault)
            ?: AppSettingsDefaults.SubtitleDefault,
        rememberPlaybackPosition = preferences.getBoolean(
            Keys.RememberPlaybackPosition,
            AppSettingsDefaults.RememberPlaybackPosition,
        ),
        playbackSpeed = preferences
            .getFloat(Keys.PlaybackSpeed, AppSettingsDefaults.PlaybackSpeed)
            .takeIf { it.isFinite() }
            ?.coerceIn(0.25f, 3.0f)
            ?: AppSettingsDefaults.PlaybackSpeed,
        appearance = preferences.enumValue(Keys.Appearance, AppSettingsDefaults.Appearance),
        libraryLayout = preferences.enumValue(Keys.LibraryLayout, AppSettingsDefaults.LibraryLayout),
    )

    override fun update(mutator: AppSettingsMutation.() -> Unit): AppSettingsSnapshot {
        val next = AppSettingsMutation(snapshot()).apply(mutator).build()
        preferences.edit()
            .putInt(Keys.SchemaVersion, APP_SETTINGS_SCHEMA_VERSION)
            .putString(Keys.DefaultQuality, next.defaultQuality)
            .putBoolean(Keys.WifiOnlyDownloads, next.wifiOnlyDownloads)
            .putInt(Keys.MaxConcurrentDownloads, next.maxConcurrentDownloads)
            .putString(Keys.SubtitleDefault, next.subtitleDefault)
            .putBoolean(Keys.RememberPlaybackPosition, next.rememberPlaybackPosition)
            .putFloat(Keys.PlaybackSpeed, next.playbackSpeed)
            .putString(Keys.Appearance, next.appearance.name)
            .putString(Keys.LibraryLayout, next.libraryLayout.name)
            .apply()
        notifyObservers(next)
        return next
    }

    override fun observe(observer: (AppSettingsSnapshot) -> Unit): SettingsSubscription {
        observers += observer
        observer(snapshot())
        return SettingsSubscription { observers -= observer }
    }

    override fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        observers.clear()
    }

    private fun ensureSchemaVersion() {
        if (!preferences.contains(Keys.SchemaVersion)) {
            preferences.edit().putInt(Keys.SchemaVersion, APP_SETTINGS_SCHEMA_VERSION).apply()
        }
    }

    private fun notifyObservers(snapshot: AppSettingsSnapshot) {
        observers.forEach { observer -> observer(snapshot) }
    }

    companion object {
        private const val PreferencesName = "offline_yt_player_settings"

        fun open(context: Context): SharedPreferencesAppSettingsStore = SharedPreferencesAppSettingsStore(
            context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE),
        )
    }
}

private object Keys {
    const val SchemaVersion = "schema_version"
    const val DefaultQuality = "download.default_quality"
    const val WifiOnlyDownloads = "download.wifi_only"
    const val MaxConcurrentDownloads = "download.max_concurrent"
    const val SubtitleDefault = "download.subtitle_default"
    const val RememberPlaybackPosition = "playback.remember_position"
    const val PlaybackSpeed = "playback.speed"
    const val Appearance = "appearance.theme"
    const val LibraryLayout = "appearance.library_layout"
}

private inline fun <reified T : Enum<T>> SharedPreferences.enumValue(key: String, defaultValue: T): T {
    val stored = getString(key, defaultValue.name) ?: return defaultValue
    return enumValues<T>().firstOrNull { it.name == stored } ?: defaultValue
}

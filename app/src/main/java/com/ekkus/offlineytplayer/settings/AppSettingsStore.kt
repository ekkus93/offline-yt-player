package com.ekkus.offlineytplayer.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet

internal enum class AppearancePreference { System, Light, Dark }

internal data class AppSettings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appearance: AppearancePreference = AppearancePreference.System,
    val libraryGrid: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

internal object AppSettingsCodec {
    fun decode(values: Map<String, *>): AppSettings {
        val schemaVersion = (values[KEY_SCHEMA_VERSION] as? Number)?.toInt() ?: 0
        val appearance = (values[KEY_APPEARANCE] as? String)
            ?.let { raw -> AppearancePreference.entries.firstOrNull { it.name == raw } }
            ?: AppearancePreference.System
        val libraryGrid = values[KEY_LIBRARY_GRID] as? Boolean ?: false
        return migrate(AppSettings(schemaVersion, appearance, libraryGrid))
    }

    fun encode(settings: AppSettings): Map<String, Any> = mapOf(
        KEY_SCHEMA_VERSION to AppSettings.CURRENT_SCHEMA_VERSION,
        KEY_APPEARANCE to settings.appearance.name,
        KEY_LIBRARY_GRID to settings.libraryGrid,
    )

    private fun migrate(settings: AppSettings): AppSettings = when {
        settings.schemaVersion <= 0 -> settings.copy(schemaVersion = AppSettings.CURRENT_SCHEMA_VERSION)
        settings.schemaVersion == AppSettings.CURRENT_SCHEMA_VERSION -> settings
        else -> AppSettings()
    }

    const val KEY_SCHEMA_VERSION = "schema_version"
    const val KEY_APPEARANCE = "appearance"
    const val KEY_LIBRARY_GRID = "library_grid"
}

internal class AppSettingsStore private constructor(
    private val preferences: SharedPreferences,
) : Closeable {
    private val observers = CopyOnWriteArraySet<(AppSettings) -> Unit>()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val current = current()
        observers.forEach { observer -> observer(current) }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        persistMigrationIfNeeded()
    }

    fun current(): AppSettings = AppSettingsCodec.decode(preferences.all)

    fun observe(observer: (AppSettings) -> Unit): Closeable {
        observers += observer
        observer(current())
        return Closeable { observers -= observer }
    }

    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val updated = transform(current()).copy(schemaVersion = AppSettings.CURRENT_SCHEMA_VERSION)
        write(updated)
        return updated
    }

    override fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        observers.clear()
    }

    private fun persistMigrationIfNeeded() {
        val current = current()
        val storedVersion = preferences.getInt(AppSettingsCodec.KEY_SCHEMA_VERSION, 0)
        if (storedVersion != AppSettings.CURRENT_SCHEMA_VERSION) write(current)
    }

    private fun write(settings: AppSettings) {
        val values = AppSettingsCodec.encode(settings)
        preferences.edit()
            .putInt(AppSettingsCodec.KEY_SCHEMA_VERSION, values.getValue(AppSettingsCodec.KEY_SCHEMA_VERSION) as Int)
            .putString(AppSettingsCodec.KEY_APPEARANCE, values.getValue(AppSettingsCodec.KEY_APPEARANCE) as String)
            .putBoolean(AppSettingsCodec.KEY_LIBRARY_GRID, values.getValue(AppSettingsCodec.KEY_LIBRARY_GRID) as Boolean)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "offline_yt_player_settings"

        fun open(context: Context): AppSettingsStore = AppSettingsStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
    }
}

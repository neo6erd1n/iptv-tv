package ru.iptvtv.data.settings

import android.content.Context
import ru.iptvtv.domain.repository.StreamSettingsRepository

class SharedPreferencesStreamSettingsRepository(
    context: Context,
) : StreamSettingsRepository {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override suspend fun getStreamUrl(): String =
        preferences.getString(STREAM_URL_KEY, "").orEmpty()

    override suspend fun saveStreamUrl(url: String) {
        preferences.edit().putString(STREAM_URL_KEY, url).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "iptv_settings"
        const val STREAM_URL_KEY = "stream_url"
    }
}

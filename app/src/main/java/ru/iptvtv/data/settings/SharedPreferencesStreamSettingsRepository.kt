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

    override suspend fun getEpgUrl(): String =
        preferences.getString(EPG_URL_KEY, "").orEmpty()

    override suspend fun saveEpgUrl(url: String) {
        preferences.edit().putString(EPG_URL_KEY, url).apply()
    }

    override suspend fun getFavoriteChannelIds(): Set<String> =
        preferences.getStringSet(FAVORITES_KEY, emptySet()).orEmpty().toSet()

    override suspend fun saveFavoriteChannelIds(ids: Set<String>) {
        preferences.edit().putStringSet(FAVORITES_KEY, ids.toSet()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "iptv_settings"
        const val STREAM_URL_KEY = "stream_url"
        const val EPG_URL_KEY = "epg_url"
        const val FAVORITES_KEY = "favorite_channel_ids"
    }
}

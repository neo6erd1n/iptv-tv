package ru.iptvtv.domain.repository

interface StreamSettingsRepository {
    suspend fun getStreamUrl(): String
    suspend fun saveStreamUrl(url: String)
    suspend fun getEpgUrl(): String
    suspend fun saveEpgUrl(url: String)
    suspend fun getFavoriteChannelIds(): Set<String>
    suspend fun saveFavoriteChannelIds(ids: Set<String>)
}

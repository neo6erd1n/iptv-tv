package ru.iptvtv.domain.repository

interface StreamSettingsRepository {
    suspend fun getStreamUrl(): String
    suspend fun saveStreamUrl(url: String)
}

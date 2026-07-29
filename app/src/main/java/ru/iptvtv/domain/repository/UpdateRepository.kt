package ru.iptvtv.domain.repository

import ru.iptvtv.domain.model.AppUpdate

interface UpdateRepository {
    suspend fun getAvailableUpdate(currentVersion: String): AppUpdate?
}

package ru.iptvtv.domain.repository

import ru.iptvtv.domain.model.Channel

interface ChannelRepository {
    suspend fun getChannels(playlistUrl: String, epgUrl: String = ""): List<Channel>
    suspend fun loadCurrentPrograms(
        channels: List<Channel>,
        epgUrl: String,
        forceRefresh: Boolean = false,
    ): List<Channel>
    suspend fun getLastEpgUpdateAt(epgUrl: String): Long?
    suspend fun shouldRefreshEpg(epgUrl: String): Boolean
    suspend fun getPrograms(channel: Channel, epgUrl: String): List<ru.iptvtv.domain.model.Program>
    suspend fun searchPrograms(
        channels: List<Channel>,
        epgUrl: String,
        query: String,
    ): List<ru.iptvtv.domain.model.ProgramSearchResult>
}

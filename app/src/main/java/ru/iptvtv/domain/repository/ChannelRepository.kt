package ru.iptvtv.domain.repository

import ru.iptvtv.domain.model.Channel

interface ChannelRepository {
    suspend fun getChannels(playlistUrl: String, epgUrl: String = ""): List<Channel>
}

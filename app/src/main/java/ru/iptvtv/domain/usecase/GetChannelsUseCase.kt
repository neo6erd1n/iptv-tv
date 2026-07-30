package ru.iptvtv.domain.usecase

import ru.iptvtv.domain.repository.ChannelRepository

class GetChannelsUseCase(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(playlistUrl: String, epgUrl: String = "") =
        repository.getChannels(playlistUrl, epgUrl)

    suspend fun loadCurrentPrograms(
        channels: List<ru.iptvtv.domain.model.Channel>,
        epgUrl: String,
        forceRefresh: Boolean = false,
    ) = repository.loadCurrentPrograms(channels, epgUrl, forceRefresh)

    suspend fun getLastEpgUpdateAt(epgUrl: String) =
        repository.getLastEpgUpdateAt(epgUrl)

    suspend fun shouldRefreshEpg(epgUrl: String) =
        repository.shouldRefreshEpg(epgUrl)
}

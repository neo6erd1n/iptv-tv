package ru.iptvtv.domain.usecase

import ru.iptvtv.domain.repository.ChannelRepository

class GetChannelsUseCase(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(playlistUrl: String, epgUrl: String = "") =
        repository.getChannels(playlistUrl, epgUrl)
}

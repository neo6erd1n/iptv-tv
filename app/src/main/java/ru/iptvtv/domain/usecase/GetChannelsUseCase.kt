package ru.iptvtv.domain.usecase

import ru.iptvtv.domain.repository.ChannelRepository

class GetChannelsUseCase(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(playlistUrl: String) =
        repository.getChannels(playlistUrl)
}

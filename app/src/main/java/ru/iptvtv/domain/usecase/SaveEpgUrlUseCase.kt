package ru.iptvtv.domain.usecase

import ru.iptvtv.domain.repository.StreamSettingsRepository

class SaveEpgUrlUseCase(
    private val repository: StreamSettingsRepository,
) {
    suspend operator fun invoke(url: String) = repository.saveEpgUrl(url)
}

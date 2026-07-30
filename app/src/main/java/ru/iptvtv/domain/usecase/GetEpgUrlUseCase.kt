package ru.iptvtv.domain.usecase

import ru.iptvtv.domain.repository.StreamSettingsRepository

class GetEpgUrlUseCase(
    private val repository: StreamSettingsRepository,
) {
    suspend operator fun invoke(): String = repository.getEpgUrl()
}

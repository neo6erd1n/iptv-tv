package ru.iptvtv.domain.usecase

import ru.iptvtv.domain.repository.StreamSettingsRepository

class GetStreamUrlUseCase(
    private val repository: StreamSettingsRepository,
) {
    suspend operator fun invoke(): String = repository.getStreamUrl()
}

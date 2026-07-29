package ru.iptvtv.domain.usecase

import ru.iptvtv.domain.repository.UpdateRepository

class CheckForUpdateUseCase(private val repository: UpdateRepository) {
    suspend operator fun invoke(currentVersion: String) =
        repository.getAvailableUpdate(currentVersion)
}

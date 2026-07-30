package ru.iptvtv.domain.model

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val category: String,
    val epgId: String = "",
    val currentProgram: String? = null,
    val currentProgramStart: Long? = null,
    val currentProgramEnd: Long? = null,
    val programs: List<Program> = emptyList(),
    val catchupSource: String = "",
    val catchupType: String = "",
    val logoUrl: String = "",
)

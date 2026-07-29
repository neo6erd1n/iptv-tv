package ru.iptvtv.domain.model

data class AppUpdate(
    val version: String,
    val title: String,
    val notes: String,
    val downloadUrl: String,
)

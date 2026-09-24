package com.craznail.flashnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SummaryMode {
    NONE,
    LOCAL,
    REMOTE
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val ocrText: String? = null,
    val summary: String? = null,
    val summaryMode: SummaryMode = SummaryMode.NONE,
    val folderId: Long? = null,
    val archivedAt: Long? = null,
    val trashedAt: Long? = null
)

package com.craznail.flashnote.ui

import com.craznail.flashnote.data.Note

internal fun filterNotes(notes: List<Note>, rawQuery: String): List<Note> {
    val query = rawQuery.trim()
    if (query.isEmpty()) return notes

    return notes.filter { note ->
        note.summary.orEmpty().contains(query, ignoreCase = true) ||
            note.ocrText.orEmpty().contains(query, ignoreCase = true)
    }
}

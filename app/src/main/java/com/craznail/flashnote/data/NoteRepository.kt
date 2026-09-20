package com.craznail.flashnote.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File

class NoteRepository(context: Context) {
    private val dao = AppDatabase.get(context).noteDao()
    private val notesDir = File(context.filesDir, "notes").also { it.mkdirs() }

    fun observeNotes(): Flow<List<Note>> = dao.observeAll()

    fun notesDirectory(): File = notesDir

    suspend fun latestNote(): Note? = dao.latest()

    suspend fun saveNote(
        imagePath: String,
        ocrText: String? = null,
        summary: String? = null,
        summaryMode: SummaryMode = SummaryMode.NONE
    ): Long =
        dao.insert(
            Note(
                imagePath = imagePath,
                ocrText = ocrText,
                summary = summary,
                summaryMode = summaryMode
            )
        )

    suspend fun delete(note: Note) {
        runCatching { File(note.imagePath).delete() }
        dao.delete(note)
    }
}

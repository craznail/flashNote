package com.craznail.flashnote.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File

class NoteRepository(context: Context) {
    private val dao = AppDatabase.get(context).noteDao()
    private val notesDir = File(context.filesDir, "notes").also { it.mkdirs() }

    fun observeNotes(): Flow<List<Note>> = dao.observeAll()

    fun notesDirectory(): File = notesDir

    suspend fun saveNote(imagePath: String, summary: String? = null): Long =
        dao.insert(Note(imagePath = imagePath, summary = summary))

    suspend fun delete(note: Note) {
        runCatching { File(note.imagePath).delete() }
        dao.delete(note)
    }
}

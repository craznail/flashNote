package com.craznail.flashnote.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File

class NoteRepository(context: Context) {
    private val dao = AppDatabase.get(context).noteDao()
    private val notesDir = File(context.filesDir, "notes").also { it.mkdirs() }

    fun observeNotes(): Flow<List<Note>> = dao.observeInbox()

    fun observeArchivedNotes(): Flow<List<Note>> = dao.observeArchived()

    fun observeTrashNotes(): Flow<List<Note>> = dao.observeTrash()

    fun observeFolderNotes(folderId: Long): Flow<List<Note>> = dao.observeFolderNotes(folderId)

    fun observeTaggedNotes(tagId: Long): Flow<List<Note>> = dao.observeTaggedNotes(tagId)

    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    fun observeTags(): Flow<List<TagEntity>> = dao.observeTags()

    fun observeFolderSummaries(): Flow<List<FolderSummary>> = dao.observeFolderSummaries()

    fun observeTagSummaries(): Flow<List<TagSummary>> = dao.observeTagSummaries()

    fun observeArchiveCount(): Flow<Int> = dao.observeArchiveCount()

    fun observeTrashCount(): Flow<Int> = dao.observeTrashCount()

    fun observeTagsForNote(noteId: Long): Flow<List<TagEntity>> = dao.observeTagsForNote(noteId)

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

    suspend fun createFolder(rawName: String): Long? {
        val name = normalizeCollectionName(rawName) ?: return null
        val inserted = dao.insertFolder(FolderEntity(name = name))
        return if (inserted >= 0) inserted else dao.folderIdByName(name)
    }

    suspend fun createTag(rawName: String, colorKey: Int): Long? {
        val name = normalizeCollectionName(rawName) ?: return null
        val inserted = dao.insertTag(TagEntity(name = name, colorKey = colorKey))
        return if (inserted >= 0) inserted else dao.tagIdByName(name)
    }

    suspend fun moveToFolder(noteId: Long, folderId: Long?) = dao.moveToFolder(noteId, folderId)

    suspend fun updateSummary(noteId: Long, summary: String?) =
        dao.updateSummary(noteId, summary?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun replaceTags(noteId: Long, tagIds: Set<Long>) =
        dao.replaceTagsForNote(noteId, tagIds)

    suspend fun archive(noteId: Long) = dao.setArchivedAt(noteId, System.currentTimeMillis())

    suspend fun restoreFromArchive(noteId: Long) = dao.setArchivedAt(noteId, null)

    suspend fun moveToTrash(noteId: Long) = dao.setTrashedAt(noteId, System.currentTimeMillis())

    suspend fun restoreFromTrash(noteId: Long) = dao.setTrashedAt(noteId, null)

    suspend fun deleteFolder(folderId: Long) = dao.deleteFolder(folderId)

    suspend fun deleteTag(tagId: Long) = dao.deleteTag(tagId)

    suspend fun permanentlyDelete(note: Note) {
        runCatching { File(note.imagePath).delete() }
        dao.delete(note)
    }
}

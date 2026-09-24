package com.craznail.flashnote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        "SELECT * FROM notes " +
            "WHERE archivedAt IS NULL AND trashedAt IS NULL " +
            "ORDER BY createdAt DESC"
    )
    fun observeInbox(): Flow<List<Note>>

    @Query(
        "SELECT * FROM notes " +
            "WHERE archivedAt IS NULL AND trashedAt IS NULL " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun latest(): Note?

    @Query("SELECT * FROM notes WHERE archivedAt IS NOT NULL AND trashedAt IS NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<Note>>

    @Query(
        "SELECT * FROM notes " +
            "WHERE folderId = :folderId AND archivedAt IS NULL AND trashedAt IS NULL " +
            "ORDER BY createdAt DESC"
    )
    fun observeFolderNotes(folderId: Long): Flow<List<Note>>

    @Query(
        "SELECT notes.* FROM notes " +
            "INNER JOIN note_tags ON notes.id = note_tags.noteId " +
            "WHERE note_tags.tagId = :tagId " +
            "AND notes.archivedAt IS NULL AND notes.trashedAt IS NULL " +
            "ORDER BY notes.createdAt DESC"
    )
    fun observeTaggedNotes(tagId: Long): Flow<List<Note>>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query(
        "SELECT folders.id, folders.name, COUNT(notes.id) AS noteCount " +
            "FROM folders LEFT JOIN notes ON notes.folderId = folders.id " +
            "AND notes.archivedAt IS NULL AND notes.trashedAt IS NULL " +
            "GROUP BY folders.id, folders.name, folders.createdAt " +
            "ORDER BY folders.createdAt ASC"
    )
    fun observeFolderSummaries(): Flow<List<FolderSummary>>

    @Query(
        "SELECT tags.id, tags.name, tags.colorKey, COUNT(notes.id) AS noteCount " +
            "FROM tags " +
            "LEFT JOIN note_tags ON tags.id = note_tags.tagId " +
            "LEFT JOIN notes ON notes.id = note_tags.noteId " +
            "AND notes.archivedAt IS NULL AND notes.trashedAt IS NULL " +
            "GROUP BY tags.id, tags.name, tags.colorKey, tags.createdAt " +
            "ORDER BY tags.createdAt ASC"
    )
    fun observeTagSummaries(): Flow<List<TagSummary>>

    @Query("SELECT COUNT(*) FROM notes WHERE archivedAt IS NOT NULL AND trashedAt IS NULL")
    fun observeArchiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE trashedAt IS NOT NULL")
    fun observeTrashCount(): Flow<Int>

    @Query(
        "SELECT tags.* FROM tags INNER JOIN note_tags ON tags.id = note_tags.tagId " +
            "WHERE note_tags.noteId = :noteId ORDER BY tags.createdAt ASC"
    )
    fun observeTagsForNote(noteId: Long): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteTags(refs: List<NoteTagCrossRef>)

    @Query("SELECT id FROM folders WHERE name = :name LIMIT 1")
    suspend fun folderIdByName(name: String): Long?

    @Query("SELECT id FROM tags WHERE name = :name LIMIT 1")
    suspend fun tagIdByName(name: String): Long?

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :noteId")
    suspend fun moveToFolder(noteId: Long, folderId: Long?)

    @Query("UPDATE notes SET summary = :summary WHERE id = :noteId")
    suspend fun updateSummary(noteId: Long, summary: String?)

    @Query("UPDATE notes SET archivedAt = :archivedAt WHERE id = :noteId")
    suspend fun setArchivedAt(noteId: Long, archivedAt: Long?)

    @Query("UPDATE notes SET trashedAt = :trashedAt WHERE id = :noteId")
    suspend fun setTrashedAt(noteId: Long, trashedAt: Long?)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearTagsForNote(noteId: Long)

    @Transaction
    suspend fun replaceTagsForNote(noteId: Long, tagIds: Set<Long>) {
        clearTagsForNote(noteId)
        if (tagIds.isNotEmpty()) {
            insertNoteTags(tagIds.map { tagId -> NoteTagCrossRef(noteId, tagId) })
        }
    }

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderFromNotes(folderId: Long)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Transaction
    suspend fun deleteFolder(folderId: Long) {
        clearFolderFromNotes(folderId)
        deleteFolderById(folderId)
    }

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

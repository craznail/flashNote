package com.craznail.flashnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteOrganizationTest {

    @Test
    fun activeNote_isInInbox() {
        val note = note()

        assertEquals(NoteBucket.INBOX, note.bucket())
    }

    @Test
    fun archivedNote_isInArchive() {
        val note = note(archivedAt = 100L)

        assertEquals(NoteBucket.ARCHIVE, note.bucket())
    }

    @Test
    fun trashedNote_isInTrashEvenIfItWasArchived() {
        val note = note(archivedAt = 100L, trashedAt = 200L)

        assertEquals(NoteBucket.TRASH, note.bucket())
    }

    @Test
    fun collectionName_isTrimmedAndBlankNamesAreRejected() {
        assertEquals("旅行计划", normalizeCollectionName("  旅行计划  "))
        assertNull(normalizeCollectionName("   "))
    }

    private fun note(
        archivedAt: Long? = null,
        trashedAt: Long? = null
    ) = Note(
        imagePath = "note.png",
        archivedAt = archivedAt,
        trashedAt = trashedAt
    )
}

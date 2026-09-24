package com.craznail.flashnote.ui

import com.craznail.flashnote.data.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesSearchTest {

    @Test
    fun blankQuery_preservesTheInboxOrder() {
        val notes = listOf(
            note(id = 3, summary = "第三条"),
            note(id = 2, summary = "第二条"),
            note(id = 1, summary = "第一条")
        )

        assertEquals(listOf(3L, 2L, 1L), filterNotes(notes, "  ").map { it.id })
    }

    @Test
    fun query_matchesSummaryIgnoringCaseAndOuterWhitespace() {
        val notes = listOf(
            note(id = 1, summary = "Release Checklist"),
            note(id = 2, summary = "Weekend route")
        )

        assertEquals(listOf(1L), filterNotes(notes, "  release  ").map { it.id })
    }

    @Test
    fun query_matchesRecognizedTextWhenSummaryDoesNotMatch() {
        val notes = listOf(
            note(id = 1, summary = "周末路线", ocr = "西湖步道和停车位置"),
            note(id = 2, summary = "产品清单", ocr = "商店素材")
        )

        assertEquals(listOf(1L), filterNotes(notes, "停车").map { it.id })
    }

    private fun note(
        id: Long,
        summary: String? = null,
        ocr: String? = null
    ) = Note(
        id = id,
        imagePath = "note-$id.png",
        summary = summary,
        ocrText = ocr
    )
}

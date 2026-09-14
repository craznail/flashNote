package com.craznail.flashnote.process

/**
 * Free-tier local summary: no cloud. Heuristic — first meaningful lines / sentences.
 */
object LocalSummary {
    fun fromOcr(ocr: String?, maxChars: Int = 120): String? {
        if (ocr.isNullOrBlank()) return null
        val lines = ocr.lines()
            .map { it.trim() }
            .filter { it.length >= 2 }
        if (lines.isEmpty()) return null
        val joined = lines.take(4).joinToString(" · ")
        return if (joined.length <= maxChars) joined
        else joined.take(maxChars - 1) + "…"
    }
}

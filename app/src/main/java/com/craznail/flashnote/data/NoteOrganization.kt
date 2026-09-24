package com.craznail.flashnote.data

enum class NoteBucket {
    INBOX,
    ARCHIVE,
    TRASH
}

fun Note.bucket(): NoteBucket = when {
    trashedAt != null -> NoteBucket.TRASH
    archivedAt != null -> NoteBucket.ARCHIVE
    else -> NoteBucket.INBOX
}

fun normalizeCollectionName(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

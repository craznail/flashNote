package com.craznail.flashnote.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.craznail.flashnote.R
import com.craznail.flashnote.data.Note
import com.craznail.flashnote.data.SummaryMode
import com.craznail.flashnote.ui.theme.FlashBackground
import com.craznail.flashnote.ui.theme.FlashError
import com.craznail.flashnote.ui.theme.FlashIndigo
import com.craznail.flashnote.ui.theme.FlashOnSurfaceMuted
import com.craznail.flashnote.ui.theme.FlashPrimary
import com.craznail.flashnote.ui.theme.FlashPurple
import com.craznail.flashnote.ui.theme.FlashSuccess
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(
    notes: List<Note>,
    overlayRunning: Boolean,
    onToggleOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(containerColor = FlashBackground) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notes_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.notes_slogan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlashOnSurfaceMuted
                    )
                }
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE5E7EB),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Functional overlay control — discreet chip, not FAB
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Surface(
                    onClick = onToggleOverlay,
                    shape = RoundedCornerShape(20.dp),
                    color = if (overlayRunning) {
                        FlashPrimary.copy(alpha = 0.12f)
                    } else {
                        Color(0xFFE5E7EB)
                    }
                ) {
                    Text(
                        text = stringResource(
                            if (overlayRunning) R.string.stop_overlay else R.string.start_overlay
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overlayRunning) FlashPrimary else FlashOnSurfaceMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (notes.isEmpty()) {
                EmptyNotesState(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notes, key = { it.id }) { note ->
                        SwipeDeleteNoteCard(
                            note = note,
                            onOpen = { onOpenNote(note) },
                            onRequestDelete = { pendingDelete = note }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    stringResource(R.string.delete_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    stringResource(R.string.delete_confirm_body),
                    color = FlashOnSurfaceMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        onDelete(note)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlashError),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.delete_note))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel), color = FlashOnSurfaceMuted)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

@Composable
private fun EmptyNotesState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFDBEAFE),
                shadowElevation = 2.dp
            ) {}
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.BottomEnd),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE0E7FF),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = FlashPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_notes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = Color(0xFF111827)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_notes_sub),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = FlashOnSurfaceMuted
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteNoteCard(
    note: Note,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
                false
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Light reveal: deepen red + icon as swipe approaches delete threshold.
            val towardDelete =
                dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val reveal = if (towardDelete) {
                dismissState.progress.coerceIn(0.2f, 1f)
            } else {
                0.2f
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FlashError.copy(alpha = 0.35f + 0.65f * reveal))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(0.45f + 0.55f * reveal)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        stringResource(R.string.delete_note),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        NoteCard(note = note, onOpen = onOpen)
    }
}

@Composable
private fun NoteCard(note: Note, onOpen: () -> Unit) {
    val snippet = noteSnippet(note)
    val badge = noteBadge(note)
    val timeLabel = relativeTime(note.createdAt)

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 0.dp
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = Uri.fromFile(File(note.imagePath)),
                contentDescription = null,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .height(76.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Time top-right of text area
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9CA3AF)
                    )
                }
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = FlashOnSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    StatusPill(badge)
                }
            }
        }
    }
}

private data class BadgeStyle(val label: String, val bg: Color, val fg: Color)

@Composable
private fun StatusPill(badge: BadgeStyle) {
    Text(
        text = badge.label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(badge.bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = badge.fg,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun noteSnippet(note: Note): String {
    note.summary?.takeIf { it.isNotBlank() }?.let { return it }
    note.ocrText?.takeIf { it.isNotBlank() }?.let { return it }
    return stringResource(R.string.image_only_hint)
}

@Composable
private fun noteBadge(note: Note): BadgeStyle = when {
    note.summaryMode == SummaryMode.REMOTE && !note.summary.isNullOrBlank() ->
        BadgeStyle(
            stringResource(R.string.badge_remote_summary),
            FlashIndigo.copy(alpha = 0.15f),
            FlashIndigo
        )
    note.summaryMode == SummaryMode.LOCAL && !note.summary.isNullOrBlank() ->
        BadgeStyle(
            stringResource(R.string.badge_local_summary),
            FlashPurple.copy(alpha = 0.15f),
            FlashPurple
        )
    !note.ocrText.isNullOrBlank() ->
        BadgeStyle(
            stringResource(R.string.badge_ocr),
            FlashSuccess.copy(alpha = 0.15f),
            FlashSuccess
        )
    else ->
        BadgeStyle(
            stringResource(R.string.badge_image_only),
            FlashPrimary.copy(alpha = 0.12f),
            FlashPrimary
        )
}

private fun relativeTime(epochMs: Long): String {
    val cal = Calendar.getInstance()
    val now = cal.clone() as Calendar
    cal.timeInMillis = epochMs
    val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)
    val dateFmt = SimpleDateFormat("M月d日", Locale.CHINA)
    val time = timeFmt.format(Date(epochMs))

    val today = now.clone() as Calendar
    today.set(Calendar.HOUR_OF_DAY, 0)
    today.set(Calendar.MINUTE, 0)
    today.set(Calendar.SECOND, 0)
    today.set(Calendar.MILLISECOND, 0)

    val yesterday = today.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)

    val noteDay = cal.clone() as Calendar
    noteDay.set(Calendar.HOUR_OF_DAY, 0)
    noteDay.set(Calendar.MINUTE, 0)
    noteDay.set(Calendar.SECOND, 0)
    noteDay.set(Calendar.MILLISECOND, 0)

    return when {
        noteDay.timeInMillis == today.timeInMillis -> "今天 $time"
        noteDay.timeInMillis == yesterday.timeInMillis -> "昨天 $time"
        else -> "${dateFmt.format(Date(epochMs))} $time"
    }
}

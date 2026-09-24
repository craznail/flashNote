package com.craznail.flashnote.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.craznail.flashnote.data.Note
import com.craznail.flashnote.data.SummaryMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val DesignBlue = Color(0xFF3478F6)
internal val DesignInk = Color(0xFF172139)
internal val DesignMuted = Color(0xFF78849B)
internal val DesignBackground = Color(0xFFF7FAFF)
internal val DesignPaleBlue = Color(0xFFEAF2FF)
internal val DesignRed = Color(0xFFF04452)

@Composable
internal fun DesignCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) { content() }
}

@Composable
internal fun NoteThumbnail(note: Note, modifier: Modifier = Modifier) {
    AsyncImage(
        model = Uri.fromFile(File(note.imagePath)),
        contentDescription = "笔记截图",
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}

internal fun noteDisplayTitle(note: Note): String {
    val source = note.summary?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: note.ocrText?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: return "截图笔记"
    val normalized = source.trim().replace(Regex("\\s+"), " ")
    val heading = normalized.substringBefore('：').substringBefore(':')
    return (if (heading.length in 2..20) heading else normalized).take(26)
}

internal fun noteDisplayExcerpt(note: Note): String =
    note.summary?.takeIf { it.isNotBlank() }
        ?: note.ocrText?.takeIf { it.isNotBlank() }
        ?: "仅保存了原始截图"

internal fun noteDisplayTime(epochMs: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val prefix = when {
        epochMs >= today.timeInMillis -> "今天"
        epochMs >= today.timeInMillis - 24 * 60 * 60 * 1000L -> "昨天"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(epochMs))
    }
    return "$prefix ${SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(epochMs))}"
}

@Composable
internal fun NoteStatusChips(note: Note) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!note.ocrText.isNullOrBlank()) {
            StatusChip("含 OCR", true)
        }
        if (!note.summary.isNullOrBlank()) {
            StatusChip(if (note.summaryMode == SummaryMode.REMOTE) "远端摘要" else "本地摘要", false)
        }
        if (note.ocrText.isNullOrBlank() && note.summary.isNullOrBlank()) {
            StatusChip("仅图", true, imageOnly = true)
        }
    }
}

@Composable
private fun StatusChip(label: String, blue: Boolean, imageOnly: Boolean = false) {
    val fg = if (blue) DesignBlue else Color(0xFF1DA66D)
    val bg = if (blue) DesignPaleBlue else Color(0xFFE9F9F1)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when {
                imageOnly -> Icons.Default.Image
                blue -> Icons.Default.Description
                else -> Icons.Default.AutoAwesome
            },
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.size(3.dp))
        Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

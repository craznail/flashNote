package com.craznail.flashnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.craznail.flashnote.R
import com.craznail.flashnote.data.Note
import com.craznail.flashnote.ui.theme.FlashBackground
import com.craznail.flashnote.ui.theme.FlashError
import com.craznail.flashnote.ui.theme.FlashOnSurfaceMuted
import com.craznail.flashnote.ui.theme.FlashPrimary
import com.craznail.flashnote.ui.theme.FlashSuccess
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    note: Note,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val caption = "${relativeTime(note.createdAt)} · ${stringResource(R.string.detail_caption_suffix)}"
    val hasSummary = !note.summary.isNullOrBlank()
    val hasOcr = !note.ocrText.isNullOrBlank()

    Scaffold(
        containerColor = FlashBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.note_detail_title),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color(0xFF111827)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_note),
                            tint = FlashError
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF111827),
                    navigationIconContentColor = Color(0xFF111827)
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AsyncImage(
                model = Uri.fromFile(File(note.imagePath)),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = FlashOnSurfaceMuted
            )

            if (hasSummary) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FlashPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = FlashPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.section_summary),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FlashPrimary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = note.summary!!,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = Color(0xFF111827)
                        )
                    }
                }
            }

            if (hasOcr) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(FlashSuccess.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "T",
                                        fontWeight = FontWeight.Bold,
                                        color = FlashSuccess,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.section_ocr),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FlashOnSurfaceMuted
                                )
                            }
                            TextButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                    cm.setPrimaryClip(
                                        ClipData.newPlainText("ocr", note.ocrText)
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = FlashPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.copy_ocr),
                                    color = FlashPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = note.ocrText!!,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFF374151)
                            )
                        }
                    }
                }
            }

            if (!hasSummary && !hasOcr) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.image_only_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlashOnSurfaceMuted
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
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
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlashError),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.delete_note))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel), color = FlashOnSurfaceMuted)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
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

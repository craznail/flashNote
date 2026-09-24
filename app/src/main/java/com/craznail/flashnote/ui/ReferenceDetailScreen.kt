package com.craznail.flashnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.craznail.flashnote.data.FolderEntity
import com.craznail.flashnote.data.Note
import com.craznail.flashnote.data.TagEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashNoteDetailScreen(
    note: Note,
    browseNotes: List<Note>,
    folders: List<FolderEntity>,
    tags: List<TagEntity>,
    selectedTagIds: Set<Long>,
    onBack: () -> Unit,
    onShowNote: (Note) -> Unit,
    onMoveToFolder: (Long?) -> Unit,
    onReplaceTags: (Set<Long>) -> Unit,
    onEditSummary: (String) -> Unit,
    onArchive: () -> Unit,
    onRestoreFromArchive: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestoreFromTrash: () -> Unit,
    onPermanentlyDelete: () -> Unit
) {
    var minimal by rememberSaveable { mutableStateOf(false) }
    var organizeOpen by remember { mutableStateOf(false) }
    var folderPickerOpen by remember { mutableStateOf(false) }
    var tagPickerOpen by remember { mutableStateOf(false) }
    var summaryEditorOpen by remember { mutableStateOf(false) }
    var imageOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var summaryDraft by remember(note.id) { mutableStateOf(note.summary.orEmpty()) }
    var tagDraft by remember(selectedTagIds) { mutableStateOf(selectedTagIds) }
    val context = LocalContext.current
    val orderedNotes = remember(browseNotes) { browseNotes.sortedByDescending { it.createdAt } }
    val noteIndex = orderedNotes.indexOfFirst { it.id == note.id }

    BackHandler(enabled = minimal) { minimal = false }

    if (minimal) {
        key(note.id) {
            MinimalReadingScreen(
                note = note,
                folderName = folders.firstOrNull { it.id == note.folderId }?.name,
                onBack = { minimal = false },
                onPrevious = { if (noteIndex > 0) onShowNote(orderedNotes[noteIndex - 1]) },
                onNext = { if (noteIndex >= 0 && noteIndex < orderedNotes.lastIndex) onShowNote(orderedNotes[noteIndex + 1]) }
            )
        }
    } else {
        Scaffold(
            containerColor = DesignBackground,
            bottomBar = {
                Surface(color = Color.White, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), shadowElevation = 5.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailAction("复制笔记", Icons.Default.ContentCopy, Modifier.weight(1f)) { copyNote(context, note) }
                        DetailAction("整理笔记", Icons.Default.Folder, Modifier.weight(1f)) { organizeOpen = true }
                        DetailAction("删除笔记", Icons.Default.DeleteOutline, Modifier.weight(1f), DesignRed) { confirmDelete = true }
                        DetailAction("极简模式", Icons.Default.AutoAwesome, Modifier.weight(1f), Color.White, DesignBlue) { minimal = true }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = DesignInk) }
                    Text("笔记详情", color = DesignInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreHoriz, "更多操作", tint = DesignInk) }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(text = { Text("归档笔记") }, onClick = { moreOpen = false; onArchive() })
                            if (note.archivedAt != null) DropdownMenuItem(text = { Text("恢复归档") }, onClick = { moreOpen = false; onRestoreFromArchive() })
                            if (note.trashedAt != null) DropdownMenuItem(text = { Text("恢复笔记") }, onClick = { moreOpen = false; onRestoreFromTrash() })
                        }
                    }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DesignCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.clickable { imageOpen = true }) { NoteThumbnail(note, Modifier.size(108.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(noteDisplayTitle(note), color = DesignInk, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
                                    Spacer(Modifier.height(7.dp))
                                    Text(noteDisplayTime(note.createdAt), color = DesignMuted, fontSize = 12.sp)
                                    Spacer(Modifier.height(5.dp))
                                    NoteStatusChips(note)
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            if (!note.ocrText.isNullOrBlank()) {
                                Text(note.ocrText, color = DesignInk, fontSize = 15.sp, lineHeight = 24.sp)
                            } else {
                                AsyncImage(
                                    model = Uri.fromFile(File(note.imagePath)),
                                    contentDescription = "笔记完整截图",
                                    modifier = Modifier.fillMaxWidth().clickable { imageOpen = true },
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                    DesignCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = DesignBlue, modifier = Modifier.size(21.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("详情摘要", color = DesignInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (note.summary.isNullOrBlank()) {
                                    TextButton(onClick = { summaryDraft = ""; summaryEditorOpen = true }) { Text("添加摘要") }
                                } else {
                                    Text("由 AI 生成", color = DesignMuted, fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(11.dp))
                            Surface(shape = RoundedCornerShape(13.dp), color = DesignPaleBlue) {
                                Text(
                                    note.summary?.takeIf { it.isNotBlank() } ?: "暂无摘要",
                                    modifier = Modifier.padding(13.dp),
                                    color = if (note.summary.isNullOrBlank()) DesignMuted else DesignInk,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (organizeOpen) {
        ModalBottomSheet(onDismissRequest = { organizeOpen = false }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("整理笔记", color = DesignInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OrganizeAction("移动到分类", Icons.Default.Folder) { organizeOpen = false; folderPickerOpen = true }
                OrganizeAction("添加标签", Icons.Default.Label) { organizeOpen = false; tagDraft = selectedTagIds; tagPickerOpen = true }
                OrganizeAction("编辑摘要", Icons.Default.EditNote) { organizeOpen = false; summaryDraft = note.summary.orEmpty(); summaryEditorOpen = true }
                OrganizeAction(if (note.archivedAt == null) "归档笔记" else "恢复归档", Icons.Default.Archive) {
                    organizeOpen = false
                    if (note.archivedAt == null) onArchive() else onRestoreFromArchive()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (folderPickerOpen) {
        AlertDialog(
            onDismissRequest = { folderPickerOpen = false },
            title = { Text("移动到分类") },
            text = {
                Column {
                    TextButton(onClick = { onMoveToFolder(null); folderPickerOpen = false }) { Text("待整理") }
                    folders.forEach { folder ->
                        TextButton(onClick = { onMoveToFolder(folder.id); folderPickerOpen = false }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { folderPickerOpen = false }) { Text("取消") } }
        )
    }
    if (tagPickerOpen) {
        AlertDialog(
            onDismissRequest = { tagPickerOpen = false },
            title = { Text("添加标签") },
            text = {
                Column {
                    tags.forEach { tag ->
                        TextButton(onClick = { tagDraft = if (tag.id in tagDraft) tagDraft - tag.id else tagDraft + tag.id }) {
                            Text("${if (tag.id in tagDraft) "✓ " else ""}#${tag.name}")
                        }
                    }
                    if (tags.isEmpty()) Text("还没有标签，可在笔记整理中创建。", color = DesignMuted)
                }
            },
            confirmButton = { Button(onClick = { onReplaceTags(tagDraft); tagPickerOpen = false }) { Text("完成") } },
            dismissButton = { TextButton(onClick = { tagPickerOpen = false }) { Text("取消") } }
        )
    }
    if (summaryEditorOpen) {
        AlertDialog(
            onDismissRequest = { summaryEditorOpen = false },
            title = { Text("编辑摘要") },
            text = { OutlinedTextField(value = summaryDraft, onValueChange = { summaryDraft = it }, minLines = 4, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { onEditSummary(summaryDraft); summaryEditorOpen = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { summaryEditorOpen = false }) { Text("取消") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (note.trashedAt == null) "删除笔记？" else "永久删除笔记？") },
            text = { Text(if (note.trashedAt == null) "笔记将移到回收站，之后仍可恢复。" else "截图、摘要和文字将无法恢复。") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    if (note.trashedAt == null) onMoveToTrash() else onPermanentlyDelete()
                }, colors = ButtonDefaults.buttonColors(containerColor = DesignRed)) { Text(if (note.trashedAt == null) "移到回收站" else "永久删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
    if (imageOpen) {
        Dialog(onDismissRequest = { imageOpen = false }) {
            AsyncImage(
                model = Uri.fromFile(File(note.imagePath)),
                contentDescription = "原始截图",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { imageOpen = false },
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun DetailAction(label: String, icon: ImageVector, modifier: Modifier, fg: Color = DesignMuted, bg: Color = DesignPaleBlue, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(13.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(21.dp))
        Text(label, color = fg, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun OrganizeAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = DesignBackground) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DesignBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = DesignInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DesignMuted)
        }
    }
}

@Composable
private fun MinimalReadingScreen(
    note: Note,
    folderName: String?,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val scroll = rememberScrollState()
    val context = LocalContext.current
    var moreOpen by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    var edgeDrag by remember { mutableFloatStateOf(0f) }
    val edgeConnection = remember(note.id, scroll, threshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source != NestedScrollSource.Drag) return androidx.compose.ui.geometry.Offset.Zero
                val atTop = scroll.value == 0 && available.y > 0
                val atBottom = scroll.value == scroll.maxValue && available.y < 0
                edgeDrag = if (atTop || atBottom) edgeDrag + available.y else 0f
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                when {
                    edgeDrag > threshold -> onPrevious()
                    edgeDrag < -threshold -> onNext()
                }
                edgeDrag = 0f
                return Velocity.Zero
            }
        }
    }
    Scaffold(containerColor = Color.White) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出极简模式", tint = DesignInk) }
                Box {
                    IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreHoriz, "更多", tint = DesignMuted) }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(text = { Text("复制笔记") }, onClick = { copyNote(context, note); moreOpen = false })
                        DropdownMenuItem(text = { Text("退出极简模式") }, onClick = { onBack(); moreOpen = false })
                    }
                }
            }
            Column(
                Modifier.weight(1f).nestedScroll(edgeConnection).verticalScroll(scroll).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Text(noteDisplayTitle(note), color = DesignInk, fontSize = 31.sp, fontWeight = FontWeight.Bold, lineHeight = 39.sp)
                Text(noteDisplayTime(note.createdAt), color = DesignMuted, fontSize = 13.sp)
                if (folderName != null) Text(folderName, color = DesignBlue, fontSize = 13.sp)
                if (!note.ocrText.isNullOrBlank()) {
                    Text(note.ocrText, color = Color(0xFF46536B), fontSize = 18.sp, lineHeight = 30.sp)
                } else if (!note.summary.isNullOrBlank()) {
                    Text(note.summary, color = Color(0xFF46536B), fontSize = 18.sp, lineHeight = 30.sp)
                } else {
                    AsyncImage(
                        model = Uri.fromFile(File(note.imagePath)),
                        contentDescription = "笔记原图",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Text("上滑 / 下滑切换笔记", color = DesignMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(12.dp))
        }
    }
}

private fun copyNote(context: Context, note: Note) {
    val text = listOfNotNull(note.summary?.takeIf { it.isNotBlank() }, note.ocrText?.takeIf { it.isNotBlank() }).joinToString("\n\n")
    if (text.isBlank()) {
        Toast.makeText(context, "这篇笔记只有图片", Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("flashNote", text))
    Toast.makeText(context, "已复制笔记文字", Toast.LENGTH_SHORT).show()
}

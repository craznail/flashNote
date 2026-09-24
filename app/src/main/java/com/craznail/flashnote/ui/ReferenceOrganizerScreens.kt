package com.craznail.flashnote.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craznail.flashnote.data.FolderSummary
import com.craznail.flashnote.data.Note
import com.craznail.flashnote.data.TagSummary

sealed interface LibraryDestination {
    data class Folder(val id: Long, val name: String) : LibraryDestination
    data class Tag(val id: Long, val name: String, val colorKey: Int) : LibraryDestination
    data object Archive : LibraryDestination
    data object Trash : LibraryDestination
}

@Composable
fun FlashOrganizerScreen(
    folders: List<FolderSummary>,
    tags: List<TagSummary>,
    archiveCount: Int,
    trashCount: Int,
    onBack: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenDestination: (LibraryDestination) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onCreateTag: (String, Int) -> Unit,
    onDeleteTag: (Long) -> Unit
) {
    var managing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var deleteFolderId by remember { mutableStateOf<Long?>(null) }
    var deleteTagId by remember { mutableStateOf<Long?>(null) }
    Scaffold(containerColor = DesignBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = DesignInk) }
                Text("笔记整理", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DesignInk)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                OrganizerTab("按分类", !managing) { managing = false }
                OrganizerTab("管理分类", managing) { managing = true }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (!managing) {
                    item { OrganizerRow("全部笔记", null, "查看所有笔记", Icons.Default.Folder, DesignBlue, onOpenAll) }
                    items(folders, key = { "folder-${it.id}" }) { folder ->
                        OrganizerRow(folder.name, folder.noteCount, null, Icons.Default.Folder, DesignBlue) {
                            onOpenDestination(LibraryDestination.Folder(folder.id, folder.name))
                        }
                    }
                    if (tags.isNotEmpty()) {
                        item { Text("标签", modifier = Modifier.padding(top = 12.dp, bottom = 3.dp), color = DesignMuted, fontSize = 13.sp) }
                        items(tags, key = { "tag-${it.id}" }) { tag ->
                            OrganizerRow("#${tag.name}", tag.noteCount, null, Icons.Default.Label, Color(0xFF8B70EE)) {
                                onOpenDestination(LibraryDestination.Tag(tag.id, tag.name, tag.colorKey))
                            }
                        }
                    }
                    item { Text("系统分类", modifier = Modifier.padding(top = 12.dp, bottom = 3.dp), color = DesignMuted, fontSize = 13.sp) }
                    item { OrganizerRow("归档", archiveCount, null, Icons.Default.Archive, DesignMuted) { onOpenDestination(LibraryDestination.Archive) } }
                    item { OrganizerRow("回收站", trashCount, null, Icons.Default.DeleteOutline, DesignRed) { onOpenDestination(LibraryDestination.Trash) } }
                } else {
                    item { Text("文件夹", color = DesignMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp)) }
                    items(folders, key = { "manage-folder-${it.id}" }) { folder ->
                        OrganizerManageRow(folder.name, folder.noteCount, Icons.Default.Folder, onDelete = { deleteFolderId = folder.id })
                    }
                    item {
                        TextButton(onClick = { draft = ""; addingTag = false; adding = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Text("新建分类")
                        }
                    }
                    item { Text("标签", color = DesignMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 3.dp)) }
                    items(tags, key = { "manage-tag-${it.id}" }) { tag ->
                        OrganizerManageRow(tag.name, tag.noteCount, Icons.Default.Label, onDelete = { deleteTagId = tag.id })
                    }
                    item {
                        TextButton(onClick = { draft = ""; addingTag = true; adding = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Text("新建标签")
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(if (addingTag) "新建标签" else "新建分类") },
            text = { OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("名称") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    if (addingTag) onCreateTag(draft.trim(), 0) else onCreateFolder(draft.trim())
                    adding = false
                }, enabled = draft.isNotBlank()) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } }
        )
    }
    if (deleteFolderId != null || deleteTagId != null) {
        AlertDialog(
            onDismissRequest = { deleteFolderId = null; deleteTagId = null },
            title = { Text("删除分类？") },
            text = { Text("分类将被删除，里面的笔记仍会保留。") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteFolderId?.let(onDeleteFolder)
                        deleteTagId?.let(onDeleteTag)
                        deleteFolderId = null
                        deleteTagId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesignRed)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteFolderId = null; deleteTagId = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun OrganizerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (selected) DesignInk else DesignMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(90.dp).height(2.dp).background(if (selected) DesignInk else Color.Transparent))
    }
}

@Composable
private fun OrganizerRow(title: String, count: Int?, subtitle: String?, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    DesignCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(31.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = DesignInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, color = DesignMuted, fontSize = 11.sp)
            }
            if (count != null) Text("$count", color = DesignMuted, fontSize = 12.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DesignMuted)
        }
    }
}

@Composable
private fun OrganizerManageRow(title: String, count: Int, icon: ImageVector, onDelete: () -> Unit) {
    DesignCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 3.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DesignBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(11.dp))
            Text(title, color = DesignInk, modifier = Modifier.weight(1f), fontSize = 14.sp)
            Text("$count", color = DesignMuted, fontSize = 12.sp)
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "删除分类", tint = DesignMuted, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun FlashCollectionScreen(
    destination: LibraryDestination,
    notes: List<Note>,
    onBack: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onRestore: (Note) -> Unit,
    onPermanentlyDelete: (Note) -> Unit
) {
    var deleteCandidate by remember { mutableStateOf<Note?>(null) }
    val title = when (destination) {
        is LibraryDestination.Folder -> destination.name
        is LibraryDestination.Tag -> "#${destination.name}"
        LibraryDestination.Archive -> "归档"
        LibraryDestination.Trash -> "回收站"
    }
    Scaffold(containerColor = DesignBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = DesignInk) }
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DesignInk)
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (notes.isEmpty()) {
                    item { Text("这里还没有笔记", color = DesignMuted, modifier = Modifier.padding(18.dp)) }
                }
                items(notes, key = { it.id }) { note ->
                    Column {
                        ReferenceNoteRow(note, onClick = { onOpenNote(note) })
                        if (destination == LibraryDestination.Archive || destination == LibraryDestination.Trash) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { onRestore(note) }) { Text("恢复") }
                                if (destination == LibraryDestination.Trash) {
                                    TextButton(onClick = { deleteCandidate = note }) { Text("永久删除", color = DesignRed) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleteCandidate?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("永久删除笔记？") },
            text = { Text("截图、摘要和文字将无法恢复。") },
            confirmButton = {
                Button(onClick = { onPermanentlyDelete(note); deleteCandidate = null }, colors = ButtonDefaults.buttonColors(containerColor = DesignRed)) { Text("永久删除") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("取消") } }
        )
    }
}

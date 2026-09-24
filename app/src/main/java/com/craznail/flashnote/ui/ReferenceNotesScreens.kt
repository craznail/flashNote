package com.craznail.flashnote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craznail.flashnote.data.FolderSummary
import com.craznail.flashnote.data.Note
import com.craznail.flashnote.R

private enum class NoteSort(val label: String) {
    NEWEST("最近更新"), OLDEST("创建时间"), TITLE_ASC("标题 A–Z"), TITLE_DESC("标题 Z–A")
}

@Composable
fun FlashHomeScreen(
    notes: List<Note>,
    folders: List<FolderSummary>,
    onOpenNote: (Note) -> Unit,
    onOpenAll: (String) -> Unit,
    onOpenOrganizer: () -> Unit,
    onOpenMy: () -> Unit,
    onStartCapture: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(notes, query) { filterNotes(notes, query).take(4) }
    Scaffold(
        containerColor = DesignBackground,
        bottomBar = {
            MainNavigation(
                selected = "home",
                onHome = {},
                onAll = { onOpenAll("") },
                onCapture = onStartCapture,
                onOrganizer = onOpenOrganizer,
                onMy = onOpenMy
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(DesignBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Description, null, tint = Color.White, modifier = Modifier.size(27.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("闪记 FlashNote", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = DesignInk)
                        Text("随时记录，智能整理", fontSize = 12.sp, color = DesignMuted)
                    }
                    IconButton(onClick = onOpenMy) {
                        Icon(Icons.Default.PersonOutline, "我的", tint = DesignInk)
                    }
                }
            }
            item { NotesSearchBox(query, { query = it }) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (query.isBlank()) "最近笔记" else "搜索结果", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DesignInk)
                    TextButton(onClick = { onOpenAll(query) }) { Text("查看更多  ›", color = DesignMuted) }
                }
            }
            if (shown.isEmpty()) {
                item { EmptyReferenceState(if (query.isBlank()) "还没有笔记，点击悬浮球保存第一张截图" else "没有找到匹配的笔记") }
            } else {
                items(shown, key = { it.id }) { note ->
                    ReferenceNoteRow(note = note, onClick = { onOpenNote(note) }, large = true)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    HomeDestinationCard(
                        title = "全部笔记", subtitle = "查看所有笔记内容",
                        icon = Icons.Default.Description,
                        background = Color(0xFFEAF3FF),
                        illustration = R.drawable.illustration_all_notes,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenAll("") }
                    )
                    HomeDestinationCard(
                        title = "笔记整理", subtitle = "按分类查看和管理",
                        icon = Icons.Default.Folder,
                        background = Color(0xFFF3F0FF),
                        categories = folders.take(2).map { "${it.name} ${it.noteCount}" },
                        modifier = Modifier.weight(1f),
                        onClick = onOpenOrganizer
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDestinationCard(title: String, subtitle: String, icon: ImageVector, background: Color, modifier: Modifier, categories: List<String> = emptyList(), illustration: Int? = null, onClick: () -> Unit) {
    Surface(modifier = modifier.height(170.dp), onClick = onClick, shape = RoundedCornerShape(20.dp), color = background, shadowElevation = 1.dp) {
        Box {
            if (illustration != null) {
                Image(
                    painter = painterResource(illustration),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().alpha(0.7f),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(15.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = DesignBlue)
                }
                Spacer(Modifier.height(14.dp))
                Text(title, color = DesignInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = DesignMuted, fontSize = 11.sp, maxLines = 1)
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        categories.forEach { category ->
                            Text(category, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.75f)).padding(horizontal = 5.dp, vertical = 3.dp), color = DesignMuted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MainNavigation(
    selected: String,
    onHome: () -> Unit,
    onAll: () -> Unit,
    onCapture: () -> Unit,
    onOrganizer: () -> Unit,
    onMy: () -> Unit
) {
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = Color.White, shadowElevation = 6.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavigationItem("首页", Icons.Default.Home, selected == "home", onHome)
            NavigationItem("全部笔记", Icons.Default.Description, selected == "all", onAll)
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(DesignBlue).clickable(onClick = onCapture),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, "开启悬浮球截屏", tint = Color.White, modifier = Modifier.size(30.dp)) }
            NavigationItem("笔记整理", Icons.Default.Folder, selected == "organizer", onOrganizer)
            NavigationItem("我的", Icons.Default.PersonOutline, selected == "my", onMy)
        }
    }
}

@Composable
private fun NavigationItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) DesignBlue else DesignMuted, modifier = Modifier.size(23.dp))
        Text(label, fontSize = 10.sp, color = if (selected) DesignBlue else DesignMuted, maxLines = 1)
    }
}

@Composable
internal fun NotesSearchBox(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = DesignMuted, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester)),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = DesignInk, fontSize = 15.sp),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) Text("搜索笔记、OCR、摘要…", color = DesignMuted, fontSize = 15.sp)
                        inner()
                    }
                }
            )
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "清空搜索", tint = DesignMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReferenceNoteRow(
    note: Note,
    onClick: () -> Unit,
    large: Boolean = false,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val cardColor = if (selected) Color(0xFFEAF3FF) else Color.White
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(17.dp),
        color = cardColor,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(3.dp))
            }
            NoteThumbnail(note, Modifier.size(if (large) 78.dp else 72.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(noteDisplayTitle(note), color = DesignInk, fontWeight = FontWeight.Bold, fontSize = if (large) 15.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(noteDisplayExcerpt(note), color = DesignMuted, fontSize = 12.sp, maxLines = if (large) 2 else 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(noteDisplayTime(note.createdAt), color = DesignMuted, fontSize = 10.sp)
                    NoteStatusChips(note)
                }
            }
            if (!selectionMode) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DesignMuted, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun EmptyReferenceState(message: String) {
    DesignCard(Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(30.dp), color = DesignMuted, fontSize = 14.sp)
    }
}

@Composable
fun FlashAllNotesScreen(
    notes: List<Note>,
    folders: List<FolderSummary>,
    initialQuery: String,
    onBack: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onMoveSelected: (Set<Long>, Long?) -> Unit,
    onTrashSelected: (Set<Long>) -> Unit
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var folderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var unfiled by rememberSaveable { mutableStateOf(false) }
    var sort by remember { mutableStateOf(NoteSort.NEWEST) }
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    val filtered = remember(notes, query, folderId, unfiled, sort) {
        val matching = filterNotes(notes, query).filter { note ->
            when {
                unfiled -> note.folderId == null
                folderId != null -> note.folderId == folderId
                else -> true
            }
        }
        when (sort) {
            NoteSort.NEWEST -> matching.sortedByDescending { it.createdAt }
            NoteSort.OLDEST -> matching.sortedBy { it.createdAt }
            NoteSort.TITLE_ASC -> matching.sortedBy { noteDisplayTitle(it) }
            NoteSort.TITLE_DESC -> matching.sortedByDescending { noteDisplayTitle(it) }
        }
    }
    fun toggle(id: Long) { selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }
    Scaffold(
        containerColor = DesignBackground,
        bottomBar = {
            if (selectionMode) {
                Surface(color = Color.White, shadowElevation = 6.dp, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("已选择 ${selectedIds.size} 项", color = DesignMuted, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { showFolderPicker = true }, enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("移动分类")
                        }
                        Button(
                            onClick = { confirmDelete = true }, enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = DesignRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                            Text("删除")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = DesignInk) }
                Column(Modifier.weight(1f)) {
                    Text(if (selectionMode) "已选择 ${selectedIds.size} 项" else "全部笔记", fontSize = 19.sp, color = DesignInk, fontWeight = FontWeight.Bold)
                    Text("共 ${notes.size} 篇笔记", color = DesignMuted, fontSize = 11.sp)
                }
                if (selectionMode) {
                    TextButton(onClick = { selectionMode = false; selectedIds = emptySet() }) { Text("取消选择", color = DesignBlue) }
                } else {
                    IconButton(onClick = { searchFocus.requestFocus(); keyboard?.show() }) { Icon(Icons.Default.Search, "搜索", tint = DesignInk) }
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreHoriz, "更多", tint = DesignInk) }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(text = { Text("选择笔记") }, onClick = { selectionMode = true; moreOpen = false })
                        }
                    }
                }
            }
            NotesSearchBox(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), searchFocus)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                CategoryChip("全部", folderId == null && !unfiled) { folderId = null; unfiled = false }
                folders.forEach { folder ->
                    CategoryChip(folder.name, folderId == folder.id) { folderId = folder.id; unfiled = false }
                }
                CategoryChip("待整理", unfiled) { folderId = null; unfiled = true }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { sortOpen = true }) { Text("${sort.label}  ⌄", color = DesignInk, fontSize = 12.sp) }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        NoteSort.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { sort = option; sortOpen = false })
                        }
                    }
                }
                IconButton(onClick = { grid = false }) { Icon(Icons.AutoMirrored.Filled.List, "列表视图", tint = if (!grid) DesignBlue else DesignMuted) }
                IconButton(onClick = { grid = true }) { Icon(Icons.Default.GridView, "网格视图", tint = if (grid) DesignBlue else DesignMuted) }
            }
            if (filtered.isEmpty()) {
                EmptyReferenceState("这里还没有笔记")
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(filtered, key = { it.id }) { note ->
                        DesignCard(Modifier.clickable { if (selectionMode) toggle(note.id) else onOpenNote(note) }) {
                            Column(Modifier.padding(9.dp)) {
                                NoteThumbnail(note, Modifier.fillMaxWidth().height(118.dp))
                                Spacer(Modifier.height(7.dp))
                                Text(noteDisplayTitle(note), color = DesignInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text(noteDisplayTime(note.createdAt), color = DesignMuted, fontSize = 10.sp)
                                if (selectionMode) Checkbox(checked = note.id in selectedIds, onCheckedChange = { toggle(note.id) })
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { note ->
                        ReferenceNoteRow(
                            note = note,
                            selected = note.id in selectedIds,
                            selectionMode = selectionMode,
                            onClick = { if (selectionMode) toggle(note.id) else onOpenNote(note) },
                            onLongClick = { selectionMode = true; toggle(note.id) }
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("确认删除所选笔记？") },
            text = { Text("将 ${selectedIds.size} 篇笔记移到回收站，可在笔记整理中恢复。") },
            confirmButton = {
                Button(onClick = {
                    onTrashSelected(selectedIds)
                    selectedIds = emptySet()
                    selectionMode = false
                    confirmDelete = false
                }, colors = ButtonDefaults.buttonColors(containerColor = DesignRed)) { Text("移到回收站") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
    if (showFolderPicker) {
        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { Text("移动到分类") },
            text = {
                Column {
                    TextButton(onClick = { onMoveSelected(selectedIds, null); selectedIds = emptySet(); selectionMode = false; showFolderPicker = false }) { Text("待整理") }
                    folders.forEach { folder ->
                        TextButton(onClick = { onMoveSelected(selectedIds, folder.id); selectedIds = emptySet(); selectionMode = false; showFolderPicker = false }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFolderPicker = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) DesignBlue else Color.White,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = if (selected) Color.White else DesignMuted, fontSize = 12.sp)
    }
}

package com.craznail.flashnote

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.craznail.flashnote.data.Note
import com.craznail.flashnote.data.OverlayPreferences
import com.craznail.flashnote.overlay.OverlayService
import com.craznail.flashnote.ui.FlashAllNotesScreen
import com.craznail.flashnote.ui.FlashCollectionScreen
import com.craznail.flashnote.ui.FlashHomeScreen
import com.craznail.flashnote.ui.FlashNoteDetailScreen
import com.craznail.flashnote.ui.FlashOrganizerScreen
import com.craznail.flashnote.ui.SettingsScreen
import com.craznail.flashnote.ui.LibraryDestination
import com.craznail.flashnote.ui.theme.FlashNoteTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    private val openSettingsRequests = MutableStateFlow(0)
    private val latestNoteToOpen = MutableStateFlow<Note?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        consumeOpenSettings(intent)
        consumeOpenLatestNote(intent)

        val app = application as FlashNoteApp
        val overlayPrefs = OverlayPreferences.get(this)

        setContent {
            FlashNoteTheme {
                var showSettings by remember { mutableStateOf(false) }
                var showLibrary by remember { mutableStateOf(false) }
                var showAllNotes by remember { mutableStateOf(false) }
                var listQuery by remember { mutableStateOf("") }
                var libraryDestination by remember {
                    mutableStateOf<LibraryDestination?>(null)
                }
                var selectedNote by remember { mutableStateOf<Note?>(null) }
                val settingsTick by openSettingsRequests.collectAsState()
                val requestedLatestNote by latestNoteToOpen.collectAsState()
                LaunchedEffect(settingsTick) {
                    if (settingsTick > 0) showSettings = true
                }
                LaunchedEffect(requestedLatestNote) {
                    requestedLatestNote?.let { note ->
                        showSettings = false
                        showLibrary = false
                        showAllNotes = false
                        libraryDestination = null
                        selectedNote = note
                        latestNoteToOpen.value = null
                    }
                }
                // Driven by the service lifecycle so the dedicated settings switch stays accurate.
                val overlayRunning by OverlayService.running.collectAsState()
                val notes by app.notes.observeNotes().collectAsState(initial = emptyList())
                val folders by app.notes.observeFolders().collectAsState(initial = emptyList())
                val tags by app.notes.observeTags().collectAsState(initial = emptyList())
                val folderSummaries by app.notes.observeFolderSummaries()
                    .collectAsState(initial = emptyList())
                val tagSummaries by app.notes.observeTagSummaries()
                    .collectAsState(initial = emptyList())
                val archiveCount by app.notes.observeArchiveCount().collectAsState(initial = 0)
                val trashCount by app.notes.observeTrashCount().collectAsState(initial = 0)
                val noteTagsFlow = remember(selectedNote?.id) {
                    selectedNote?.let { app.notes.observeTagsForNote(it.id) } ?: flowOf(emptyList())
                }
                val selectedNoteTags by noteTagsFlow.collectAsState(initial = emptyList())
                val collectionNotesFlow = remember(libraryDestination) {
                    when (val destination = libraryDestination) {
                        is LibraryDestination.Folder -> app.notes.observeFolderNotes(destination.id)
                        is LibraryDestination.Tag -> app.notes.observeTaggedNotes(destination.id)
                        LibraryDestination.Archive -> app.notes.observeArchivedNotes()
                        LibraryDestination.Trash -> app.notes.observeTrashNotes()
                        null -> flowOf(emptyList())
                    }
                }
                val collectionNotes by collectionNotesFlow.collectAsState(initial = emptyList())
                val localSummary by app.prefs.localSummaryEnabled.collectAsState()
                val feedbackBadgePersistent by overlayPrefs.feedbackBadgePersistent.collectAsState()
                val ballSize by overlayPrefs.ballSize.collectAsState()
                val simulatePremium by app.prefs.simulatePremium.collectAsState()
                val remoteAi by app.prefs.remoteAiEnabled.collectAsState()
                val remoteBaseUrl by app.prefs.remoteAiBaseUrlFlow.collectAsState()
                val remoteApiKey by app.prefs.remoteAiApiKeyFlow.collectAsState()
                val remoteModel by app.prefs.remoteAiModelFlow.collectAsState()

                BackHandler(
                    enabled = showSettings || selectedNote != null ||
                        libraryDestination != null || showLibrary || showAllNotes
                ) {
                    when {
                        showSettings -> showSettings = false
                        selectedNote != null -> selectedNote = null
                        libraryDestination != null -> libraryDestination = null
                        showLibrary -> showLibrary = false
                        showAllNotes -> showAllNotes = false
                    }
                }

                when {
                    showSettings -> {
                        SettingsScreen(
                            overlayRunning = overlayRunning,
                            localSummaryEnabled = localSummary,
                            feedbackBadgePersistent = feedbackBadgePersistent,
                            ballSize = ballSize,
                            simulatePremium = simulatePremium,
                            remoteAiEnabled = remoteAi,
                            remoteAiBaseUrl = remoteBaseUrl,
                            remoteAiApiKey = remoteApiKey,
                            remoteAiModel = remoteModel,
                            onOverlayRunningChange = { enabled ->
                                if (enabled) {
                                    ensureOverlayPermission {
                                        OverlayService.start(this@MainActivity)
                                    }
                                } else {
                                    OverlayService.stop(this@MainActivity)
                                }
                            },
                            onLocalSummaryChange = { app.prefs.setLocalSummaryEnabled(it) },
                            onFeedbackBadgePersistentChange = {
                                overlayPrefs.setFeedbackBadgePersistent(it)
                                OverlayService.updateFeedbackBadgePersistence(it)
                            },
                            onBallSizeChange = {
                                overlayPrefs.setBallSize(it)
                                OverlayService.updateBallSize(it)
                            },
                            onSimulatePremiumChange = { app.prefs.setSimulatePremium(it) },
                            onRemoteAiChange = { app.prefs.setRemoteAiEnabled(it) },
                            onSaveRemoteAiConfig = { url, key, model ->
                                app.prefs.setRemoteAiConfig(url, key, model)
                            },
                            onOpenBatterySettings = { openBatteryUnrestricted() },
                            onBack = { showSettings = false }
                        )
                    }
                    selectedNote != null -> {
                        val note = selectedNote!!
                        FlashNoteDetailScreen(
                            note = note,
                            browseNotes = if (collectionNotes.any { it.id == note.id }) collectionNotes else notes,
                            folders = folders,
                            tags = tags,
                            selectedTagIds = selectedNoteTags.mapTo(linkedSetOf()) { it.id },
                            onBack = { selectedNote = null },
                            onShowNote = { selectedNote = it },
                            onMoveToFolder = { folderId ->
                                lifecycleScope.launch {
                                    app.notes.moveToFolder(note.id, folderId)
                                    selectedNote = note.copy(folderId = folderId)
                                }
                            },
                            onReplaceTags = { tagIds ->
                                lifecycleScope.launch { app.notes.replaceTags(note.id, tagIds) }
                            },
                            onEditSummary = { summary ->
                                lifecycleScope.launch {
                                    app.notes.updateSummary(note.id, summary)
                                    selectedNote = note.copy(summary = summary.trim().ifBlank { null })
                                }
                            },
                            onArchive = {
                                lifecycleScope.launch {
                                    app.notes.archive(note.id)
                                    selectedNote = null
                                }
                            },
                            onRestoreFromArchive = {
                                lifecycleScope.launch {
                                    app.notes.restoreFromArchive(note.id)
                                    selectedNote = null
                                }
                            },
                            onMoveToTrash = {
                                lifecycleScope.launch {
                                    app.notes.moveToTrash(note.id)
                                    selectedNote = null
                                }
                            },
                            onRestoreFromTrash = {
                                lifecycleScope.launch {
                                    app.notes.restoreFromTrash(note.id)
                                    selectedNote = null
                                }
                            },
                            onPermanentlyDelete = {
                                lifecycleScope.launch {
                                    app.notes.permanentlyDelete(note)
                                    selectedNote = null
                                }
                            }
                        )
                    }
                    libraryDestination != null -> {
                        val destination = libraryDestination!!
                        FlashCollectionScreen(
                            destination = destination,
                            notes = collectionNotes,
                            onBack = { libraryDestination = null },
                            onOpenNote = { selectedNote = it },
                            onRestore = { note ->
                                lifecycleScope.launch {
                                    when (destination) {
                                        LibraryDestination.Archive ->
                                            app.notes.restoreFromArchive(note.id)
                                        LibraryDestination.Trash ->
                                            app.notes.restoreFromTrash(note.id)
                                        else -> Unit
                                    }
                                }
                            },
                            onPermanentlyDelete = { note ->
                                lifecycleScope.launch { app.notes.permanentlyDelete(note) }
                            }
                        )
                    }
                    showLibrary -> {
                        FlashOrganizerScreen(
                            folders = folderSummaries,
                            tags = tagSummaries,
                            archiveCount = archiveCount,
                            trashCount = trashCount,
                            onBack = { showLibrary = false },
                            onOpenAll = { showLibrary = false; listQuery = ""; showAllNotes = true },
                            onOpenDestination = { libraryDestination = it },
                            onCreateFolder = { name ->
                                lifecycleScope.launch { app.notes.createFolder(name) }
                            },
                            onCreateTag = { name, colorKey ->
                                lifecycleScope.launch { app.notes.createTag(name, colorKey) }
                            },
                            onDeleteFolder = { id ->
                                lifecycleScope.launch { app.notes.deleteFolder(id) }
                            },
                            onDeleteTag = { id ->
                                lifecycleScope.launch { app.notes.deleteTag(id) }
                            }
                        )
                    }
                    showAllNotes -> {
                        FlashAllNotesScreen(
                            notes = notes,
                            folders = folderSummaries,
                            initialQuery = listQuery,
                            onBack = { showAllNotes = false },
                            onOpenNote = { selectedNote = it },
                            onMoveSelected = { ids, folderId ->
                                lifecycleScope.launch {
                                    ids.forEach { app.notes.moveToFolder(it, folderId) }
                                }
                            },
                            onTrashSelected = { ids ->
                                lifecycleScope.launch {
                                    ids.forEach { app.notes.moveToTrash(it) }
                                }
                            }
                        )
                    }
                    else -> {
                        FlashHomeScreen(
                            notes = notes,
                            folders = folderSummaries,
                            onOpenNote = { selectedNote = it },
                            onOpenAll = { query -> listQuery = query; showAllNotes = true },
                            onOpenOrganizer = { showLibrary = true },
                            onOpenMy = { showSettings = true },
                            onStartCapture = {
                                ensureOverlayPermission {
                                    OverlayService.start(this@MainActivity)
                                    Toast.makeText(this@MainActivity, "悬浮球已开启，点击浮球截屏", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun openBatteryUnrestricted() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun ensureOverlayPermission(onGranted: () -> Unit) {
        if (Settings.canDrawOverlays(this)) {
            onGranted()
        } else {
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenSettings(intent)
        consumeOpenLatestNote(intent)
        maybeStartOverlayFromIntent(intent)
    }

    private fun consumeOpenSettings(intent: Intent?) {
        if (intent == null) return
        val want = intent.action == ACTION_OPEN_SETTINGS ||
            intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        if (!want) return
        intent.putExtra(EXTRA_OPEN_SETTINGS, false)
        if (intent.action == ACTION_OPEN_SETTINGS) intent.action = null
        openSettingsRequests.value = openSettingsRequests.value + 1
    }

    private fun consumeOpenLatestNote(intent: Intent?) {
        if (intent == null) return
        val want = intent.action == ACTION_OPEN_LATEST_NOTE ||
            intent.getBooleanExtra(EXTRA_OPEN_LATEST_NOTE, false)
        if (!want) return

        intent.putExtra(EXTRA_OPEN_LATEST_NOTE, false)
        if (intent.action == ACTION_OPEN_LATEST_NOTE) intent.action = null

        lifecycleScope.launch {
            val latest = (application as FlashNoteApp).notes.latestNote()
            if (latest != null) {
                latestNoteToOpen.value = latest
            } else {
                Toast.makeText(this@MainActivity, R.string.no_notes_yet, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Defer FGS start until after first frame — avoids main-thread jam on cold start/TCG
        maybeStartOverlayFromIntent(intent)
    }

    /** Emulator / automation: am start -n …/.MainActivity -a com.craznail.flashnote.START_OVERLAY */
    private fun maybeStartOverlayFromIntent(intent: Intent?) {
        if (intent?.action != ACTION_START_OVERLAY &&
            intent?.getBooleanExtra(EXTRA_START_OVERLAY, false) != true
        ) {
            return
        }
        // Consume so we don't re-trigger on every resume
        setIntent(Intent(intent).setAction(null).putExtra(EXTRA_START_OVERLAY, false))
        window.decorView.post {
            ensureOverlayPermission {
                OverlayService.start(this)
            }
        }
    }

    companion object {
        const val ACTION_START_OVERLAY = "com.craznail.flashnote.START_OVERLAY"
        const val EXTRA_START_OVERLAY = "startOverlay"
        const val ACTION_OPEN_SETTINGS = "com.craznail.flashnote.OPEN_SETTINGS"
        const val EXTRA_OPEN_SETTINGS = "openSettings"
        const val ACTION_OPEN_LATEST_NOTE = "com.craznail.flashnote.OPEN_LATEST_NOTE"
        const val EXTRA_OPEN_LATEST_NOTE = "openLatestNote"
    }
}

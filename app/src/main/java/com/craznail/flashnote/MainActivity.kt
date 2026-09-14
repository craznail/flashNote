package com.craznail.flashnote

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.craznail.flashnote.data.Note
import com.craznail.flashnote.overlay.OverlayService
import com.craznail.flashnote.ui.NoteDetailScreen
import com.craznail.flashnote.ui.NotesScreen
import com.craznail.flashnote.ui.SettingsScreen
import com.craznail.flashnote.ui.theme.FlashNoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as FlashNoteApp

        setContent {
            FlashNoteTheme {
                var showSettings by remember { mutableStateOf(false) }
                var selectedNote by remember { mutableStateOf<Note?>(null) }
                var overlayRunning by remember {
                    mutableStateOf(OverlayService.isRunning(this@MainActivity))
                }
                val notes by app.notes.observeNotes().collectAsState(initial = emptyList())
                val localSummary by app.prefs.localSummaryEnabled.collectAsState()
                val simulatePremium by app.prefs.simulatePremium.collectAsState()
                val remoteAi by app.prefs.remoteAiEnabled.collectAsState()

                when {
                    showSettings -> {
                        SettingsScreen(
                            localSummaryEnabled = localSummary,
                            simulatePremium = simulatePremium,
                            remoteAiEnabled = remoteAi,
                            onLocalSummaryChange = { app.prefs.setLocalSummaryEnabled(it) },
                            onSimulatePremiumChange = { app.prefs.setSimulatePremium(it) },
                            onRemoteAiChange = { app.prefs.setRemoteAiEnabled(it) },
                            onBack = { showSettings = false }
                        )
                    }
                    selectedNote != null -> {
                        val note = selectedNote!!
                        NoteDetailScreen(
                            note = note,
                            onBack = { selectedNote = null },
                            onDelete = {
                                lifecycleScope.launch {
                                    app.notes.delete(note)
                                    selectedNote = null
                                }
                            }
                        )
                    }
                    else -> {
                        NotesScreen(
                            notes = notes,
                            overlayRunning = overlayRunning,
                            onToggleOverlay = {
                                if (overlayRunning) {
                                    OverlayService.stop(this@MainActivity)
                                    overlayRunning = false
                                } else {
                                    ensureOverlayPermission {
                                        OverlayService.start(this@MainActivity)
                                        overlayRunning = true
                                    }
                                }
                            },
                            onOpenSettings = { showSettings = true },
                            onOpenNote = { selectedNote = it },
                            onDelete = { note ->
                                lifecycleScope.launch { app.notes.delete(note) }
                            }
                        )
                    }
                }
            }
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
}

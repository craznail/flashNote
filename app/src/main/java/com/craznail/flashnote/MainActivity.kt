package com.craznail.flashnote

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
                // Driven by OverlayService lifecycle — covers chip toggle AND START_OVERLAY intent
                val overlayRunning by OverlayService.running.collectAsState()
                val notes by app.notes.observeNotes().collectAsState(initial = emptyList())
                val localSummary by app.prefs.localSummaryEnabled.collectAsState()
                val simulatePremium by app.prefs.simulatePremium.collectAsState()
                val remoteAi by app.prefs.remoteAiEnabled.collectAsState()
                val remoteBaseUrl by app.prefs.remoteAiBaseUrlFlow.collectAsState()
                val remoteApiKey by app.prefs.remoteAiApiKeyFlow.collectAsState()
                val remoteModel by app.prefs.remoteAiModelFlow.collectAsState()

                when {
                    showSettings -> {
                        SettingsScreen(
                            localSummaryEnabled = localSummary,
                            simulatePremium = simulatePremium,
                            remoteAiEnabled = remoteAi,
                            remoteAiBaseUrl = remoteBaseUrl,
                            remoteAiApiKey = remoteApiKey,
                            remoteAiModel = remoteModel,
                            onLocalSummaryChange = { app.prefs.setLocalSummaryEnabled(it) },
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
                                } else {
                                    ensureOverlayPermission {
                                        OverlayService.start(this@MainActivity)
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
        maybeStartOverlayFromIntent(intent)
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
    }
}

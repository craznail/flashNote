package com.craznail.flashnote

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.craznail.flashnote.data.NoteRepository
import com.craznail.flashnote.data.PreferencesManager

class FlashNoteApp : Application() {
    lateinit var notes: NoteRepository
        private set
    lateinit var prefs: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        notes = NoteRepository(this)
        prefs = PreferencesManager.get(this)
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY,
                getString(R.string.notification_channel_overlay),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CAPTURE,
                getString(R.string.notification_channel_capture),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val CHANNEL_OVERLAY = "overlay"
        const val CHANNEL_CAPTURE = "capture"
    }
}

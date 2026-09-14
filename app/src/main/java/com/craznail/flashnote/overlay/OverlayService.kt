package com.craznail.flashnote.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.craznail.flashnote.FlashNoteApp
import com.craznail.flashnote.MainActivity
import com.craznail.flashnote.R
import com.craznail.flashnote.capture.CaptureService
import com.craznail.flashnote.capture.ProjectionPermissionActivity

/**
 * Foreground service hosting the floating ball.
 * Path: Overlay → Capture (MediaProjection) → Save PNG + Room → Feedback.
 */
class OverlayService : Service() {

    private var ballView: OverlayBallView? = null
    private var windowManager: WindowManager? = null

    /** When true, next capture requests local summary (prefs may also enable). */
    @Volatile private var wantSummary = false
    /** When true, skip OCR and summary — image only. */
    @Volatile private var wantImageOnly = false
    @Volatile private var showedContinuousTip = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        showBall()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_SAVED -> {
                val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
                val toast = intent.getStringExtra(EXTRA_TOAST)
                    ?: getString(R.string.saved_to_notes)
                ballView?.showSuccessFeedback(
                    toastText = toast,
                    thumbnailPath = path
                )
            }
            ACTION_SHOW_UNAUTHORIZED -> {
                ballView?.showFailureUnauthorized()
            }
            ACTION_PROJECTION_READY -> {
                if (!showedContinuousTip) {
                    showedContinuousTip = true
                    ballView?.showSuccessFeedback(
                        toastText = getString(R.string.continuous_capture_ready),
                        thumbnailPath = null
                    )
                }
            }
            ACTION_PROJECTION_LOST -> {
                showedContinuousTip = false
                ballView?.showSuccessFeedback(
                    toastText = getString(R.string.projection_lost_reauth),
                    thumbnailPath = null
                )
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, FlashNoteApp.CHANNEL_OVERLAY)
            .setContentTitle(getString(R.string.notification_overlay_title))
            .setContentText(getString(R.string.notification_overlay_text))
            .setSmallIcon(R.drawable.ic_flash_note)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, 0)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showBall() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ballView = OverlayBallView(this).also { ball ->
            ball.onTap = {
                wantSummary = false
                wantImageOnly = false
                triggerCapture()
            }
            ball.onSaveImageOnly = {
                wantSummary = false
                wantImageOnly = true
                triggerCapture()
            }
            ball.onSaveImageAndSummary = {
                wantSummary = true
                wantImageOnly = false
                triggerCapture()
            }
            ball.attach(windowManager!!)
        }
    }

    private fun triggerCapture() {
        if (CaptureService.hasActiveProjection()) {
            CaptureService.startCapture(
                this,
                withSummary = wantSummary,
                imageOnly = wantImageOnly
            )
        } else {
            // No token → system MediaProjection consent (brief translucent activity)
            startActivity(
                Intent(this, ProjectionPermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(ProjectionPermissionActivity.EXTRA_WITH_SUMMARY, wantSummary)
                    putExtra(ProjectionPermissionActivity.EXTRA_IMAGE_ONLY, wantImageOnly)
                }
            )
        }
    }

    override fun onDestroy() {
        CaptureService.stop(this)
        ballView?.let { v -> runCatching { windowManager?.removeView(v) } }
        ballView = null
        windowManager = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.craznail.flashnote.STOP_OVERLAY"
        const val ACTION_SHOW_SAVED = "com.craznail.flashnote.SHOW_SAVED"
        const val ACTION_SHOW_UNAUTHORIZED = "com.craznail.flashnote.SHOW_UNAUTHORIZED"
        const val ACTION_PROJECTION_READY = "com.craznail.flashnote.PROJECTION_READY"
        const val ACTION_PROJECTION_LOST = "com.craznail.flashnote.PROJECTION_LOST"
        const val EXTRA_IMAGE_PATH = "imagePath"
        const val EXTRA_TOAST = "toastText"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            )
        }

        fun notifySaved(
            context: Context,
            imagePath: String? = null,
            toastText: String? = null
        ) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_SHOW_SAVED)
                    .putExtra(EXTRA_IMAGE_PATH, imagePath)
                    .putExtra(EXTRA_TOAST, toastText)
            )
        }

        fun notifyUnauthorized(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_SHOW_UNAUTHORIZED)
            )
        }

        fun notifyProjectionReady(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_PROJECTION_READY)
            )
        }

        fun notifyProjectionLost(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_PROJECTION_LOST)
            )
        }

        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            return am.getRunningServices(50).any {
                it.service.className == OverlayService::class.java.name
            }
        }
    }
}

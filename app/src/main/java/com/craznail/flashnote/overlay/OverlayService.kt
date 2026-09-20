package com.craznail.flashnote.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.craznail.flashnote.FlashNoteApp
import com.craznail.flashnote.MainActivity
import com.craznail.flashnote.R
import com.craznail.flashnote.capture.CaptureService
import com.craznail.flashnote.capture.ProjectionPermissionActivity
import com.craznail.flashnote.data.OverlayBallSize

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
        instance = this
        setRunning(true)
        // Must hit startForeground before ANY heavy UI work — TCG/slow devices
        // otherwise trip ForegroundServiceDidNotStartInTimeException (~5–10s).
        startAsForeground()
        // Defer WindowManager ball attach so onCreate returns immediately.
        Handler(Looper.getMainLooper()).post { showBall() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-assert FGS promptly if we were started via startForegroundService
        // while already created (e.g. notifySaved) — cheap if already foreground.
        startAsForeground()
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_SAVED -> {
                val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
                val toast = intent.getStringExtra(EXTRA_TOAST)
                // Green check only on plain save; fallback / config tips ride as side pill.
                val tip = toast?.takeIf {
                    it.isNotBlank() && it != getString(R.string.saved_to_notes)
                }
                ballView?.showSuccessFeedback(
                    tipText = tip,
                    thumbnailPath = path
                )
            }
            ACTION_SHOW_UNAUTHORIZED -> {
                val reason = intent.getStringExtra(EXTRA_FAIL_REASON)
                    ?: getString(R.string.unauthorized_capture)
                ballView?.showFailure(reason)
            }
            ACTION_PROJECTION_READY -> {
                if (!showedContinuousTip) {
                    showedContinuousTip = true
                    // System tip only — no green check (locked UI)
                    ballView?.showSystemTip(getString(R.string.continuous_capture_ready))
                }
            }
            ACTION_PROJECTION_LOST -> {
                showedContinuousTip = false
                ballView?.showFailure(getString(R.string.projection_lost_reauth))
            }
            ACTION_SHOW_TOAST -> {
                val toast = intent.getStringExtra(EXTRA_TOAST) ?: return START_STICKY
                val sticky = intent.getBooleanExtra(EXTRA_STICKY, false)
                if (sticky) {
                    // 「摘要生成中…」 — light sticky side pill until success/fail clears it
                    ballView?.showLoadingPill(toast)
                } else {
                    ballView?.showPlainToast(toast, durationMs = 1_200L)
                }
            }
            ACTION_CLEAR_TOAST -> {
                ballView?.clearSidePill(immediate = true)
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
            .setSmallIcon(R.drawable.ic_stat_flash_note)
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
        if (ballView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ballView = OverlayBallView(this).also { ball ->
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
            ball.onOpenSettings = {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                        action = MainActivity.ACTION_OPEN_SETTINGS
                    }
                )
            }
            ball.onExit = {
                CaptureService.stop(this)
                stopSelf()
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
        if (instance === this) instance = null
        CaptureService.stop(this)
        ballView?.let { v -> runCatching { windowManager?.removeView(v) } }
        ballView = null
        windowManager = null
        setRunning(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.craznail.flashnote.STOP_OVERLAY"
        const val ACTION_SHOW_SAVED = "com.craznail.flashnote.SHOW_SAVED"
        const val ACTION_SHOW_UNAUTHORIZED = "com.craznail.flashnote.SHOW_UNAUTHORIZED"
        const val ACTION_PROJECTION_READY = "com.craznail.flashnote.PROJECTION_READY"
        const val ACTION_PROJECTION_LOST = "com.craznail.flashnote.PROJECTION_LOST"
        const val ACTION_SHOW_TOAST = "com.craznail.flashnote.SHOW_TOAST"
        const val ACTION_CLEAR_TOAST = "com.craznail.flashnote.CLEAR_TOAST"
        const val EXTRA_IMAGE_PATH = "imagePath"
        const val EXTRA_TOAST = "toastText"
        const val EXTRA_STICKY = "stickyToast"
        const val EXTRA_FAIL_REASON = "failReason"
        private const val NOTIF_ID = 1001

        @Volatile
        private var instance: OverlayService? = null

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

        fun updateFeedbackBadgePersistence(enabled: Boolean) {
            instance?.ballView?.setFeedbackBadgePersistent(enabled)
        }

        fun updateBallSize(size: OverlayBallSize) {
            instance?.ballView?.setBallSize(size)
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

        fun notifyUnauthorized(context: Context, reason: String? = null) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_SHOW_UNAUTHORIZED)
                    .putExtra(EXTRA_FAIL_REASON, reason)
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

        /** Timed tip pill (no green check). */
        fun notifyToast(context: Context, text: String) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_SHOW_TOAST)
                    .putExtra(EXTRA_TOAST, text)
                    .putExtra(EXTRA_STICKY, false)
            )
        }

        /** Sticky side pill while remote summary runs — cleared on save / fail. */
        fun notifyLoading(context: Context, text: String) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_SHOW_TOAST)
                    .putExtra(EXTRA_TOAST, text)
                    .putExtra(EXTRA_STICKY, true)
            )
        }

        fun clearToast(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_CLEAR_TOAST)
            )
        }

        private val _running = MutableStateFlow(false)
        /** True while OverlayService is alive — UI collects this for the inbox chip. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private fun setRunning(value: Boolean) {
            _running.value = value
        }

        fun isRunning(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = _running.value
    }
}

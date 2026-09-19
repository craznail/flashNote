package com.craznail.flashnote.capture

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.craznail.flashnote.FlashNoteApp
import com.craznail.flashnote.R
import com.craznail.flashnote.data.PreferencesManager
import com.craznail.flashnote.data.SummaryMode
import com.craznail.flashnote.overlay.OverlayService
import com.craznail.flashnote.process.LocalOcr
import com.craznail.flashnote.process.LocalSummary
import com.craznail.flashnote.process.RemoteAiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds MediaProjection + a **persistent VirtualDisplay** for the process lifetime.
 * Releasing the VD after each shot causes HyperOS/Android 14 to drop「共享屏幕中」~10s later.
 */
class CaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val capturing = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var withSummary = false
    private var imageOnly = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WITH_PROJECTION -> {
                withSummary = intent.getBooleanExtra(EXTRA_WITH_SUMMARY, false)
                imageOnly = intent.getBooleanExtra(EXTRA_IMAGE_ONLY, false)
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (code == Activity.RESULT_OK && data != null) {
                    startAsForeground()
                    setupProjection(code, data)
                    ensureVirtualDisplay()
                    OverlayService.notifyProjectionReady(this)
                    mainHandler.postDelayed({ doCapture() }, 400)
                } else {
                    OverlayService.notifyUnauthorized(this, getString(R.string.unauthorized_capture))
                    if (!hasActiveProjection()) stopSelf()
                }
            }
            ACTION_CAPTURE -> {
                withSummary = intent.getBooleanExtra(EXTRA_WITH_SUMMARY, false)
                imageOnly = intent.getBooleanExtra(EXTRA_IMAGE_ONLY, false)
                startAsForeground()
                ensureVirtualDisplay()
                doCapture()
            }
            ACTION_STOP -> {
                // If system restarted us as FGS for a pending STOP, promote first.
                runCatching { startAsForeground() }
                teardown()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                if (hasActiveProjection()) {
                    startAsForeground()
                    ensureVirtualDisplay()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, FlashNoteApp.CHANNEL_CAPTURE)
            .setContentTitle(getString(R.string.notification_capture_holding_title))
            .setContentText(getString(R.string.notification_capture_holding_text))
            .setSmallIcon(R.drawable.ic_stat_flash_note)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun readScreenMetrics() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection?.stop()
        projection = mpm.getMediaProjection(resultCode, data)?.also { mp ->
            activeProjection = mp
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection.onStop — system revoked screen share")
                    activeProjection = null
                    projection = null
                    releaseVirtualDisplay()
                    OverlayService.notifyProjectionLost(this@CaptureService)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }, mainHandler)
        }
    }

    /** Keep VD alive for the whole projection session (Xiaomi ~10s drop otherwise). */
    private fun ensureVirtualDisplay() {
        val mp = projection ?: activeProjection ?: return
        if (virtualDisplay != null && imageReader != null) return
        readScreenMetrics()
        releaseVirtualDisplay()

        val thread = HandlerThread("flashnote-vd").also { it.start() }
        captureThread = thread
        captureHandler = Handler(thread.looper)

        val reader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, /*maxImages*/ 3
        )
        imageReader = reader

        virtualDisplay = mp.createVirtualDisplay(
            "flashnote-keepalive",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )
        Log.i(TAG, "VirtualDisplay keepalive created ${screenWidth}x$screenHeight")
    }

    private fun doCapture() {
        val mp = projection ?: activeProjection
        if (mp == null) {
            startActivity(
                ProjectionPermissionActivity.intent(this, withSummary, imageOnly)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        ensureVirtualDisplay()
        if (!capturing.compareAndSet(false, true)) return

        scope.launch {
            try {
                // Overlay windows carry FLAG_SECURE, so MediaProjection excludes them
                // without hiding the UI and producing a visible blink.
                val bitmap = withContext(Dispatchers.IO) { grabBitmapFromReader() }
                if (bitmap != null) {
                    val path = withContext(Dispatchers.IO) { savePng(bitmap) }
                    val app = application as FlashNoteApp
                    val prefs = PreferencesManager.get(this@CaptureService)
                    var ocr: String? = null
                    var summary: String? = null
                    var mode = SummaryMode.NONE
                    var toastMsg: String? = null
                    val summaryRequested =
                        withSummary || prefs.localSummaryEnabled.value
                    val skipText = imageOnly || (!withSummary && !prefs.localSummaryEnabled.value)
                    if (!skipText) {
                        ocr = withContext(Dispatchers.Default) { LocalOcr.recognize(bitmap) }
                        if (summaryRequested) {
                            val useRemote =
                                withSummary && prefs.isPremium && prefs.remoteAiEnabled.value
                            if (useRemote) {
                                if (!RemoteAiClient.isConfigured(prefs)) {
                                    summary = LocalSummary.fromOcr(ocr)
                                    if (summary != null) {
                                        mode = SummaryMode.LOCAL
                                    }
                                    toastMsg = getString(R.string.remote_not_configured)
                                } else {
                                    OverlayService.notifyLoading(
                                        this@CaptureService,
                                        getString(R.string.summarizing_remote)
                                    )
                                    val remote = withContext(Dispatchers.IO) {
                                        RemoteAiClient.summarize(prefs, ocr)
                                    }
                                    if (remote.isSuccess) {
                                        summary = remote.getOrNull()
                                        mode = SummaryMode.REMOTE
                                    } else {
                                        summary = LocalSummary.fromOcr(ocr)
                                        if (summary != null) {
                                            mode = SummaryMode.LOCAL
                                            toastMsg = getString(R.string.remote_fallback_local)
                                        } else {
                                            toastMsg = getString(R.string.remote_fallback_image)
                                        }
                                    }
                                }
                            } else {
                                summary = LocalSummary.fromOcr(ocr)
                                mode = if (summary != null) SummaryMode.LOCAL else SummaryMode.NONE
                            }
                        }
                    }
                    bitmap.recycle()
                    app.notes.saveNote(path, ocr, summary, mode)
                    OverlayService.notifySaved(
                        this@CaptureService,
                        path,
                        toastMsg ?: getString(R.string.saved_to_notes)
                    )
                } else {
                    OverlayService.notifyUnauthorized(this@CaptureService, getString(R.string.capture_failed_save))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Clears sticky loading pill if remote was in-flight when capture errored.
                OverlayService.notifyUnauthorized(this@CaptureService, getString(R.string.capture_failed_save))
            } finally {
                capturing.set(false)
                // Keep FGS + VD + MediaProjection alive.
            }
        }
    }

    private fun grabBitmapFromReader(): Bitmap? {
        val reader = imageReader ?: return null
        val width = screenWidth
        val height = screenHeight
        if (width <= 0 || height <= 0) return null

        // Drain stale frames, then wait briefly for a fresh one
        fun drain() {
            while (true) {
                val img = reader.acquireLatestImage() ?: break
                img.close()
            }
        }
        drain()

        val deadline = System.currentTimeMillis() + 2000
        var bitmap: Bitmap? = null
        while (System.currentTimeMillis() < deadline && bitmap == null) {
            val image: Image? = reader.acquireLatestImage()
            if (image != null) {
                try {
                    bitmap = imageToBitmap(image, width, height)
                } finally {
                    image.close()
                }
            } else {
                try {
                    Thread.sleep(30)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        return bitmap
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bmp = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buffer)
        return if (bmp.width == width) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height).also {
            if (it != bmp) bmp.recycle()
        }
    }

    private fun savePng(bitmap: Bitmap): String {
        val app = application as FlashNoteApp
        val dir = app.notes.notesDirectory()
        val name = "note_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(Date()) + ".png"
        val file = File(dir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
    }

    private fun teardown() {
        releaseVirtualDisplay()
        projection?.stop()
        projection = null
        activeProjection = null
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FlashNote.Capture"
        const val ACTION_START_WITH_PROJECTION = "com.craznail.flashnote.START_WITH_PROJECTION"
        const val ACTION_CAPTURE = "com.craznail.flashnote.CAPTURE"
        const val ACTION_STOP = "com.craznail.flashnote.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_WITH_SUMMARY = "withSummary"
        const val EXTRA_IMAGE_ONLY = "imageOnly"
        private const val NOTIF_ID = 1002

        @Volatile
        private var activeProjection: MediaProjection? = null

        fun hasActiveProjection(): Boolean = activeProjection != null

        fun stop(context: Context) {
            context.startService(
                Intent(context, CaptureService::class.java).setAction(ACTION_STOP)
            )
        }

        fun startWithProjection(
            context: Context,
            resultCode: Int,
            data: Intent,
            withSummary: Boolean = false,
            imageOnly: Boolean = false
        ) {
            val i = Intent(context, CaptureService::class.java).apply {
                action = ACTION_START_WITH_PROJECTION
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_WITH_SUMMARY, withSummary)
                putExtra(EXTRA_IMAGE_ONLY, imageOnly)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun startCapture(
            context: Context,
            withSummary: Boolean = false,
            imageOnly: Boolean = false
        ) {
            val i = Intent(context, CaptureService::class.java).apply {
                action = ACTION_CAPTURE
                putExtra(EXTRA_WITH_SUMMARY, withSummary)
                putExtra(EXTRA_IMAGE_ONLY, imageOnly)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}

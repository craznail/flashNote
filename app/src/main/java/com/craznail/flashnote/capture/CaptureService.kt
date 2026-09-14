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
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.craznail.flashnote.FlashNoteApp
import com.craznail.flashnote.R
import com.craznail.flashnote.data.PreferencesManager
import com.craznail.flashnote.overlay.OverlayService
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
 * MediaProjection → PNG in app files → Room note → overlay「已保存到笔记」.
 */
class CaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private val capturing = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var withSummary = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WITH_PROJECTION -> {
                withSummary = intent.getBooleanExtra(EXTRA_WITH_SUMMARY, false)
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
                    mainHandler.postDelayed({ doCapture() }, 350)
                } else {
                    OverlayService.notifyUnauthorized(this)
                    stopSelf()
                }
            }
            ACTION_CAPTURE -> {
                withSummary = intent.getBooleanExtra(EXTRA_WITH_SUMMARY, false)
                startAsForeground()
                doCapture()
            }
            ACTION_STOP -> {
                teardown()
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, FlashNoteApp.CHANNEL_CAPTURE)
            .setContentTitle(getString(R.string.notification_capture_title))
            .setSmallIcon(R.drawable.ic_flash_note)
            .setOngoing(true)
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

    private fun setupProjection(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection?.stop()
        projection = mpm.getMediaProjection(resultCode, data)?.also { mp ->
            activeProjection = mp
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    activeProjection = null
                    teardownDisplays()
                }
            }, mainHandler)
        }
    }

    private fun doCapture() {
        val mp = projection ?: activeProjection
        if (mp == null) {
            startActivity(ProjectionPermissionActivity.intent(this, withSummary))
            return
        }
        if (!capturing.compareAndSet(false, true)) return

        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { grabBitmap(mp) }
                if (bitmap != null) {
                    val path = withContext(Dispatchers.IO) { savePng(bitmap) }
                    bitmap.recycle()
                    val app = application as FlashNoteApp
                    // Local summary: no-op generation for slice ① (null even if requested)
                    val prefs = PreferencesManager.get(this@CaptureService)
                    val summary: String? = if (withSummary || prefs.localSummaryEnabled.value) {
                        null // stub — generation not implemented
                    } else null
                    app.notes.saveNote(path, summary)
                    OverlayService.notifySaved(this@CaptureService, path)
                } else {
                    OverlayService.notifyUnauthorized(this@CaptureService)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                OverlayService.notifyUnauthorized(this@CaptureService)
            } finally {
                capturing.set(false)
                mainHandler.postDelayed({
                    if (!capturing.get()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                }, 1500)
            }
        }
    }

    private fun grabBitmap(mp: MediaProjection): Bitmap? {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        teardownDisplays()

        val thread = HandlerThread("flashnote-capture").also { it.start() }
        captureThread = thread
        val handler = Handler(thread.looper)

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = mp.createVirtualDisplay(
            "flashnote-vd",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )

        val deadline = System.currentTimeMillis() + 2500
        var bitmap: Bitmap? = null
        while (System.currentTimeMillis() < deadline && bitmap == null) {
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
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
                    bitmap = Bitmap.createBitmap(bmp, 0, 0, width, height)
                    if (bmp != bitmap) bmp.recycle()
                } finally {
                    image.close()
                }
            } else {
                try {
                    Thread.sleep(40)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        teardownDisplays()
        return bitmap
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

    private fun teardownDisplays() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        captureThread?.quitSafely()
        captureThread = null
    }

    private fun teardown() {
        teardownDisplays()
        projection?.stop()
        projection = null
        activeProjection = null
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_WITH_PROJECTION = "com.craznail.flashnote.START_WITH_PROJECTION"
        const val ACTION_CAPTURE = "com.craznail.flashnote.CAPTURE"
        const val ACTION_STOP = "com.craznail.flashnote.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_WITH_SUMMARY = "withSummary"
        private const val NOTIF_ID = 1002

        @Volatile
        private var activeProjection: MediaProjection? = null

        fun hasActiveProjection(): Boolean = activeProjection != null

        fun startWithProjection(
            context: Context,
            resultCode: Int,
            data: Intent,
            withSummary: Boolean = false
        ) {
            val i = Intent(context, CaptureService::class.java).apply {
                action = ACTION_START_WITH_PROJECTION
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_WITH_SUMMARY, withSummary)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun startCapture(context: Context, withSummary: Boolean = false) {
            val i = Intent(context, CaptureService::class.java).apply {
                action = ACTION_CAPTURE
                putExtra(EXTRA_WITH_SUMMARY, withSummary)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}

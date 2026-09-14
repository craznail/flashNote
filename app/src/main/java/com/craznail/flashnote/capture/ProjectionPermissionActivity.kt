package com.craznail.flashnote.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import com.craznail.flashnote.overlay.OverlayService

/**
 * One-shot MediaProjection consent. Prefer entire screen on Android 14+.
 * After grant, CaptureService keeps the token for the process lifetime.
 */
class ProjectionPermissionActivity : Activity() {

    private var withSummary = false
    private var imageOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Already holding a live token → should not reach here; finish quietly
        if (CaptureService.hasActiveProjection()) {
            CaptureService.startCapture(this, withSummary, imageOnly)
            finish()
            return
        }
        withSummary = intent.getBooleanExtra(EXTRA_WITH_SUMMARY, false)
        imageOnly = intent.getBooleanExtra(EXTRA_IMAGE_ONLY, false)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= 34) {
            // Prefer whole screen — avoid "共享一个应用" friction on Android 14+
            mpm.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            @Suppress("DEPRECATION")
            mpm.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(captureIntent, REQ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ) {
            if (resultCode == RESULT_OK && data != null) {
                CaptureService.startWithProjection(this, resultCode, data, withSummary, imageOnly)
            } else {
                OverlayService.notifyUnauthorized(this)
            }
        }
        finish()
    }

    companion object {
        private const val REQ = 9001
        const val EXTRA_WITH_SUMMARY = "withSummary"
        const val EXTRA_IMAGE_ONLY = "imageOnly"

        fun intent(
            context: Context,
            withSummary: Boolean = false,
            imageOnly: Boolean = false
        ) =
            Intent(context, ProjectionPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_WITH_SUMMARY, withSummary)
                .putExtra(EXTRA_IMAGE_ONLY, imageOnly)
    }
}

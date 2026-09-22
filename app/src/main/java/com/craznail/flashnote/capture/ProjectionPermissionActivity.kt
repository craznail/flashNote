package com.craznail.flashnote.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.craznail.flashnote.overlay.OverlayService

/**
 * One-shot MediaProjection consent.
 * API 34+: MUST use createConfigForDefaultDisplay() so the system dialog
 * only offers whole-screen capture (no「共享一个应用」/「下一步」 path on AOSP).
 * Some OEMs may still show a picker — after grant, CaptureService holds the token.
 */
class ProjectionPermissionActivity : Activity() {

    private var withSummary = false
    private var imageOnly = false
    private var selectionTop: Int? = null
    private var selectionBottom: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        withSummary = intent.getBooleanExtra(EXTRA_WITH_SUMMARY, false)
        imageOnly = intent.getBooleanExtra(EXTRA_IMAGE_ONLY, false)
        selectionTop = intent.getIntExtra(EXTRA_SELECTION_TOP, -1).takeIf { it >= 0 }
        selectionBottom = intent.getIntExtra(EXTRA_SELECTION_BOTTOM, -1).takeIf { it >= 0 }

        if (CaptureService.hasActiveProjection()) {
            Log.i(TAG, "reuse active MediaProjection — skip consent UI")
            CaptureService.startCapture(this, withSummary, imageOnly, selectionTop, selectionBottom)
            finish()
            return
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent: Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val config = MediaProjectionConfig.createConfigForDefaultDisplay()
            Log.i(TAG, "API34+ whole-display MediaProjectionConfig")
            mpm.createScreenCaptureIntent(config)
        } else {
            Log.i(TAG, "pre-34 default createScreenCaptureIntent")
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
                CaptureService.startWithProjection(
                    this,
                    resultCode,
                    data,
                    withSummary,
                    imageOnly,
                    selectionTop,
                    selectionBottom
                )
            } else {
                OverlayService.notifyUnauthorized(this)
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "FlashNote.Projection"
        private const val REQ = 9001
        const val EXTRA_WITH_SUMMARY = "withSummary"
        const val EXTRA_IMAGE_ONLY = "imageOnly"
        const val EXTRA_SELECTION_TOP = "selectionTop"
        const val EXTRA_SELECTION_BOTTOM = "selectionBottom"

        fun intent(
            context: Context,
            withSummary: Boolean = false,
            imageOnly: Boolean = false,
            selectionTop: Int? = null,
            selectionBottom: Int? = null
        ) =
            Intent(context, ProjectionPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_WITH_SUMMARY, withSummary)
                .putExtra(EXTRA_IMAGE_ONLY, imageOnly)
                .apply {
                    selectionTop?.let { putExtra(EXTRA_SELECTION_TOP, it) }
                    selectionBottom?.let { putExtra(EXTRA_SELECTION_BOTTOM, it) }
                }
    }
}

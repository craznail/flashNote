package com.craznail.flashnote.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.craznail.flashnote.overlay.OverlayService

/**
 * Transparent one-shot Activity for MediaProjection consent.
 * Finishes immediately after result so the ball does not steal focus long-term.
 * Denial → overlay failure feedback「未授权截屏」; tap ball again to re-enter.
 */
class ProjectionPermissionActivity : Activity() {

    private var withSummary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        withSummary = intent.getBooleanExtra(EXTRA_WITH_SUMMARY, false)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ) {
            if (resultCode == RESULT_OK && data != null) {
                CaptureService.startWithProjection(this, resultCode, data, withSummary)
            } else {
                OverlayService.notifyUnauthorized(this)
            }
        }
        finish()
    }

    companion object {
        private const val REQ = 9001
        const val EXTRA_WITH_SUMMARY = "withSummary"

        fun intent(context: Context, withSummary: Boolean = false) =
            Intent(context, ProjectionPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_WITH_SUMMARY, withSummary)
    }
}

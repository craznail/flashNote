package com.craznail.flashnote.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent

/**
 * One-shot high quality screenshot backend.
 *
 * Unlike MediaProjection this does not keep a virtual display / screen-sharing session alive,
 * so OEM "screen sharing protection" does not have a persistent projection to degrade.
 */
class FlashNoteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: FlashNoteAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun requestScreenshot(callback: (Result<Bitmap>) -> Unit): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        try {
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                buffer,
                                screenshot.colorSpace
                            )
                            val softwareBitmap = hardwareBitmap
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBitmap?.recycle()

                            if (softwareBitmap != null) {
                                callback(Result.success(softwareBitmap))
                            } else {
                                callback(
                                    Result.failure(
                                        ScreenshotCaptureException(
                                            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR
                                        )
                                    )
                                )
                            }
                        } catch (t: Throwable) {
                            callback(Result.failure(t))
                        } finally {
                            buffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        callback(Result.failure(ScreenshotCaptureException(errorCode)))
                    }
                }
            )
            return true
        }
    }
}

class ScreenshotCaptureException(
    val errorCode: Int
) : RuntimeException("Accessibility screenshot failed: errorCode=$errorCode")

package com.craznail.flashnote.capture

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares the original PNG through the same readable content URI used by the clipboard. */
internal object NoteImageTransfer {
    private fun imageUri(context: Context, path: String) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(path).also { require(it.isFile) { "Screenshot is missing: $path" } }
    )

    fun copy(context: Context, path: String) {
        val uri = imageUri(context, path)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "flashNote screenshot", uri))
    }

    fun share(context: Context, path: String) {
        val uri = imageUri(context, path)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "flashNote screenshot", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享图片").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

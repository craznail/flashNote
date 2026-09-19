package com.craznail.flashnote.overlay

/** Keeps overlay windows visible to the user while excluding them from capture output. */
internal object OverlayCapturePolicy {
    fun secureFlags(baseFlags: Int, secureFlag: Int): Int = baseFlags or secureFlag
}

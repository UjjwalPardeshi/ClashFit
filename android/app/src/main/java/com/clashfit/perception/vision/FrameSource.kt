package com.clashfit.perception.vision

import android.graphics.Bitmap

/**
 * A camera source that can hand back a small picture of a recent moment.
 *
 * The referee's eyes need the frame from the bottom of the worst rep. This is the only way a
 * camera frame ever leaves the analysis pipeline in this app, and it goes exactly one place: the
 * on-device model, in memory, for one generation, then recycled. There is no path from here to
 * storage or to the network, and none should ever be added.
 */
interface FrameSource {
    /**
     * A downscaled copy of the frame nearest [tMs], or null if it has already rolled out of the
     * short ring the camera keeps. The caller owns the returned bitmap and must recycle it.
     */
    fun frameNear(tMs: Long): Bitmap?
}

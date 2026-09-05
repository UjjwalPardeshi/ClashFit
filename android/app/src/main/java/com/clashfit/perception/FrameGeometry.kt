package com.clashfit.perception

/**
 * The pixel geometry of one camera analysis frame.
 *
 * Two facts about a frame decide whether the skeleton lands on the body, and both are easy to
 * forget because neither shows up until you hold a real phone:
 *
 *  - **The buffer can be wider than the image.** A plane's rows are padded out to its row stride,
 *    so reading it as if the rows were packed slides every row after the first sideways.
 *  - **The frame arrives on its side.** The sensor is mounted at an angle to the phone, and the
 *    camera reports how far it has to be turned to stand up. A frame fed to the detector without
 *    that turn is a picture of someone lying down, and the landmarks come back in a frame that has
 *    nothing in common with the preview the player is looking at.
 *
 * This holds no Android types on purpose, so the arithmetic can be tested without a device.
 */
internal data class FrameGeometry(
    /** The width of the image inside the buffer, before it is turned upright. */
    val sourceWidth: Int,
    /** The height of the image inside the buffer, before it is turned upright. */
    val sourceHeight: Int,
    /** Columns actually stored per row, padding included. Never less than [sourceWidth]. */
    val bufferWidth: Int,
    /** Clockwise degrees needed to stand the frame up: 0, 90, 180 or 270. */
    val rotationDeg: Int,
) {
    /** True when standing the frame up swaps its width and height. */
    val turned: Boolean get() = rotationDeg == 90 || rotationDeg == 270

    /** The width of the upright image, which is the one the landmarks are normalised against. */
    val width: Int get() = if (turned) sourceHeight else sourceWidth

    /** The height of the upright image. */
    val height: Int get() = if (turned) sourceWidth else sourceHeight

    /** Width over height of the upright image. The overlay needs this to undo the preview's crop. */
    val aspect: Float get() = if (height <= 0) 0f else width.toFloat() / height

    /** True when the buffer can go to the detector untouched: already upright and already packed. */
    val readyAsIs: Boolean get() = rotationDeg == 0 && bufferWidth == sourceWidth

    /**
     * Where to move the source after rotating it about the origin, so its visible corner lands
     * back at (0, 0). Any padding columns then fall outside the upright rectangle and are clipped.
     */
    val translateX: Float get() = if (rotationDeg == 90 || rotationDeg == 180) width.toFloat() else 0f

    /** The other half of [translateX]. */
    val translateY: Float get() = if (rotationDeg == 180 || rotationDeg == 270) height.toFloat() else 0f

    override fun toString(): String =
        "${sourceWidth}x$sourceHeight buffer $bufferWidth rot $rotationDeg -> ${width}x$height aspect ${"%.3f".format(aspect)}"

    companion object {
        /**
         * Reads the geometry off an analysis frame's dimensions, its first plane's strides and the
         * rotation the camera reports. A stride that does not divide, or a rotation that is not a
         * quarter turn, is snapped to the nearest sane value rather than trusted.
         */
        fun of(width: Int, height: Int, rowStride: Int, pixelStride: Int, rotationDeg: Int): FrameGeometry {
            val stride = if (pixelStride > 0) rowStride / pixelStride else width
            val turn = ((rotationDeg % 360) + 360) % 360
            return FrameGeometry(
                sourceWidth = width,
                sourceHeight = height,
                bufferWidth = maxOf(stride, width),
                rotationDeg = ((turn + 45) / 90 * 90) % 360,
            )
        }
    }
}

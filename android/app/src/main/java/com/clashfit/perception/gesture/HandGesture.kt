package com.clashfit.perception.gesture

/**
 * The hand shapes the referee can read.
 *
 * The player is two metres from the phone with the screen facing them and cannot reach it. Voice
 * works until the room is loud, which at a venue is always. A hand held up to the camera works in
 * any room, needs no permission dialog, and the camera is already running at thirty frames a second
 * looking at exactly the person making the shape.
 *
 * These are the canned categories of MediaPipe's gesture recogniser, renamed to what they mean here.
 * Only three are wired to anything: an open palm, a thumb up and a closed fist. The rest are
 * recognised and ignored, so a wave or a victory sign during a set never fires a command by accident.
 */
enum class HandGesture(val mediaPipeName: String) {
    OPEN_PALM("Open_Palm"),
    THUMB_UP("Thumb_Up"),
    CLOSED_FIST("Closed_Fist"),
    THUMB_DOWN("Thumb_Down"),
    VICTORY("Victory"),
    POINTING_UP("Pointing_Up"),
    I_LOVE_YOU("ILoveYou"),
    NONE("None");

    companion object {
        private val byName = entries.associateBy { it.mediaPipeName }

        /** The recogniser's category label, or NONE for anything unexpected. */
        fun fromMediaPipe(name: String?): HandGesture = byName[name] ?: NONE
    }
}

/** One recogniser reading: what was seen, how sure it was, and when. */
data class GestureReading(val gesture: HandGesture, val score: Float, val tMs: Long)

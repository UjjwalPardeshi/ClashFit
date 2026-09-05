package com.clashfit.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.clashfit.R

/**
 * The looping demonstration of a movement, shown under an exercise in the picker.
 *
 * The clips are animated WebP rather than video on purpose: a player per row in a scrolling list
 * is a lifecycle problem, and these are silent three-second loops. `AnimatedImageDrawable` plays
 * them with no dependency and no surface — it needs API 28, and the app's floor is 29.
 *
 * The drawings are on white, so they sit on a white card. Tinting them to the app's warm ink
 * would leave a seam at the edge of the artwork, which reads worse than an honest diagram card.
 */
private val DEMO_ANIMATION: Map<String, Int> = mapOf(
    "lateral_raise" to R.drawable.ex_lateral_raise,
    "bicep_curl" to R.drawable.ex_bicep_curl,
    "shoulder_press" to R.drawable.ex_shoulder_press,
    "squat" to R.drawable.ex_squat,
)

/** Whether there is a clip for this exercise at all. */
fun hasExerciseDemo(exerciseId: String): Boolean = exerciseId in DEMO_ANIMATION

/**
 * Loops the movement. With reduce motion on it holds the first frame instead, which still shows
 * the shape and asks nothing of the eye.
 */
@Composable
fun ExerciseDemo(exerciseId: String, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val resId = DEMO_ANIMATION[exerciseId] ?: return
    val context = LocalContext.current

    // Decoded once per exercise, not on every recomposition: these are a couple of hundred KB.
    val drawable = remember(resId) {
        runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, resId))
        }.getOrNull()
    } ?: return

    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "How the movement looks, looping"
                    setImageDrawable(drawable)
                }
            },
            update = {
                if (drawable is AnimatedImageDrawable) {
                    if (reduceMotion) {
                        drawable.stop()
                    } else {
                        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        drawable.start()
                    }
                }
            },
            onRelease = { if (drawable is AnimatedImageDrawable) drawable.stop() },
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

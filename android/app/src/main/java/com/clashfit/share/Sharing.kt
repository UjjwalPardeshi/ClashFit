package com.clashfit.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Handing one picture to another app, and nothing else.
 *
 * Everything here is user-initiated. There is no background upload, no analytics ping and no
 * "sharing is caring" nudge: a card exists because somebody pressed a button, it is written to
 * app-private storage, and the receiving app gets a read grant for that single file which expires
 * with its activity. Nothing is ever written to shared storage, so no storage permission is
 * needed and no other app can browse what has been shared.
 *
 * The one honest caveat, said in the UI rather than buried here: the picture contains the route,
 * so sharing it publishes where the player was. See `ShareNotice`.
 */
object Sharing {

    private const val AUTHORITY_SUFFIX = ".csv_provider"
    private const val DIR = "share"

    /** Instagram's documented story intent. Falls back to the sheet wherever it is not handled. */
    private const val INSTAGRAM_STORY_ACTION = "com.instagram.share.ADD_TO_STORY"
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /**
     * Writes [bitmap] into app-private storage and returns a shareable URI.
     *
     * The directory is wiped of previous cards first. A share folder that only grows is a pile of
     * a person's movements sitting on disk forever, and the only file that matters is the one
     * about to be sent.
     */
    fun writeCard(context: Context, bitmap: Bitmap, name: String = "clashfit-activity"): Uri {
        val dir = File(context.filesDir, DIR).apply {
            if (exists()) listFiles()?.forEach { it.delete() } else mkdirs()
        }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    /** The ordinary Android share sheet: WhatsApp, Instagram, mail, anything installed. */
    fun shareSheet(context: Context, uri: Uri, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share activity").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    /**
     * Straight into a WhatsApp chat picker.
     *
     * Returns false when WhatsApp is not installed, so the caller can fall back to the sheet
     * rather than showing a button that does nothing. Both the consumer and the business package
     * are tried, because on a lot of phones only the second one is there.
     */
    fun shareToWhatsApp(context: Context, uri: Uri, text: String): Boolean {
        for (pkg in listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                setPackage(pkg)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (tryStart(context, intent)) return true
        }
        return false
    }

    /**
     * Straight into an Instagram story, as a full-bleed background.
     *
     * Instagram's story intent wants the image as the intent *data* with a type, not as an
     * `EXTRA_STREAM`, which is the detail that silently turns this into a no-op when it is
     * written like an ordinary share. The read grant has to be given to Instagram explicitly as
     * well: the story composer is a different process and the implicit chooser grant does not
     * reach it.
     *
     * Returns false when Instagram is absent or refuses, so the caller can fall back to the sheet.
     */
    fun shareToInstagramStory(context: Context, uri: Uri): Boolean {
        val intent = Intent(INSTAGRAM_STORY_ACTION).apply {
            setDataAndType(uri, "image/png")
            setPackage(INSTAGRAM_PACKAGE)
            putExtra("interactive_asset_uri", uri)
            putExtra("content_url", "https://clash-fit.vercel.app")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.grantUriPermission(INSTAGRAM_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return tryStart(context, intent)
    }

    fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    }.getOrDefault(false)

    fun hasWhatsApp(context: Context): Boolean =
        isInstalled(context, WHATSAPP_PACKAGE) || isInstalled(context, WHATSAPP_BUSINESS_PACKAGE)

    fun hasInstagram(context: Context): Boolean = isInstalled(context, INSTAGRAM_PACKAGE)

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        // A share started from a non-activity context needs its own task, and the run summary can
        // be reached from a notification where that is exactly the case.
        context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

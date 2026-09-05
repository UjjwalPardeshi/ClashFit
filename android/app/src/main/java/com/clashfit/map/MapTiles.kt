package com.clashfit.map

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * OpenStreetMap tiles, configured once and on the app's terms.
 *
 * Three things are set here and every one of them is deliberate.
 *
 * **A real user agent.** The OSM Foundation's tile usage policy requires a distinctive one, and a
 * client that sends the library default is indistinguishable from every other osmdroid app on the
 * internet — which is exactly the traffic they block. Ours names the app and its home page.
 *
 * **App-private cache.** osmdroid will happily put its tile cache on shared external storage,
 * which on older API levels means asking for a storage permission. `PERMISSION_ALLOW_LIST` in
 * `app/build.gradle.kts` would fail the build for it, and rightly: nothing about drawing a map
 * needs access to the user's photos. Both paths are pinned under `filesDir`, so the cache lives
 * and dies with the app and is covered by the same "no cloud backup" rule as the training
 * database.
 *
 * **A modest cache ceiling.** Enough that a route you have already looked at redraws with the
 * radio off, and not so much that the app quietly eats a gigabyte of a loaner phone.
 *
 * What is deliberately *not* here is any form of bulk area download. The same usage policy
 * forbids it, and an app whose pitch is that it behaves well when nobody is looking should not
 * open by scraping a volunteer-funded tile server. Tiles are cached as they are viewed; that is
 * all.
 */
object MapTiles {

    /** Kept modest on purpose: a loaner phone at an event has better uses for its storage. */
    private const val CACHE_MAX_BYTES = 48L * 1024 * 1024
    private const val CACHE_TRIM_BYTES = 36L * 1024 * 1024

    @Volatile private var configured = false

    /**
     * Idempotent. Safe to call from any composable that is about to show a map, which is the
     * point: there is no initialisation order to get wrong and nothing to remember in `AppGraph`.
     */
    fun ensureConfigured(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val ctx = context.applicationContext
            val base = File(ctx.filesDir, "osmdroid")
            val tiles = File(base, "tiles")
            base.mkdirs()
            tiles.mkdirs()

            Configuration.getInstance().apply {
                load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                userAgentValue = "ClashFit/${appVersion(ctx)} (+https://clash-fit.vercel.app)"
                osmdroidBasePath = base
                osmdroidTileCache = tiles
                tileFileSystemCacheMaxBytes = CACHE_MAX_BYTES
                tileFileSystemCacheTrimBytes = CACHE_TRIM_BYTES
                // How quickly the first screenful arrives.
                //
                // osmdroid ships with two download threads, which on a phone opening a map for
                // the first time means the twelve or so tiles of a screenful arrive six deep and
                // the map is visibly grey for several seconds. Four is still well inside what any
                // ordinary map client does, and halves that wait. The disk threads matter on a
                // second look at the same place, where the tiles are already cached and the only
                // cost is reading them.
                tileDownloadThreads = 4
                tileFileSystemThreads = 8
                tileDownloadMaxQueueSize = 64
                // A street does not move. Honouring a short cache header by re-fetching tiles the
                // phone already has is a slow map and wasted traffic on someone else's servers;
                // a week is the usage policy's own suggested minimum.
                expirationExtendedDuration = 7L * 24 * 60 * 60 * 1000

                // Nothing about a map belongs in logcat on a demo phone.
                isDebugMode = false
                isDebugTileProviders = false
                isDebugMapView = false
                isDebugMapTileDownloader = false
            }
            configured = true
        }
    }

    private fun appVersion(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.1.0"
    }.getOrDefault("0.1.0")

    /** Bytes currently held in the tile cache, for the settings screen to show and offer to clear. */
    fun cacheBytes(context: Context): Long {
        val tiles = File(context.applicationContext.filesDir, "osmdroid/tiles")
        if (!tiles.exists()) return 0L
        return tiles.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /** Deletes every cached tile. The map still works; it just has to fetch again. */
    fun clearCache(context: Context) {
        val tiles = File(context.applicationContext.filesDir, "osmdroid/tiles")
        runCatching { tiles.deleteRecursively(); tiles.mkdirs() }
    }
}

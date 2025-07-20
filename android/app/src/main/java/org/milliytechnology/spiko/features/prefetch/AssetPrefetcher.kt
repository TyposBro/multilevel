package org.milliytechnology.spiko.features.prefetch

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetPrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient // Make sure OkHttp is provided via Hilt
) {
    private val cacheMap = ConcurrentHashMap<String, File>()
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDir = File(context.cacheDir, "asset_prefetch_cache").apply { mkdirs() }

    /**
     * Starts prefetching a list of URLs sequentially in the background.
     * This function returns immediately.
     */
    fun prefetch(urls: List<String>) {
        prefetchScope.launch {
            Log.d("AssetPrefetcher", "Starting prefetch for ${urls.size} assets.")
            for (url in urls) {
                // Check if already cached or currently being downloaded by another call
                if (cacheMap.containsKey(url) || url.isBlank()) {
                    Log.d("AssetPrefetcher", "Cache HIT for (skipping download): $url")
                    continue
                }
                Log.d("AssetPrefetcher", "Prefetching: $url")
                downloadUrl(url)
            }
            Log.d("AssetPrefetcher", "Prefetching queue finished.")
        }
    }

    /**
     * Retrieves a cached file for a given URL.
     * Returns the [File] if it's in the cache, or null otherwise.
     */
    fun get(url: String): File? {
        return cacheMap[url]
    }

    /**
     * Downloads a single URL and stores it in the cache. This is a blocking call.
     * @return The cached [File] on success, null on failure.
     */
    private fun downloadUrl(url: String): File? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("AssetPrefetcher", "Failed to download $url: ${response.code}")
                return null
            }

            val file = File(cacheDir, url.hashCode().toString())
            response.body?.byteStream()?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Response body was null for $url")

            cacheMap[url] = file
            Log.i("AssetPrefetcher", "Successfully cached $url to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("AssetPrefetcher", "Error downloading $url", e)
            null
        }
    }

    /**
     * Clears the entire on-disk and in-memory cache.
     * Should be called when the exam is completely finished or the app closes.
     */
    fun clearCache() {
        prefetchScope.launch {
            cacheDir.deleteRecursively()
            cacheMap.clear()
            Log.i("AssetPrefetcher", "Cache cleared.")
        }
    }
}
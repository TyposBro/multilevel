package org.milliytechnology.spiko.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.milliytechnology.spiko.features.prefetch.AssetPrefetcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetPrefetcher: AssetPrefetcher // Inject the prefetcher
) {

    private var currentMediaPlayer: MediaPlayer? = null

    suspend fun playFromRawAndWait(@RawRes rawResId: Int) {
        // This function doesn't need the prefetcher, logic remains the same.
        val uri = "android.resource://${context.packageName}/$rawResId".toUri()
        playDataSource { it.setDataSource(context, uri) }
    }

    suspend fun playFromUrlAndWait(url: String) {
        if (url.isBlank()) return

        // --- CORE CHANGE: CHECK CACHE FIRST ---
        val cachedFile = assetPrefetcher.get(url)
        if (cachedFile != null) {
            Log.i("AudioPlayer", "Playing from CACHE: $url")
            playDataSource { it.setDataSource(cachedFile.absolutePath) }
        } else {
            Log.w("AudioPlayer", "CACHE MISS. Streaming from network: $url")
            playDataSource { it.setDataSource(url) }
        }
    }

    private suspend fun playDataSource(setSourceAction: (MediaPlayer) -> Unit) {
        stopCurrentSound()

        val player = MediaPlayer()
        currentMediaPlayer = player

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation {
                    Log.d(
                        "AudioPlayer",
                        "Coroutine cancelled. The 'finally' block will handle cleanup."
                    )
                }

                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA).build()
                    )
                    setSourceAction(player)
                    player.setOnPreparedListener { it.start() }
                    player.setOnCompletionListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    player.setOnErrorListener { _, what, extra ->
                        val error = RuntimeException("MediaPlayer error: what=$what, extra=$extra")
                        if (continuation.isActive) continuation.resumeWithException(error)
                        true
                    }
                    player.prepareAsync()
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } finally {
            cleanupPlayerInstance(player, "playDataSource finally block")
        }
    }

    private fun cleanupPlayerInstance(player: MediaPlayer, reason: String) {
        Log.d("AudioPlayer", "Cleaning up MediaPlayer instance due to: $reason")
        try {
            if (player.isPlaying) player.stop()
            player.release()
        } catch (e: Exception) {
            Log.w("AudioPlayer", "Exception during cleanup: ${e.message}")
        }

        if (currentMediaPlayer == player) {
            currentMediaPlayer = null
        }
    }

    private fun stopCurrentSound() {
        currentMediaPlayer?.let { cleanupPlayerInstance(it, "stopCurrentSound explicit call") }
    }

    fun release() {
        Log.d("AudioPlayer", "AudioPlayer.release() called.")
        stopCurrentSound()
    }
}
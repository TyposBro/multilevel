package com.typosbro.multilevel.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import androidx.core.net.toUri
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AudioPlayer {

    private var currentMediaPlayer: MediaPlayer? = null
    private var tempAudioFile: File? = null

    // --- NEW ROBUST SUSPENDABLE PLAYBACK FUNCTIONS ---

    /**
     * Plays audio from a raw resource and suspends the coroutine until playback is complete or fails.
     * This version is robust against race conditions using a try/finally block for cleanup.
     */
    suspend fun playFromRawAndWait(context: Context, @RawRes rawResId: Int) {
        stopCurrentSound() // Stop any sound that might be playing.

        val player = MediaPlayer()
        currentMediaPlayer = player // Assign so it can be stopped externally if needed.

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val uri = "android.resource://${context.packageName}/$rawResId".toUri()

                continuation.invokeOnCancellation {
                    Log.d(
                        "AudioPlayer",
                        "SUSPEND: Coroutine cancelled for raw $rawResId. The 'finally' block will handle cleanup."
                    )
                }

                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA).build()
                    )
                    player.setDataSource(context, uri)
                    player.setOnPreparedListener { it.start() }
                    player.setOnCompletionListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    player.setOnErrorListener { _, what, extra ->
                        val error =
                            RuntimeException("MediaPlayer error for raw $rawResId: what=$what, extra=$extra")
                        if (continuation.isActive) continuation.resumeWithException(error)
                        true
                    }
                    player.prepareAsync()
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } finally {
            // This is guaranteed to run once the coroutine is done, preventing race conditions.
            cleanupPlayerInstance(player, "playFromRawAndWait finally block")
        }
    }

    /**
     * Plays audio from a URL and suspends the coroutine until playback is complete or fails.
     * This version is robust against race conditions using a try/finally block for cleanup.
     */
    suspend fun playFromUrlAndWait(context: Context, url: String) {
        stopCurrentSound()

        val player = MediaPlayer()
        currentMediaPlayer = player

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation {
                    Log.d(
                        "AudioPlayer",
                        "SUSPEND: Coroutine cancelled for URL $url. The 'finally' block will handle cleanup."
                    )
                }

                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA).build()
                    )
                    player.setDataSource(url)
                    player.setOnPreparedListener { it.start() }
                    player.setOnCompletionListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    player.setOnErrorListener { _, what, extra ->
                        val error =
                            RuntimeException("MediaPlayer error for URL $url: what=$what, extra=$extra")
                        if (continuation.isActive) continuation.resumeWithException(error)
                        true
                    }
                    player.prepareAsync()
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } finally {
            cleanupPlayerInstance(player, "playFromUrlAndWait finally block")
        }
    }


    private fun cleanupPlayerInstance(player: MediaPlayer, reason: String) {
        Log.d("AudioPlayer", "Cleaning up MediaPlayer instance due to: $reason")
        try {
            // A simple way to check if the player is still valid is to catch the IllegalStateException.
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        } catch (e: IllegalStateException) {
            // This is expected if the player was already released, so we can log it as a warning.
            Log.w(
                "AudioPlayer",
                "Player was already released or in a bad state. Reason: ${e.message}"
            )
        } catch (e: Exception) {
            Log.w("AudioPlayer", "Generic exception during cleanup: ${e.message}")
        }

        // Only nullify the global variable if it's the one we're cleaning up.
        if (currentMediaPlayer == player) {
            currentMediaPlayer = null
        }

        // Clean up temp file if this was a TTS playback
        if (reason.contains("TTS")) {
            tempAudioFile?.delete()
            tempAudioFile = null
        }
    }

    private fun stopCurrentSound() {
        currentMediaPlayer?.let { playerToStop ->
            Log.d("AudioPlayer", "stopCurrentSound called. Stopping player.")
            cleanupPlayerInstance(playerToStop, "stopCurrentSound explicit call")
        }
    }

    fun release() {
        Log.d("AudioPlayer", "AudioPlayer.release() called.")
        stopCurrentSound()
    }
}
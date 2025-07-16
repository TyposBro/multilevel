package com.typosbro.multilevel.data.repositories

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import java.io.File

object DebugLogRepository {
    private const val TAG = "DebugLogRepository"

    @RequiresApi(Build.VERSION_CODES.S)
    fun saveTranscript(
        context: Context,
        audioFile: File?,
        transcript: List<TranscriptEntry>
    ) {
        if (audioFile == null) {
            Log.w(TAG, "Cannot save transcript, audio file is null.")
            return
        }
        if (transcript.isEmpty()) {
            Log.w(TAG, "Transcript is empty, skipping save.")
            return
        }

        try {
            val transcriptFileName = audioFile.name.replace(".wav", ".txt")
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
            val transcriptFile = File(dir, transcriptFileName)

            val content = transcript.joinToString(separator = "\n") { "[${it.speaker}] ${it.text}" }
            transcriptFile.writeText(content)

            Log.i(TAG, "Successfully saved transcript to: ${transcriptFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transcript file", e)
        }
    }
}
package com.typosbro.multilevel.features.whisper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.typosbro.multilevel.MainActivity

class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "exam_recording_channel"
        private const val CHANNEL_NAME = "Exam Recording"

        const val ACTION_START_RECORDING = "start_recording"
        const val ACTION_STOP_RECORDING = "stop_recording"
    }

    private val binder = LocalBinder()
    private var recorder: Recorder? = null
    private var recorderListener: Recorder.RecorderListener? = null

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "RecordingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                startForegroundRecording()
            }

            ACTION_STOP_RECORDING -> {
                stopRecording()
                stopSelf()
            }
        }

        // Return START_NOT_STICKY so service doesn't restart if killed
        return START_NOT_STICKY
    }

    fun setRecorderListener(listener: Recorder.RecorderListener) {
        this.recorderListener = listener
    }

    fun startRecording(): Boolean {
        if (recorder?.isRecording == true) {
            Log.d(TAG, "Recording already in progress")
            return true
        }

        return try {
            recorder = Recorder(this, recorderListener ?: object : Recorder.RecorderListener {
                override fun onDataReceived(samples: FloatArray) {
                    // Default empty implementation
                }

                override fun onRecordingStopped() {
                    // Default empty implementation
                }
            })

            recorder?.start()
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Recording started in foreground service")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            false
        }
    }

    fun stopRecording() {
        recorder?.stop()
        recorder = null
        stopForeground(true)
        Log.d(TAG, "Recording stopped")
    }

    fun isRecording(): Boolean {
        return recorder?.isRecording == true
    }

    private fun startForegroundRecording() {
        startRecording()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for exam recording session"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }

        PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IELTS Speaking Test")
            .setContentText("Recording in progress...")
//            .setSmallIcon(R.drawable.ic_mic) // Make sure you have this icon
            .setContentIntent(pendingIntent)
//            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        Log.d(TAG, "RecordingService destroyed")
    }
}
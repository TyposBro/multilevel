package com.typosbro.multilevel.features.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.typosbro.multilevel.R

class TimerSoundPlayer(context: Context) {
    private val soundPool: SoundPool
    private val beepSoundId: Int
    private val tickSoundId: Int

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        // Ensure you have beep.mp3 and tick.mp3 in res/raw
        beepSoundId = soundPool.load(context, R.raw.sec_60, 1)
        tickSoundId = soundPool.load(context, R.raw.sec_60, 1)
    }

    fun playBeep() {
        soundPool.play(beepSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playTick() {
        soundPool.play(tickSoundId, 1f, 1f, 1, 0, 1f)
    }
}

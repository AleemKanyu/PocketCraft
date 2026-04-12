package com.pocketcraft.server.sound

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SoundManager {

    suspend fun playServerStart(context: Context) = withContext(Dispatchers.Default) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 92)
            try {
                playTone(toneGen, ToneGenerator.TONE_CDMA_PIP, 110)
                Thread.sleep(70)
                playTone(toneGen, ToneGenerator.TONE_SUP_PIP, 120)
                Thread.sleep(72)
                playTone(toneGen, ToneGenerator.TONE_PROP_BEEP2, 160)
            } finally {
                toneGen.release()
            }
        } catch (e: Exception) {
            // Silent fallback if audio not available
        }
    }

    suspend fun playServerStop(context: Context) = withContext(Dispatchers.Default) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 200)
            toneGen.release()
        } catch (e: Exception) {
            // Silent fallback
        }
    }

    private fun playTone(toneGen: ToneGenerator, tone: Int, durationMs: Int) {
        toneGen.startTone(tone, durationMs)
        Thread.sleep(durationMs.toLong())
    }
}

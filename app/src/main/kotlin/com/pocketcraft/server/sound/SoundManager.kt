package com.pocketcraft.server.sound

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SoundManager {

    suspend fun playServerStart(context: Context) = withContext(Dispatchers.Default) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            toneGen.release()
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
}

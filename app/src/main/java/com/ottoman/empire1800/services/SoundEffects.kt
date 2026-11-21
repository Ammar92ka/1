package com.ottoman.empire1800.services

import android.media.AudioManager
import android.media.ToneGenerator

enum class SoundEffectType {
    SEAL,
    DECREE,
    ALERT,
}

/** مؤثرات صوتية خفيفة ومولّدة برمجيا (بدون أي ملفات وسائط أو مكتبات خارجية). */
object SoundEffects {
    fun play(type: SoundEffectType, muted: Boolean) {
        if (muted) return
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 48)
            try {
                when (type) {
                    SoundEffectType.SEAL -> tg.startTone(ToneGenerator.TONE_PROP_ACK, 110)
                    SoundEffectType.DECREE -> tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 130)
                    SoundEffectType.ALERT -> tg.startTone(ToneGenerator.TONE_PROP_NACK, 180)
                }
            } finally {
                // Release after short tone window on background thread to avoid cutting off early
                Thread {
                    try {
                        Thread.sleep(240)
                    } catch (_: InterruptedException) {
                    } finally {
                        try {
                            tg.release()
                        } catch (_: Throwable) {
                        }
                    }
                }.start()
            }
        } catch (_: Throwable) {
            // Audio hardware unavailable or running in JVM unit test
        }
    }
}

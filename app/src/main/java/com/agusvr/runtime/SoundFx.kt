package com.agusvr.runtime

import android.media.ToneGenerator
import android.media.AudioManager

/**
 * Agus VR Runtime — feedback sonoro mínimo (tons do sistema, sem assets).
 * Desativável pelo switch "Som".
 */
class SoundFx {
    private var gen: ToneGenerator? = null
    var enabled = true
    private var lastHoverSound = 0L

    init {
        try { gen = ToneGenerator(AudioManager.STREAM_MUSIC, 40) } catch (_: Exception) {}
    }

    fun hover() {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastHoverSound < 180) return
        lastHoverSound = now
        try { gen?.startTone(ToneGenerator.TONE_PROP_BEEP, 45) } catch (_: Exception) {}
    }

    fun select() {
        if (!enabled) return
        try { gen?.startTone(ToneGenerator.TONE_PROP_ACK, 110) } catch (_: Exception) {}
    }

    fun grab() {
        if (!enabled) return
        try { gen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 60) } catch (_: Exception) {}
    }

    fun release() {
        if (!enabled) return
        try { gen?.startTone(ToneGenerator.TONE_PROP_BEEP, 70) } catch (_: Exception) {}
    }

    fun dispose() {
        try { gen?.release() } catch (_: Exception) {}
        gen = null
    }
}

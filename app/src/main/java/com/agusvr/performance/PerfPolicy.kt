package com.agusvr.performance

import com.agusvr.storage.Settings

/**
 * Agus Performance — políticas de desempenho e qualidade adaptativa.
 *
 * Modos:
 *  0 = Economia    (máxima duração de bateria)
 *  1 = Desempenho  (máximo FPS, menos efeitos)
 *  2 = Equilibrado (padrão)
 *  3 = Qualidade   (máxima fidelidade)
 *
 * O [AdaptiveQuality] reduz efeitos automaticamente quando o dispositivo
 * está sobrecarregado (FPS abaixo da meta ou temperatura alta) e volta a
 * subir quando há folga.
 */
class PerfPolicy {

    companion object {
        const val MODE_ECO = 0
        const val MODE_PERF = 1
        const val MODE_BALANCED = 2
        const val MODE_QUALITY = 3

        fun modeName(m: Int): String = when (m) {
            MODE_ECO -> "Economia"
            MODE_PERF -> "Desempenho"
            MODE_BALANCED -> "Equilibrado"
            MODE_QUALITY -> "Qualidade"
            else -> "?"
        }
    }

    /** Aplica o modo ao Settings (valores-alvo). O usuário ainda pode ajustar fino. */
    fun applyMode(s: Settings, mode: Int) {
        s.perfMode = mode
        when (mode) {
            MODE_ECO -> {
                s.resolutionScale = 0.5f; s.fpsCap = 30; s.quality = 0
                s.effectsEnabled = false; s.shadowsEnabled = false
            }
            MODE_PERF -> {
                s.resolutionScale = 0.7f; s.fpsCap = 60; s.quality = 0
                s.effectsEnabled = false; s.shadowsEnabled = false
            }
            MODE_BALANCED -> {
                s.resolutionScale = 0.85f; s.fpsCap = 60; s.quality = 1
                s.effectsEnabled = true; s.shadowsEnabled = true
            }
            MODE_QUALITY -> {
                s.resolutionScale = 1.0f; s.fpsCap = 72; s.quality = 2
                s.effectsEnabled = true; s.shadowsEnabled = true
            }
        }
    }
}

/**
 * Reduz/aumenta qualidade em degraus conforme a carga real do dispositivo.
 * Degraus (de cima para baixo):
 *  4 tudo ligado, escala cheia
 *  3 efeitos reduzidos
 *  2 escala de resolução reduzida
 *  1 sombras+efeitos off, escala mínima
 *  0 mínimo absoluto (fps reduzido)
 */
class AdaptiveQuality(private val perf: PerfMonitor) {

    var step: Int = 3
        private set
    var lastChangeReason: String = "ok"
        private set

    private var lowTimeMs = 0L
    private var okTimeMs = 0L
    var autoThrottleEnabled = true

    /** Níveis efetivos derivados do degrau atual. */
    val scaleMultiplier: Float get() = when (step) {
        4 -> 1.0f; 3 -> 0.95f; 2 -> 0.8f; 1 -> 0.65f; else -> 0.55f
    }
    val effectsAllowed: Boolean get() = step >= 3
    val shadowsAllowed: Boolean get() = step >= 2
    val fpsCapReduction: Int get() = if (step == 0) 15 else 0

    fun update(dtMs: Float, targetFps: Int, settings: Settings) {
        if (!autoThrottleEnabled) return
        val target = targetFps.coerceAtLeast(24).toFloat()
        val fpsNow = perf.fps
        val hot = perf.batteryTempC in 0f..100f && perf.batteryTempC >= 42f

        if (fpsNow < target * 0.78f || hot) {
            lowTimeMs += dtMs.toLong(); okTimeMs = 0
            if (lowTimeMs > 2500 && step > 0) {
                step--
                lowTimeMs = 0
                lastChangeReason = if (hot) "temperatura alta" else "FPS baixo (${fpsNow.toInt()}/${target.toInt()})"
            }
        } else if (fpsNow > target * 0.94f) {
            okTimeMs += dtMs.toLong(); lowTimeMs = 0
            if (okTimeMs > 12000 && step < 4) {
                step++
                okTimeMs = 0
                lastChangeReason = "folga de desempenho — qualidade restaurada"
            }
        } else {
            lowTimeMs = 0; okTimeMs = 0
        }

        // Emergência térmica
        if (perf.batteryTempC in 0f..100f && perf.batteryTempC >= 46f && step > 0) {
            step = 0
            lastChangeReason = "emergência térmica"
        }
    }

    fun applyTo(settings: Settings) {
        // Aplicado pelo engine a cada mudança de degrau
        settings.effectsEnabled = effectsAllowed && settings.perfMode != PerfPolicy.MODE_ECO
        settings.shadowsEnabled = shadowsAllowed && settings.perfMode != PerfPolicy.MODE_ECO
    }
}

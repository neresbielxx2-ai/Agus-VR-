package com.agusvr.runtime

import android.content.Context

/**
 * Agus VR Runtime — guarda de boot.
 *
 * Fases do boot do motor VR (persistidas a cada etapa):
 *   activity.onCreate → engine.init → glview.anexada → primeiro.frame
 *   → hands.carregando → hands.ok
 *
 * Se o app abre e a fase salva não passou de "primeiro.frame", a sessão
 * anterior morreu cedo → MODO SEGURO (sem câmera/MediaPipe). Se a fase
 * parou em "hands.carregando", o culpado foi o pipeline de hand tracking
 * (ex.: crash nativo do MediaPipe) — o modo seguro pula a câmera e o
 * usuário pode reativar depois pelo app Sistema.
 */
object BootGuard {

    private const val PREFS = "agus_boot"
    private const val KEY = "boot_phase"
    private const val KEY_FAIL = "boot_fail"

    const val PHASE_FIRST_FRAME = "primeiro.frame"
    const val PHASE_HANDS_OK = "hands.ok"

    var safeMode = false
        private set
    var lastPhase = ""
        private set
    var failurePoint = ""
        private set

    fun init(ctx: Context) {
        try {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            lastPhase = p.getString(KEY, "") ?: ""
            val healthy = lastPhase == PHASE_FIRST_FRAME || lastPhase == PHASE_HANDS_OK
            safeMode = lastPhase.isNotEmpty() && !healthy
            if (safeMode) {
                failurePoint = lastPhase
                p.edit().putString(KEY_FAIL, lastPhase).apply()
            } else {
                failurePoint = p.getString(KEY_FAIL, "") ?: ""
            }
        } catch (_: Throwable) {}
    }

    /** Marca a fase atual (sobrevive a crash). */
    fun mark(ctx: Context, phase: String) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, phase).apply()
            lastPhase = phase
        } catch (_: Throwable) {}
    }

    fun firstFrame(ctx: Context) = mark(ctx, PHASE_FIRST_FRAME)

    /** Hand tracking carregou sem matar o app: sistema totalmente saudável. */
    fun markHealthy(ctx: Context) {
        mark(ctx, PHASE_HANDS_OK)
        safeMode = false
        failurePoint = ""
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_FAIL, "").apply()
        } catch (_: Throwable) {}
    }

    /** O usuário pediu para limpar o histórico de falha. */
    fun clearFailure(ctx: Context) {
        failurePoint = ""
        safeMode = false
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_FAIL, "").putString(KEY, PHASE_FIRST_FRAME).apply()
        } catch (_: Throwable) {}
    }
}

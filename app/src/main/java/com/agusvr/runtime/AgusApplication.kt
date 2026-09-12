package com.agusvr.runtime

import android.app.Application

/**
 * Agus VR — Application global.
 * Instala o capturador de falhas ANTES de qualquer atividade existir,
 * para que nenhum crash (nem em super.onCreate) passe sem registro.
 */
class AgusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            CrashLog.install(this)
            BootGuard.init(this)
        } catch (_: Throwable) {}
    }
}

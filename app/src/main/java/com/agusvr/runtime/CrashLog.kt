package com.agusvr.runtime

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Agus VR Runtime — registro de falhas.
 *
 * Qualquer exceção que escaparia para o usuário (GL thread, engine,
 * sensores) é capturada e gravada em filesDir/crashlog.txt. O launcher
 * mostra o último erro, permitindo diagnóstico em dispositivos reais.
 */
object CrashLog {

    private const val NAME = "crashlog.txt"
    private const val MAX_BYTES = 24_000

    private fun file(ctx: Context): File = File(ctx.filesDir, NAME)

    /** Registra um erro (thread-safe, com limite de tamanho). */
    @Synchronized
    fun log(ctx: Context, tag: String, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val entry = "\n[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())}] $tag\n" + sw.toString().take(2500) + "\n"
            val f = file(ctx)
            var content = if (f.exists()) f.readText() else ""
            content += entry
            if (content.length > MAX_BYTES) content = content.takeLast(MAX_BYTES)
            f.writeText(content)
        } catch (_: Throwable) {}
    }

    fun logMessage(ctx: Context, tag: String, msg: String) {
        try {
            val f = file(ctx)
            var content = if (f.exists()) f.readText() else ""
            content += "\n[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())}] $tag: $msg\n"
            if (content.length > MAX_BYTES) content = content.takeLast(MAX_BYTES)
            f.writeText(content)
        } catch (_: Throwable) {}
    }

    fun tail(ctx: Context, lines: Int = 12): String? {
        return try {
            val f = file(ctx)
            if (!f.exists()) null
            else f.readText().trim().lines().takeLast(lines).joinToString("\n").ifBlank { null }
        } catch (_: Throwable) { null }
    }

    fun clear(ctx: Context) {
        try { file(ctx).delete() } catch (_: Throwable) {}
    }

    /** Handler global: grava a falha antes de repassar ao handler padrão. */
    fun install(ctx: Context) {
        val appCtx = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(appCtx, "uncaught@${thread.name}", throwable)
            if (prev != null) prev.uncaughtException(thread, throwable)
        }
    }
}

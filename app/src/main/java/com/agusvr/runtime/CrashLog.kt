package com.agusvr.runtime

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Agus VR Runtime — registro de falhas.
 *
 * Qualquer exceção que escaparia para o usuário (GL thread, engine,
 * sensores, câmera) é capturada e gravada em filesDir/crashlog.txt.
 * O launcher mostra o erro (começando pela EXCEÇÃO, não pelo fim da
 * pilha), permitindo diagnóstico em dispositivos reais.
 */
object CrashLog {

    private const val NAME = "crashlog.txt"
    private const val MAX_BYTES = 48_000

    private fun file(ctx: Context): File = File(ctx.filesDir, NAME)

    private fun stamp(): String =
        java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())

    /**
     * Registra um erro. O formato começa pela linha mais útil:
     * "=== FATAL tag @ thread: ClasseExcecao: mensagem" — e depois o
     * topo da pilha (até 40 frames) + cadeia de causas.
     */
    @Synchronized
    fun log(ctx: Context, tag: String, t: Throwable) {
        try {
            val sb = StringBuilder()
            sb.append("\n=== FATAL ").append(stamp()).append(' ')
                .append(tag).append(" @ ").append(Thread.currentThread().name)
                .append("\n").append(t.javaClass.name).append(": ")
                .append(t.message ?: "(sem mensagem)").append('\n')
            val frames = t.stackTrace
            val n = minOf(frames.size, 40)
            for (i in 0 until n) sb.append("  at ").append(frames[i].toString()).append('\n')
            if (frames.size > n) sb.append("  ... +").append(frames.size - n).append(" frames\n")
            var cause: Throwable? = t.cause
            var depth = 0
            while (cause != null && depth < 3) {
                sb.append("Causado por: ").append(cause.javaClass.name)
                    .append(": ").append(cause.message ?: "").append('\n')
                val cf = cause.stackTrace
                for (i in 0 until minOf(cf.size, 12)) {
                    sb.append("  at ").append(cf[i].toString()).append('\n')
                }
                cause = cause.cause
                depth++
            }
            appendFile(ctx, sb.toString())
        } catch (_: Throwable) {}
    }

    fun logMessage(ctx: Context, tag: String, msg: String) {
        try {
            appendFile(ctx, "\n[${stamp()}] $tag: $msg\n")
        } catch (_: Throwable) {}
    }

    private fun appendFile(ctx: Context, entry: String) {
        val f = file(ctx)
        var content = if (f.exists()) f.readText() else ""
        content += entry
        if (content.length > MAX_BYTES) content = content.takeLast(MAX_BYTES)
        f.writeText(content)
    }

    /** Log completo (até 8 KB), começando pelos registros mais antigos. */
    fun readAll(ctx: Context): String? {
        return try {
            val f = file(ctx)
            if (!f.exists()) null
            else f.readText().trim().take(8000).ifBlank { null }
        } catch (_: Throwable) { null }
    }

    /** Últimas N linhas (útil para resumo curto). */
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
            try {
                log(appCtx, "uncaught", throwable)
                logMessage(appCtx, "uncaught",
                    "thread=${thread.name} tipo=${throwable.javaClass.name}")
            } catch (_: Throwable) {}
            if (prev != null) prev.uncaughtException(thread, throwable)
        }
    }
}

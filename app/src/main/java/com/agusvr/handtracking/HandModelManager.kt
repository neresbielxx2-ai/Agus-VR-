package com.agusvr.handtracking

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Agus Hand Tracking — gerenciamento do modelo `hand_landmarker.task`.
 *
 * O modelo (~8 MB) é baixado na primeira execução para o armazenamento
 * interno (mantém o APK enxuto). Sem o modelo o hand tracking é
 * desativado e o app informa isso claramente — nunca finge tracking.
 */
object HandModelManager {

    const val MODEL_URL =
        "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"

    sealed class Status {
        object Ready : Status()
        object Missing : Status()
        object Downloading : Status()
        class Error(val msg: String) : Status()
    }

    @Volatile
    var downloading = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun modelFile(context: Context): File =
        File(context.filesDir, "models/hand_landmarker.task")

    /** Procura o modelo em assets primeiro, depois no filesDir. */
    fun resolveModelPath(context: Context): String? {
        // Empacotado em assets (se o build incluir)
        try {
            context.assets.open("hand_landmarker.task").use { input ->
                val f = modelFile(context)
                if (!f.exists() || f.length() < 100_000) {
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { input.copyTo(it) }
                }
                if (f.length() > 100_000) return f.absolutePath
            }
        } catch (_: Exception) {}
        // Baixado anteriormente
        val f = modelFile(context)
        return if (f.exists() && f.length() > 100_000) f.absolutePath else null
    }

    fun status(context: Context): Status {
        if (downloading) return Status.Downloading
        return if (resolveModelPath(context) != null) Status.Ready
        else lastError?.let { Status.Error(it) } ?: Status.Missing
    }

    /** Baixa o modelo (corrotina). Retorna true em sucesso. */
    suspend fun download(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (downloading) return@withContext false
        downloading = true
        lastError = null
        try {
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.connect()
            val code = conn.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code")
            val f = modelFile(context)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "hand_landmarker.task.tmp")
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            }
            if (tmp.length() < 100_000) throw java.io.IOException("download incompleto")
            if (f.exists()) f.delete()
            tmp.renameTo(f)
            true
        } catch (e: Exception) {
            lastError = e.message ?: "erro desconhecido"
            false
        } finally {
            downloading = false
        }
    }
}

package com.agusvr.storage

import android.content.Context
import org.json.JSONObject

/**
 * Agus Storage — modelo de configurações do Agus VR.
 * Persistido em JSON no armazenamento interno (filesDir/settings.json).
 */
data class Settings(
    // Qualidade gráfica: 0=baixa, 1=média, 2=alta
    var quality: Int = 1,
    // Escala de resolução interna: 0.5 .. 1.0 (fração da resolução nativa)
    var resolutionScale: Float = 0.85f,
    // Limite de FPS desejado
    var fpsCap: Int = 60,
    // Modo de desempenho: 0=economia, 1=desempenho, 2=equilibrado, 3=qualidade
    var perfMode: Int = 2,
    var handTrackingEnabled: Boolean = true,
    var effectsEnabled: Boolean = true,
    var soundEnabled: Boolean = true,
    var shadowsEnabled: Boolean = true,
    // Sensibilidade do ray / seleção por aproximação (0.5..2.0)
    var pointerSensitivity: Float = 1.0f,
    // Distância (m) em que a aproximação confirma a seleção
    var selectDistance: Float = 0.35f,
    var dwellMs: Int = 550,
    // IPD (distância interpupilar, metros) usado no estéreo
    var ipd: Float = 0.063f,
    // Câmera usada para hand tracking: 0=traseira, 1=frontal
    var cameraId: Int = 0,
    var focusModeEnabled: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("quality", quality)
        .put("resolutionScale", resolutionScale.toDouble())
        .put("fpsCap", fpsCap)
        .put("perfMode", perfMode)
        .put("handTrackingEnabled", handTrackingEnabled)
        .put("effectsEnabled", effectsEnabled)
        .put("soundEnabled", soundEnabled)
        .put("shadowsEnabled", shadowsEnabled)
        .put("pointerSensitivity", pointerSensitivity.toDouble())
        .put("selectDistance", selectDistance.toDouble())
        .put("dwellMs", dwellMs)
        .put("ipd", ipd.toDouble())
        .put("cameraId", cameraId)
        .put("focusModeEnabled", focusModeEnabled)

    companion object {
        fun fromJson(j: JSONObject): Settings {
            val d = Settings()
            return Settings(
                quality = j.optInt("quality", d.quality),
                resolutionScale = j.optDouble("resolutionScale", d.resolutionScale.toDouble()).toFloat(),
                fpsCap = j.optInt("fpsCap", d.fpsCap),
                perfMode = j.optInt("perfMode", d.perfMode),
                handTrackingEnabled = j.optBoolean("handTrackingEnabled", d.handTrackingEnabled),
                effectsEnabled = j.optBoolean("effectsEnabled", d.effectsEnabled),
                soundEnabled = j.optBoolean("soundEnabled", d.soundEnabled),
                shadowsEnabled = j.optBoolean("shadowsEnabled", d.shadowsEnabled),
                pointerSensitivity = j.optDouble("pointerSensitivity", d.pointerSensitivity.toDouble()).toFloat(),
                selectDistance = j.optDouble("selectDistance", d.selectDistance.toDouble()).toFloat(),
                dwellMs = j.optInt("dwellMs", d.dwellMs),
                ipd = j.optDouble("ipd", d.ipd.toDouble()).toFloat(),
                cameraId = j.optInt("cameraId", d.cameraId),
                focusModeEnabled = j.optBoolean("focusModeEnabled", d.focusModeEnabled)
            )
        }
    }
}

/**
 * Agus Storage — camada de persistência simples, thread-safe e sem dependências.
 */
class SettingsStore(context: Context) {
    private val file = java.io.File(context.filesDir, "settings.json")
    private val lock = Any()

    @Volatile
    var current: Settings = Settings()
        private set

    init { load() }

    fun load() {
        synchronized(lock) {
            current = try {
                if (file.exists()) Settings.fromJson(JSONObject(file.readText()))
                else Settings()
            } catch (_: Exception) {
                Settings()
            }
        }
    }

    fun save() {
        synchronized(lock) {
            try { file.writeText(current.toJson().toString(2)) } catch (_: Exception) {}
        }
    }

    fun update(block: (Settings) -> Unit) {
        synchronized(lock) {
            block(current)
            try { file.writeText(current.toJson().toString(2)) } catch (_: Exception) {}
        }
    }
}

/**
 * Agus Storage — persistência genérica de pares chave/valor (última sessão, etc).
 */
class KvStore(context: Context) {
    private val prefs = context.getSharedPreferences("agus_kv", Context.MODE_PRIVATE)

    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getString(key: String, def: String = ""): String = prefs.getString(key, def) ?: def
    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun getLong(key: String, def: Long = 0L): Long = prefs.getLong(key, def)
    fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getBool(key: String, def: Boolean = false): Boolean = prefs.getBoolean(key, def)
}

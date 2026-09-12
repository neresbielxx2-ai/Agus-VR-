package com.agusvr.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import java.io.File

/**
 * Agus Performance — monitor de sistema.
 * Coleta FPS real, tempo de frame, memória, bateria, temperatura e carga de
 * CPU lendo fontes reais do sistema (nenhum valor é simulado).
 */
class PerfMonitor(private val context: Context) {

    // FPS / frame
    @Volatile var fps: Float = 0f
        private set
    @Volatile var frameMs: Float = 0f
        private set
    private var emaFps = 0f
    private var emaFrameMs = 0f

    // Bateria / térmica
    @Volatile var batteryLevel: Int = -1
        private set
    @Volatile var batteryTempC: Float = -1f
        private set
    @Volatile var charging: Boolean = false
        private set

    // CPU
    @Volatile var cpuLoadPct: Float = 0f
        private set
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L
    private var lastCpuSample = 0L

    // Memória
    @Volatile var javaHeapMb: Float = 0f
        private set
    @Volatile var nativeHeapMb: Float = 0f
        private set
    @Volatile var systemFreeRamMb: Long = 0
        private set

    private var fpsWindowFrames = 0
    private var fpsWindowStart = System.nanoTime()

    /** Chamado a cada frame renderizado. */
    fun onFrame(dtMs: Float) {
        // Médias exponenciais
        if (emaFps == 0f) emaFps = 1000f / dtMs.coerceAtLeast(0.01f) else {
            val inst = 1000f / dtMs.coerceAtLeast(0.01f)
            emaFps += (inst - emaFps) * 0.08f
        }
        emaFrameMs += (dtMs - emaFrameMs) * 0.08f
        fps = emaFps
        frameMs = emaFrameMs

        fpsWindowFrames++
        val now = System.nanoTime()
        if (now - fpsWindowStart > 500_000_000L) {
            fpsWindowStart = now
            fpsWindowFrames = 0
            sampleSystem()
        }
    }

    /** Amostra lenta (2x/segundo): bateria, CPU, memória. */
    private fun sampleSystem() {
        // Bateria (fonte real: sticky broadcast do sistema)
        try {
            val bat: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (bat != null) {
                val level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                batteryLevel = if (scale > 0) level * 100 / scale else -1
                batteryTempC = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -100) / 10f
                val st = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                        st == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {}

        // CPU via /proc/stat
        try {
            val txt = File("/proc/stat").readLines().firstOrNull() ?: ""
            val parts = txt.split(Regex("\\s+"))
            if (parts.size >= 5 && parts[0] == "cpu") {
                var total = 0L
                for (i in 1 until parts.size) total += parts[i].toLongOrNull() ?: 0L
                val idle = (parts[4].toLongOrNull() ?: 0L)
                val dt = total - lastCpuTotal
                val di = idle - lastCpuIdle
                if (dt > 0 && lastCpuSample > 0) {
                    cpuLoadPct = ((dt - di).toFloat() / dt.toFloat() * 100f).coerceIn(0f, 100f)
                }
                lastCpuTotal = total
                lastCpuIdle = idle
                lastCpuSample = System.nanoTime()
            }
        } catch (_: Exception) {}

        // Memória
        try {
            val rt = Runtime.getRuntime()
            javaHeapMb = (rt.totalMemory() - rt.freeMemory()) / 1048576f
            nativeHeapMb = Debug.getNativeHeapAllocatedSize() / 1048576f
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            systemFreeRamMb = mi.availMem / 1048576L
        } catch (_: Exception) {}
    }

    fun report(): String {
        val mem = String.format("%.0f MB Java + %.0f MB nativa", javaHeapMb, nativeHeapMb)
        val bat = if (batteryLevel >= 0) "$batteryLevel%${if (charging) " ⚡" else ""} · ${String.format("%.1f", batteryTempC)}°C" else "n/d"
        return "FPS ${String.format("%.0f", fps)} · frame ${String.format("%.1f", frameMs)} ms\n" +
                "CPU ${String.format("%.0f", cpuLoadPct)}% · RAM livre ${systemFreeRamMb} MB\n" +
                "Memória $mem\nBateria $bat"
    }
}

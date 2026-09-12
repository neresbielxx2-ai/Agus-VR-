package com.agusvr.spatial

import android.graphics.Paint
import com.agusvr.apps.SceneEnv
import com.agusvr.storage.AppDataStore
import com.agusvr.ui.VrPanel

/**
 * Agus Spatial System — notas posicionadas no ambiente (Spatial Notes).
 *
 * Cada nota é um painel 3D agarrável (punho para mover, mão aberta para
 * soltar) persistido pelo Agus Storage com posição + rotação.
 */
object SpatialNotes {

    val COLORS = arrayOf(
        intArrayOf(0x0B, 0x2A, 0x33, 0x6E, 0xE7, 0xFF),   // ciano
        intArrayOf(0x0B, 0x33, 0x1E, 0x7D, 0xFF, 0xB2),   // verde
        intArrayOf(0x33, 0x26, 0x0B, 0xFF, 0xC9, 0x6E),   // âmbar
        intArrayOf(0x22, 0x0B, 0x33, 0xC9, 0x8C, 0xFF)    // violeta
    )

    fun colorName(i: Int): String = when (i and 3) {
        0 -> "Ciano"; 1 -> "Verde"; 2 -> "Âmbar"; else -> "Violeta"
    }

    /** Cria uma nota no mundo e registra no ambiente do app. */
    fun spawn(env: SceneEnv, note: AppDataStore.Note): VrPanel {
        val panel = VrPanel("note_${note.id}", 0.6f, 0.42f, env.meshes, 512)
        panel.customDraw = { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val col = COLORS[note.color and 3]
            // borda colorida
            p.color = android.graphics.Color.rgb(col[3], col[4], col[5])
            p.textSize = h * 0.16f
            p.textAlign = Paint.Align.CENTER
            // texto com quebra simples
            val words = note.text.split(" ")
            var line = ""
            var y = h * 0.34f
            for (wd in words) {
                val test = if (line.isEmpty()) wd else "$line $wd"
                if (p.measureText(test) > w * 0.86f && line.isNotEmpty()) {
                    c.drawText(line, w / 2f, y, p)
                    y += h * 0.19f
                    line = wd
                    if (y > h * 0.9f) break
                } else line = test
            }
            if (y <= h * 0.9f && line.isNotEmpty()) c.drawText(line, w / 2f, y, p)
        }
        panel.place(note.pos[0], note.pos[1], note.pos[2], note.yaw)
        panel.target.grabbable = true
        panel.target.hoverLabel = "Nota espacial (agarre p/ mover)"
        env.addPanel(panel)
        return panel
    }

    /** Posição padrão para novas notas: à frente da visão atual. */
    fun defaultSpawnPos(env: SceneEnv): FloatArray {
        val f = env.head().forward
        return floatArrayOf(f[0] * 1.6f, 0.15f + f[1] * 1.6f, f[2] * 1.6f)
    }
}

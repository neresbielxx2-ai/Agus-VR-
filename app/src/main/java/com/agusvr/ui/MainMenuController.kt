package com.agusvr.ui

import com.agusvr.interaction.PanelElement
import com.agusvr.runtime.VrEngine
import kotlin.math.pow

/**
 * Agus UI — menu principal espacial (cards flutuantes).
 *
 * Cards: Biblioteca · Aplicativos · Agus Hand Lab · Configurações ·
 * Performance · Espaço Pessoal · Sistema.
 *
 * Adaptive UI: a ordem dos cards prioriza os apps mais usados
 * (contagem persistida pelo Agus Storage).
 */
class MainMenuController(private val engine: VrEngine) {

    companion object {
        const val BASE_W = 3.4f
        const val BASE_H = 1.7f
    }

    private val cardIds = listOf(
        "library", "apps", "handlab", "settings", "performance", "personal", "system"
    )
    private val labels = mapOf(
        "library" to "Biblioteca",
        "apps" to "Aplicativos",
        "handlab" to "Agus Hand Lab",
        "settings" to "Configurações",
        "performance" to "Performance",
        "personal" to "Espaço Pessoal",
        "system" to "Sistema"
    )

    lateinit var panel: VrPanel
        private set
    private var anim = 1f
    private var built = false

    fun build() {
        panel = VrPanel("main_menu", BASE_W, BASE_H, engine.renderer.meshes, 1400)
        panel.title = "AGUS VR"
        panel.subtitle = "Aponte com o indicador e aproxime a mão para selecionar"
        panel.place(0f, 0.02f, -2.35f, 0f)
        rebuild()
        panel.registerTo(engine.transparent, engine.textures)
        engine.panels.add(panel)
        engine.interactables.add(panel.target)
        built = true
    }

    /** (Re)monta os cards com ordenação adaptativa por uso. */
    fun rebuild() {
        if (!built) return
        panel.clearElements()
        val order = engine.data.sortByUsage(cardIds)
        val mostUsed = order.firstOrNull()

        val cw = 0.215f
        val ch = 0.27f
        val gap = 0.024f
        val rows = listOf(4, 3)
        val rowY = floatArrayOf(-0.015f, -0.315f)

        var idx = 0
        for (row in rows.indices) {
            val n = rows[row]
            val totalW = n * cw + (n - 1) * gap
            var x = -totalW / 2f
            for (c in 0 until n) {
                if (idx >= order.size) break
                val id = order[idx]
                val uses = engine.data.useCount(id)
                val el = panel.addButton(
                    id = id,
                    label = labels[id] ?: id,
                    x = x, y = rowY[row], w = cw, h = ch,
                    accent = id == mostUsed && uses > 0,
                    meta = if (uses > 0) "usado ${uses}x" else ""
                ) { onCardSelected(it) }
                idx++
                x += cw + gap
            }
        }
        panel.repaint()
    }

    private fun onCardSelected(el: PanelElement) {
        engine.apps.launch(el.id)
    }

    /** Animação suave de entrada/saída. */
    fun update(dtMs: Float) {
        if (!built) return
        val target = if (engine.menuVisible) 1f else 0f
        val t = (dtMs / 140f).coerceIn(0f, 1f)
        anim += (target - anim) * (1f - (1f - t).pow(2f))
        val s = 0.86f + 0.14f * anim
        panel.item.scale[0] = BASE_W * s
        panel.item.scale[1] = BASE_H * s
        panel.item.color[3] = anim.coerceIn(0f, 1f)
        val vis = anim > 0.03f
        panel.item.visible = vis
        panel.target.enabled = vis
    }

    fun updatePose(forward: FloatArray) {
        // O menu vive fixo no mundo (workspace espacial); nada a fazer aqui.
    }
}

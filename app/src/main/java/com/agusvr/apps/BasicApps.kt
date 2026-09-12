package com.agusvr.apps

import android.graphics.Paint
import com.agusvr.performance.PerfPolicy
import com.agusvr.ui.VrPanel

/**
 * Agus Applications — Biblioteca: experiências disponíveis.
 */
class LibraryApp : AgusApp {
    override val id = "library"
    override val name = "Biblioteca"

    override fun build(env: SceneEnv) {
        val panel = VrPanel("library", 2.2f, 1.5f, env.meshes, 1100)
        panel.title = "Biblioteca"
        panel.subtitle = "Experiências instaladas no Agus VR"
        val items = listOf(
            Triple("handlab", "Agus Hand Lab", "laboratório de hand tracking"),
            Triple("personal", "Espaço Pessoal", "seu ambiente com notas espaciais"),
            Triple("notes", "Notas Espaciais", "crie e organize notas no espaço")
        )
        var y = -0.05f
        for ((appId, label, meta) in items) {
            panel.addButton(appId, label, -0.45f, y, 0.9f, 0.17f, meta = meta) { el ->
                env.launch(el.id)
            }
            y -= 0.20f
        }
        panel.addButton("back", "◂ Voltar", -0.33f, -0.68f, 0.3f, 0.14f) { env.exitToMenu() }
        panel.customDraw = { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = VrPanel.TEXT_DIM
            p.textSize = h * 0.042f
            c.drawText(
                "Para adicionar experiências novas: README → “Como criar novos aplicativos”",
                w * 0.06f, h * 0.97f, p
            )
        }
        panel.place(0f, 0.05f, -2.2f, 0f)
        env.addPanel(panel)
    }
}

/**
 * Agus Applications — Aplicativos: utilitários do sistema.
 */
class AppsApp : AgusApp {
    override val id = "apps"
    override val name = "Aplicativos"

    override fun build(env: SceneEnv) {
        val panel = VrPanel("apps_grid", 2.4f, 1.4f, env.meshes, 1200)
        panel.title = "Aplicativos"
        val grid = listOf(
            "performance" to "Performance",
            "notes" to "Notas Espaciais",
            "settings" to "Configurações",
            "system" to "Sistema",
            "handlab" to "Agus Hand Lab",
            "personal" to "Espaço Pessoal"
        )
        var x = -0.48f; var y = -0.03f
        var i = 0
        for ((appId, label) in grid) {
            panel.addButton(appId, label, x, y, 0.44f, 0.22f) { el -> env.launch(el.id) }
            x += 0.48f
            if (++i % 2 == 0) { x = -0.48f; y -= 0.26f }
        }
        panel.addButton("back", "◂ Voltar", -0.35f, -0.56f, 0.3f, 0.16f) { env.exitToMenu() }
        panel.place(0f, 0.05f, -2.2f, 0f)
        env.addPanel(panel)
    }
}

/**
 * Agus Applications — Configurações dentro do VR (aplicadas ao vivo).
 */
class SettingsApp : AgusApp {
    override val id = "settings"
    override val name = "Configurações"

    override fun build(env: SceneEnv) {
        val panel = VrPanel("settings_vr", 2.3f, 1.75f, env.meshes, 1150)
        panel.title = "Configurações"
        panel.subtitle = "Alterações aplicadas em tempo real"

        fun refresh(panel: VrPanel) = panel.repaint()

        panel.addButton("hands", "", -0.48f, -0.04f, 0.44f, 0.14f) { _ ->
            env.settings().handTrackingEnabled = !env.settings().handTrackingEnabled
            env.saveSettings(); env.hands().refreshFlags()
            env.toast(if (env.settings().handTrackingEnabled) "Hand tracking ON (reabra o VR p/ câmera)" else "Hand tracking OFF")
            rebuild(panel, env)
        }
        panel.addButton("fx", "", 0.04f, -0.04f, 0.44f, 0.14f) { _ ->
            env.settings().effectsEnabled = !env.settings().effectsEnabled
            env.saveSettings(); rebuild(panel, env)
        }
        panel.addButton("sound", "", -0.48f, -0.21f, 0.44f, 0.14f) { _ ->
            env.settings().soundEnabled = !env.settings().soundEnabled
            env.saveSettings(); rebuild(panel, env)
        }
        panel.addButton("shadows", "", 0.04f, -0.21f, 0.44f, 0.14f) { _ ->
            env.settings().shadowsEnabled = !env.settings().shadowsEnabled
            env.saveSettings(); rebuild(panel, env)
        }
        panel.addButton("res", "", -0.48f, -0.38f, 0.44f, 0.14f) { _ ->
            val opts = listOf(0.5f, 0.7f, 0.85f, 1.0f)
            val i = opts.indexOfFirst { kotlin.math.abs(it - env.settings().resolutionScale) < 0.03f }
            env.settings().resolutionScale = opts[(if (i < 0) 0 else i) + 1 and 3]
            env.saveSettings(); rebuild(panel, env)
        }
        panel.addButton("fps", "", 0.04f, -0.38f, 0.44f, 0.14f) { _ ->
            val opts = listOf(30, 45, 60, 72)
            val i = opts.indexOf(env.settings().fpsCap).let { if (it < 0) 2 else it }
            env.settings().fpsCap = opts[(i + 1) % opts.size]
            env.saveSettings(); rebuild(panel, env)
        }
        panel.addButton("mode", "", -0.48f, -0.55f, 0.44f, 0.14f) { _ ->
            val next = (env.settings().perfMode + 1) % 4
            PerfPolicy().applyMode(env.settings(), next)
            env.saveSettings(); rebuild(panel, env)
        }
        panel.addButton("focus", "Focus Mode", 0.04f, -0.55f, 0.44f, 0.14f) { _ ->
            env.engine.toggleFocus()
        }
        panel.addButton("recenter", "Recentrar visão", -0.48f, -0.72f, 0.44f, 0.14f, accent = true) { _ ->
            env.recenter()
        }
        panel.addButton("back", "◂ Voltar", 0.04f, -0.72f, 0.44f, 0.14f) { env.exitToMenu() }

        rebuild(panel, env)
        panel.place(0f, 0.1f, -2.2f, 0f)
        env.addPanel(panel)
    }

    private fun rebuild(panel: VrPanel, env: SceneEnv) {
        val s = env.settings()
        fun find(id: String) = panel.elements.firstOrNull { it.id == id }
        find("hands")?.label = "Hand Tracking: ${if (s.handTrackingEnabled) "ON" else "OFF"}"
        find("fx")?.label = "Efeitos: ${if (s.effectsEnabled) "ON" else "OFF"}"
        find("sound")?.label = "Som: ${if (s.soundEnabled) "ON" else "OFF"}"
        find("shadows")?.label = "Sombras: ${if (s.shadowsEnabled) "ON" else "OFF"}"
        find("res")?.label = "Resolução: ${(s.resolutionScale * 100).toInt()}%"
        find("fps")?.label = "FPS: ${s.fpsCap}"
        find("mode")?.label = "Modo: ${PerfPolicy.modeName(s.perfMode)}"
        panel.repaint()
    }
}

/**
 * Agus Applications — Performance: telemetria real do sistema.
 */
class PerformanceApp : AgusApp {
    override val id = "performance"
    override val name = "Performance"

    private var lastPaint = 0L
    private lateinit var panel: VrPanel
    private lateinit var env: SceneEnv

    override fun build(env: SceneEnv) {
        this.env = env
        panel = VrPanel("perf_app", 2.3f, 1.5f, env.meshes, 1150)
        panel.title = "Performance"
        panel.subtitle = "Telemetria real do dispositivo"
        panel.customDraw = { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = VrPanel.TEXT
            p.textSize = h * 0.072f
            var y = h * 0.26f
            for (ln in env.engine.perf.report().split("\n")) {
                c.drawText(ln, w * 0.06f, y, p); y += h * 0.085f
            }
            p.color = VrPanel.TEXT_DIM
            p.textSize = h * 0.055f
            val a = env.engine.adaptive
            c.drawText("Qualidade automática: nível ${a.step} — ${a.lastChangeReason}", w * 0.06f, y + h * 0.02f, p)
        }
        for ((i, mode) in listOf(0, 1, 2, 3).withIndex()) {
            panel.addButton("mode$mode", PerfPolicy.modeName(mode), -0.46f + i * 0.24f, -0.55f, 0.22f, 0.15f) { _ ->
                PerfPolicy().applyMode(env.settings(), mode)
                env.saveSettings()
                env.toast("Modo ${PerfPolicy.modeName(mode)}")
            }
        }
        panel.addButton("hud", "HUD na visão", -0.48f, -0.75f, 0.44f, 0.13f, accent = true) { _ ->
            env.hudToggle()
        }
        panel.addButton("back", "◂ Voltar", 0.04f, -0.75f, 0.44f, 0.13f) { env.exitToMenu() }
        panel.place(0f, 0.1f, -2.15f, 0f)
        env.addPanel(panel)
    }

    override fun update(dtMs: Float) {
        val now = System.currentTimeMillis()
        if (now - lastPaint > 500) {
            lastPaint = now
            panel.repaint()
        }
    }
}

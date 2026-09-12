package com.agusvr.apps

import android.graphics.Paint
import com.agusvr.handtracking.HandModelManager
import com.agusvr.spatial.SpatialNotes
import com.agusvr.storage.AppDataStore
import com.agusvr.ui.VrPanel
import com.agusvr.bridge.AgusBridge

/**
 * Agus Applications — Espaço Pessoal: ambiente calmo com notas espaciais,
 * foco e atalhos.
 */
class PersonalSpaceApp : AgusApp {
    override val id = "personal"
    override val name = "Espaço Pessoal"

    private val notes = mutableListOf<AppDataStore.Note>()
    private val notePanels = mutableListOf<VrPanel>()
    private lateinit var env: SceneEnv
    private var nextIdx = 0
    private val presets = listOf(
        "Respirar fundo", "Beber água", "Ótima ideia!", "Lembrar disto depois"
    )

    override fun build(env: SceneEnv) {
        this.env = env
        // Notas persistidas
        val saved = env.data().loadNotes()
        for (n in saved) { notePanels.add(SpatialNotes.spawn(env, n)); notes.add(n) }
        if (saved.isEmpty()) {
            val n = AppDataStore.Note("n_welcome", "Bem-vindo ao seu Espaço Pessoal ✦", 0,
                floatArrayOf(0f, 0.3f, -1.8f), 0f)
            notePanels.add(SpatialNotes.spawn(env, n))
            notes.add(n)
        }

        val panel = VrPanel("personal_ctrl", 1.9f, 1.1f, env.meshes, 1000)
        panel.title = "Espaço Pessoal"
        panel.subtitle = "Suas notas vivem aqui, flutuando"
        panel.addButton("new_note", "✚ Nova nota", -0.48f, -0.08f, 0.44f, 0.16f, accent = true) { _ ->
            val text = presets[nextIdx % presets.size]; nextIdx++
            val pos = SpatialNotes.defaultSpawnPos(env)
            val n = AppDataStore.Note(
                "n_${System.currentTimeMillis()}", text,
                (notes.size) % 4, pos, 0f
            )
            notePanels.add(SpatialNotes.spawn(env, n))
            notes.add(n)
            persist(env)
            env.toast("Nota criada — agarre com o punho para mover")
        }
        panel.addButton("focus", "Focus Mode", 0.04f, -0.08f, 0.44f, 0.16f) { _ ->
            env.engine.toggleFocus()
        }
        panel.addButton("recenter", "Recentrar", -0.48f, -0.28f, 0.44f, 0.16f) { _ ->
            env.recenter()
        }
        panel.addButton("clear", "Limpar notas", 0.04f, -0.28f, 0.44f, 0.16f) { _ ->
            env.data().saveNotes(emptyList())
            env.toast("Notas removidas (recarregue para ver)")
            env.exitToMenu()
        }
        panel.addButton("back", "◂ Voltar", -0.48f, -0.48f, 0.44f, 0.16f) { env.exitToMenu() }
        panel.place(0f, -0.15f, -2.1f, 0f)
        env.addPanel(panel)
    }

    private fun persist(env: SceneEnv) {
        env.data().saveNotes(notes)
    }

    override fun dispose() {
        // Spatial workspace: salva as posições finais (notas podem ter sido
        // movidas com o punho durante a sessão).
        for (i in notes.indices) {
            if (i >= notePanels.size) break
            val item = notePanels[i].item
            notes[i].pos[0] = item.pos[0]
            notes[i].pos[1] = item.pos[1]
            notes[i].pos[2] = item.pos[2]
            notes[i].yaw = item.yaw
        }
        env.data().saveNotes(notes)
    }
}

/**
 * Agus Applications — Notas Espaciais: criação rápida de notas.
 */
class NotesApp : AgusApp {
    override val id = "notes"
    override val name = "Notas Espaciais"

    private val notes = mutableListOf<AppDataStore.Note>()
    private val notePanels = mutableListOf<VrPanel>()
    private var colorIdx = 0
    private lateinit var listPanel: VrPanel
    private lateinit var env: SceneEnv

    private val presets = listOf(
        "Ideia", "Lembrete", "Tarefa importante", "Comprar pão", "Teste Agus VR", "Reunião às 15h"
    )

    override fun build(env: SceneEnv) {
        this.env = env
        for (n in env.data().loadNotes()) {
            notePanels.add(SpatialNotes.spawn(env, n)); notes.add(n)
        }

        val panel = VrPanel("notes_ctrl", 2.0f, 1.4f, env.meshes, 1000)
        panel.title = "Notas Espaciais"
        panel.subtitle = "Notas posicionadas no ambiente, persistidas"
        var y = -0.06f
        for (txt in presets) {
            panel.addButton("p_" + txt.hashCode(), txt, -0.45f, y, 0.9f, 0.13f) { el ->
                createNote(env, el.label)
            }
            y -= 0.16f
        }
        panel.addButton("color", "Cor: ${SpatialNotes.colorName(colorIdx)}", -0.48f, -0.82f, 0.44f, 0.13f) { el ->
            colorIdx = (colorIdx + 1) % 4
            el.label = "Cor: ${SpatialNotes.colorName(colorIdx)}"
            panel.repaint()
        }
        panel.addButton("back", "◂ Voltar", 0.04f, -0.82f, 0.44f, 0.13f) { env.exitToMenu() }
        panel.place(-0.9f, 0.05f, -2.0f, 0.3f)
        env.addPanel(panel)

        listPanel = VrPanel("notes_list", 1.3f, 1.0f, env.meshes, 700)
        listPanel.title = "Suas notas"
        listPanel.customDraw = { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = VrPanel.TEXT_DIM
            p.textSize = h * 0.07f
            var yy = h * 0.26f
            if (notes.isEmpty()) c.drawText("Nenhuma nota ainda.", w * 0.08f, yy, p)
            for (n in notes) {
                c.drawText("• ${n.text.take(22)}", w * 0.08f, yy, p)
                yy += h * 0.1f
                if (yy > h * 0.9f) break
            }
        }
        listPanel.place(1.0f, 0.1f, -2.0f, -0.3f)
        env.addPanel(listPanel)
    }

    private fun createNote(env: SceneEnv, text: String) {
        val pos = SpatialNotes.defaultSpawnPos(env)
        val n = AppDataStore.Note("n_${System.currentTimeMillis()}", text, colorIdx, pos, 0f)
        notePanels.add(SpatialNotes.spawn(env, n))
        notes.add(n)
        env.data().saveNotes(notes)
        listPanel.repaint()
        env.toast("Nota criada")
    }

    override fun dispose() {
        for (i in notes.indices) {
            if (i >= notePanels.size) break
            val item = notePanels[i].item
            notes[i].pos[0] = item.pos[0]; notes[i].pos[1] = item.pos[1]; notes[i].pos[2] = item.pos[2]
            notes[i].yaw = item.yaw
        }
        env.data().saveNotes(notes)
    }
}

/**
 * Agus Applications — Sistema: compatibilidade, modelo, ponte Roblox.
 */
class SystemApp : AgusApp {
    override val id = "system"
    override val name = "Sistema"

    private lateinit var infoPanel: VrPanel
    private lateinit var env: SceneEnv

    override fun build(env: SceneEnv) {
        this.env = env
        infoPanel = VrPanel("system_info", 2.4f, 1.6f, env.meshes, 1200)
        infoPanel.title = "Sistema"
        infoPanel.customDraw = { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = VrPanel.TEXT
            p.textSize = h * 0.052f
            var y = h * 0.20f
            for (ln in env.engine.profile.summary.split("\n")) {
                if (ln.isBlank()) continue
                c.drawText(ln, w * 0.05f, y, p); y += h * 0.065f
            }
            p.color = VrPanel.TEXT_DIM
            val ht = env.hands()
            c.drawText("Hand tracking: ${ht.statusMessage}", w * 0.05f, y + h * 0.02f, p)
            c.drawText(
                "Modelo: " + if (HandModelManager.status(env.activity()).let { it is HandModelManager.Status.Ready }) "pronto" else "ausente/baixando",
                w * 0.05f, y + h * 0.09f, p
            )
            val bridgeOn = AgusBridge.isEnabled()
            c.drawText(
                if (bridgeOn) "Ponte Roblox ativa em ${AgusBridge.endpoint(env.activity())}"
                else "Ponte Roblox: desativada",
                w * 0.05f, y + h * 0.16f, p
            )
            c.drawText("Política: o APK do Roblox nunca é modificado.", w * 0.05f, y + h * 0.23f, p)
        }
        infoPanel.addButton("bridge", "Ligar ponte Roblox", -0.48f, -0.6f, 0.44f, 0.14f, accent = true) { el ->
            if (AgusBridge.isEnabled()) {
                AgusBridge.stop()
                el.label = "Ligar ponte Roblox"
            } else {
                AgusBridge.start(env.engine)
                el.label = "Desligar ponte"
            }
            infoPanel.repaint()
        }
        infoPanel.addButton("reactivate_hands", "Reativar hand tracking", -0.48f, -0.78f, 0.44f, 0.14f) { _ ->
            env.engine.forceStartHands()
        }
        infoPanel.addButton("back", "◂ Voltar", 0.04f, -0.6f, 0.44f, 0.14f) { env.exitToMenu() }
        infoPanel.place(0f, 0.1f, -2.2f, 0f)
        env.addPanel(infoPanel)
    }

    override fun update(dtMs: Float) {
        // Repinta pouco: só quando o usuário está olhando (aqui: a cada 1s)
        if ((System.currentTimeMillis() / 1000) != lastSec) {
            lastSec = System.currentTimeMillis() / 1000
            infoPanel.repaint()
        }
    }

    private var lastSec = -1L
}

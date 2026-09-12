package com.agusvr.apps

import android.graphics.Paint
import com.agusvr.handtracking.GestureClassifier
import com.agusvr.interaction.BoxTarget
import com.agusvr.interaction.SphereTarget
import com.agusvr.runtime.gl.SceneObject
import com.agusvr.ui.VrPanel
import com.agusvr.util.M
import kotlin.math.sin

/**
 * Agus Hand Lab — ambiente de teste do hand tracking.
 *
 * Conteúdo:
 *  - modelo visual das mãos (esqueleto 3D vindo do tracking real)
 *  - passthrough da câmera para enxergar as próprias mãos
 *  - cubos "smart" (brilham com a aproximação e podem ser agarrados)
 *  - painel de botões com contadores (feedback real de seleção)
 *  - smart object (esfera que pulsa com a mão e com o ray)
 *  - alvo de precisão para treinar o Agus Point Interaction
 *  - painel com os gestos detectados em tempo real
 */
class HandLabApp : AgusApp {

    override val id = "handlab"
    override val name = "Agus Hand Lab"

    private lateinit var env: SceneEnv
    private lateinit var gesturePanel: VrPanel
    private lateinit var buttonPanel: VrPanel
    private lateinit var helpPanel: VrPanel

    private var counterA = 0
    private var counterB = 0
    private var score = 0
    private var effectsOn = true
    private var lastGestureText = ""
    private var lastGesturePaint = 0L
    private var timeSec = 0f

    private val cubes = ArrayList<Pair<SceneObject, BoxTarget>>()
    private val shadows = ArrayList<SceneObject>()
    private lateinit var smartSphere: SceneObject
    private lateinit var smartTarget: SphereTarget
    private lateinit var targetRing: SceneObject

    override fun build(env: SceneEnv) {
        this.env = env
        val m = env.meshes

        // Ver as próprias mãos
        env.setPassthrough(true)

        // ---------------------------------------------------------- cubos
        val colors = arrayOf(
            floatArrayOf(1.0f, 0.35f, 0.35f),
            floatArrayOf(0.45f, 1.0f, 0.55f),
            floatArrayOf(0.45f, 0.62f, 1.0f)
        )
        for (i in 0 until 3) {
            val cube = SceneObject(m.cube, SceneObject.PROG_LIT)
            cube.pos[0] = -0.55f + i * 0.55f
            cube.pos[1] = -0.42f
            cube.pos[2] = -1.45f
            cube.scale[0] = 0.18f; cube.scale[1] = 0.18f; cube.scale[2] = 0.18f
            cube.yaw = 0.4f + i * 0.35f
            cube.color[0] = colors[i][0]; cube.color[1] = colors[i][1]; cube.color[2] = colors[i][2]
            env.addItem(cube)

            val t = BoxTarget("cube_$i", cube, floatArrayOf(0.11f, 0.11f, 0.11f)) { _ ->
                env.toast("Cubo ${"ABC"[i]} selecionado")
            }
            t.hoverLabel = "Cubo ${"ABC"[i]} (agarre com punho)"
            env.addInteractable(t)
            cubes.add(cube to t)

            // Sombra suave (blob) no chão
            val sh = SceneObject(m.disc, SceneObject.PROG_UNLIT)
            sh.pitch = -Math.PI.toFloat() / 2f
            sh.pos[0] = cube.pos[0]; sh.pos[1] = -1.59f; sh.pos[2] = cube.pos[2]
            sh.scale[0] = 0.3f; sh.scale[1] = 0.3f
            sh.color[0] = 0f; sh.color[1] = 0f; sh.color[2] = 0f; sh.color[3] = 0.4f
            sh.depthWrite = false
            env.addItem(sh, opaque = false)
            shadows.add(sh)
        }

        // ---------------------------------------------------- smart sphere
        smartSphere = SceneObject(m.sphere, SceneObject.PROG_LIT)
        smartSphere.pos[0] = 0f; smartSphere.pos[1] = 0.25f; smartSphere.pos[2] = -1.7f
        smartSphere.scale[0] = 0.11f; smartSphere.scale[1] = 0.11f; smartSphere.scale[2] = 0.11f
        smartSphere.color[0] = 0.55f; smartSphere.color[1] = 0.95f; smartSphere.color[2] = 1.0f
        env.addItem(smartSphere)
        smartTarget = SphereTarget("smart_sphere", smartSphere, 0.16f) { _ ->
            env.toast("Smart Object ativado ✦")
        }
        smartTarget.hoverLabel = "Smart Object"
        env.addInteractable(smartTarget)

        // --------------------------------------------------- alvo de precisão
        targetRing = SceneObject(m.ring, SceneObject.PROG_UNLIT)
        targetRing.pos[0] = 0f; targetRing.pos[1] = 0.55f; targetRing.pos[2] = -3.2f
        targetRing.scale[0] = 0.5f; targetRing.scale[1] = 0.5f
        targetRing.color[0] = 0.49f; targetRing.color[1] = 1.0f; targetRing.color[2] = 0.70f; targetRing.color[3] = 0.85f
        targetRing.billboard = true
        targetRing.depthWrite = false
        env.addItem(targetRing, opaque = false)
        val targetHit = SphereTarget("precision_target", targetRing, 0.28f) { _ ->
            score++
            env.toast("Alvo! acertos: $score")
        }
        targetHit.hoverLabel = "Alvo de precisão"
        env.addInteractable(targetHit)

        // -------------------------------------------------- painel de gestos
        gesturePanel = VrPanel("lab_gestures", 1.3f, 0.62f, m, 900)
        gesturePanel.title = "Gestos detectados"
        gesturePanel.customDraw = { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = VrPanel.TEXT
            p.textSize = h * 0.13f
            val lines = lastGestureText.split("\n")
            var y = h * 0.34f
            for (ln in lines) { c.drawText(ln, w * 0.07f, y, p); y += h * 0.18f }
        }
        gesturePanel.place(-1.25f, 0.35f, -1.85f, 0.45f)
        env.addPanel(gesturePanel)

        // --------------------------------------------------- painel de botões
        buttonPanel = VrPanel("lab_buttons", 1.35f, 1.0f, m, 950)
        buttonPanel.title = "Botões de teste"
        buttonPanel.customDraw = { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = VrPanel.TEXT_DIM
            p.textSize = h * 0.055f
            c.drawText("Contador A: $counterA · Contador B: $counterB · Alvos: $score", w * 0.07f, h * 0.95f, p)
        }
        buttonPanel.addButton("btn_a", "Botão A", -0.46f, -0.06f, 0.42f, 0.2f) { _ ->
            counterA++; refreshCounters()
        }
        buttonPanel.addButton("btn_b", "Botão B", 0.04f, -0.06f, 0.42f, 0.2f) { _ ->
            counterB++; refreshCounters()
        }
        buttonPanel.addButton("btn_fx", "Efeitos: ON", -0.46f, -0.3f, 0.42f, 0.2f, accent = true) { el ->
            effectsOn = !effectsOn
            el.label = if (effectsOn) "Efeitos: ON" else "Efeitos: OFF"
            buttonPanel.repaint()
        }
        buttonPanel.addButton("btn_exit", "Sair do Lab", 0.04f, -0.3f, 0.42f, 0.2f) { _ ->
            env.exitToMenu()
        }
        buttonPanel.place(1.25f, 0.15f, -1.85f, -0.45f)
        buttonPanel.target.grabbable = true
        buttonPanel.target.hoverLabel = "Painel de botões (agarre p/ mover)"
        env.addPanel(buttonPanel)

        // --------------------------------------------------------- ajuda
        helpPanel = VrPanel("lab_help", 1.7f, 0.5f, m, 1100)
        helpPanel.title = "Como testar"
        helpPanel.customDraw = { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = VrPanel.TEXT_DIM
            p.textSize = h * 0.115f
            val lines = arrayOf(
                "☝ Aponte com o indicador: círculo + ray aparecem",
                "✊ Feche o punho perto de um cubo para agarrar",
                "🖐 Mão aberta solta · Aproxime a mão p/ selecionar"
            )
            var y = h * 0.40f
            for (ln in lines) { c.drawText(ln, w * 0.05f, y, p); y += h * 0.2f }
        }
        helpPanel.place(0f, -0.72f, -1.9f, 0f)
        env.addPanel(helpPanel)

        env.toast("Agus Hand Lab — mostre suas mãos à câmera")
    }

    private fun refreshCounters() {
        buttonPanel.repaint()
    }

    override fun update(dtMs: Float) {
        timeSec += dtMs / 1000f
        val hf = env.hands().frame
        val now = System.currentTimeMillis()

        // Painel de gestos (atualização limitada a ~7 Hz)
        if (now - lastGesturePaint > 140) {
            lastGesturePaint = now
            val sb = StringBuilder()
            if (!hf.modelReady) {
                sb.append("Modelo ausente — baixe na tela inicial")
            } else if (!hf.cameraGranted) {
                sb.append("Câmera sem permissão")
            } else {
                val l = hf.left; val r = hf.right
                sb.append("Esquerda: ").append(if (l.present) GestureClassifier.label(l.gesture) else "não vista")
                sb.append('\n')
                sb.append("Direita: ").append(if (r.present) GestureClassifier.label(r.gesture) else "não vista")
                sb.append('\n')
                sb.append(env.hands().statusMessage)
                if (hf.processingMs > 0f) sb.append(String.format(" · %.0f ms", hf.processingMs))
            }
            val txt = sb.toString()
            if (txt != lastGestureText) {
                lastGestureText = txt
                gesturePanel.repaint()
            }
        }

        // Smart objects: brilho por proximidade da palma (reação real)
        for ((cube, _) in cubes) {
            var nearest = 10f
            for (h in listOf(hf.left, hf.right)) {
                if (h.present && now - h.lastSeenMs < 600) {
                    nearest = minOf(nearest, M.dist(h.palm, cube.pos))
                }
            }
            val glow = (1f - (nearest / 0.6f)).coerceIn(0f, 1f) * 0.5f
            // não sobrescreve o destaque de hover/seleção
            if (cube.emissive < glow) cube.emissive = glow
            else cube.emissive = maxOf(cube.emissive - dtMs / 800f, glow)
        }

        // Esfera smart: flutua e pulsa
        smartSphere.pos[1] = 0.25f + sin(timeSec * 1.4f) * 0.05f
        val nearHand = (listOf(hf.left, hf.right).any {
            it.present && now - it.lastSeenMs < 600 && M.dist(it.palm, smartSphere.pos) < 0.5f
        })
        val pulse = if (nearHand) 0.7f + sin(timeSec * 8f) * 0.3f else 0.25f
        if (smartSphere.emissive < pulse) smartSphere.emissive = pulse
        else smartSphere.emissive = maxOf(smartSphere.emissive - dtMs / 500f, pulse)

        targetRing.color[3] = 0.75f + sin(timeSec * 2.2f) * 0.2f

        // Sombras acompanham os cubos
        for (i in cubes.indices) {
            shadows[i].pos[0] = cubes[i].first.pos[0]
            shadows[i].pos[2] = cubes[i].first.pos[2]
            shadows[i].visible = env.settings().shadowsEnabled
        }
    }

    override fun dispose() {
        env.setPassthrough(false)
    }
}

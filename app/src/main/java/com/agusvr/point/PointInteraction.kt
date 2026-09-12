package com.agusvr.point

import com.agusvr.handtracking.HandFrame
import com.agusvr.interaction.Interactable
import com.agusvr.interaction.InteractionManager
import com.agusvr.interaction.TargetState
import com.agusvr.runtime.gl.DynamicDraw
import com.agusvr.runtime.gl.DynamicLines
import com.agusvr.runtime.gl.MeshSet
import com.agusvr.runtime.gl.SceneObject
import com.agusvr.util.M
import com.agusvr.util.Ray
import com.agusvr.storage.Settings

/**
 * Agus Point Interaction — sistema proprietário de apontar.
 *
 * ☝️ ──────────────── ○
 *
 * Quando somente o indicador está estendido:
 *  - entra no modo apontar;
 *  - um círculo aparece na ponta do dedo;
 *  - uma linha discreta parte da ponta do dedo na direção do apontado;
 *  - a linha detecta botões, apps, objetos, menus e elementos do Lab;
 *  - a seleção acontece por APROXIMAÇÃO da mão enquanto aponta
 *    (com debounce e confirmação — nunca por passagem rápida do ray).
 */
class PointInteraction(
    private val meshes: MeshSet,
    private val interaction: InteractionManager,
    private val settings: () -> Settings
) {
    companion object {
        const val MAX_RANGE = 9f
        const val MIN_RANGE = 0.05f
    }

    // ------------------------------------------------------------- visuals
    val ring = SceneObject(meshes.ring, SceneObject.PROG_UNLIT).apply {
        billboard = true
        depthWrite = false
        visible = false
        sortBias = 10f
        color[0] = 0.43f; color[1] = 0.90f; color[2] = 1.0f; color[3] = 0.9f
    }

    val progressDisc = SceneObject(meshes.disc, SceneObject.PROG_UNLIT).apply {
        billboard = true
        depthWrite = false
        visible = false
        sortBias = 11f
        color[0] = 0.49f; color[1] = 1.0f; color[2] = 0.70f; color[3] = 0.35f
    }

    val rayLines = DynamicLines(4)
    val rayDraw = DynamicDraw(rayLines).apply {
        color[0] = 0.43f; color[1] = 0.90f; color[2] = 1.0f; color[3] = 0.35f
    }

    // --------------------------------------------------------------- estado
    var active = false
        private set
    val tip = FloatArray(3)
    val dir = FloatArray(3)
    val ray = Ray(FloatArray(3), FloatArray(3))
    var circleRadius = 0.024f
        private set
    var hitDistance = MAX_RANGE
        private set
    var pointingSide = -1
        private set

    private val pts = FloatArray(6)

    /**
     * Atualiza o sistema. Retorna o alvo sob o ray (se houver).
     */
    fun update(frame: HandFrame, interactables: List<Interactable>, dtMs: Float): Interactable? {
        val hand = frame.pointingHand()

        if (hand == null) {
            if (active) interaction.update(null, null, -1f, null, dtMs, 0.35f)
            setVisible(false)
            active = false
            pointingSide = -1
            return null
        }

        active = true
        pointingSide = hand.side

        tip[0] = hand.indexTip[0]; tip[1] = hand.indexTip[1]; tip[2] = hand.indexTip[2]
        dir[0] = hand.indexDir[0]; dir[1] = hand.indexDir[1]; dir[2] = hand.indexDir[2]
        ray.origin[0] = tip[0]; ray.origin[1] = tip[1]; ray.origin[2] = tip[2]
        ray.dir[0] = dir[0]; ray.dir[1] = dir[1]; ray.dir[2] = dir[2]

        // Raycast: alvo mais próximo dentro do alcance
        var best: Interactable? = null
        var bestD = MAX_RANGE
        for (t in interactables) {
            if (!t.enabled) continue
            val d = t.rayHit(ray)
            if (d > MIN_RANGE && d < bestD) {
                bestD = d
                best = t
            }
        }
        hitDistance = bestD

        // Seleção por aproximação (distância da mão + direção + alvo)
        interaction.update(ray, best, bestD, hand.palm, dtMs, settings().selectDistance)

        updateVisuals(best)
        return best
    }

    private fun updateVisuals(target: Interactable?) {
        setVisible(true)

        // Círculo na ponta do dedo (levemente à frente)
        val ringPos = ring.pos
        ringPos[0] = tip[0] + dir[0] * 0.03f
        ringPos[1] = tip[1] + dir[1] * 0.03f
        ringPos[2] = tip[2] + dir[2] * 0.03f

        // Tamanho: maior quando longe, encolhe ao confirmar a aproximação
        val p = interaction.progress
        val distFactor = if (target != null) (1.15f - hitDistance * 0.12f).coerceIn(0.6f, 1.25f) else 1f
        circleRadius = 0.026f * distFactor * (1f - 0.55f * p)
        ring.scale[0] = circleRadius * 2f
        ring.scale[1] = circleRadius * 2f
        ring.scale[2] = circleRadius * 2f

        // Disco de progresso (preenche conforme a mão se aproxima)
        progressDisc.visible = p > 0.02f
        progressDisc.pos[0] = ringPos[0]; progressDisc.pos[1] = ringPos[1]; progressDisc.pos[2] = ringPos[2]
        val dr = circleRadius * 0.62f * p
        progressDisc.scale[0] = dr * 2f; progressDisc.scale[1] = dr * 2f; progressDisc.scale[2] = dr * 2f

        // Cores por estado (feedback claro e suave)
        when {
            p > 0.02f -> { // confirmando — ciano → verde
                ring.color[0] = 0.43f + (0.49f - 0.43f) * p
                ring.color[1] = 0.90f + (1.00f - 0.90f) * p
                ring.color[2] = 1.00f + (0.70f - 1.00f) * p
                rayDraw.color[3] = 0.5f
            }
            target != null -> { // detectado — destaque
                ring.color[0] = 0.66f; ring.color[1] = 0.96f; ring.color[2] = 1.0f
                rayDraw.color[3] = 0.45f
            }
            else -> { // apontando livre
                ring.color[0] = 0.43f; ring.color[1] = 0.90f; ring.color[2] = 1.0f
                rayDraw.color[3] = 0.3f
            }
        }
        ring.color[3] = 0.95f

        // Linha/ray: da ponta do dedo até o alvo (ou até 60% do alcance)
        val end = if (target != null) hitDistance else MAX_RANGE * 0.6f
        pts[0] = tip[0] + dir[0] * 0.05f
        pts[1] = tip[1] + dir[1] * 0.05f
        pts[2] = tip[2] + dir[2] * 0.05f
        pts[3] = tip[0] + dir[0] * end
        pts[4] = tip[1] + dir[1] * end
        pts[5] = tip[2] + dir[2] * end
        rayLines.setPoints(pts, 2, android.opengl.GLES30.GL_LINES)
    }

    private fun setVisible(v: Boolean) {
        ring.visible = v
        progressDisc.visible = v
        rayDraw.visible = v
    }

    /** Alvo atualmente detectado (para rótulos/toasts). */
    fun currentTarget(): Interactable? = interaction.hovered
}

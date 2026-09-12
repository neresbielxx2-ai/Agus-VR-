package com.agusvr.interaction

import com.agusvr.runtime.gl.SceneObject
import com.agusvr.util.Intersect
import com.agusvr.util.M
import com.agusvr.util.Ray
import kotlin.math.cos
import kotlin.math.sin

/** Estados visuais de um alvo (feedback claro, sem estilo "painel militar"). */
enum class TargetState { NORMAL, DETECTED, SELECTING, OPENING }

/** Como a seleção foi confirmada. */
enum class SelectSource { APPROACH, DWELL, TOUCH }

/**
 * Agus Interaction — qualquer coisa que o ray pode detectar:
 * botões, apps, cards de menu, objetos do Agus Hand Lab, notas…
 */
interface Interactable {
    val id: String
    var enabled: Boolean
    val hoverLabel: String get() = ""
    val grabbable: Boolean get() = false
    val center: FloatArray

    /** Distância de interseção ou -1f. */
    fun rayHit(ray: Ray): Float

    // Callbacks
    fun onHoverStart() {}
    fun onHoverEnd() {}
    fun onSelect(source: SelectSource) {}
    fun onReleased() {}
    fun onGrabbed() {}

    /** Feedback visual por estado (implementação opcional). */
    fun applyState(state: TargetState, progress: Float) {}
}

/** Alvo esférico (smart objects, botões redondos). */
class SphereTarget(
    override val id: String,
    private val item: SceneObject,
    private val radius: Float,
    var onSelectAction: ((SelectSource) -> Unit)? = null
) : Interactable {
    override var enabled = true
    override var hoverLabel = id
    override val center: FloatArray get() = item.pos
    var baseEmissive = 0f

    override fun rayHit(ray: Ray): Float {
        if (!enabled) return -1f
        return Intersect.raySphere(ray, item.pos, radius)
    }

    override fun onSelect(source: SelectSource) {
        onSelectAction?.invoke(source)
    }

    override fun applyState(state: TargetState, progress: Float) {
        item.emissive = when (state) {
            TargetState.NORMAL -> baseEmissive
            TargetState.DETECTED -> 0.55f
            TargetState.SELECTING -> 0.55f + 0.45f * progress
            TargetState.OPENING -> 1.0f
        }
    }
}

/** Alvo em caixa (cubos do laboratório, props). */
class BoxTarget(
    override val id: String,
    val item: SceneObject,
    private val halfExtent: FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f),
    var onSelectAction: ((SelectSource) -> Unit)? = null
) : Interactable {
    override var enabled = true
    override var hoverLabel = id
    override var grabbable = true
    override val center: FloatArray get() = item.pos

    override fun rayHit(ray: Ray): Float {
        if (!enabled) return -1f
        val min = floatArrayOf(item.pos[0] - halfExtent[0], item.pos[1] - halfExtent[1], item.pos[2] - halfExtent[2])
        val max = floatArrayOf(item.pos[0] + halfExtent[0], item.pos[1] + halfExtent[1], item.pos[2] + halfExtent[2])
        return Intersect.rayAABB(ray, min, max)
    }

    override fun onSelect(source: SelectSource) {
        onSelectAction?.invoke(source)
    }

    override fun applyState(state: TargetState, progress: Float) {
        item.emissive = when (state) {
            TargetState.NORMAL -> 0f
            TargetState.DETECTED -> 0.35f
            TargetState.SELECTING -> 0.35f + 0.5f * progress
            TargetState.OPENING -> 0.9f
        }
    }
}

/** Alvo em quad (painel de UI com subelementos — botões, cards). */
class PanelTarget(
    override val id: String,
    val item: SceneObject,
    val width: Float,
    val height: Float,
    /** Resolve UV local (-0.5..0.5) para um subelemento (ou null). */
    val elementAt: (u: Float, v: Float) -> PanelElement?
) : Interactable {
    override var enabled = true
    override var hoverLabel = id
    override var grabbable = false
    override val center: FloatArray get() = item.pos
    private val uv = FloatArray(2)
    var hoveredElement: PanelElement? = null

    override fun rayHit(ray: Ray): Float {
        if (!enabled) return -1f
        return Intersect.rayQuad(ray, item.pos, item.yaw, width, height, uv)
    }

    /** UV do último acerto (para o painel pintar o hover). */
    fun lastUV(out: FloatArray) {
        out[0] = uv[0]; out[1] = uv[1]
    }

    fun updateHover(ray: Ray): PanelElement? {
        val d = rayHit(ray)
        if (d < 0f) { hoveredElement = null; return null }
        hoveredElement = elementAt(uv[0], uv[1])
        return hoveredElement
    }

    override fun applyState(state: TargetState, progress: Float) {
        // O painel desenha o próprio feedback no canvas
    }
}

/** Elemento dentro de um painel (botão, card, item de lista). */
class PanelElement(
    val id: String,
    var label: String,
    // retângulo em coordenadas locais do painel (-0.5..0.5)
    var x: Float, var y: Float, var w: Float, var h: Float
) {
    var enabled = true
    var accent = false
    var meta: String = ""
    var onSelect: ((PanelElement) -> Unit)? = null

    fun contains(u: Float, v: Float): Boolean =
        u >= x && u <= x + w && v >= y && v <= y + h
}

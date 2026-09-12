package com.agusvr.interaction

import com.agusvr.util.M
import com.agusvr.util.Ray

/**
 * Agus Interaction — regras de seleção por aproximação.
 *
 * Fluxo:
 *  1. Ray sobre o alvo por HOVER_CONFIRM_MS → alvo DETECTED (destaque).
 *  2. Palma da mão entra na janela de distância (MIN_APPROACH até
 *     selectDistance) enquanto continua apontando → progresso sobe até 1.
 *  3. Progresso completo → seleção confirmada (OPENING → ação executada).
 *
 * Anti clique-acidental:
 *  - hover precisa ser estável (debounce de entrada);
 *  - após uma seleção, o MESMO alvo entra em cooldown;
 *  - sair da janela de distância faz o progresso decair.
 */
class InteractionManager {

    companion object {
        const val HOVER_CONFIRM_MS = 260f      // debounce de hover
        const val APPROACH_CONFIRM_MS = 650f   // tempo para confirmar aproximação
        const val MIN_APPROACH_M = 0.04f       // mão colada no alvo não re-seleciona
        const val COOLDOWN_MS = 1000L          // pós-seleção do mesmo alvo
        const val OPENING_MS = 380f
    }

    var hovered: Interactable? = null
        private set
    var hoverMs = 0f
        private set
    var detected = false
        private set
    var progress = 0f
        private set
    var opening = false
        private set
    private var openingMs = 0f
    private var openingTarget: Interactable? = null

    private val cooldown = HashMap<String, Long>()

    /** Alvo cuja seleção foi confirmada (para o engine disparar som/ação). */
    var onSelectEvent: ((Interactable, SelectSource) -> Unit)? = null

    /** Ponto do acerto do ray no alvo (para medir a aproximação da mão). */
    val hitPoint = FloatArray(3)

    fun update(
        ray: Ray?,
        hit: Interactable?,
        hitDist: Float,
        palm: FloatArray?,
        dtMs: Float,
        selectDistance: Float
    ) {
        // Abertura em andamento (animação pós-seleção)
        if (opening) {
            openingMs += dtMs
            val t = (openingMs / OPENING_MS).coerceIn(0f, 1f)
            openingTarget?.applyState(TargetState.OPENING, t)
            if (openingMs >= OPENING_MS) {
                openingTarget?.applyState(TargetState.NORMAL, 0f)
                opening = false
                openingTarget = null
            }
        }

        // Troca ou perda de alvo
        if (hit !== hovered) {
            hovered?.onHoverEnd()
            hovered?.applyState(TargetState.NORMAL, 0f)
            hovered = hit
            hoverMs = 0f
            detected = false
            progress = 0f
            hit?.onHoverStart()
        }

        val target = hovered
        if (target == null || ray == null) {
            hoverMs = 0f; detected = false; progress = 0f
            return
        }

        // Ponto do acerto (para medir distância da mão)
        ray.pointAt(if (hitDist > 0f) hitDist else 1f, hitPoint)

        hoverMs += dtMs

        // Debounce: só considera "detectado" após hover estável
        if (!detected) {
            if (hoverMs >= HOVER_CONFIRM_MS) {
                detected = true
            } else {
                target.applyState(TargetState.NORMAL, 0f)
                return
            }
        }

        if (inCooldown(target)) {
            target.applyState(TargetState.DETECTED, 0f)
            progress = 0f
            return
        }

        // Seleção por aproximação: mão apontando + palma dentro da janela
        if (palm != null) {
            val palmDist = M.dist(palm, hitPoint)
            val inWindow = palmDist <= selectDistance && palmDist >= MIN_APPROACH_M
            if (inWindow) {
                progress += dtMs / APPROACH_CONFIRM_MS
            } else {
                progress -= dtMs / (APPROACH_CONFIRM_MS * 0.6f)
            }
            progress = progress.coerceIn(0f, 1f)
        }

        if (progress >= 1f) {
            // Confirma intenção
            cooldown[target.id] = System.currentTimeMillis() + COOLDOWN_MS
            opening = true
            openingMs = 0f
            openingTarget = target
            target.applyState(TargetState.OPENING, 0f)
            target.onSelect(SelectSource.APPROACH)
            onSelectEvent?.invoke(target, SelectSource.APPROACH)
            progress = 0f
            detected = false
            hoverMs = 0f
        } else {
            target.applyState(
                if (progress > 0.02f) TargetState.SELECTING else TargetState.DETECTED,
                progress
            )
        }
    }

    /** Seleção imediata (toque na tela / fallback gaze). */
    fun forceSelect(target: Interactable) {
        cooldown[target.id] = System.currentTimeMillis() + COOLDOWN_MS
        target.onSelect(SelectSource.TOUCH)
        onSelectEvent?.invoke(target, SelectSource.TOUCH)
    }

    private fun inCooldown(t: Interactable): Boolean {
        val until = cooldown[t.id] ?: return false
        if (System.currentTimeMillis() > until) {
            cooldown.remove(t.id)
            return false
        }
        return true
    }

    fun clear() {
        hovered?.onHoverEnd()
        hovered = null
        hoverMs = 0f; detected = false; progress = 0f
        opening = false; openingTarget = null
    }
}

/**
 * Agus Interaction — controlador de GRAB/RELEASE.
 *
 * Punho fechado (FIST) com um objeto "grabbable" perto da palma → segura.
 * Mão aberta (OPEN_HAND) ou soltar → RELEASE no ponto atual.
 */
class GrabController {

    var held: Interactable? = null
        private set
    private val grabOffset = FloatArray(3)
    var onGrab: ((Interactable) -> Unit)? = null
    var onRelease: ((Interactable) -> Unit)? = null

    fun update(
        fistActive: Boolean,
        openActive: Boolean,
        palm: FloatArray?,
        reach: Float,
        nearGrabbable: Interactable?,
        moveHeld: (Interactable, FloatArray) -> Unit
    ) {
        if (held == null) {
            if (fistActive && palm != null && nearGrabbable != null) {
                held = nearGrabbable
                grabOffset[0] = nearGrabbable.center[0] - palm[0]
                grabOffset[1] = nearGrabbable.center[1] - palm[1]
                grabOffset[2] = nearGrabbable.center[2] - palm[2]
                nearGrabbable.onGrabbed()
                onGrab?.invoke(nearGrabbable)
            }
        } else {
            if (openActive || palm == null) {
                val h = held!!
                held = null
                h.onReleased()
                onRelease?.invoke(h)
            } else if (fistActive) {
                val dest = FloatArray(3)
                dest[0] = palm[0] + grabOffset[0]
                dest[1] = palm[1] + grabOffset[1]
                dest[2] = palm[2] + grabOffset[2]
                moveHeld(held!!, dest)
            }
        }
    }

    fun drop() {
        held?.onReleased()
        held = null
    }
}

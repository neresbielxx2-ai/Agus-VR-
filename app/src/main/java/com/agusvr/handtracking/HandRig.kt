package com.agusvr.handtracking

import android.opengl.GLES30
import com.agusvr.runtime.gl.DynamicDraw
import com.agusvr.runtime.gl.DynamicLines
import com.agusvr.runtime.gl.MeshSet
import com.agusvr.runtime.gl.SceneObject

/**
 * Agus Hand Tracking — visualização 3D das mãos (modelo visual).
 *
 * 21 esferas por mão nas posições dos keypoints + ossos ligando as
 * articulações, tudo derivado do tracking real. Esquerda = ciano,
 * direita = violeta.
 */
class HandRig(meshes: MeshSet) {

    companion object {
        // Conexões padrão dos 21 landmarks (MediaPipe Hands)
        private val BONES = intArrayOf(
            0, 1, 1, 2, 2, 3, 3, 4,
            0, 5, 5, 6, 6, 7, 7, 8,
            5, 9, 9, 10, 10, 11, 11, 12,
            9, 13, 13, 14, 14, 15, 15, 16,
            13, 17, 17, 18, 18, 19, 19, 20,
            0, 17
        )
    }

    val leftItems = Array(21) { SceneObject(meshes.sphere, SceneObject.PROG_LIT) }
    val rightItems = Array(21) { SceneObject(meshes.sphere, SceneObject.PROG_LIT) }

    val bonesL = DynamicLines(64)
    val bonesR = DynamicLines(64)
    val drawL = DynamicDraw(bonesL).apply { color[0] = 0.43f; color[1] = 0.9f; color[2] = 1.0f; color[3] = 0.75f }
    val drawR = DynamicDraw(bonesR).apply { color[0] = 0.75f; color[1] = 0.55f; color[2] = 1.0f; color[3] = 0.75f }

    private val ptsL = FloatArray(BONES.size * 3)
    private val ptsR = FloatArray(BONES.size * 3)

    init {
        for (it in leftItems) {
            it.scale[0] = 0.016f; it.scale[1] = 0.016f; it.scale[2] = 0.016f
            it.color[0] = 0.43f; it.color[1] = 0.9f; it.color[2] = 1.0f; it.color[3] = 0.95f
            it.emissive = 0.35f
        }
        for (it in rightItems) {
            it.scale[0] = 0.016f; it.scale[1] = 0.016f; it.scale[2] = 0.016f
            it.color[0] = 0.75f; it.color[1] = 0.55f; it.color[2] = 1.0f; it.color[3] = 0.95f
            it.emissive = 0.35f
        }
    }

    fun register(opaque: ArrayList<SceneObject>, dynamics: ArrayList<DynamicDraw>) {
        for (it in leftItems) opaque.add(it)
        for (it in rightItems) opaque.add(it)
        dynamics.add(drawL)
        dynamics.add(drawR)
    }

    fun update(frame: HandFrame) {
        apply(frame.left, leftItems, ptsL, bonesL, drawL)
        apply(frame.right, rightItems, ptsR, bonesR, drawR)
    }

    private fun apply(
        st: HandState,
        items: Array<SceneObject>,
        pts: FloatArray,
        lines: DynamicLines,
        draw: DynamicDraw
    ) {
        val fresh = st.present && System.currentTimeMillis() - st.lastSeenMs < 600
        for (j in 0 until 21) {
            val it = items[j]
            it.visible = fresh
            if (fresh) {
                it.pos[0] = st.world[j][0]
                it.pos[1] = st.world[j][1]
                it.pos[2] = st.world[j][2]
                // Ponta do indicador maior (é o ponteiro)
                val s = if (j == 8) 0.024f else 0.014f
                it.scale[0] = s; it.scale[1] = s; it.scale[2] = s
            }
        }
        draw.visible = fresh
        if (fresh) {
            for (b in BONES.indices) {
                val w = st.world[BONES[b]]
                pts[b * 3] = w[0]; pts[b * 3 + 1] = w[1]; pts[b * 3 + 2] = w[2]
            }
            lines.setPoints(pts, BONES.size, GLES30.GL_LINES)
        }
    }
}

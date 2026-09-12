package com.agusvr.runtime

import android.opengl.GLES30
import com.agusvr.runtime.gl.DynamicDraw
import com.agusvr.runtime.gl.DynamicLines
import com.agusvr.runtime.gl.MeshSet
import com.agusvr.runtime.gl.SceneObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * Agus VR Runtime — ambiente base: grade de chão, horizonte e orbes
 * ambientes (efeitos). Visual escuro, limpo e futurista.
 */
class EnvironmentScene(private val meshes: MeshSet) {

    val grid = SceneObject(meshes.grid, SceneObject.PROG_UNLIT).apply {
        color[0] = 0.10f; color[1] = 0.16f; color[2] = 0.28f; color[3] = 0.55f
        depthWrite = false
    }

    val horizon = SceneObject(meshes.ring, SceneObject.PROG_UNLIT).apply {
        pitch = -Math.PI.toFloat() / 2f
        scale[0] = 30f; scale[1] = 30f
        color[0] = 0.43f; color[1] = 0.9f; color[2] = 1.0f; color[3] = 0.10f
        depthWrite = false
        sortBias = -10f
    }

    private val orbs = ArrayList<SceneObject>()
    private val orbPhase = ArrayList<Float>()
    private val orbBaseY = ArrayList<Float>()

    /** Pontos de estrelas (efeito). */
    val stars = DynamicLines(400)
    val starsDraw = DynamicDraw(stars).apply {
        color[0] = 0.75f; color[1] = 0.85f; color[2] = 1.0f; color[3] = 0.35f
    }

    var effectsOn = true
    var dim = 1f

    fun build(opaque: ArrayList<SceneObject>, transparent: ArrayList<SceneObject>, dynamics: ArrayList<DynamicDraw>) {
        transparent.add(grid)
        transparent.add(horizon)
        dynamics.add(starsDraw)

        val rnd = java.util.Random(7)
        for (i in 0 until 8) {
            val orb = SceneObject(meshes.sphere, SceneObject.PROG_LIT)
            val ang = rnd.nextFloat() * Math.PI.toFloat() * 2f
            val dist = 3.5f + rnd.nextFloat() * 4f
            orb.pos[0] = sin(ang) * dist
            orb.pos[1] = -1.0f + rnd.nextFloat() * 2.4f
            orb.pos[2] = -cos(ang) * dist
            val s = 0.03f + rnd.nextFloat() * 0.05f
            orb.scale[0] = s; orb.scale[1] = s; orb.scale[2] = s
            orb.color[0] = 0.43f; orb.color[1] = 0.9f; orb.color[2] = 1.0f
            orb.emissive = 0.8f
            opaque.add(orb)
            orbs.add(orb)
            orbPhase.add(rnd.nextFloat() * 10f)
            orbBaseY.add(orb.pos[1])
        }

        // Estrelas: pontos aleatórios na cúpula
        val pts = FloatArray(300 * 3)
        for (i in 0 until 300) {
            val az = rnd.nextFloat() * Math.PI.toFloat() * 2f
            val el = rnd.nextFloat() * 1.2f + 0.05f
            val r = 40f
            pts[i * 3] = sin(az) * cos(el) * r
            pts[i * 3 + 1] = sin(el) * r * 0.6f - 4f
            pts[i * 3 + 2] = -cos(az) * cos(el) * r
        }
        stars.setPoints(pts, 300, GLES30.GL_POINTS)
    }

    fun update(dtMs: Float, timeSec: Float) {
        for (i in orbs.indices) {
            val orb = orbs[i]
            orb.visible = effectsOn
            orb.pos[1] = orbBaseY[i] + sin(timeSec * 0.6f + orbPhase[i]) * 0.25f
        }
        grid.color[3] = 0.55f * dim
        horizon.color[3] = 0.10f * dim
        starsDraw.visible = effectsOn
        starsDraw.color[3] = 0.35f * dim
    }
}

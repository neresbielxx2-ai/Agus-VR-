package com.agusvr.runtime.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Agus VR Runtime — malhas estáticas e dinâmicas em VBOs.
 * Atributos: 0=posição, 1=normal, 2=uv (mesmo layout dos shaders).
 */
class Mesh {
    var posBuf = 0; var nrmBuf = 0; var uvBuf = 0
    var count = 0
    var mode: Int = GLES30.GL_TRIANGLES

    fun upload(pos: FloatArray, nrm: FloatArray?, uv: FloatArray?, drawMode: Int) {
        mode = drawMode
        count = pos.size / 3
        posBuf = makeBuf(pos)
        nrmBuf = if (nrm != null) makeBuf(nrm) else 0
        uvBuf = if (uv != null) makeBuf(uv) else 0
    }

    private fun makeBuf(data: FloatArray): Int {
        val bb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer().put(data)
        fb.position(0)
        val out = IntArray(1)
        GLES30.glGenBuffers(1, out, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, out[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        return out[0]
    }

    /** Liga os atributos deste mesh (programa já ativo). */
    fun bind() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, posBuf)
        GLES30.glEnableVertexAttribArray(Programs.A_POS)
        GLES30.glVertexAttribPointer(Programs.A_POS, 3, GLES30.GL_FLOAT, false, 0, 0)
        if (nrmBuf != 0) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, nrmBuf)
            GLES30.glEnableVertexAttribArray(Programs.A_NORMAL)
            GLES30.glVertexAttribPointer(Programs.A_NORMAL, 3, GLES30.GL_FLOAT, false, 0, 0)
        } else {
            GLES30.glDisableVertexAttribArray(Programs.A_NORMAL)
        }
        if (uvBuf != 0) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, uvBuf)
            GLES30.glEnableVertexAttribArray(Programs.A_UV)
            GLES30.glVertexAttribPointer(Programs.A_UV, 2, GLES30.GL_FLOAT, false, 0, 0)
        } else {
            GLES30.glDisableVertexAttribArray(Programs.A_UV)
        }
    }

    fun draw() {
        bind()
        GLES30.glDrawArrays(mode, 0, count)
    }
}

/** Linhas dinâmicas (ray do dedo, ossos da mão) regravadas a cada frame. */
class DynamicLines(private val maxPoints: Int) {
    private var buf = 0
    private val data = FloatArray(maxPoints * 3)
    var pointCount = 0
        private set
    var mode: Int = GLES30.GL_LINES

    fun ensureBuffer() {
        if (buf == 0) {
            val out = IntArray(1)
            GLES30.glGenBuffers(1, out, 0)
            buf = out[0]
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, null, GLES30.GL_DYNAMIC_DRAW)
        }
    }

    /** Substitui o conteúdo. [pts] = sequências de (x,y,z). */
    fun setPoints(pts: FloatArray, nPts: Int, drawMode: Int) {
        ensureBuffer()
        pointCount = nPts.coerceAtMost(maxPoints)
        mode = drawMode
        System.arraycopy(pts, 0, data, 0, (pointCount * 3).coerceAtMost(pts.size))
        val fb = ByteBuffer.allocateDirect(pointCount * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data, 0, pointCount * 3)
        fb.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, pointCount * 3 * 4, fb)
    }

    fun draw() {
        if (pointCount < 2 || buf == 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf)
        GLES30.glEnableVertexAttribArray(Programs.A_POS)
        GLES30.glVertexAttribPointer(Programs.A_POS, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDisableVertexAttribArray(Programs.A_NORMAL)
        GLES30.glDisableVertexAttribArray(Programs.A_UV)
        GLES30.glDrawArrays(mode, 0, pointCount)
    }
}

/** Fábrica de geometria procedural. */
object Meshes {

    fun cube(): Mesh {
        val p = ArrayList<Float>(216)
        val n = ArrayList<Float>(216)
        // faces: normal, 4 cantos
        val faces = arrayOf(
            floatArrayOf(0f, 0f, 1f,  -0.5f, -0.5f, 0.5f,  0.5f, -0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  -0.5f, 0.5f, 0.5f),
            floatArrayOf(0f, 0f, -1f,  0.5f, -0.5f, -0.5f,  -0.5f, -0.5f, -0.5f,  -0.5f, 0.5f, -0.5f,  0.5f, 0.5f, -0.5f),
            floatArrayOf(1f, 0f, 0f,  0.5f, -0.5f, 0.5f,  0.5f, -0.5f, -0.5f,  0.5f, 0.5f, -0.5f,  0.5f, 0.5f, 0.5f),
            floatArrayOf(-1f, 0f, 0f,  -0.5f, -0.5f, -0.5f,  -0.5f, -0.5f, 0.5f,  -0.5f, 0.5f, 0.5f,  -0.5f, 0.5f, -0.5f),
            floatArrayOf(0f, 1f, 0f,  -0.5f, 0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  0.5f, 0.5f, -0.5f,  -0.5f, 0.5f, -0.5f),
            floatArrayOf(0f, -1f, 0f,  -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f, 0.5f,  -0.5f, -0.5f, 0.5f)
        )
        for (f in faces) {
            val nx = f[0]; val ny = f[1]; val nz = f[2]
            val idx = intArrayOf(0, 1, 2, 0, 2, 3)
            for (i in idx) {
                p.add(f[3 + i * 3]); p.add(f[4 + i * 3]); p.add(f[5 + i * 3])
                n.add(nx); n.add(ny); n.add(nz)
            }
        }
        val m = Mesh()
        m.upload(p.toFloatArray(), n.toFloatArray(), null, GLES30.GL_TRIANGLES)
        return m
    }

    fun sphere(rings: Int = 16, sectors: Int = 24): Mesh {
        val p = ArrayList<Float>()
        val n = ArrayList<Float>()
        for (r in 0 until rings) {
            val phi0 = PI * r / rings
            val phi1 = PI * (r + 1) / rings
            for (s in 0 until sectors) {
                val t0 = 2.0 * PI * s / sectors
                val t1 = 2.0 * PI * (s + 1) / sectors
                val v00 = sph(phi0, t0); val v10 = sph(phi1, t0)
                val v11 = sph(phi1, t1); val v01 = sph(phi0, t1)
                if (r != 0) { p.addAll(v00); n.addAll(v00); p.addAll(v10); n.addAll(v10); p.addAll(v01); n.addAll(v01) }
                if (r != rings - 1) { p.addAll(v10); n.addAll(v10); p.addAll(v11); n.addAll(v11); p.addAll(v01); n.addAll(v01) }
            }
        }
        val m = Mesh()
        m.upload(p.toFloatArray(), n.toFloatArray(), null, GLES30.GL_TRIANGLES)
        return m
    }

    private fun sph(phi: Double, t: Double): FloatArray = floatArrayOf(
        (sin(phi) * cos(t)).toFloat(),
        cos(phi).toFloat(),
        (sin(phi) * sin(t)).toFloat()
    )

    /** Quad 1x1 no plano XY, normal +Z, UV 0..1. */
    fun quad(): Mesh {
        val p = floatArrayOf(
            -0.5f, -0.5f, 0f,  0.5f, -0.5f, 0f,  0.5f, 0.5f, 0f,
            -0.5f, -0.5f, 0f,  0.5f, 0.5f, 0f,  -0.5f, 0.5f, 0f
        )
        val n = FloatArray(18)
        for (i in 0 until 6) { n[i * 3 + 2] = 1f }
        val uv = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 0f)
        val m = Mesh()
        m.upload(p, n, uv, GLES30.GL_TRIANGLES)
        return m
    }

    /** Anel (coroa) no plano XY — o círculo da ponta do dedo. */
    fun ring(segments: Int = 48, inner: Float = 0.62f): Mesh {
        val p = ArrayList<Float>()
        val n = ArrayList<Float>()
        for (s in 0 until segments) {
            val a0 = 2.0 * PI * s / segments
            val a1 = 2.0 * PI * (s + 1) / segments
            val o0 = floatArrayOf(cos(a0).toFloat(), sin(a0).toFloat(), 0f)
            val o1 = floatArrayOf(cos(a1).toFloat(), sin(a1).toFloat(), 0f)
            val i0 = floatArrayOf(o0[0] * inner, o0[1] * inner, 0f)
            val i1 = floatArrayOf(o1[0] * inner, o1[1] * inner, 0f)
            p.addAll(i0); p.addAll(o0); p.addAll(o1)
            p.addAll(i0); p.addAll(o1); p.addAll(i1)
            for (k in 0 until 6) n.addAll(listOf(0f, 0f, 1f))
        }
        val m = Mesh()
        m.upload(p.toFloatArray(), n.toFloatArray(), null, GLES30.GL_TRIANGLES)
        return m
    }

    /** Disco preenchido (feedback de progresso de seleção). */
    fun disc(segments: Int = 48): Mesh {
        val p = ArrayList<Float>()
        val n = ArrayList<Float>()
        for (s in 0 until segments) {
            val a0 = 2.0 * PI * s / segments
            val a1 = 2.0 * PI * (s + 1) / segments
            p.addAll(listOf(0f, 0f, 0f))
            p.add(cos(a0).toFloat()); p.add(sin(a0).toFloat()); p.add(0f)
            p.add(cos(a1).toFloat()); p.add(sin(a1).toFloat()); p.add(0f)
            for (k in 0 until 3) n.addAll(listOf(0f, 0f, 1f))
        }
        val m = Mesh()
        m.upload(p.toFloatArray(), n.toFloatArray(), null, GLES30.GL_TRIANGLES)
        return m
    }

    /** Grade de chão em linhas (y=0). */
    fun grid(extent: Float = 10f, step: Float = 1f): Mesh {
        val pts = ArrayList<Float>()
        var v = -extent
        while (v <= extent + 0.001f) {
            pts.addAll(listOf(v, 0f, -extent, v, 0f, extent))
            pts.addAll(listOf(-extent, 0f, v, extent, 0f, v))
            v += step
        }
        val m = Mesh()
        m.upload(pts.toFloatArray(), null, null, GLES30.GL_LINES)
        return m
    }
}

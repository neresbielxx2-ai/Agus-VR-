package com.agusvr.runtime.gl

import android.opengl.Matrix

/**
 * Agus VR Runtime — objeto de cena renderizável.
 * Mantém transformação, material mínimo e flags de ordenação.
 */
class SceneObject(
    val mesh: Mesh,
    var program: Int = PROG_LIT
) {
    companion object {
        const val PROG_LIT = 0
        const val PROG_UNLIT = 1
        const val PROG_TEX = 2
        const val PROG_EXT = 3
    }

    val pos = FloatArray(3)
    var yaw = 0f
    var pitch = 0f
    var roll = 0f
    val scale = floatArrayOf(1f, 1f, 1f)

    /** Cor RGBA (alpha usado em transparentes). */
    val color = floatArrayOf(1f, 1f, 1f, 1f)
    var emissive = 0f

    var billboard = false
    var depthWrite = true
    var visible = true
    var textureId = 0
    /** Viés de ordenação para transparentes (maior = desenhado depois). */
    var sortBias = 0f

    val model = FloatArray(16)
    private val tmp = FloatArray(16)

    /** Recalcula a matriz modelo. Chamado pelo renderer a cada frame. */
    fun computeModel(eyePos: FloatArray, eyeRight: FloatArray, eyeUp: FloatArray) {
        if (billboard) {
            // Olhando para a câmera
            val zx = eyePos[0] - pos[0]
            val zy = eyePos[1] - pos[1]
            val zz = eyePos[2] - pos[2]
            val zl = kotlin.math.sqrt(zx * zx + zy * zy + zz * zz).coerceAtLeast(1e-6f)
            val fx = eyeRight[1] * eyeUp[2] - eyeRight[2] * eyeUp[1]
            // base ortonormal: x=right, y=up, z=normalizada(pos→olho)
            model[0] = eyeRight[0] * scale[0]; model[1] = eyeRight[1] * scale[0]; model[2] = eyeRight[2] * scale[0]; model[3] = 0f
            model[4] = eyeUp[0] * scale[1]; model[5] = eyeUp[1] * scale[1]; model[6] = eyeUp[2] * scale[1]; model[7] = 0f
            model[8] = zx / zl * scale[2]; model[9] = zy / zl * scale[2]; model[10] = zz / zl * scale[2]; model[11] = 0f
            model[12] = pos[0]; model[13] = pos[1]; model[14] = pos[2]; model[15] = 1f
            return
        }
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, pos[0], pos[1], pos[2])
        if (yaw != 0f) Matrix.rotateM(model, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        if (pitch != 0f) Matrix.rotateM(model, 0, Math.toDegrees(pitch.toDouble()).toFloat(), 1f, 0f, 0f)
        if (roll != 0f) Matrix.rotateM(model, 0, Math.toDegrees(roll.toDouble()).toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(model, 0, scale[0], scale[1], scale[2])
    }
}

/** Desenho dinâmico de linhas (ray do dedo, ossos da mão). */
class DynamicDraw(val lines: DynamicLines) {
    val color = floatArrayOf(1f, 1f, 1f, 1f)
    var visible = true
}

/** Textura 2D criada a partir de um Canvas/Bitmap (UI do Agus VR). */
class CanvasTexture {
    var texId = 0
        private set
    var bitmap: android.graphics.Bitmap? = null
        private set
    var dirty = false

    fun setBitmap(bmp: android.graphics.Bitmap) {
        bitmap = bmp
        dirty = true
    }

    /** Chamado na thread GL. */
    fun uploadIfNeeded() {
        val bmp = bitmap ?: return
        if (texId == 0) {
            val out = IntArray(1)
            android.opengl.GLES30.glGenTextures(1, out, 0)
            texId = out[0]
            android.opengl.GLES30.glBindTexture(android.opengl.GLES30.GL_TEXTURE_2D, texId)
            android.opengl.GLES30.glTexParameteri(android.opengl.GLES30.GL_TEXTURE_2D, android.opengl.GLES30.GL_TEXTURE_MIN_FILTER, android.opengl.GLES30.GL_LINEAR)
            android.opengl.GLES30.glTexParameteri(android.opengl.GLES30.GL_TEXTURE_2D, android.opengl.GLES30.GL_TEXTURE_MAG_FILTER, android.opengl.GLES30.GL_LINEAR)
            android.opengl.GLES30.glTexParameteri(android.opengl.GLES30.GL_TEXTURE_2D, android.opengl.GLES30.GL_TEXTURE_WRAP_S, android.opengl.GLES30.GL_CLAMP_TO_EDGE)
            android.opengl.GLES30.glTexParameteri(android.opengl.GLES30.GL_TEXTURE_2D, android.opengl.GLES30.GL_TEXTURE_WRAP_T, android.opengl.GLES30.GL_CLAMP_TO_EDGE)
            android.opengl.GLUtils.texImage2D(android.opengl.GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            dirty = false
        } else if (dirty) {
            android.opengl.GLES30.glBindTexture(android.opengl.GLES30.GL_TEXTURE_2D, texId)
            synchronized(bmp) {
                android.opengl.GLUtils.texSubImage2D(android.opengl.GLES30.GL_TEXTURE_2D, 0, 0, 0, bmp)
            }
            dirty = false
        }
    }

    fun destroy() {
        if (texId != 0) {
            android.opengl.GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
            texId = 0
        }
    }
}

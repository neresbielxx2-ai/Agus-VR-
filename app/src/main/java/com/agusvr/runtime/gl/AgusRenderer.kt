package com.agusvr.runtime.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.agusvr.runtime.VrEngine
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tan
import kotlin.math.atan
import kotlin.math.PI

/**
 * Agus VR Runtime — renderer estéreo side-by-side.
 *
 * Renderiza a cena duas vezes (olho esquerdo/direito deslocados pelo IPD)
 * e aplica escala de resolução via FBO quando resolutionScale < 1
 * (controle real de resolução para desempenho).
 */
class AgusRenderer(private val engine: VrEngine) : GLSurfaceView.Renderer {

    val programs = Programs()
    lateinit var meshes: MeshSet

    var screenW = 0
        private set
    var screenH = 0
        private set

    /** Escala de resolução efetiva (settings × adaptive quality). */
    var renderScale = 1f

    private val fbo = ScaledFbo()

    // Matrizes por frame
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)

    // Base do olho atual (para billboards)
    val eyePos = FloatArray(3)
    val eyeRight = FloatArray(3)
    val eyeUp = FloatArray(3)
    val eyeFwd = FloatArray(3)

    private var lastFrameNs = 0L
    var fpsCap = 0

    // ------------------------------------------------------------------ init
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.02f, 0.03f, 0.06f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        programs.compile()
        meshes = MeshSet()
        meshes.uploadAll()
        engine.onGlReady()
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenW = width
        screenH = height
        fbo.release()
    }

    // ------------------------------------------------ frame
    override fun onDrawFrame(gl: GL10?) {
        // Upload de texturas de UI pendentes (canvas → GPU)
        for (t in engine.textures) t.uploadIfNeeded()

        val now = System.nanoTime()
        var dtMs = (now - lastFrameNs) / 1_000_000f
        lastFrameNs = now
        if (dtMs > 250f) dtMs = 16f

        // FPS cap (o GLSurfaceView entrega na taxa do display; dormimos o resto)
        if (fpsCap in 10..120) {
            val targetMs = 1000f / fpsCap
            if (dtMs < targetMs - 1.5f) {
                try { Thread.sleep((targetMs - dtMs).toLong()) } catch (_: InterruptedException) {}
                lastFrameNs = System.nanoTime()
                dtMs = targetMs
            }
        }

        engine.tick(dtMs)

        val scale = renderScale.coerceIn(0.4f, 1.0f)
        val fw = ((screenW * scale).toInt()).coerceAtLeast(64)
        val fh = ((screenH * scale).toInt()).coerceAtLeast(64)

        if (scale < 0.999f) {
            fbo.ensure(fw, fh)
            fbo.bind()
            renderScene(fw, fh)
            fbo.blitToScreen(screenW, screenH)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            renderScene(screenW, screenH)
        }
    }

    private fun renderScene(fw: Int, fh: Int) {
        GLES30.glViewport(0, 0, fw, fh)
        GLES30.glClearColor(engine.sky[0], engine.sky[1], engine.sky[2], 1f)

        val half = fw / 2
        renderEye(0, 0, fw, fh)          // olho esquerdo
        renderEye(1, half, fw, fh)        // olho direito
    }

    private fun renderEye(eye: Int, xOff: Int, fw: Int, fh: Int) {
        GLES30.glViewport(xOff, 0, fw / 2, fh)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val ht = engine.head
        val ipd = engine.ipd
        val side = if (eye == 0) -0.5f else 0.5f

        eyePos[0] = ht.forward[0] * 0f + ht.right[0] * ipd * side
        eyePos[1] = ht.right[1] * ipd * side + 0f
        eyePos[2] = ht.right[2] * ipd * side
        eyePos[1] += 0f // origem na cabeça (3DoF)

        eyeFwd[0] = ht.forward[0]; eyeFwd[1] = ht.forward[1]; eyeFwd[2] = ht.forward[2]
        eyeUp[0] = ht.up[0]; eyeUp[1] = ht.up[1]; eyeUp[2] = ht.up[2]
        eyeRight[0] = ht.right[0]; eyeRight[1] = ht.right[1]; eyeRight[2] = ht.right[2]

        Matrix.setLookAtM(
            view, 0,
            eyePos[0], eyePos[1], eyePos[2],
            eyePos[0] + eyeFwd[0], eyePos[1] + eyeFwd[1], eyePos[2] + eyeFwd[2],
            eyeUp[0], eyeUp[1], eyeUp[2]
        )
        val aspect = (fw / 2f) / fh
        val fovy = 70f
        Matrix.perspectiveM(proj, 0, fovy, aspect, 0.04f, 80f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        // 1) Passthrough da câmera (fundo)
        if (engine.passthroughTex != 0) {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            drawPassthrough()
            GLES30.glDepthMask(true)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        }

        // 2) Opacos
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        for (item in engine.opaque) {
            if (item.visible) drawItem(item)
        }

        // 3) Transparentes + linhas dinâmicas (blend, sem escrita de depth)
        val tr = engine.transparent
        val dyn = engine.dynamics
        if (tr.isNotEmpty() || dyn.isNotEmpty()) {
            if (tr.size > 1) {
                tr.sortWith(Comparator { a, b ->
                    val da = dist2(a); val db = dist2(b)
                    if (kotlin.math.abs(da - db) > 0.01f) db.compareTo(da) else a.sortBias.compareTo(b.sortBias)
                })
            }
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)
            for (item in tr) {
                if (item.visible) drawItem(item)
            }
            for (d in dyn) {
                if (!d.visible) continue
                GLES30.glUseProgram(programs.unlit)
                GLES30.glUniformMatrix4fv(programs.unlitMvp, 1, false, vp, 0)
                GLES30.glUniform4f(programs.unlitColor, d.color[0], d.color[1], d.color[2], d.color[3])
                d.lines.draw()
            }
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    private fun dist2(o: SceneObject): Float {
        val dx = o.pos[0] - eyePos[0]; val dy = o.pos[1] - eyePos[1]; val dz = o.pos[2] - eyePos[2]
        return dx * dx + dy * dy + dz * dz
    }

    private fun drawItem(item: SceneObject) {
        item.computeModel(eyePos, eyeRight, eyeUp)
        Matrix.multiplyMM(mvp, 0, vp, 0, item.model, 0)
        when (item.program) {
            SceneObject.PROG_LIT -> {
                GLES30.glUseProgram(programs.lit)
                GLES30.glUniformMatrix4fv(programs.litMvp, 1, false, mvp, 0)
                GLES30.glUniformMatrix4fv(programs.litModel, 1, false, item.model, 0)
                GLES30.glUniform4f(programs.litColor, item.color[0], item.color[1], item.color[2], item.color[3])
                GLES30.glUniform3f(programs.litLight, -0.35f, -1f, -0.45f)
                GLES30.glUniform1f(programs.litEmissive, item.emissive)
                GLES30.glUniform3f(programs.litEye, eyePos[0], eyePos[1], eyePos[2])
                GLES30.glUniform3f(programs.litFogColor, engine.sky[0], engine.sky[1], engine.sky[2])
                GLES30.glUniform1f(programs.litFogDensity, engine.fogDensity)
                item.mesh.draw()
            }
            SceneObject.PROG_UNLIT -> {
                GLES30.glUseProgram(programs.unlit)
                GLES30.glUniformMatrix4fv(programs.unlitMvp, 1, false, mvp, 0)
                GLES30.glUniform4f(programs.unlitColor, item.color[0], item.color[1], item.color[2], item.color[3])
                item.mesh.draw()
            }
            SceneObject.PROG_TEX -> {
                val tex = item.textureId
                if (tex == 0) return
                GLES30.glUseProgram(programs.tex)
                GLES30.glUniformMatrix4fv(programs.texMvp, 1, false, mvp, 0)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
                GLES30.glUniform1i(programs.texSampler, 0)
                GLES30.glUniform1f(programs.texAlpha, item.color[3])
                item.mesh.draw()
            }
        }
    }

    private fun drawPassthrough() {
        GLES30.glUseProgram(programs.ext)
        // Quad em NDC cobrindo a tela inteira
        Matrix.setIdentityM(mvp, 0)
        Matrix.scaleM(mvp, 0, 2f, 2f, 1f)
        GLES30.glUniformMatrix4fv(programs.extMvp, 1, false, mvp, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(0x8D65 /*GL_TEXTURE_EXTERNAL_OES*/, engine.passthroughTex)
        GLES30.glUniform1i(programs.extSampler, 0)
        GLES30.glUniform1f(programs.extAlpha, 1f)
        GLES30.glUniformMatrix4fv(programs.extTexMat, 1, false, engine.passthroughTexMat, 0)
        meshes.ndcQuad.draw()
    }
}

/** Conjunto de malhas padrão do runtime. */
class MeshSet {
    lateinit var cube: Mesh
    lateinit var sphere: Mesh
    lateinit var sphereSmall: Mesh
    lateinit var quad: Mesh
    lateinit var ring: Mesh
    lateinit var disc: Mesh
    lateinit var grid: Mesh
    lateinit var ndcQuad: Mesh

    fun uploadAll() {
        cube = Meshes.cube()
        sphere = Meshes.sphere(14, 20)
        sphereSmall = sphere
        quad = Meshes.quad()
        ring = Meshes.ring()
        disc = Meshes.disc()
        grid = Meshes.grid(12f, 1f)
        ndcQuad = Mesh().also {
            it.upload(
                floatArrayOf(
                    -1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f,
                    -1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f
                ),
                null,
                floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f),
                GLES30.GL_TRIANGLES
            )
        }
    }
}

/** FBO com textura de cor + depth renderbuffer para escala de resolução. */
class ScaledFbo {
    private var fbo = 0
    private var tex = 0
    private var depth = 0
    private var w = 0
    private var h = 0

    fun ensure(nw: Int, nh: Int) {
        if (fbo != 0 && w == nw && h == nh) return
        release()
        w = nw; h = nh
        val out = IntArray(1)

        GLES30.glGenFramebuffers(1, out, 0); fbo = out[0]
        GLES30.glGenTextures(1, out, 0); tex = out[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glGenRenderbuffers(1, out, 0); depth = out[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depth)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, w, h)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depth)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
    }

    fun blitToScreen(sw: Int, sh: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
        GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, sw, sh, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_LINEAR)
    }

    fun release() {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (tex != 0) GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
        if (depth != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(depth), 0)
        fbo = 0; tex = 0; depth = 0; w = 0; h = 0
    }
}

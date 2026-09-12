package com.agusvr.runtime

import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.agusvr.R
import com.agusvr.runtime.gl.AgusRenderer

/**
 * Agus VR Runtime — atividade que hospeda o motor VR.
 *
 * GLSurfaceView (GLES 3.0) + touch lateral + imersivo total.
 * Boot protegido: qualquer falha é registrada (CrashLog) e, se a última
 * sessão morreu antes do 1º frame, esta sessão entra em modo seguro
 * (sem câmera/MediaPipe) em vez de repetir o crash.
 */
class VrActivity : AppCompatActivity() {

    private lateinit var engine: VrEngine
    private lateinit var glView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        BootGuard.mark(this, "activity.onCreate")
        CrashLog.logMessage(this, "boot", "VrActivity onCreate (safeMode=${BootGuard.safeMode})")

        try {
            engine = VrEngine(this)
            BootGuard.mark(this, "engine.construido")
            engine.init()
            BootGuard.mark(this, "engine.init")

            glView = GLSurfaceView(this)
            glView.setEGLContextClientVersion(3)
            // Sem chooser explícito: deixa o GLSurfaceView escolher a config
            // EGL mais compatível com o GPU do aparelho.

            val renderer = AgusRenderer(engine)
            engine.attachRenderer(renderer)
            glView.setRenderer(renderer)
            glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

            glView.setOnTouchListener { _, ev: MotionEvent ->
                engine.touch.onTouchEvent(ev)
                true
            }

            // IMPORTANTE: setContentView ANTES de immersive(). O modo
            // imersivo usa window.insetsController, que exige o DecorView
            // já criado — chamá-lo antes do setContentView dava
            // NullPointerException no Android 11+.
            setContentView(glView)
            BootGuard.mark(this, "glview.anexada")
            immersive()

            // O hand tracking só inicia DEPOIS do primeiro frame renderizado
            // (engine.onFirstFrame). Assim o menu VR sempre aparece antes, e
            // se o pipeline de câmera/MediaPipe cair sabemos exatamente onde.
            engine.safeModeSession = BootGuard.safeMode
            if (BootGuard.safeMode) {
                engine.toast(
                    "Modo seguro: hand tracking desativado nesta sessão " +
                    "(parou em: ${BootGuard.failurePoint.ifEmpty { BootGuard.lastPhase }})", 7000
                )
                CrashLog.logMessage(this, "boot", "modo seguro ativo — câmera/MediaPipe pulados")
            }
        } catch (t: Throwable) {
            CrashLog.log(this, "VrActivity.onCreate", t)
            showFatalView(t)
        }
    }

    private fun showFatalView(t: Throwable) {
        try {
            val tv = TextView(this)
            tv.setBackgroundColor(resources.getColor(R.color.agus_bg, theme))
            tv.setTextColor(resources.getColor(R.color.agus_text, theme))
            tv.setPadding(48, 96, 48, 48)
            tv.textSize = 14f
            tv.text = "O motor VR falhou ao iniciar.\n\n" +
                    "Erro: ${t.javaClass.simpleName}\n${t.message}\n\n" +
                    "O diagnóstico foi salvo — abra a tela inicial do Agus VR " +
                    "para ver os detalhes."
            tv.setOnClickListener { finish() }
            setContentView(tv)
        } catch (_: Throwable) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            immersive()
            if (::glView.isInitialized) glView.onResume()
            if (::engine.isInitialized) engine.head.start()
        } catch (t: Throwable) {
            CrashLog.log(this, "VrActivity.onResume", t)
        }
    }

    override fun onPause() {
        try {
            if (::engine.isInitialized) engine.head.stop()
            if (::glView.isInitialized) glView.onPause()
        } catch (t: Throwable) {
            CrashLog.log(this, "VrActivity.onPause", t)
        }
        super.onPause()
    }

    override fun onDestroy() {
        try { if (::engine.isInitialized) engine.shutdown() } catch (_: Throwable) {}
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        try {
            if (::engine.isInitialized) {
                when {
                    engine.appActive -> engine.goHome()
                    engine.menuVisible -> finish()
                    else -> engine.toggleMenu()
                }
                return
            }
        } catch (_: Throwable) {}
        finish()
        // Intencionalmente SEM super: navegação controlada pelo runtime.
    }

    private fun immersive() {
        try {
            // Garante que o DecorView exista antes de tocar nos insets —
            // insetsController sem DecorView dá NPE no Android 11+.
            window.decorView
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.let {
                    it.hide(android.view.WindowInsets.Type.systemBars())
                    it.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            }
        } catch (_: Throwable) {
            // Modo imersivo é cosmético — nunca deve derrubar o motor.
        }
    }
}

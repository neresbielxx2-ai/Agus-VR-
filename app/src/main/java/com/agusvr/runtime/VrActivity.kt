package com.agusvr.runtime

import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.agusvr.runtime.gl.AgusRenderer

/**
 * Agus VR Runtime — atividade que hospeda o motor VR.
 *
 * GLSurfaceView (GLES 3.0) + touch lateral + imersivo total.
 * O ciclo de vida da câmera acompanha esta atividade via CameraX.
 */
class VrActivity : AppCompatActivity() {

    private lateinit var engine: VrEngine
    private lateinit var glView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        CrashLog.install(this)

        engine = VrEngine(this)
        engine.init()

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(3)
        // Sem chooser explícito: deixa o GLSurfaceView escolher a config EGL
        // mais compatível com o GPU do aparelho (evita "No configs match").

        val renderer = AgusRenderer(engine)
        engine.attachRenderer(renderer)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        glView.setOnTouchListener { _, ev: MotionEvent ->
            engine.touch.onTouchEvent(ev)
            true
        }

        setContentView(glView)

        // Hand tracking real (câmera + MediaPipe), se disponível/permitido
        engine.startHands()
    }

    override fun onResume() {
        super.onResume()
        immersive()
        glView.onResume()
        engine.head.start()
    }

    override fun onPause() {
        engine.head.stop()
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        engine.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            engine.appActive -> engine.goHome()
            engine.menuVisible -> finish()
            else -> engine.toggleMenu()
        }
        // Intencionalmente SEM super: navegação controlada pelo runtime.
    }

    private fun immersive() {
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
    }
}

package com.agusvr.input

import android.view.MotionEvent

/**
 * Agus Input — entrada por toque na lateral do headset / celular fora do
 * headset. Reconhece: tap simples, tap duplo e long-press.
 * Usado como fallback de seleção quando o hand tracking está indisponível
 * e como atalho de menu/recenter.
 */
class TouchInput {

    var onTap: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastTapTime = 0L
    private var longFired = false

    fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                downX = ev.x; downY = ev.y
                longFired = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX; val dy = ev.y - downY
                if (!longFired && System.currentTimeMillis() - downTime > 550 &&
                    dx * dx + dy * dy < 900f
                ) {
                    longFired = true
                    onLongPress?.invoke()
                }
            }
            MotionEvent.ACTION_UP -> {
                val dt = System.currentTimeMillis() - downTime
                val moved = (ev.x - downX) * (ev.x - downX) + (ev.y - downY) * (ev.y - downY)
                if (!longFired && dt < 300 && moved < 900f) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 320) {
                        onDoubleTap?.invoke()
                        lastTapTime = 0
                    } else {
                        lastTapTime = now
                        onTap?.invoke()
                    }
                }
            }
        }
        return true
    }
}

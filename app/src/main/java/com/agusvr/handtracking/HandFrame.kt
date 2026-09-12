package com.agusvr.handtracking

/**
 * Agus Hand Tracking — modelo de dados de uma mão em um frame.
 *
 * `world[i]`  → posição 3D do keypoint i no espaço do mundo (metros),
 *               já transformada pela orientação da cabeça.
 * `norm[i]`   → posição normalizada na imagem da câmera (x,y) + z relativo.
 */
class HandState {
    companion object {
        const val SIDE_LEFT = 0
        const val SIDE_RIGHT = 1
    }

    var present = false
    var side = SIDE_RIGHT
    var score = 0f

    var gesture = Gesture.NONE
    var lastGestureChange = 0L

    val norm = Array(21) { FloatArray(3) }
    val world = Array(21) { FloatArray(3) }
    private val smooth = Array(21) { FloatArray(3) }
    private var hasSmooth = false

    val palm = FloatArray(3)        // centro da palma (média 0,5,9,13,17)
    val indexTip = FloatArray(3)
    val indexDir = FloatArray(3)    // direção do indicador (aponta para frente)
    var pinchDistance = 0f
    var imageHandSize = 0f
    var estimatedDistance = 0f

    var lastSeenMs = 0L

    /** Aplica suavização exponencial nas posições do mundo. */
    fun smoothWorld(dt: Float) {
        if (!hasSmooth || dt > 0.5f) {
            for (i in 0 until 21) {
                smooth[i][0] = world[i][0]; smooth[i][1] = world[i][1]; smooth[i][2] = world[i][2]
            }
            hasSmooth = true
            return
        }
        // taxa ~0.45/frame a 60fps: resposta rápida sem tremor visível
        val r = 0.45f
        val t = 1f - (1f - r).let { base -> kotlin.math.exp(dt * 60f * kotlin.math.ln(base)) }
        for (i in 0 until 21) {
            smooth[i][0] += (world[i][0] - smooth[i][0]) * t
            smooth[i][1] += (world[i][1] - smooth[i][1]) * t
            smooth[i][2] += (world[i][2] - smooth[i][2]) * t
        }
    }

    fun copySmoothedOut() {
        if (!hasSmooth) return
        for (i in 0 until 21) {
            world[i][0] = smooth[i][0]; world[i][1] = smooth[i][1]; world[i][2] = smooth[i][2]
        }
        // Recalcula derivados suavizados
        palm[0] = (world[0][0] + world[5][0] + world[9][0] + world[13][0] + world[17][0]) / 5f
        palm[1] = (world[0][1] + world[5][1] + world[9][1] + world[13][1] + world[17][1]) / 5f
        palm[2] = (world[0][2] + world[5][2] + world[9][2] + world[13][2] + world[17][2]) / 5f
        indexTip[0] = world[8][0]; indexTip[1] = world[8][1]; indexTip[2] = world[8][2]
        var dx = world[8][0] - world[6][0]
        var dy = world[8][1] - world[6][1]
        var dz = world[8][2] - world[6][2]
        val l = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
        indexDir[0] = dx / l; indexDir[1] = dy / l; indexDir[2] = dz / l
    }
}

/**
 * Estado completo do hand tracking em um frame.
 */
class HandFrame {
    val left = HandState()
    val right = HandState()
    var timestamp = 0L
    var processingMs = 0f

    /** Estado do pipeline (exibido na UI, sem esconder limitações). */
    var enabled = false
    var modelReady = false
    var cameraGranted = false

    fun hand(side: Int): HandState = if (side == HandState.SIDE_LEFT) left else right

    /** Mão que está apontando (prioridade direita; ignora dados velhos). */
    fun pointingHand(): HandState? {
        val now = System.currentTimeMillis()
        if (right.present && now - right.lastSeenMs < 600 && right.gesture == Gesture.POINT) return right
        if (left.present && now - left.lastSeenMs < 600 && left.gesture == Gesture.POINT) return left
        return null
    }

    fun anyPresent(): Boolean = left.present || right.present
}

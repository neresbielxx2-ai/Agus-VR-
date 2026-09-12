package com.agusvr.handtracking

import kotlin.math.sqrt

/**
 * Agus Hand Tracking — gestos reconhecidos a partir dos 21 keypoints.
 * Nenhum gesto é animado/simulado: todos derivam das posições reais dos
 * landmarks detectados pelo MediaPipe.
 */
enum class Gesture {
    NONE,        // mão irreconhecível / outro gesto
    OPEN_HAND,   // mão aberta (todos os dedos estendidos)
    FIST,        // punho fechado
    POINT,       // apontar somente com o indicador
    PINCH,       // polegar + indicador juntos
    TWO          // indicador + médio (V)
}

/**
 * Classificador geométrico de gestos.
 * Usa distâncias relativas normalizadas pelo tamanho da mão na imagem,
 * o que torna o reconhecimento independente da distância da câmera.
 *
 * Índices dos landmarks (MediaPipe Hands):
 *  0 punho · 4 ponta polegar · 8 ponta indicador · 12 ponta médio ·
 *  16 ponta anelar · 20 ponta mindinho · 9 base do médio
 */
object GestureClassifier {

    private fun dist(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Tamanho de referência da mão: punho→base do médio (imagem normalizada). */
    fun handSize(n: Array<FloatArray>): Float = dist(n[0], n[9]).coerceAtLeast(1e-4f)

    /** Distância polegar-indicador normalizada pelo tamanho da mão. */
    fun pinchDistance(n: Array<FloatArray>): Float = dist(n[4], n[8]) / handSize(n)

    private fun fingerExtended(n: Array<FloatArray>, tip: Int, pip: Int): Boolean {
        // Dedo estendido: ponta mais distante do punho que a articulação PIP
        return dist(n[tip], n[0]) > dist(n[pip], n[0]) * 1.08f
    }

    private fun thumbExtended(n: Array<FloatArray>): Boolean {
        // Polegar aberto: ponta longe da base do mindinho
        return dist(n[4], n[17]) > dist(n[3], n[17]) * 1.05f
    }

    /** Classifica a mão. [n] = 21 landmarks normalizados (x,y,z). */
    fun classify(n: Array<FloatArray>): Gesture {
        val hs = handSize(n)
        if (hs <= 1e-4f) return Gesture.NONE

        val indexExt = fingerExtended(n, 8, 6)
        val middleExt = fingerExtended(n, 12, 10)
        val ringExt = fingerExtended(n, 16, 14)
        val pinkyExt = fingerExtended(n, 20, 18)
        val thumbExt = thumbExtended(n)
        val pinch = pinchDistance(n)

        // PINCH: polegar e indicador tocando (médio não estendido)
        if (pinch < 0.30f && !middleExt) return Gesture.PINCH

        val extCount = (if (indexExt) 1 else 0) + (if (middleExt) 1 else 0) +
                (if (ringExt) 1 else 0) + (if (pinkyExt) 1 else 0)

        return when {
            extCount == 0 -> Gesture.FIST
            indexExt && !middleExt && !ringExt && !pinkyExt -> Gesture.POINT
            indexExt && middleExt && !ringExt && !pinkyExt -> Gesture.TWO
            extCount == 4 && thumbExt -> Gesture.OPEN_HAND
            extCount >= 3 && thumbExt -> Gesture.OPEN_HAND
            else -> Gesture.NONE
        }
    }

    /** Nome amigável (UI). */
    fun label(g: Gesture): String = when (g) {
        Gesture.NONE -> "—"
        Gesture.OPEN_HAND -> "Mão aberta"
        Gesture.FIST -> "Punho"
        Gesture.POINT -> "Apontando"
        Gesture.PINCH -> "Pinch"
        Gesture.TWO -> "Dois dedos"
    }
}

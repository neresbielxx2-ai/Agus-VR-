package com.agusvr.handtracking

import kotlin.math.tan

/**
 * Agus Hand Tracking — projeção dos keypoints 2D da câmera para o mundo 3D.
 *
 * Estratégia (monocular, sem profundidade de hardware):
 *  1. A distância da palma é estimada pelo TAMANHO da mão na imagem
 *     (mão maior = mais perto). Isso dá resposta real de aproximação.
 *  2. x/y normalizados são abertos pelo campo de visão da câmera.
 *  3. O z relativo do MediaPipe ajusta a profundidade por keypoint
 *     (dedos à frente do punho ficam à frente no mundo).
 *  4. O resultado é ancorado na orientação atual da cabeça, de modo que
 *     a mão fica "presa" ao mundo enquanto você olha para os lados.
 *
 * A imagem fornecida ao modelo já chega espelhada (visão de espelho),
 * portanto aqui não há nova inversão — o que aparece à direita do
 * usuário é a mão direita.
 */
class HandProjection {

    /** Campo de visão horizontal aproximado da câmera (graus). */
    var fovHDeg = 68f

    /** Aspecto da imagem de análise (largura/altura). */
    var imageAspect = 640f / 480f

    /** Constante de estimativa de distância: D = distK / tamanhoDaMao. */
    var distK = 0.105f

    fun estimateDistance(imageHandSize: Float): Float {
        val s = imageHandSize.coerceIn(0.02f, 0.8f)
        return (distK / s).coerceIn(0.30f, 3.0f)
    }

    /**
     * Projeta um keypoint para o mundo.
     * @param nx,ny coordenadas normalizadas 0..1 na imagem (já espelhada)
     * @param nz    profundidade relativa do MediaPipe (negativo = mais perto)
     * @param palmD distância estimada da palma (m)
     */
    fun project(
        nx: Float, ny: Float, nz: Float, palmD: Float,
        eye: FloatArray, fwd: FloatArray, right: FloatArray, up: FloatArray,
        out: FloatArray
    ) {
        val tanH = tan(Math.toRadians(fovHDeg / 2.0)).toFloat()
        val tanV = tanH / imageAspect
        val vx = (nx - 0.5f) * 2f * tanH * palmD
        val vy = -(ny - 0.5f) * 2f * tanV * palmD
        val d = palmD * (1f + nz.coerceIn(-0.5f, 0.5f) * 0.9f)
        out[0] = eye[0] + right[0] * vx + up[0] * vy + fwd[0] * d
        out[1] = eye[1] + right[1] * vx + up[1] * vy + fwd[1] * d
        out[2] = eye[2] + right[2] * vx + up[2] * vy + fwd[2] * d
    }
}

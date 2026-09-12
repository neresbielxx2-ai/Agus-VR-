package com.agusvr.util

import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Agus VR — utilitários matemáticos mínimos (vetores 3D e quatérnions).
 * Vetores: FloatArray[3]. Quatérnions: FloatArray[4] = (w, x, y, z).
 */
object M {

    // ------------------------------------------------------------------ vet
    fun v(x: Float, y: Float, z: Float): FloatArray = floatArrayOf(x, y, z)

    fun add(a: FloatArray, b: FloatArray): FloatArray =
        floatArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])

    fun sub(a: FloatArray, b: FloatArray): FloatArray =
        floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])

    fun scale(a: FloatArray, s: Float): FloatArray =
        floatArrayOf(a[0] * s, a[1] * s, a[2] * s)

    fun dot(a: FloatArray, b: FloatArray): Float =
        a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    fun cross(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )

    fun len(a: FloatArray): Float = sqrt(dot(a, a))

    fun dist(a: FloatArray, b: FloatArray): Float = len(sub(a, b))

    fun norm(a: FloatArray): FloatArray {
        val l = len(a)
        return if (l < 1e-8f) floatArrayOf(0f, 0f, -1f) else scale(a, 1f / l)
    }

    fun lerp(a: FloatArray, b: FloatArray, t: Float): FloatArray = floatArrayOf(
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t
    )

    /** Suavização exponencial independente de dt (taxa ~ resposta por segundo). */
    fun smooth(cur: FloatArray, target: FloatArray, rate: Float, dt: Float) {
        val r = rate.coerceIn(0.01f, 1f)
        val t = 1f - (1f - r).pow(dt * 60f)
        cur[0] += (target[0] - cur[0]) * t
        cur[1] += (target[1] - cur[1]) * t
        cur[2] += (target[2] - cur[2]) * t
    }

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v
    fun clamp01(v: Float): Float = clamp(v, 0f, 1f)

    // ---------------------------------------------------------------- quat
    fun qIdentity(): FloatArray = floatArrayOf(1f, 0f, 0f, 0f)

    fun qFromAxisAngle(axis: FloatArray, angRad: Float): FloatArray {
        val n = norm(axis)
        val s = sin(angRad / 2f)
        return floatArrayOf(cos(angRad / 2f), n[0] * s, n[1] * s, n[2] * s)
    }

    fun qMul(a: FloatArray, b: FloatArray): FloatArray {
        val aw = a[0]; val ax = a[1]; val ay = a[2]; val az = a[3]
        val bw = b[0]; val bx = b[1]; val by = b[2]; val bz = b[3]
        return floatArrayOf(
            aw * bw - ax * bx - ay * by - az * bz,
            aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw
        )
    }

    fun qConj(q: FloatArray): FloatArray = floatArrayOf(q[0], -q[1], -q[2], -q[3])

    /** Rotaciona o vetor v pelo quatérnion q. */
    fun qRot(q: FloatArray, v: FloatArray): FloatArray {
        // t = 2 * cross(q.xyz, v)
        val qx = q[1]; val qy = q[2]; val qz = q[3]
        val tx = 2f * (qy * v[2] - qz * v[1])
        val ty = 2f * (qz * v[0] - qx * v[2])
        val tz = 2f * (qx * v[1] - qy * v[0])
        return floatArrayOf(
            v[0] + q[0] * tx + (qy * tz - qz * ty),
            v[1] + q[0] * ty + (qz * tx - qx * tz),
            v[2] + q[0] * tz + (qx * ty - qy * tx)
        )
    }

    /** Yaw (em torno de Y) de uma direção; 0 = olhando para -Z. */
    fun yawOf(dir: FloatArray): Float = atan2(dir[0], -dir[2])
}

/** Raio 3D usado pelo Agus Point Interaction e pelo raycast de objetos. */
class Ray(val origin: FloatArray, val dir: FloatArray) {
    fun pointAt(t: Float, out: FloatArray) {
        out[0] = origin[0] + dir[0] * t
        out[1] = origin[1] + dir[1] * t
        out[2] = origin[2] + dir[2] * t
    }
}

/** Testes de interseção raio-geometria. */
object Intersect {

    /** Raio vs AABB (slab method). Retorna distância ou -1f. */
    fun rayAABB(ray: Ray, min: FloatArray, max: FloatArray): Float {
        var tmin = 0f
        var tmax = Float.MAX_VALUE
        for (i in 0..2) {
            if (kotlin.math.abs(ray.dir[i]) < 1e-8f) {
                if (ray.origin[i] < min[i] || ray.origin[i] > max[i]) return -1f
            } else {
                val inv = 1f / ray.dir[i]
                var t1 = (min[i] - ray.origin[i]) * inv
                var t2 = (max[i] - ray.origin[i]) * inv
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                if (t1 > tmin) tmin = t1
                if (t2 < tmax) tmax = t2
                if (tmin > tmax) return -1f
            }
        }
        return tmin
    }

    /** Raio vs esfera. Retorna distância ou -1f. */
    fun raySphere(ray: Ray, center: FloatArray, radius: Float): Float {
        val oc = subVec(ray.origin, center)
        val b = M.dot(oc, ray.dir)
        val c = M.dot(oc, oc) - radius * radius
        val disc = b * b - c
        if (disc < 0f) return -1f
        val t = -b - sqrt(disc)
        return if (t >= 0f) t else -1f
    }

    private fun subVec(a: FloatArray, b: FloatArray): FloatArray = M.sub(a, b)

    /**
     * Raio vs quad orientado (painel). O quad está em [center], orientado por
     * [yaw] (rotação em torno de Y), com largura/altura locais em metros.
     * Retorna distância ou -1f; se atingir, preenche [outUV] com coordenadas
     * locais normalizadas (-0.5..0.5).
     */
    fun rayQuad(
        ray: Ray,
        center: FloatArray,
        yaw: Float,
        width: Float,
        height: Float,
        outUV: FloatArray
    ): Float {
        // Normal do painel: a "frente" local (-Z) rotacionada por yaw em torno de Y
        val nrm = floatArrayOf(-sin(yaw), 0f, -cos(yaw))
        val denom = M.dot(nrm, ray.dir)
        if (kotlin.math.abs(denom) < 1e-7f) return -1f
        val toC = M.sub(center, ray.origin)
        val t = M.dot(toC, nrm) / denom
        if (t < 0f) return -1f
        val hit = floatArrayOf(
            ray.origin[0] + ray.dir[0] * t,
            ray.origin[1] + ray.dir[1] * t,
            ray.origin[2] + ray.dir[2] * t
        )
        // Para o espaço local do painel
        val local = M.sub(hit, center)
        val c = cos(yaw); val s = sin(yaw)
        // Rotação inversa por yaw
        val lx = local[0] * c - local[2] * s
        val ly = local[1]
        if (kotlin.math.abs(lx) > width / 2f || kotlin.math.abs(ly) > height / 2f) return -1f
        outUV[0] = lx / width          // -0.5..0.5, esquerda→direita
        outUV[1] = -ly / height         // -0.5..0.5, baixo→cima (invertido p/ canvas)
        return t
    }
}

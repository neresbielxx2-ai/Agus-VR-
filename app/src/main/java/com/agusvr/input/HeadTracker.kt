package com.agusvr.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.agusvr.util.M

/**
 * Agus Input — head tracking 3DoF real.
 *
 * Usa o sensor TYPE_ROTATION_VECTOR (fusão de giroscópio + acelerômetro +
 * magnetômetro pelo sistema), que fornece orientação absoluta estável.
 * A orientação é convertida para quatérnion; um offset de "recenter"
 * define para onde é "frente". O suporte de encaixe do celular (mount)
 * rotaciona os eixos para o modo paisagem do headset.
 */
class HeadTracker(context: Context) : SensorEventListener {

    companion object {
        const val MOUNT_LANDSCAPE_CCW = 0  // topo do celular à esquerda
        const val MOUNT_LANDSCAPE_CW = 1   // topo do celular à direita
    }

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var sensor: Sensor? = null

    var mount: Int = MOUNT_LANDSCAPE_CCW

    @Volatile private var qSensor = M.qIdentity()
    @Volatile private var qOffset = M.qIdentity()
    @Volatile var available: Boolean = false
        private set

    private val qTmp = FloatArray(4)

    // Vetores derivados (cache por frame)
    val forward = floatArrayOf(0f, 0f, -1f)
    val up = floatArrayOf(0f, 1f, 0f)
    val right = floatArrayOf(1f, 0f, 0f)

    fun start() {
        sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val s = sensor
        if (s != null) {
            available = true
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        } else {
            available = false
        }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getQuaternionFromVector(qTmp, event.values)
        // qTmp retorna (x,y,z,w) — normalizamos para (w,x,y,z)
        synchronized(this) {
            qSensor[0] = qTmp[3]; qSensor[1] = qTmp[0]
            qSensor[2] = qTmp[1]; qSensor[3] = qTmp[2]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Define a orientação atual como "olhando para frente". */
    fun recenter() {
        synchronized(this) {
            val qFull = M.qMul(qSensor, mountQuat())
            qOffset = M.qConj(qFull)
        }
    }

    private fun mountQuat(): FloatArray {
        // Rotaciona os eixos do dispositivo para o frame de visão:
        // queremos viewUp(0,1,0) → topo do aparelho em paisagem.
        val angle = if (mount == MOUNT_LANDSCAPE_CCW) -Math.PI / 2.0 else Math.PI / 2.0
        return M.qFromAxisAngle(floatArrayOf(0f, 0f, 1f), angle.toFloat())
    }

    /** Atualiza os vetores forward/up/right. Chamado uma vez por frame. */
    fun update() {
        val q = synchronized(this) { M.qMul(qOffset, M.qMul(qSensor, mountQuat())) }
        val f = M.qRot(q, floatArrayOf(0f, 0f, -1f))
        val u = M.qRot(q, floatArrayOf(0f, 1f, 0f))
        forward[0] = f[0]; forward[1] = f[1]; forward[2] = f[2]
        up[0] = u[0]; up[1] = u[1]; up[2] = u[2]
        val r = M.cross(u, f)
        right[0] = r[0]; right[1] = r[1]; right[2] = r[2]
    }

    /** Quatérnion de visão atual (para exportar à ponte Roblox). */
    fun viewQuat(out: FloatArray) {
        val q = synchronized(this) { M.qMul(qOffset, M.qMul(qSensor, mountQuat())) }
        out[0] = q[0]; out[1] = q[1]; out[2] = q[2]; out[3] = q[3]
    }
}

package com.agusvr.compat

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build

/**
 * Agus Compatibility Layer
 * ------------------------
 * Detecta capacidades reais do dispositivo e decide quais subsistemas do
 * Agus VR podem funcionar — e qual fallback usar quando algo não está
 * disponível. Nada aqui "finge" funcionar: cada recurso reporta um estado
 * verdadeiro que a UI (app Sistema) exibe ao usuário.
 */
class DeviceProfile(context: Context) {

    val sdkInt: Int = Build.VERSION.SDK_INT
    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}"

    val hasGyroscope: Boolean
    val hasAccelerometer: Boolean
    val hasRotationVector: Boolean
    val hasCamera: Boolean
    val hasFlash: Boolean
    val headTrackingFeature: Boolean
    val openxrHint: Boolean

    /** Resumo textual exibido no app Sistema. */
    val summary: String

    init {
        val pm = context.packageManager
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        hasGyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        hasAccelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        hasRotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        headTrackingFeature =
            pm.hasSystemFeature("android.hardware.vr.headtracking")
        // Não existe API pública universal para OpenXR em qualquer Android;
        // verificamos apenas indícios (feature XR do Android 15+).
        openxrHint = pm.hasSystemFeature("android.hardware.xr.supports_hmd")

        val sb = StringBuilder()
        sb.append("Dispositivo: ").append(deviceName).append(" (Android ").append(sdkInt).append(")\n")
        sb.append("Head tracking (rotação): ").append(if (hasRotationVector) "OK" else "INDISPONÍVEL — use touch").append('\n')
        sb.append("Giroscópio: ").append(if (hasGyroscope) "OK" else "ausente (tracking suavizado reduzido)").append('\n')
        sb.append("Câmera: ").append(if (hasCamera) "OK" else "INDISPONÍVEL — hand tracking desativado, fallback gaze+touch").append('\n')
        sb.append("VR nativo (head-tracking API): ").append(if (headTrackingFeature) "presente" else "não declarado").append('\n')
        summary = sb.toString()
    }

    // ------------------------------------------------ decisões de fallback
    /** O motor VR pode fazer head tracking 3DoF real? */
    fun canHeadTrack(): Boolean = hasRotationVector

    /** Hand tracking real é possível (câmera + permissão concedida)? */
    fun canHandTrack(cameraGranted: Boolean): Boolean = hasCamera && cameraGranted

    companion object {
        /**
         * O APK oficial do Roblox NÃO é modificado pelo Agus VR.
         * A integração futura acontece somente via protocolo de dados
         * (ver docs/ROBLOX_STUDIO.md).
         */
        const val ROBLOX_POLICY = "read-only-integration"
    }
}

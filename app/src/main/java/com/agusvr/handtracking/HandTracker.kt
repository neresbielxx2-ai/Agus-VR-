package com.agusvr.handtracking

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.agusvr.storage.Settings
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors

/**
 * Agus Hand Tracking — pipeline real de tracking:
 *
 *   CameraX (ImageAnalysis) → bitmap girado/espelhado
 *      → MediaPipe HandLandmarker (21 keypoints, 2 mãos)
 *      → classificação de gestos (estabilizada)
 *      → projeção 2D→3D ancorada na cabeça
 *      → HandFrame publicado para o engine
 *
 * Nada aqui é simulado: se o modelo não existe, a câmera foi negada ou a
 * detecção falha, o estado `modelReady`/`cameraGranted` fica falso e o
 * runtime usa fallback (gaze + toque).
 */
class HandTracker(private val activity: Activity, private val settings: () -> Settings) {

    companion object {
        private const val TAG = "AgusHands"
    }

    /** Snapshot dos eixos da cabeça, fornecido pelo engine (thread-safe). */
    class HeadBasis {
        val eye = FloatArray(3)
        val fwd = FloatArray(3)
        val right = FloatArray(3)
        val up = FloatArray(3)
    }

    var headBasisProvider: (() -> HeadBasis)? = null

    val frame = HandFrame()
    private val frameLock = Any()

    val projection = HandProjection()

    private var landmarker: HandLandmarker? = null
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var lastVideoTs = -1L
    private var lastDetectMs = 0L
    private var lastDetectElapsed = 0L

    // Estabilização de gestos (evita flicker entre estados)
    private val rawGesture = IntArray(2) { -1 }
    private val rawCount = IntArray(2)

    // Passthrough (fundo com a câmera no Agus Hand Lab)
    var passthroughTexId = 0
        private set
    var surfaceTexture: SurfaceTexture? = null
        private set
    val texMatrix = FloatArray(16)
    @Volatile var frameAvailable = false
    private var previewSurface: android.view.Surface? = null

    @Volatile var statusMessage = "inicializando"
        private set

    /** Chamado quando o MediaPipe carregou com sucesso (fase saudável). */
    var onPipelineReady: (() -> Unit)? = null

    // ---------------------------------------------------------------- setup
    fun start(lifecycleOwner: LifecycleOwner) {
        owner = lifecycleOwner
        if (!settings().handTrackingEnabled) {
            statusMessage = "hand tracking desativado nas configurações"
            updateFlags()
            return
        }
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            statusMessage = "permissão de câmera negada"
            updateFlags()
            return
        }
        val modelPath = HandModelManager.resolveModelPath(activity)
        if (modelPath == null) {
            statusMessage = "modelo ausente — baixe na tela inicial"
            updateFlags()
            return
        }
        try {
            val base = BaseOptions.builder().setModelAssetPath(modelPath).build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(2)
                .build()
            landmarker = HandLandmarker.createFromOptions(activity, options)
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao criar HandLandmarker", e)
            statusMessage = "erro ao carregar modelo: ${e.message}"
            com.agusvr.runtime.CrashLog.log(activity, "HandLandmarker", e)
            updateFlags()
            return
        }
        try { onPipelineReady?.invoke() } catch (_: Throwable) {}
        main.post { bindCamera() }
    }

    /** Reconstrói o pipeline (ex.: modelo baixado durante a sessão). */
    fun restart(lifecycleOwner: LifecycleOwner) {
        stopInternal()
        start(lifecycleOwner)
    }

    private fun bindCamera() {
        try {
            val ctx = activity
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val p = future.get()
                    provider = p
                    bindUseCases()
                } catch (e: Throwable) {
                    Log.e(TAG, "CameraProvider falhou", e)
                    statusMessage = "câmera indisponível: ${e.message}"
                    updateFlags()
                }
            }, main::post)
        } catch (e: Throwable) {
            statusMessage = "câmera indisponível"
            updateFlags()
        }
    }

    private fun bindUseCases() {
        val p = provider ?: return
        val o = owner ?: return
        p.unbindAll()

        val facing = if (settings().cameraId == 0)
            CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { proxy -> onImage(proxy) }

        val useCases = mutableListOf<UseCase>(analysis)

        val st = surfaceTexture
        if (st != null) {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider { request ->
                try {
                    st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                    val surface = android.view.Surface(st)
                    previewSurface = surface
                    request.provideSurface(surface, { r -> r.run() }) { }
                } catch (e: Throwable) {
                    Log.e(TAG, "provideSurface falhou", e)
                }
            }
            useCases.add(preview)
        }

        try {
            val selector = CameraSelector.Builder().requireLensFacing(facing).build()
            p.bindToLifecycle(o, selector, *useCases.toTypedArray())
            statusMessage = "rastreamento ativo"
            updateFlags()
        } catch (e: Throwable) {
            Log.e(TAG, "bind falhou", e)
            statusMessage = "câmera em uso por outro app?"
            updateFlags()
        }
    }

    /** Anexa a textura de passthrough (chamado pelo engine após GL ready). */
    fun attachPassthrough(texId: Int) {
        passthroughTexId = texId
        val st = SurfaceTexture(texId)
        st.setDefaultBufferSize(1280, 720)
        st.setOnFrameAvailableListener { frameAvailable = true }
        surfaceTexture = st
        // Re-anexa a câmera incluindo o Preview
        if (provider != null) main.post { bindUseCases() }
    }

    /** Remove o passthrough (volta a usar só ImageAnalysis). */
    fun detachPassthrough() {
        if (surfaceTexture == null) return
        try { surfaceTexture?.release() } catch (_: Exception) {}
        surfaceTexture = null
        try { previewSurface?.release() } catch (_: Exception) {}
        previewSurface = null
        if (provider != null) main.post { bindUseCases() }
    }

    /** Atualiza a textura externa; chamado na thread GL. */
    fun consumePassthroughFrame() {
        val st = surfaceTexture ?: return
        if (frameAvailable) {
            frameAvailable = false
            try {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
            } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------ detecção
    private fun onImage(proxy: ImageProxy) {
        try {
            val lm = landmarker
            if (lm == null || !settings().handTrackingEnabled) return

            // Limita a taxa de detecção conforme o modo de desempenho
            val minInterval = if (settings().perfMode == 0) 66L else 33L
            val elapsed = SystemClock.elapsedRealtime()
            if (elapsed - lastDetectMs < minInterval) return
            lastDetectMs = elapsed

            var bmp = proxy.toBitmap()
            val rot = proxy.imageInfo.rotationDegrees
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            // Espelha para visão "de espelho" (intuitiva; handedness correta)
            val flip = Matrix().apply { postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, flip, true)

            projection.imageAspect = bmp.width.toFloat() / bmp.height.toFloat()

            // O bitmap já chega girado/espelhado; sem rotação adicional.
            val mpImage = BitmapImageBuilder(bmp).build()

            var ts = SystemClock.elapsedRealtime()
            if (ts <= lastVideoTs) ts = lastVideoTs + 1
            lastVideoTs = ts

            val t0 = SystemClock.elapsedRealtimeNanos()
            val result = lm.detectForVideo(mpImage, ts)
            val procMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f

            val basis = headBasisProvider?.invoke()

            synchronized(frameLock) {
                frame.timestamp = ts
                frame.processingMs = procMs
                frame.left.present = false
                frame.right.present = false

                val hands = result.landmarks()
                val handed = result.handedness()

                for (i in hands.indices) {
                    val lms = hands[i]
                    if (lms.size < 21) continue
                    val cat = handed.getOrNull(i)?.getOrNull(0)
                    val sideName = cat?.categoryName() ?: "Right"
                    val st = if (sideName == "Left") frame.left else frame.right
                    val prevSeen = st.lastSeenMs
                    val nowElapsed = SystemClock.elapsedRealtime()
                    st.present = true
                    st.side = if (sideName == "Left") HandState.SIDE_LEFT else HandState.SIDE_RIGHT
                    st.score = cat?.score() ?: 0f

                    for (j in 0 until 21) {
                        st.norm[j][0] = lms[j].x()
                        st.norm[j][1] = lms[j].y()
                        st.norm[j][2] = lms[j].z()
                    }
                    st.imageHandSize = GestureClassifier.handSize(st.norm)
                    st.pinchDistance = GestureClassifier.pinchDistance(st.norm)
                    st.estimatedDistance = projection.estimateDistance(st.imageHandSize)

                    if (basis != null) {
                        for (j in 0 until 21) {
                            projection.project(
                                st.norm[j][0], st.norm[j][1], st.norm[j][2],
                                st.estimatedDistance,
                                basis.eye, basis.fwd, basis.right, basis.up,
                                st.world[j]
                            )
                        }
                    }

                    // Gesture com estabilização (2 frames consecutivos)
                    val raw = GestureClassifier.classify(st.norm)
                    val idx = st.side
                    if (raw.ordinal == rawGesture[idx]) {
                        rawCount[idx]++
                    } else {
                        rawGesture[idx] = raw.ordinal
                        rawCount[idx] = 1
                    }
                    if (rawCount[idx] >= 2 && st.gesture != raw) {
                        st.gesture = raw
                        st.lastGestureChange = SystemClock.elapsedRealtime()
                    }

                    // Suavização temporal das posições 3D
                    if (prevSeen > 0 && nowElapsed - prevSeen < 500) {
                        st.smoothWorld((nowElapsed - prevSeen) / 1000f)
                    }
                    st.copySmoothedOut()
                    st.lastSeenMs = nowElapsed
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "detecção falhou", e)
        } finally {
            proxy.close()
        }
    }

    private fun updateFlags() {
        val s = settings()
        synchronized(frameLock) {
            frame.enabled = s.handTrackingEnabled
            frame.modelReady = HandModelManager.resolveModelPath(activity) != null
            frame.cameraGranted =
                activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun refreshFlags() = updateFlags()

    // ------------------------------------------------------------- shutdown
    fun stop() {
        main.post { stopInternal() }
    }

    private fun stopInternal() {
        try { provider?.unbindAll() } catch (_: Exception) {}
        try { landmarker?.close() } catch (_: Exception) {}
        landmarker = null
        try { previewSurface?.release() } catch (_: Exception) {}
        previewSurface = null
        try { surfaceTexture?.release() } catch (_: Exception) {}
        surfaceTexture = null
    }

    fun destroy() {
        stop()
        executor.shutdown()
    }
}

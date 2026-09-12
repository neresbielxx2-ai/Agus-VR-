package com.agusvr.runtime

import android.graphics.Paint
import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.agusvr.apps.AppManager
import com.agusvr.compat.DeviceProfile
import com.agusvr.handtracking.Gesture
import com.agusvr.handtracking.HandFrame
import com.agusvr.handtracking.HandRig
import com.agusvr.handtracking.HandTracker
import com.agusvr.input.HeadTracker
import com.agusvr.input.TouchInput
import com.agusvr.interaction.BoxTarget
import com.agusvr.interaction.GrabController
import com.agusvr.interaction.Interactable
import com.agusvr.interaction.InteractionManager
import com.agusvr.interaction.PanelTarget
import com.agusvr.performance.AdaptiveQuality
import com.agusvr.performance.PerfMonitor
import com.agusvr.performance.PerfPolicy
import com.agusvr.point.PointInteraction
import com.agusvr.runtime.gl.AgusRenderer
import com.agusvr.runtime.gl.CanvasTexture
import com.agusvr.runtime.gl.DynamicDraw
import com.agusvr.runtime.gl.SceneObject
import com.agusvr.storage.AppDataStore
import com.agusvr.storage.SettingsStore
import com.agusvr.ui.MainMenuController
import com.agusvr.ui.VrPanel
import com.agusvr.util.M
import com.agusvr.util.Ray

/**
 * Agus VR Runtime — engine central.
 *
 * Orquestra todos os módulos:
 *  Head tracking → Hand tracking → Agus Point Interaction → Interaction →
 *  UI espacial → Applications → Spatial → Performance → Storage/Compat.
 */
class VrEngine(val activity: VrActivity) {

    // ------------------------------------------------------------ módulos
    val settingsStore = SettingsStore(activity)
    val data = AppDataStore(activity)
    val profile = DeviceProfile(activity)
    val perf = PerfMonitor(activity)
    val adaptive = AdaptiveQuality(perf)
    val policy = PerfPolicy()
    val sound = SoundFx()

    val head = HeadTracker(activity)
    val touch = TouchInput()
    val hands = HandTracker(activity) { settingsStore.current }

    lateinit var renderer: AgusRenderer
    lateinit var point: PointInteraction
    lateinit var handRig: HandRig
    lateinit var environment: EnvironmentScene
    lateinit var menu: MainMenuController
    lateinit var apps: AppManager

    val interaction = InteractionManager()
    val grab = GrabController()

    // ------------------------------------------------------------- cenas
    val opaque = ArrayList<SceneObject>()
    val transparent = ArrayList<SceneObject>()
    val dynamics = ArrayList<DynamicDraw>()
    val textures = ArrayList<CanvasTexture>()
    val panels = ArrayList<VrPanel>()
    val interactables = ArrayList<Interactable>()

    // Estado visível ao renderer
    val sky = floatArrayOf(0.014f, 0.020f, 0.045f)
    var fogDensity = 0.045f
    val ipd: Float get() = settingsStore.current.ipd

    private var extTexId = 0
    /** Id da textura de passthrough — 0 quando o preview está desligado. */
    val passthroughTex: Int
        get() = if (hands.surfaceTexture != null) extTexId else 0
    val passthroughTexMat = FloatArray(16)

    // ------------------------------------------------------------ estado
    var glReady = false
        private set
    var menuVisible = true
    var focusMode = false
    var appActive = false
        private set
    private var timeSec = 0f
    private var lastHovered: Interactable? = null

    // Toast / chip de alvo / HUD
    private lateinit var toastPanel: VrPanel
    private var toastMsg = ""
    private var toastUntil = 0L
    private lateinit var targetChip: VrPanel
    private var chipLabel = ""
    private lateinit var hudPanel: VrPanel
    var hudVisible = false
    private var lastHudSec = -1

    // Atalhos de gesto (hold)
    private val holdTimers = HashMap<String, Float>()
    private val holdFired = HashMap<String, Boolean>()
    private var lastShortcutAt = 0L

    // -------------------------------------------------------------- setup
    fun init() {
        sound.enabled = settingsStore.current.soundEnabled
        head.start()
        wireTouch()
        hands.headBasisProvider = {
            val b = HandTracker.HeadBasis()
            b.fwd[0] = head.forward[0]; b.fwd[1] = head.forward[1]; b.fwd[2] = head.forward[2]
            b.right[0] = head.right[0]; b.right[1] = head.right[1]; b.right[2] = head.right[2]
            b.up[0] = head.up[0]; b.up[1] = head.up[1]; b.up[2] = head.up[2]
            b
        }
    }

    fun startHands() {
        hands.refreshFlags()
        hands.start(activity)
    }

    fun attachRenderer(r: AgusRenderer) {
        renderer = r
    }

    /** Chamado na thread GL quando o contexto está pronto. */
    fun onGlReady() {
        val meshes = renderer.meshes

        environment = EnvironmentScene(meshes)
        environment.build(opaque, transparent, dynamics)

        point = PointInteraction(meshes, interaction) { settingsStore.current }
        transparent.add(point.ring)
        transparent.add(point.progressDisc)
        dynamics.add(point.rayDraw)

        handRig = HandRig(meshes)
        handRig.register(opaque, dynamics)

        menu = MainMenuController(this)
        menu.build()

        // Toast
        toastPanel = VrPanel("toast", 1.5f, 0.3f, meshes, 1024).also { p ->
            p.customDraw = { c, w, h ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = VrPanel.TEXT
                paint.textSize = h * 0.34f
                paint.textAlign = Paint.Align.CENTER
                c.drawText(toastMsg, w / 2f, h * 0.62f, paint)
            }
            p.item.visible = false
            p.registerTo(transparent, textures)
        }

        // Chip do alvo atual
        targetChip = VrPanel("target_chip", 1.1f, 0.22f, meshes, 768).also { p ->
            p.customDraw = { c, w, h ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = VrPanel.ACCENT
                paint.textSize = h * 0.36f
                paint.textAlign = Paint.Align.CENTER
                c.drawText(chipLabel, w / 2f, h * 0.63f, paint)
            }
            p.item.visible = false
            p.registerTo(transparent, textures)
        }

        // HUD de performance
        hudPanel = VrPanel("hud_perf", 1.0f, 0.8f, meshes, 640).also { p ->
            p.title = "Performance"
            p.customDraw = { c, w, h ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = VrPanel.TEXT
                paint.textSize = h * 0.085f
                val lines = perf.report().split("\n")
                var y = h * 0.30f
                for (ln in lines) { c.drawText(ln, w * 0.08f, y, paint); y += h * 0.115f }
                paint.color = VrPanel.TEXT_DIM
                paint.textSize = h * 0.07f
                c.drawText("Auto: ${adaptive.lastChangeReason} (nível ${adaptive.step})", w * 0.08f, y + h * 0.02f, paint)
            }
            p.item.visible = false
            p.registerTo(transparent, textures)
        }
        panels.add(toastPanel); panels.add(targetChip); panels.add(hudPanel)

        apps = AppManager(this)

        // Passthrough (textura externa da câmera)
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texIds[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        extTexId = texIds[0]

        interaction.onSelectEvent = { t, _ ->
            sound.select()
            com.agusvr.bridge.AgusBridge.recordEvent("select", t.id)
        }
        grab.onGrab = { t ->
            sound.grab()
            com.agusvr.bridge.AgusBridge.recordEvent("grab", t.id)
        }
        grab.onRelease = { t ->
            sound.release()
            com.agusvr.bridge.AgusBridge.recordEvent("release", t.id)
        }

        glReady = true
    }

    // --------------------------------------------------------------- tick
    fun tick(dtMs: Float) {
        timeSec += dtMs / 1000f
        head.update()

        // Passthrough da câmera
        hands.consumePassthroughFrame()
        System.arraycopy(hands.texMatrix, 0, passthroughTexMat, 0, 16)

        val hf = hands.frame
        updateGestureShortcuts(hf, dtMs)

        // Agus Point Interaction (ray + círculo + seleção por aproximação)
        point.update(hf, interactables, dtMs)

        // Hover de elementos de painel
        updatePanelHovers()

        // Grab / release
        updateGrab(hf)

        // Painéis (flash, progresso)
        for (p in panels) p.update(dtMs)

        // Mãos visuais + ambiente
        handRig.update(hf)
        environment.effectsOn = settingsStore.current.effectsEnabled && adaptive.effectsAllowed
        environment.dim = if (focusMode) 0.35f else 1f
        environment.update(dtMs, timeSec)

        // App atual + menu
        apps.update(dtMs)
        menu.update(dtMs)

        // Overlays dependentes da cabeça
        updateOverlays()

        sound.enabled = settingsStore.current.soundEnabled

        // Performance + qualidade adaptativa
        perf.onFrame(dtMs)
        adaptive.update(dtMs, settingsStore.current.fpsCap, settingsStore.current)
        renderer.renderScale =
            (settingsStore.current.resolutionScale * adaptive.scaleMultiplier).coerceIn(0.4f, 1f)
        renderer.fpsCap = (settingsStore.current.fpsCap - adaptive.fpsCapReduction).coerceAtLeast(20)
        fogDensity = if (focusMode) 0.09f else 0.045f

        for (p in panels) p.syncTexture()
    }

    // ------------------------------------------------------------ helpers
    private fun updatePanelHovers() {
        val hoveredTarget = interaction.hovered
        for (p in panels) {
            if (!p.item.visible || !p.target.enabled) continue
            if (hoveredTarget === p.target && point.active) {
                val el = p.target.updateHover(point.ray)
                p.setHovered(el)
                p.hoverProgress = interaction.progress
            } else {
                if (p.hovered != null) p.setHovered(null)
                p.hoverProgress = 0f
            }
        }
        if (hoveredTarget !== lastHovered) {
            if (hoveredTarget != null && interaction.detected) sound.hover()
            lastHovered = hoveredTarget
        }
    }

    private fun updateGrab(hf: HandFrame) {
        val fistHand = when {
            hf.right.present && hf.right.gesture == Gesture.FIST -> hf.right
            hf.left.present && hf.left.gesture == Gesture.FIST -> hf.left
            else -> null
        }
        val openHand = (hf.left.present && hf.left.gesture == Gesture.OPEN_HAND) ||
                (hf.right.present && hf.right.gesture == Gesture.OPEN_HAND)

        var near: Interactable? = null
        if (grab.held == null && fistHand != null) {
            var bestD = 0.30f
            for (t in interactables) {
                if (!t.enabled || !t.grabbable) continue
                val d = M.dist(t.center, fistHand.palm)
                if (d < bestD) { bestD = d; near = t }
            }
        }

        grab.update(
            fistActive = fistHand != null,
            openActive = openHand,
            palm = fistHand?.palm,
            reach = 0.30f,
            nearGrabbable = near
        ) { target, dest ->
            when (target) {
                is BoxTarget -> {
                    target.item.pos[0] = dest[0]; target.item.pos[1] = dest[1]; target.item.pos[2] = dest[2]
                }
                is PanelTarget -> {
                    target.item.pos[0] = dest[0]; target.item.pos[1] = dest[1]; target.item.pos[2] = dest[2]
                }
            }
        }
    }

    private fun updateGestureShortcuts(hf: HandFrame, dtMs: Float) {
        val now = System.currentTimeMillis()
        if (now - lastShortcutAt < 400) return

        val stable = HashMap<String, Boolean>()
        fun consider(key: String) { stable[key] = true }

        for (hand in listOf(hf.left, hf.right)) {
            val fresh = hand.present && now - hand.lastSeenMs < 600
            if (!fresh) continue
            when (hand.gesture) {
                Gesture.OPEN_HAND -> consider("OPEN_HOLD")
                Gesture.FIST -> if (grab.held == null) consider("FIST_HOLD")
                Gesture.TWO -> consider("TWO_HOLD")
                Gesture.PINCH -> if (point.currentTarget() == null) consider("PINCH_AIR")
                else -> {}
            }
        }

        for (key in listOf("OPEN_HOLD", "FIST_HOLD", "TWO_HOLD", "PINCH_AIR")) {
            val active = stable[key] == true
            val t = holdTimers[key] ?: 0f
            if (active) {
                val nt = t + dtMs
                holdTimers[key] = nt
                if (nt >= 900f && holdFired[key] != true) {
                    holdFired[key] = true
                    fireShortcut(data.shortcutFor(key))
                    lastShortcutAt = now
                }
            } else {
                holdTimers[key] = 0f
                holdFired[key] = false
            }
        }
    }

    fun fireShortcut(action: String) {
        when (action) {
            "menu" -> toggleMenu()
            "focus" -> toggleFocus()
            "recenter" -> recenter()
            "perf" -> hudVisible = !hudVisible
            "home" -> goHome()
            else -> {}
        }
    }

    // ------------------------------------------------------------- ações
    fun recenter() {
        head.recenter()
        toast("Visão recentralizada")
    }

    fun toggleMenu() {
        menuVisible = !menuVisible
    }

    fun toggleFocus() {
        focusMode = !focusMode
        if (focusMode) {
            menuVisible = false
            hudVisible = false
            toast("Focus Mode ativo")
        } else {
            toast("Focus Mode desativado")
        }
    }

    fun goHome() {
        apps.close()
        appActive = false
        menuVisible = true
        menu.rebuild()
    }

    fun onAppLaunched() {
        appActive = true
        menuVisible = false
    }

    fun toast(msg: String, ms: Long = 2400) {
        toastMsg = msg
        toastUntil = System.currentTimeMillis() + ms
        if (glReady) toastPanel.repaint()
    }

    /** Liga/desliga o fundo de câmera (passthrough) — usado pelo Hand Lab. */
    fun setPassthrough(on: Boolean) {
        if (on) hands.attachPassthrough(passthroughTex)
        else hands.detachPassthrough()
    }

    fun passthroughActive(): Boolean = hands.surfaceTexture != null

    /** Ray de gaze (fallback para toque quando não há hand tracking). */
    fun gazeRay(): Ray = Ray(
        floatArrayOf(0f, 0f, 0f),
        floatArrayOf(head.forward[0], head.forward[1], head.forward[2])
    )

    // ------------------------------------------------------------ overlays
    private fun updateOverlays() {
        val f = head.forward; val u = head.up; val r = head.right
        val faceYaw = M.yawOf(floatArrayOf(-f[0], 0f, -f[2]))

        // Toast: abaixo do centro da visão
        val tShow = System.currentTimeMillis() < toastUntil && toastMsg.isNotEmpty()
        toastPanel.item.visible = tShow
        if (tShow) {
            toastPanel.item.pos[0] = f[0] * 1.6f + u[0] * -0.55f
            toastPanel.item.pos[1] = f[1] * 1.6f + u[1] * -0.55f
            toastPanel.item.pos[2] = f[2] * 1.6f + u[2] * -0.55f
            toastPanel.item.yaw = faceYaw
        }

        // Chip do alvo (identifica o elemento apontado)
        val hovered = interaction.hovered
        val showChip = hovered != null && interaction.detected && point.active
        if (showChip) {
            val label = if (interaction.progress > 0.02f)
                "● ${hovered.hoverLabel} — aproxime a mão…"
            else
                "◦ ${hovered.hoverLabel}"
            if (label != chipLabel) { chipLabel = label; targetChip.repaint() }
            targetChip.item.visible = true
            targetChip.item.pos[0] = f[0] * 1.7f + u[0] * -0.72f
            targetChip.item.pos[1] = f[1] * 1.7f + u[1] * -0.72f
            targetChip.item.pos[2] = f[2] * 1.7f + u[2] * -0.72f
            targetChip.item.yaw = faceYaw
        } else {
            targetChip.item.visible = false
        }

        // HUD de performance (esquerda da visão)
        hudPanel.item.visible = hudVisible
        if (hudVisible) {
            hudPanel.item.pos[0] = f[0] * 1.5f + r[0] * -0.85f + u[0] * 0.35f
            hudPanel.item.pos[1] = f[1] * 1.5f + r[1] * -0.85f + u[1] * 0.35f
            hudPanel.item.pos[2] = f[2] * 1.5f + r[2] * -0.85f + u[2] * 0.35f
            hudPanel.item.yaw = faceYaw
            if ((timeSec * 4f).toInt() != lastHudSec) {
                lastHudSec = (timeSec * 4f).toInt()
                hudPanel.repaint()
            }
        }
    }

    // --------------------------------------------------------------- touch
    private fun wireTouch() {
        touch.onTap = {
            // Fallback real: seleção pelo centro da visão (gaze + tap)
            val ray = gazeRay()
            var best: Interactable? = null
            var bestD = 40f
            for (t in interactables) {
                if (!t.enabled) continue
                val d = t.rayHit(ray)
                if (d in 0.05f..bestD) { bestD = d; best = t }
            }
            if (best != null) interaction.forceSelect(best)
        }
        touch.onLongPress = { toggleMenu() }
        touch.onDoubleTap = { recenter() }
    }

    // ------------------------------------------------------------- shutdown
    fun shutdown() {
        head.stop()
        hands.destroy()
        sound.dispose()
    }
}

package com.agusvr.apps

import com.agusvr.handtracking.HandTracker
import com.agusvr.input.HeadTracker
import com.agusvr.interaction.Interactable
import com.agusvr.runtime.VrEngine
import com.agusvr.runtime.gl.CanvasTexture
import com.agusvr.runtime.gl.DynamicDraw
import com.agusvr.runtime.gl.MeshSet
import com.agusvr.runtime.gl.SceneObject
import com.agusvr.storage.AppDataStore
import com.agusvr.storage.Settings
import com.agusvr.ui.VrPanel

/**
 * Agus Applications — contrato de um aplicativo VR do Agus.
 * Qualquer app novo implementa esta interface e se registra no AppManager.
 */
interface AgusApp {
    val id: String
    val name: String
    fun build(env: SceneEnv)
    fun update(dtMs: Float) {}
    fun dispose() {}
}

/**
 * Fachada do engine para os apps: tudo que um app adiciona é rastreado
 * aqui e removido de forma limpa no [AppManager.close].
 */
class SceneEnv(val engine: VrEngine) {

    private val opaqueItems = ArrayList<SceneObject>()
    private val transparentItems = ArrayList<SceneObject>()
    private val dyns = ArrayList<DynamicDraw>()
    private val texs = ArrayList<CanvasTexture>()
    private val targets = ArrayList<Interactable>()
    val panels = ArrayList<VrPanel>()

    val meshes: MeshSet get() = engine.renderer.meshes

    fun addItem(item: SceneObject, opaque: Boolean = true) {
        if (opaque) engine.opaque.add(item) else engine.transparent.add(item)
        if (opaque) opaqueItems.add(item) else transparentItems.add(item)
    }

    fun addDynamic(d: DynamicDraw) {
        engine.dynamics.add(d)
        dyns.add(d)
    }

    fun addPanel(panel: VrPanel) {
        panel.registerTo(engine.transparent, engine.textures)
        engine.panels.add(panel)
        engine.interactables.add(panel.target)
        panels.add(panel)
        texs.add(panel.texture)
        transparentItems.add(panel.item)
        targets.add(panel.target)
    }

    fun addInteractable(t: Interactable) {
        engine.interactables.add(t)
        targets.add(t)
    }

    // Atalhos úteis para apps
    fun toast(msg: String) = engine.toast(msg)
    fun settings(): Settings = engine.settingsStore.current
    fun saveSettings() = engine.settingsStore.save()
    fun data(): AppDataStore = engine.data
    fun launch(appId: String) = engine.apps.launch(appId)
    fun exitToMenu() = engine.goHome()
    fun head(): HeadTracker = engine.head
    fun hands(): HandTracker = engine.hands
    fun setPassthrough(on: Boolean) = engine.setPassthrough(on)
    fun recenter() = engine.recenter()
    fun hudToggle() { engine.hudVisible = !engine.hudVisible }
    fun activity() = engine.activity

    /** Remove do engine tudo que este ambiente criou. */
    fun disposeAll() {
        engine.opaque.removeAll(opaqueItems)
        engine.transparent.removeAll(transparentItems)
        engine.dynamics.removeAll(dyns)
        engine.textures.removeAll(texs)
        engine.interactables.removeAll(targets)
        engine.panels.removeAll(panels)
        for (t in texs) t.destroy()
        opaqueItems.clear(); transparentItems.clear()
        dyns.clear(); texs.clear(); targets.clear(); panels.clear()
    }
}

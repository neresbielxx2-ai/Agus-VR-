package com.agusvr.apps

import com.agusvr.runtime.VrEngine

/**
 * Agus Applications — registro e ciclo de vida dos apps VR.
 *
 * Para adicionar um app novo:
 *  1. criar a classe implementando [AgusApp];
 *  2. registrar o factory em [create];
 *  3. (opcional) aparecer no menu/estantes via id.
 */
class AppManager(private val engine: VrEngine) {

    companion object {
        val ALL_IDS = listOf(
            "handlab", "library", "apps", "settings",
            "performance", "personal", "system", "notes"
        )
    }

    var current: AgusApp? = null
        private set
    var currentId: String? = null
        private set
    private var env: SceneEnv? = null

    fun launch(id: String) {
        if (id == currentId) return
        close()
        val app = create(id) ?: run {
            engine.toast("App desconhecido: $id")
            return
        }
        val e = SceneEnv(engine)
        app.build(e)
        current = app
        currentId = id
        env = e
        engine.data.recordUse(id)
        engine.onAppLaunched()
        engine.toast("Abrindo ${app.name}")
    }

    fun close() {
        current?.let {
            try { it.dispose() } catch (_: Exception) {}
        }
        env?.disposeAll()
        current = null
        env = null
        currentId = null
    }

    fun update(dtMs: Float) {
        current?.update(dtMs)
    }

    private fun create(id: String): AgusApp? = when (id) {
        "handlab" -> HandLabApp()
        "library" -> LibraryApp()
        "apps" -> AppsApp()
        "settings" -> SettingsApp()
        "performance" -> PerformanceApp()
        "personal" -> PersonalSpaceApp()
        "system" -> SystemApp()
        "notes" -> NotesApp()
        else -> null
    }
}

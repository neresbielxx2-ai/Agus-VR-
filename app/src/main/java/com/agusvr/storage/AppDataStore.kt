package com.agusvr.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Agus Storage — dados dinâmicos do runtime:
 *  - uso de aplicativos (base do Adaptive UI)
 *  - notas espaciais (Spatial Notes)
 *  - atalhos de gestos (Gesture Shortcuts)
 */
class AppDataStore(context: Context) {

    private val dir = File(context.filesDir, "agusvr").apply { mkdirs() }
    private val usageFile = File(dir, "usage.json")
    private val notesFile = File(dir, "notes.json")
    private val shortcutsFile = File(dir, "shortcuts.json")
    private val lock = Any()

    // ------------------------------------------------ Adaptive UI (uso)
    private val usage = HashMap<String, Int>()

    init {
        loadUsage()
        if (shortcutsFile.isFile.not()) installDefaultShortcuts()
    }

    private fun loadUsage() {
        try {
            if (usageFile.exists()) {
                val j = JSONObject(usageFile.readText())
                val keys = j.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    usage[k] = j.optInt(k, 0)
                }
            }
        } catch (_: Exception) {}
    }

    fun recordUse(appId: String) {
        synchronized(lock) {
            usage[appId] = (usage[appId] ?: 0) + 1
            try {
                val j = JSONObject()
                for ((k, v) in usage) j.put(k, v)
                usageFile.writeText(j.toString())
            } catch (_: Exception) {}
        }
    }

    fun useCount(appId: String): Int = synchronized(lock) { usage[appId] ?: 0 }

    /** Ordena uma lista de ids do mais usado para o menos usado. */
    fun sortByUsage(ids: List<String>): List<String> =
        ids.sortedByDescending { useCount(it) }

    // ------------------------------------------------ Spatial Notes
    data class Note(
        val id: String,
        var text: String,
        var color: Int,          // 0=ciano, 1=verde, 2=âmbar, 3=violeta
        val pos: FloatArray,     // posição no mundo (m)
        var yaw: Float
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("text", text)
            .put("color", color)
            .put("yaw", yaw.toDouble())
            .put("pos", JSONArray().put(pos[0].toDouble()).put(pos[1].toDouble()).put(pos[2].toDouble()))
    }

    fun loadNotes(): MutableList<Note> {
        val out = mutableListOf<Note>()
        try {
            if (notesFile.exists()) {
                val arr = JSONArray(notesFile.readText())
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    val p = j.optJSONArray("pos") ?: continue
                    val pos = floatArrayOf(
                        p.optDouble(0, 0.0).toFloat(),
                        p.optDouble(1, 1.2).toFloat(),
                        p.optDouble(2, -1.5).toFloat()
                    )
                    out.add(
                        Note(
                            id = j.optString("id", "note_$i"),
                            text = j.optString("text", "Nota"),
                            color = j.optInt("color", 0),
                            pos = pos,
                            yaw = j.optDouble("yaw", 0.0).toFloat()
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return out
    }

    fun saveNotes(notes: List<Note>) {
        synchronized(lock) {
            try {
                val arr = JSONArray()
                for (n in notes) arr.put(n.toJson())
                notesFile.writeText(arr.toString())
            } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------ Gesture Shortcuts
    /**
     * Ações disponíveis para atalhos de gesto:
     *  menu / focus / recenter / perf / home / none
     */
    val shortcutActions = listOf("menu", "focus", "recenter", "perf", "home", "none")
    private val shortcuts = LinkedHashMap<String, String>()

    private fun installDefaultShortcuts() {
        shortcuts["OPEN_HOLD"] = "menu"       // mão aberta parada ~1s → menu
        shortcuts["FIST_HOLD"] = "focus"      // punho fechado ~1s → focus mode
        shortcuts["TWO_HOLD"] = "perf"        // dois dedos (V) ~1s → HUD de performance
        shortcuts["PINCH_AIR"] = "recenter"   // pinch sem alvo → recentralizar
        saveShortcuts()
    }

    fun loadShortcuts(): Map<String, String> {
        synchronized(lock) {
            try {
                if (shortcutsFile.exists()) {
                    shortcuts.clear()
                    val j = JSONObject(shortcutsFile.readText())
                    val keys = j.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        shortcuts[k] = j.optString(k, "none")
                    }
                }
            } catch (_: Exception) {}
            if (shortcuts.isEmpty()) installDefaultShortcuts()
            return LinkedHashMap(shortcuts)
        }
    }

    fun setShortcut(gestureKey: String, action: String) {
        synchronized(lock) {
            shortcuts[gestureKey] = action
            saveShortcuts()
        }
    }

    fun shortcutFor(gestureKey: String): String =
        synchronized(lock) { shortcuts[gestureKey] ?: "none" }

    private fun saveShortcuts() {
        try {
            val j = JSONObject()
            for ((k, v) in shortcuts) j.put(k, v)
            shortcutsFile.writeText(j.toString())
        } catch (_: Exception) {}
    }
}

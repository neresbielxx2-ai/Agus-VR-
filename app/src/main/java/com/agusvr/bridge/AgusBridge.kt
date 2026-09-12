package com.agusvr.bridge

import android.content.Context
import android.net.wifi.WifiManager
import com.agusvr.handtracking.GestureClassifier
import com.agusvr.runtime.VrEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Agus Compatibility Layer — ponte de dados para o Roblox Studio.
 *
 * IMPORTANTE (política): o Agus VR nunca modifica, distribui ou interage
 * com o APK oficial do Roblox. Esta ponte apenas EXPORTA dados de
 * tracking em um protocolo aberto (JSON por TCP, uma linha por pacote),
 * para que experiências PRÓPRIAS criadas no Roblox Studio possam
 * reproduzir/receber esses dados via relay — ver docs/ROBLOX_STUDIO.md.
 */
object AgusBridge {

    const val PROTOCOL_VERSION = 1
    const val DEFAULT_PORT = 28097

    private var server: TcpBridgeServer? = null
    private val events = ArrayDeque<JSONObject>()
    private val eventsLock = Any()

    fun isEnabled(): Boolean = server != null

    fun start(engine: VrEngine, port: Int = DEFAULT_PORT): Boolean {
        if (server != null) return true
        val s = TcpBridgeServer(port, { packetJson(engine) }, hz = 30)
        val ok = s.start()
        if (ok) server = s
        return ok
    }

    fun stop() {
        server?.stop()
        server = null
    }

    /** Eventos de interação entram aqui e são drenados no próximo pacote. */
    fun recordEvent(type: String, target: String) {
        synchronized(eventsLock) {
            if (events.size > 64) events.removeFirst()
            events.addLast(JSONObject().put("type", type).put("target", target))
        }
    }

    fun endpoint(context: Context): String {
        val ip = localIp(context)
        return "tcp://$ip:$DEFAULT_PORT"
    }

    private fun localIp(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            String.format("%d.%d.%d.%d", ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }

    // ------------------------------------------------------------ pacotes
    /**
     * Formato do pacote (v1) — uma linha JSON por frame:
     * {
     *   "v":1, "t":<ms>,
     *   "head":{"pos":[0,0,0],"rot":[w,x,y,z]},
     *   "hands":[{ "side":"L|R", "present":bool, "gesture":"POINT|...",
     *              "palm":[x,y,z],
     *              "index":{"tip":[..],"dir":[..],"extended":bool},
     *              "pinch":0.21,
     *              "landmarks":[[x,y,z] × 21] }, ...],
     *   "events":[{"type":"select|grab|release","target":"id"}],
     *   "perf":{"fps":60,"mode":"Equilibrado"}
     * }
     */
    fun packetJson(engine: VrEngine): String {
        val root = JSONObject()
        root.put("v", PROTOCOL_VERSION)
        root.put("t", System.currentTimeMillis())

        // Cabeça (3DoF rotacional; posição fixa na origem)
        val q = FloatArray(4)
        engine.head.viewQuat(q)
        val head = JSONObject()
        head.put("pos", JSONArray().put(0).put(0).put(0))
        head.put("rot", JSONArray().put(r3(q[0])).put(r3(q[1])).put(r3(q[2])).put(r3(q[3])))
        root.put("head", head)

        // Mãos
        val hands = JSONArray()
        val hf = engine.hands.frame
        for (h in listOf(hf.left, hf.right)) {
            val j = JSONObject()
            j.put("side", if (h.side == 0) "L" else "R")
            j.put("present", h.present && System.currentTimeMillis() - h.lastSeenMs < 600)
            j.put("gesture", GestureClassifier.label(h.gesture))
            j.put("palm", JSONArray().put(r3(h.palm[0])).put(r3(h.palm[1])).put(r3(h.palm[2])))
            val idx = JSONObject()
            idx.put("tip", JSONArray().put(r3(h.indexTip[0])).put(r3(h.indexTip[1])).put(r3(h.indexTip[2])))
            idx.put("dir", JSONArray().put(r3(h.indexDir[0])).put(r3(h.indexDir[1])).put(r3(h.indexDir[2])))
            idx.put("extended", h.gesture.name == "POINT")
            j.put("index", idx)
            j.put("pinch", r3(h.pinchDistance))
            val lms = JSONArray()
            for (i in 0 until 21) {
                lms.put(JSONArray().put(r3(h.world[i][0])).put(r3(h.world[i][1])).put(r3(h.world[i][2])))
            }
            j.put("landmarks", lms)
            hands.put(j)
        }
        root.put("hands", hands)

        // Eventos acumulados
        val ev = JSONArray()
        synchronized(eventsLock) {
            while (events.isNotEmpty()) ev.put(events.removeFirst())
        }
        root.put("events", ev)

        // Telemetria útil para testes no Studio
        val perf = JSONObject()
        perf.put("fps", engine.perf.fps.toInt())
        perf.put("mode", com.agusvr.performance.PerfPolicy.modeName(engine.settingsStore.current.perfMode))
        root.put("perf", perf)

        return root.toString()
    }

    private fun r3(v: Float): Double = (Math.round(v * 1000.0) / 1000.0)
}

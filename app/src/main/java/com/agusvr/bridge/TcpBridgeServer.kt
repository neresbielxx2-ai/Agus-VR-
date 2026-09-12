package com.agusvr.bridge

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Agus Compatibility Layer / Roblox bridge — servidor TCP local.
 *
 * Transmite pacotes JSON (um por linha) na frequência configurada para
 * qualquer cliente conectado (ex.: relay em PC que alimenta o Roblox
 * Studio). Protocolo documentado em docs/PROTOCOL.md e
 * docs/ROBLOX_STUDIO.md.
 */
class TcpBridgeServer(
    private val port: Int,
    private val packetProvider: () -> String,
    private val hz: Int = 30
) {
    companion object { private const val TAG = "AgusBridge" }

    private var serverSocket: ServerSocket? = null
    private var running = AtomicBoolean(false)
    private val clients = ArrayList<PrintWriter>()
    private val clientsLock = Any()

    fun start(): Boolean {
        if (running.get()) return true
        return try {
            val ss = ServerSocket(port)
            serverSocket = ss
            running.set(true)

            Thread({
                while (running.get()) {
                    try {
                        val client = ss.accept()
                        handleClient(client)
                    } catch (_: Exception) {
                        if (running.get()) try { Thread.sleep(200) } catch (_: InterruptedException) {}
                    }
                }
            }, "agus-bridge-accept").apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "não foi possível abrir porta $port", e)
            false
        }
    }

    private fun handleClient(socket: Socket) {
        val out = PrintWriter(socket.getOutputStream(), true)
        synchronized(clientsLock) { clients.add(out) }
        Thread({
            try {
                val period = 1000L / hz.coerceIn(5, 60)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                socket.soTimeout = 50
                while (running.get() && !socket.isClosed) {
                    out.println(packetProvider())
                    // consome PING/lixo sem bloquear o ciclo
                    try {
                        while (reader.ready()) reader.readLine()
                    } catch (_: Exception) {}
                    Thread.sleep(period)
                }
            } catch (_: Exception) {
            } finally {
                synchronized(clientsLock) { clients.remove(out) }
                try { socket.close() } catch (_: Exception) {}
            }
        }, "agus-bridge-client").apply { isDaemon = true; start() }
    }

    fun connectedCount(): Int = synchronized(clientsLock) { clients.size }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        synchronized(clientsLock) {
            for (c in clients) { try { c.close() } catch (_: Exception) {} }
            clients.clear()
        }
    }
}

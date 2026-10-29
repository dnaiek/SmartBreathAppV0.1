package cl.smartbreath.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/** Classroom TCP demo. Plain TCP is NOT encrypted; use only on a trusted local network. */
class TcpBridge(
    private val onStatus: (String) -> Unit,
    private val onLine: (String) -> Unit
) {
    companion object {
        const val PORT = 45454
        fun localIpv4(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val network = interfaces.nextElement()
                    if (!network.isUp || network.isLoopback) continue
                    val addresses = network.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            val ip = address.hostAddress ?: continue
                            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip
                        }
                    }
                }
            } catch (_: SocketException) { }
            return "IP no disponible; verifica la red local"
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var output: BufferedWriter? = null
    @Volatile private var running = false
    private var serverJob: Job? = null
    private var clientJob: Job? = null

    private fun attach(connected: Socket) {
        synchronized(writeLock) {
            socket = connected
            output = BufferedWriter(OutputStreamWriter(connected.getOutputStream(), Charsets.UTF_8))
        }
    }

    private fun detach(connected: Socket) {
        synchronized(writeLock) {
            if (socket === connected) {
                output = null
                socket = null
            }
        }
        try { connected.close() } catch (_: Exception) { }
    }

    fun startServer() {
        if (running) return
        running = true
        serverJob = scope.launch {
            try {
                ServerSocket(PORT).use { server ->
                    serverSocket = server
                    onStatus("Servidor listo en puerto $PORT. Esperando cliente…")
                    while (running) {
                        val accepted = server.accept()
                        attach(accepted)
                        onStatus("Cliente conectado: ${accepted.inetAddress.hostAddress}")
                        try {
                            val reader = accepted.getInputStream().bufferedReader(Charsets.UTF_8)
                            while (running) {
                                val line = reader.readLine() ?: break
                                onLine(line)
                            }
                        } catch (e: Exception) {
                            if (running) onStatus("Cliente desconectado: ${e.message ?: e.javaClass.simpleName}")
                        } finally {
                            detach(accepted)
                            if (running) onStatus("Cliente desconectado. Esperando nueva conexión…")
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) onStatus("Error del servidor: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                serverSocket = null
                running = false
            }
        }
    }

    fun connect(host: String) {
        if (clientJob?.isActive == true) return
        running = true
        clientJob = scope.launch {
            val connected = Socket()
            try {
                onStatus("Conectando a $host:$PORT…")
                connected.connect(InetSocketAddress(host.trim(), PORT), 5000)
                attach(connected)
                onStatus("Conectado al monitor $host:$PORT")
                val reader = connected.getInputStream().bufferedReader(Charsets.UTF_8)
                while (running) {
                    val line = reader.readLine() ?: break
                    onLine(line)
                }
                if (running) onStatus("Conexión cerrada por el monitor")
            } catch (e: Exception) {
                if (running) onStatus("Conexión fallida: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                detach(connected)
            }
        }
    }

    /** Never write sockets on the Compose/UI thread. */
    fun send(line: String) {
        scope.launch {
            synchronized(writeLock) {
                val currentSocket = socket
                val currentOutput = output
                if (currentSocket == null || currentSocket.isClosed || currentOutput == null) return@synchronized
                try {
                    currentOutput.write(line)
                    currentOutput.newLine()
                    currentOutput.flush()
                } catch (e: Exception) {
                    onStatus("Error enviando mensaje: ${e.message ?: e.javaClass.simpleName}")
                    output = null
                    socket = null
                    try { currentSocket.close() } catch (_: Exception) { }
                }
            }
        }
    }

    fun close() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) { }
        synchronized(writeLock) {
            try { socket?.close() } catch (_: Exception) { }
            socket = null
            output = null
        }
        serverSocket = null
        serverJob?.cancel()
        clientJob?.cancel()
        onStatus("Desconectado")
    }
}

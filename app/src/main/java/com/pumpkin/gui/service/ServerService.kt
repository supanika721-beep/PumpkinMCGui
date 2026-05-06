package com.pumpkin.gui.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pumpkin.gui.MainActivity
import com.pumpkin.gui.R
import kotlinx.coroutines.*
import java.io.*

class ServerService : Service() {

    companion object {
        const val CHANNEL_ID = "pumpkin_server_channel"
        const val NOTIF_ID = 1
        const val TAG = "ServerService"

        // Callback untuk UI
        var onLogLine: ((String) -> Unit)? = null
        var onStateChanged: ((Boolean) -> Unit)? = null
        var onPlayersChanged: ((List<String>) -> Unit)? = null

        var isRunning = false
    }

    private val binder = ServerBinder()
    private var serverProcess: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // List pemain online (parse dari log)
    private val onlinePlayers = mutableListOf<String>()

    inner class ServerBinder : Binder() {
        fun getService(): ServerService = this@ServerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startServer()
            "STOP" -> stopServer()
        }
        return START_NOT_STICKY
    }

    fun startServer() {
        if (isRunning) return

        val binaryFile = File(filesDir, "pumpkin")

        if (!binaryFile.exists()) {
            onLogLine?.invoke("[ERROR] Binary 'pumpkin' tidak ditemukan di ${filesDir.absolutePath}")
            onLogLine?.invoke("[INFO] Letakkan binary pumpkin ke folder tersebut dan restart app")
            return
        }

        // Pastikan executable
        binaryFile.setExecutable(true, false)

        startForeground(NOTIF_ID, buildNotification("Server sedang berjalan..."))

        scope.launch {
            try {
                val pb = ProcessBuilder(binaryFile.absolutePath)
                pb.directory(filesDir)
                pb.redirectErrorStream(true)
                pb.environment()["HOME"] = filesDir.absolutePath
                pb.environment()["TMPDIR"] = cacheDir.absolutePath

                serverProcess = pb.start()
                stdinWriter = BufferedWriter(OutputStreamWriter(serverProcess!!.outputStream))

                isRunning = true
                withContext(Dispatchers.Main) {
                    onStateChanged?.invoke(true)
                    onLogLine?.invoke("[SYSTEM] Server Pumpkin berhasil dijalankan!")
                }

                // Baca output log secara real-time
                val reader = BufferedReader(InputStreamReader(serverProcess!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val logLine = line!!
                    withContext(Dispatchers.Main) {
                        onLogLine?.invoke(logLine)
                        parsePlayerEvents(logLine)
                    }
                }

                // Proses selesai
                val exitCode = serverProcess?.waitFor() ?: -1
                withContext(Dispatchers.Main) {
                    onLogLine?.invoke("[SYSTEM] Server berhenti (exit code: $exitCode)")
                    onStateChanged?.invoke(false)
                    onPlayersChanged?.invoke(emptyList())
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error menjalankan server", e)
                withContext(Dispatchers.Main) {
                    onLogLine?.invoke("[ERROR] ${e.message}")
                    onStateChanged?.invoke(false)
                }
            } finally {
                isRunning = false
                onlinePlayers.clear()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    fun stopServer() {
        scope.launch {
            try {
                // Kirim command "stop" ke server sebelum kill
                sendCommand("stop")
                delay(2000)
                serverProcess?.destroy()
            } catch (e: Exception) {
                serverProcess?.destroyForcibly()
            } finally {
                withContext(Dispatchers.Main) {
                    onLogLine?.invoke("[SYSTEM] Server dihentikan oleh pengguna")
                    onStateChanged?.invoke(false)
                    onPlayersChanged?.invoke(emptyList())
                }
                isRunning = false
                onlinePlayers.clear()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    fun sendCommand(command: String) {
        scope.launch {
            try {
                stdinWriter?.write(command)
                stdinWriter?.newLine()
                stdinWriter?.flush()
                withContext(Dispatchers.Main) {
                    onLogLine?.invoke("> $command")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onLogLine?.invoke("[ERROR] Gagal kirim command: ${e.message}")
                }
            }
        }
    }

    // Parse log Pumpkin untuk deteksi pemain join/leave
    private fun parsePlayerEvents(line: String) {
        // Format log Pumpkin: "... PlayerName joined the game"
        val joinRegex = Regex("""(\w+) (joined|logged in)""")
        val leaveRegex = Regex("""(\w+) (left|disconnected|logged out)""")

        joinRegex.find(line)?.let { match ->
            val player = match.groupValues[1]
            if (!onlinePlayers.contains(player)) {
                onlinePlayers.add(player)
                onPlayersChanged?.invoke(onlinePlayers.toList())
            }
        }

        leaveRegex.find(line)?.let { match ->
            val player = match.groupValues[1]
            onlinePlayers.remove(player)
            onPlayersChanged?.invoke(onlinePlayers.toList())
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pumpkin Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifikasi status server Minecraft"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 Pumpkin Server")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        serverProcess?.destroyForcibly()
    }
}

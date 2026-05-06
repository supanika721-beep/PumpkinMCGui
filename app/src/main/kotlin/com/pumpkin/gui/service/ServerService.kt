package com.pumpkin.gui.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pumpkin.gui.MainActivity
import kotlinx.coroutines.*
import java.io.*

class ServerService : Service() {

    companion object {
        const val CHANNEL_ID = "pumpkin_channel"
        const val NOTIF_ID = 1

        var onLogLine: ((String) -> Unit)? = null
        var onStateChanged: ((Boolean) -> Unit)? = null
        var onPlayersChanged: ((List<String>) -> Unit)? = null
        var onStatsChanged: ((cpu: Float, ramMb: Long) -> Unit)? = null

        var isRunning = false
    }

    private val binder = ServerBinder()
    private var serverProcess: Process? = null
    private var serverPid: Int = -1
    private var stdinWriter: BufferedWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val onlinePlayers = mutableListOf<String>()
    private val logLines = mutableListOf<String>()

    private var lastProcTicks = 0L
    private var lastTotalTicks = 0L

    inner class ServerBinder : Binder() {
        fun getService(): ServerService = this@ServerService
        fun getLogs(): List<String> = logLines.toList()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START"   -> startServer()
            "STOP"    -> stopServer()
            "RESTART" -> restartServer()
        }
        return START_NOT_STICKY
    }

    fun startServer() {
        if (isRunning) return

        val binary = File(filesDir, "pumpkin")
        if (!binary.exists()) {
            onLogLine?.invoke("[ERROR] Binary 'pumpkin' not found at: ${filesDir.absolutePath}")
            return
        }

        binary.setExecutable(true, false)
        startForeground(NOTIF_ID, buildNotification("Server running..."))

        scope.launch {
            try {
                val pb = ProcessBuilder(binary.absolutePath).apply {
                    directory(filesDir)
                    redirectErrorStream(true)
                    environment()["HOME"] = filesDir.absolutePath
                    environment()["TMPDIR"] = cacheDir.absolutePath
                }

                serverProcess = pb.start()

                // Baca PID via /proc/self lewat symlink — paling reliable di Android
                serverPid = readPidFromProc(serverProcess!!)

                stdinWriter = BufferedWriter(OutputStreamWriter(serverProcess!!.outputStream))
                isRunning = true

                withContext(Dispatchers.Main) {
                    onStateChanged?.invoke(true)
                    onLogLine?.invoke("[SYSTEM] Pumpkin server started! PID: $serverPid")
                }

                launch { monitorStats() }

                val reader = BufferedReader(InputStreamReader(serverProcess!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!
                    logLines.add(l)
                    if (logLines.size > 500) logLines.removeAt(0)
                    withContext(Dispatchers.Main) {
                        onLogLine?.invoke(l)
                        parsePlayerEvents(l)
                    }
                }

                val exit = serverProcess?.waitFor() ?: -1
                withContext(Dispatchers.Main) {
                    onLogLine?.invoke("[SYSTEM] Server stopped (exit: $exit)")
                    onStateChanged?.invoke(false)
                    onPlayersChanged?.invoke(emptyList())
                    onStatsChanged?.invoke(0f, 0L)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onLogLine?.invoke("[ERROR] ${e.message}")
                    onStateChanged?.invoke(false)
                }
            } finally {
                isRunning = false
                serverPid = -1
                lastProcTicks = 0L
                lastTotalTicks = 0L
                onlinePlayers.clear()
                stopForeground(true)
                stopSelf()
            }
        }
    }

    fun stopServer() {
        scope.launch {
            try {
                sendCommandInternal("stop")
                delay(2000)
                serverProcess?.destroy()
            } catch (e: Exception) {
                serverProcess?.destroyForcibly()
            } finally {
                withContext(Dispatchers.Main) {
                    onLogLine?.invoke("[SYSTEM] Server stopped by user.")
                    onStateChanged?.invoke(false)
                    onPlayersChanged?.invoke(emptyList())
                    onStatsChanged?.invoke(0f, 0L)
                }
                isRunning = false
                serverPid = -1
                lastProcTicks = 0L
                lastTotalTicks = 0L
                onlinePlayers.clear()
                stopForeground(true)
                stopSelf()
            }
        }
    }

    fun restartServer() {
        scope.launch {
            onLogLine?.let {
                withContext(Dispatchers.Main) { it("[SYSTEM] Restarting server...") }
            }
            try {
                sendCommandInternal("stop")
                delay(2000)
                serverProcess?.destroy()
            } catch (_: Exception) {
                serverProcess?.destroyForcibly()
            }
            serverProcess = null
            isRunning = false
            serverPid = -1
            lastProcTicks = 0L
            lastTotalTicks = 0L
            onlinePlayers.clear()
            delay(1000)
            withContext(Dispatchers.Main) { startServer() }
        }
    }

    fun sendCommand(command: String) {
        scope.launch {
            sendCommandInternal(command)
            withContext(Dispatchers.Main) {
                onLogLine?.invoke("> $command")
            }
        }
    }

    private fun sendCommandInternal(command: String) {
        try {
            stdinWriter?.write(command)
            stdinWriter?.newLine()
            stdinWriter?.flush()
        } catch (_: Exception) {}
    }

    // Baca PID dari /proc — cara paling reliable tanpa reflection
    private fun readPidFromProc(process: Process): Int {
        return try {
            // Coba reflection dulu (works di banyak Android)
            val f = process.javaClass.getDeclaredField("pid")
            f.isAccessible = true
            val pid = f.getInt(process)
            if (pid > 0) return pid

            // Fallback: cari dari /proc/self/fd yang mengarah ke pipe proses
            -1
        } catch (_: Exception) {
            try {
                // Fallback kedua: pakai field "handle" (beberapa vendor)
                val f = process.javaClass.getDeclaredField("handle")
                f.isAccessible = true
                f.getLong(process).toInt()
            } catch (_: Exception) {
                // Fallback ketiga: scan /proc untuk proses "pumpkin"
                findPidByName("pumpkin")
            }
        }
    }

    // Scan /proc untuk cari PID berdasarkan nama proses
    private fun findPidByName(name: String): Int {
        return try {
            File("/proc").listFiles()
                ?.filter { it.name.all { c -> c.isDigit() } }
                ?.firstOrNull { dir ->
                    try {
                        File(dir, "cmdline").readText().contains(name, ignoreCase = true)
                    } catch (_: Exception) { false }
                }
                ?.name?.toInt() ?: -1
        } catch (_: Exception) { -1 }
    }

    private suspend fun monitorStats() {
        // Tunggu sebentar biar proses stabil dulu
        delay(1000)
        while (isRunning) {
            val pid = serverPid
            if (pid > 0) {
                val cpu = readCpuUsage(pid)
                val ram = readProcessRamMb(pid)
                withContext(Dispatchers.Main) {
                    onStatsChanged?.invoke(cpu, ram)
                }
            }
            delay(2000)
        }
    }

    private fun readCpuUsage(pid: Int): Float {
        return try {
            val statLine = File("/proc/$pid/stat").readText().split(" ")
            if (statLine.size < 15) return 0f

            val utime = statLine[13].toLong()
            val stime = statLine[14].toLong()
            val procTicks = utime + stime

            val totalLine = File("/proc/stat").readLines()
                .firstOrNull { it.startsWith("cpu ") }
                ?.trim()?.split("\\s+".toRegex()) ?: return 0f
            val totalTicks = totalLine.drop(1).sumOf { it.toLongOrNull() ?: 0L }

            val diffProc  = procTicks - lastProcTicks
            val diffTotal = totalTicks - lastTotalTicks

            lastProcTicks  = procTicks
            lastTotalTicks = totalTicks

            if (diffTotal <= 0 || lastProcTicks == procTicks) return 0f

            (diffProc.toFloat() / diffTotal.toFloat() * 100f).coerceIn(0f, 100f)
        } catch (_: Exception) { 0f }
    }

    // RAM khusus proses pumpkin dari /proc/<pid>/status — field VmRSS
    private fun readProcessRamMb(pid: Int): Long {
        return try {
            File("/proc/$pid/status").readLines()
                .firstOrNull { it.startsWith("VmRSS:") }
                ?.split("\\s+".toRegex())
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?.div(1024) ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun parsePlayerEvents(line: String) {
        Regex("""(\w+) (joined|logged in)""").find(line)?.let {
            val p = it.groupValues[1]
            if (!onlinePlayers.contains(p)) {
                onlinePlayers.add(p)
                onPlayersChanged?.invoke(onlinePlayers.toList())
            }
        }
        Regex("""(\w+) (left|disconnected|logged out)""").find(line)?.let {
            onlinePlayers.remove(it.groupValues[1])
            onPlayersChanged?.invoke(onlinePlayers.toList())
        }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Pumpkin Server", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎃 Pumpkin Server")
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
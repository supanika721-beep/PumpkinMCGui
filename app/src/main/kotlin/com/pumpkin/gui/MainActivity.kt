package com.pumpkin.gui

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pumpkin.gui.databinding.ActivityMainBinding
import com.pumpkin.gui.service.ServerService
import com.pumpkin.gui.ui.LogAdapter
import com.pumpkin.gui.ui.PlayerAdapter
import java.io.File

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = checkNotNull(_binding)

    private var serverService: ServerService? = null
    private var isBound = false
    private val logAdapter = LogAdapter()
    private val playerAdapter = PlayerAdapter()
    private var scrollPending = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            serverService = (b as ServerService.ServerBinder).getService()
            isBound = true
        }
        override fun onServiceDisconnected(n: ComponentName?) {
            isBound = false
            serverService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupViews()
        setupCallbacks()
        checkBinary()
    }

    private fun checkBinary() {
        val dest = File(filesDir, "pumpkin")
        if (!dest.exists()) {
            logAdapter.addLog("[SYSTEM] Extracting pumpkin binary...")
            try {
                resources.openRawResource(R.raw.pumpkin).use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                dest.setExecutable(true, false)
                logAdapter.addLog("[SYSTEM] Binary extracted successfully, ready to run!")
            } catch (e: Exception) {
                logAdapter.addLog("[ERROR] Failed to extract binary: ${e.message}")
            }
        } else {
            logAdapter.addLog("[SYSTEM] Binary found, ready to run!")
        }
    }

    private fun setupViews() {
        binding.rvLog.apply {
            adapter = logAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).also { it.stackFromEnd = true }
        }
        binding.rvPlayers.apply {
            adapter = playerAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        binding.btnStartStop.setOnClickListener {
            if (ServerService.isRunning) confirmStop() else startServer()
        }

        binding.btnRestart.setOnClickListener {
            if (!ServerService.isRunning) {
                Toast.makeText(this, "Server is not running", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Restart Server?")
                .setMessage("Server will restart, players will be disconnected briefly.")
                .setPositiveButton("Restart") { _, _ ->
                    startService(Intent(this, ServerService::class.java).apply { action = "RESTART" })
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnCopyLog.setOnClickListener {
            val logs = logAdapter.getAllLogs().joinToString("\n")
            if (logs.isEmpty()) {
                Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("pumpkin_log", logs))
            Toast.makeText(this, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSend.setOnClickListener { sendCommand() }
        binding.etCommand.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { sendCommand(); true } else false
        }

        binding.btnShowPath.setOnClickListener {
            val path = filesDir.absolutePath
            MaterialAlertDialogBuilder(this)
                .setTitle("📁 Binary Location")
                .setMessage("Binary stored at:\n\n$path/pumpkin")
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy Path") { _, _ ->
                    val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("path", path))
                    Toast.makeText(this, "Path copied!", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun setupCallbacks() {
        ServerService.onLogLine = { line ->
            logAdapter.addLog(line)
            if (!scrollPending) {
                scrollPending = true
                binding.rvLog.postDelayed({
                    binding.rvLog.scrollToPosition(logAdapter.itemCount - 1)
                    scrollPending = false
                }, 100)
            }
        }
        ServerService.onStateChanged = { running -> updateState(running) }
        ServerService.onPlayersChanged = { players ->
            playerAdapter.updatePlayers(players)
            binding.tvPlayerCount.text = "Players Online: ${players.size}"
        }
        ServerService.onStatsChanged = { cpu, ramMb ->
            binding.tvCpu.text = "CPU: ${"%.1f".format(cpu)}%"
            binding.tvRam.text = "RAM: ${ramMb}MB"
            binding.progressCpu.progress = cpu.toInt()
            // Anggap max RAM proses 2GB untuk progress bar
            binding.progressRam.progress = (ramMb / 20).toInt().coerceIn(0, 100)
        }
    }

    private fun startServer() {
        val intent = Intent(this, ServerService::class.java).apply { action = "START" }
        startService(intent)
        bindService(intent, conn, Context.BIND_AUTO_CREATE)
        logAdapter.addLog("[SYSTEM] Starting server...")
    }

    private fun confirmStop() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Stop Server?")
            .setMessage("All players will be disconnected.")
            .setPositiveButton("Stop") { _, _ ->
                startService(Intent(this, ServerService::class.java).apply { action = "STOP" })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendCommand() {
        val cmd = binding.etCommand.text.toString().trim()
        if (cmd.isEmpty()) return
        if (!ServerService.isRunning) {
            Toast.makeText(this, "Server is not running", Toast.LENGTH_SHORT).show()
            return
        }
        serverService?.sendCommand(cmd)
        binding.etCommand.text?.clear()
    }

    private fun updateState(running: Boolean) {
        binding.btnStartStop.text = if (running) "⏹ Stop" else "▶ Start"
        binding.tvStatus.text = if (running) "● RUNNING" else "● STOPPED"
        binding.tvStatus.setTextColor(
            if (running) getColor(android.R.color.holo_green_light)
            else getColor(android.R.color.holo_red_light)
        )
        if (!running) {
            binding.tvPlayerCount.text = "Players Online: 0"
            binding.tvCpu.text = "CPU: 0.0%"
            binding.tvRam.text = "RAM: 0MB"
            binding.progressCpu.progress = 0
            binding.progressRam.progress = 0
        }
    }

    override fun onStart() {
        super.onStart()
        if (ServerService.isRunning) {
            bindService(Intent(this, ServerService::class.java), conn, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) { unbindService(conn); isBound = false }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
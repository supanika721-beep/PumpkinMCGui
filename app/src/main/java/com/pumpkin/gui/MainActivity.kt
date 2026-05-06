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

    private lateinit var binding: ActivityMainBinding
    private var serverService: ServerService? = null
    private var isBound = false

    private val logAdapter = LogAdapter()
    private val playerAdapter = PlayerAdapter()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as ServerService.ServerBinder
            serverService = b.getService()
            isBound = true
            updateServerState(ServerService.isRunning)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            serverService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupCallbacks()
        checkBinaryExists()
    }

    private fun setupViews() {
        // Console log RecyclerView
        binding.rvLog.apply {
            adapter = logAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }

        // Players RecyclerView
        binding.rvPlayers.apply {
            adapter = playerAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // Start/Stop button
        binding.btnStartStop.setOnClickListener {
            if (ServerService.isRunning) {
                showStopConfirmation()
            } else {
                startServer()
            }
        }

        // Send command
        binding.btnSend.setOnClickListener { sendCommand() }
        binding.etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCommand()
                true
            } else false
        }

        // Copy path button - bantu user tau dimana harus taruh binary
        binding.btnShowPath.setOnClickListener {
            val path = filesDir.absolutePath
            MaterialAlertDialogBuilder(this)
                .setTitle("📁 Lokasi Binary")
                .setMessage("Letakkan file 'pumpkin' di:\n\n$path\n\nGunakan Termux atau file manager untuk copy binary ke folder ini.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy Path") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", path))
                    Toast.makeText(this, "Path disalin!", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun setupCallbacks() {
        ServerService.onLogLine = { line ->
            logAdapter.addLog(line)
            binding.rvLog.scrollToPosition(logAdapter.itemCount - 1)
        }

        ServerService.onStateChanged = { running ->
            updateServerState(running)
        }

        ServerService.onPlayersChanged = { players ->
            playerAdapter.updatePlayers(players)
            binding.tvPlayerCount.text = "Pemain Online: ${players.size}"
        }
    }

    private fun startServer() {
        val binary = File(filesDir, "pumpkin")
        if (!binary.exists()) {
            showBinaryNotFoundDialog()
            return
        }
        val intent = Intent(this, ServerService::class.java).apply {
            action = "START"
        }
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        logAdapter.addLog("[SYSTEM] Memulai server...")
    }

    private fun showStopConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hentikan Server?")
            .setMessage("Server akan dihentikan dan semua pemain akan disconnect.")
            .setPositiveButton("Hentikan") { _, _ ->
                val intent = Intent(this, ServerService::class.java).apply {
                    action = "STOP"
                }
                startService(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun sendCommand() {
        val cmd = binding.etCommand.text.toString().trim()
        if (cmd.isEmpty()) return
        if (!ServerService.isRunning) {
            Toast.makeText(this, "Server tidak berjalan", Toast.LENGTH_SHORT).show()
            return
        }
        serverService?.sendCommand(cmd)
        binding.etCommand.text?.clear()
    }

    private fun updateServerState(running: Boolean) {
        if (running) {
            binding.btnStartStop.text = "⏹ Stop Server"
            binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            binding.tvStatus.text = "● RUNNING"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            binding.btnStartStop.text = "▶ Start Server"
            binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_dark))
            binding.tvStatus.text = "● STOPPED"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light))
            binding.tvPlayerCount.text = "Pemain Online: 0"
        }
    }

    private fun checkBinaryExists() {
        val binary = File(filesDir, "pumpkin")
        if (!binary.exists()) {
            logAdapter.addLog("[SYSTEM] Selamat datang di Pumpkin Server GUI!")
            logAdapter.addLog("[WARN] Binary 'pumpkin' belum ditemukan.")
            logAdapter.addLog("[INFO] Tekan tombol 📁 untuk melihat lokasi penyimpanan binary.")
        } else {
            logAdapter.addLog("[SYSTEM] Binary 'pumpkin' ditemukan! Siap dijalankan.")
        }
    }

    private fun showBinaryNotFoundDialog() {
        val path = filesDir.absolutePath
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Binary Tidak Ditemukan")
            .setMessage("File 'pumpkin' tidak ditemukan.\n\nCopy binary ke:\n$path\n\nContoh via Termux:\ncp ~/pumpkin \"$path/pumpkin\"")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        if (ServerService.isRunning) {
            val intent = Intent(this, ServerService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}

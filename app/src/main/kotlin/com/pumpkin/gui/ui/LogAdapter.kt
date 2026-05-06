package com.pumpkin.gui.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val logs = mutableListOf<String>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        v.setPadding(8, 2, 8, 2)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = logs[position]
        holder.tv.text = line
        holder.tv.textSize = 11f
        holder.tv.typeface = Typeface.MONOSPACE
        holder.tv.setTextColor(colorFor(line))
    }

    override fun getItemCount() = logs.size

    fun addLog(raw: String) {
        // Strip semua ANSI escape codes: \x1b[...m
        val clean = raw.replace(Regex("\u001B\\[[;\\d]*m"), "").trim()
        if (clean.isEmpty()) return

        if (logs.size >= 500) {
            logs.removeAt(0)
            notifyItemRemoved(0)
        }
        logs.add(clean)
        notifyItemInserted(logs.size - 1)
    }

    private fun colorFor(line: String): Int = when {
        line.contains("ERROR", ignoreCase = true)  -> Color.parseColor("#FF5555")
        line.contains("WARN", ignoreCase = true)   -> Color.parseColor("#FFB86C")
        line.contains("INFO", ignoreCase = true)   -> Color.parseColor("#50FA7B")
        line.startsWith("[SYSTEM]")                -> Color.parseColor("#8BE9FD")
        line.startsWith("[ERROR]")                 -> Color.parseColor("#FF5555")
        line.startsWith(">")                       -> Color.parseColor("#BD93F9")
        else -> Color.parseColor("#F8F8F2")
    }
    
    fun getAllLogs(): List<String> = logs.toList()
}
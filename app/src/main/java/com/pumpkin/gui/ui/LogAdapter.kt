package com.pumpkin.gui.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val logs = mutableListOf<String>()
    private val MAX_LOGS = 500

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLog: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        view.setPadding(8, 2, 8, 2)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val line = logs[position]
        holder.tvLog.text = line
        holder.tvLog.textSize = 11f
        holder.tvLog.typeface = android.graphics.Typeface.MONOSPACE

        // Warnai berdasarkan prefix
        holder.tvLog.setTextColor(when {
            line.startsWith("[ERROR]") -> Color.parseColor("#FF5555")
            line.startsWith("[WARN]")  -> Color.parseColor("#FFB86C")
            line.startsWith("[SYSTEM]")-> Color.parseColor("#8BE9FD")
            line.startsWith("[INFO]")  -> Color.parseColor("#A8FF78")
            line.startsWith(">")       -> Color.parseColor("#BD93F9")
            else -> Color.parseColor("#F8F8F2")
        })
    }

    override fun getItemCount() = logs.size

    fun addLog(line: String) {
        logs.add(line)
        // Batasi max log supaya tidak OOM
        if (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        notifyItemInserted(logs.size - 1)
    }

    fun clearLogs() {
        logs.clear()
        notifyDataSetChanged()
    }
}

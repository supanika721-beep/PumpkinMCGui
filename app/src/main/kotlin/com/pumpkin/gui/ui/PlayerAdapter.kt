package com.pumpkin.gui.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlayerAdapter : RecyclerView.Adapter<PlayerAdapter.VH>() {

    private val players = mutableListOf<String>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = "🟢 ${players[position]}"
        holder.tv.textSize = 13f
    }

    override fun getItemCount() = players.size

    fun updatePlayers(list: List<String>) {
        players.clear()
        players.addAll(list)
        notifyDataSetChanged()
    }
}

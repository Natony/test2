package com.example.test2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConfigAdapter(
    private val onItemClick: (ConfigItem) -> Unit,
    private val onDeleteClick: (ConfigItem) -> Unit
) : RecyclerView.Adapter<ConfigAdapter.ViewHolder>() {

    private val items = mutableListOf<ConfigItem>()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_config, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvAddress.text = "${item.ipAddress}:${item.port}"
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    // Thêm phương thức updateData
    fun updateData(newItems: List<ConfigItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
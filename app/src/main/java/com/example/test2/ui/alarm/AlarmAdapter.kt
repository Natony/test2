package com.example.test2.ui.alarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.R
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter for displaying alarms in a RecyclerView
 */
class AlarmAdapter(private val listener: AlarmAdapterListener) :
    ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    interface AlarmAdapterListener {
        fun onAlarmSelected(alarm: Alarm, isSelected: Boolean)
        fun onAlarmClick(alarm: Alarm)
    }

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val severityIndicator: View = itemView.findViewById(R.id.vwSeverityIndicator)
        private val alarmCode: TextView = itemView.findViewById(R.id.tvAlarmCode)
        private val alarmMessage: TextView = itemView.findViewById(R.id.tvAlarmMessage)
        private val alarmTime: TextView = itemView.findViewById(R.id.tvAlarmTime)
        private val acknowledgedTextView: TextView = itemView.findViewById(R.id.tvAcknowledged)
        private val checkbox: CheckBox = itemView.findViewById(R.id.cbSelected)

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun bind(alarm: Alarm, listener: AlarmAdapterListener) {
            // Set alarm data
            alarmCode.text = alarm.code
            alarmMessage.text = alarm.message
            alarmTime.text = dateFormat.format(alarm.timestamp)

            // Set severity color
            severityIndicator.setBackgroundColor(alarm.severity.color)

            // Show/hide acknowledged status
            acknowledgedTextView.visibility = if (alarm.isAcknowledged) View.VISIBLE else View.GONE

            // Handle checkbox state without triggering listener
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = alarm.selected

            // Setup click listeners
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                alarm.selected = isChecked
                listener.onAlarmSelected(alarm, isChecked)
            }

            itemView.setOnClickListener {
                listener.onAlarmClick(alarm)
            }

            // Visual indication for active/inactive alarms
            if (!alarm.isActive) {
                itemView.alpha = 0.6f
            } else {
                itemView.alpha = 1.0f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    // Update alarm selection state
    fun setSelected(alarmId: Int, isSelected: Boolean) {
        val currentList = currentList.toMutableList()
        val position = currentList.indexOfFirst { it.id == alarmId }

        if (position != -1) {
            currentList[position] = currentList[position].copy(selected = isSelected)
            submitList(currentList)
        }
    }

    // Select/deselect all alarms
    fun selectAll(select: Boolean) {
        val updatedList = currentList.map { it.copy(selected = select) }
        submitList(updatedList)
    }

    // Get all selected alarms
    fun getSelectedAlarms(): List<Alarm> {
        return currentList.filter { it.selected }
    }
}

/**
 * DiffUtil callback for optimized RecyclerView updates
 */
class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
    override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
        return oldItem == newItem
    }
}
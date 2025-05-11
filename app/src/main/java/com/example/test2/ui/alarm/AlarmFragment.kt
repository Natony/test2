package com.example.test2.ui.alarm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.R
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AlarmFragment : Fragment(), AlarmAdapter.AlarmAdapterListener {

    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var tvConnectionStatus: TextView
    private lateinit var rvAlarms: RecyclerView
    private lateinit var btnAcknowledge: Button
    private lateinit var btnAcknowledgeAll: Button

    private val viewModel: AlarmViewModel by viewModels {
        AlarmViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_alarm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus)
        rvAlarms = view.findViewById(R.id.rvAlarms)
        btnAcknowledge = view.findViewById(R.id.btnAcknowledge)
        btnAcknowledgeAll = view.findViewById(R.id.btnAcknowledgeAll)

        // Setup RecyclerView
        setupRecyclerView()

        // Setup button listeners
        setupButtons()

        // Observe ViewModel state
        observeViewModelState()

        // Initialize alarms monitoring
        viewModel.initializeAlarms()
    }

    private fun setupRecyclerView() {
        alarmAdapter = AlarmAdapter(this)
        rvAlarms.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alarmAdapter
        }
    }

    private fun setupButtons() {
        btnAcknowledge.setOnClickListener {
            val selectedAlarms = alarmAdapter.getSelectedAlarms()
            if (selectedAlarms.isNotEmpty()) {
                viewModel.acknowledgeAlarms(selectedAlarms.map { it.id })
            } else {
                Toast.makeText(requireContext(), "No alarms selected", Toast.LENGTH_SHORT).show()
            }
        }

        btnAcknowledgeAll.setOnClickListener {
            viewModel.acknowledgeAllAlarms()
        }
    }

    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe connection status - StateFlow already has distinctUntilChanged behavior
                launch {
                    viewModel.connectionStatus.collect { isConnected ->
                        updateConnectionStatus(isConnected)
                    }
                }

                // Observe alarms
                launch {
                    viewModel.alarms.collect { alarms ->
                        alarmAdapter.submitList(alarms)

                        // Update buttons state based on whether there are active alarms
                        val hasActiveAlarms = alarms.any { it.isActive && !it.isAcknowledged }
                        btnAcknowledgeAll.isEnabled = hasActiveAlarms
                    }
                }

                // Observe selected alarm count - only apply map with distinctUntilChanged if needed
                launch {
                    viewModel.alarms
                        .map { alarms -> alarms.count { it.selected } }
                        .collect { selectedCount ->
                            btnAcknowledge.isEnabled = selectedCount > 0
                        }
                }
            }
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        tvConnectionStatus.apply {
            text = if (isConnected) "Connected" else "Disconnected"
            setTextColor(resources.getColor(
                if (isConnected) android.R.color.holo_green_dark
                else android.R.color.holo_red_dark,
                null
            ))
        }
    }

    override fun onAlarmSelected(alarm: Alarm, isSelected: Boolean) {
        viewModel.updateAlarmSelection(alarm.id, isSelected)
    }

    override fun onAlarmClick(alarm: Alarm) {
        // Example of showing alarm details or taking action on alarm click
        if (alarm.isAcknowledged && !alarm.isActive) {
            // If alarm is acknowledged and inactive, clear it
            viewModel.clearAcknowledgedAlarm(alarm.id)
        } else if (!alarm.isAcknowledged) {
            // Toggle selection for unacknowledged alarms
            viewModel.updateAlarmSelection(alarm.id, !alarm.selected)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.pauseConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only call shutdown if the fragment is being destroyed completely
        if (requireActivity().isFinishing) {
            viewModel.shutdown()
        }
    }
}
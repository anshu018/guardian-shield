package com.guardianshield.parent.ui.controls

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.guardianshield.parent.databinding.FragmentControlsBinding
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.domain.models.CommandType
import com.guardianshield.parent.domain.models.RemoteCommand
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ControlsFragment : Fragment() {

    private var _binding: FragmentControlsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ControlsViewModel by viewModels()

    private var spinnerAdapter: ArrayAdapter<String>? = null
    private var cachedChildrenList = emptyList<Child>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentControlsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinner()
        setupListeners()
        observeViewModel()
    }

    private fun setupSpinner() {
        spinnerAdapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mutableListOf()
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerChildren.adapter = spinnerAdapter

        binding.spinnerChildren.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < cachedChildrenList.size) {
                    val child = cachedChildrenList[position]
                    viewModel.selectChild(child.id)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.btnLockDevice.setOnClickListener {
            viewModel.sendRemoteCommand(CommandType.LOCK, emptyMap())
            Toast.makeText(requireContext(), "Lock Command Dispatched", Toast.LENGTH_SHORT).show()
        }

        binding.btnStartSiren.setOnClickListener {
            viewModel.sendRemoteCommand(CommandType.ALARM, mapOf("action" to "start"))
            Toast.makeText(requireContext(), "Start Siren Dispatched", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopSiren.setOnClickListener {
            viewModel.sendRemoteCommand(CommandType.ALARM, mapOf("action" to "stop"))
            Toast.makeText(requireContext(), "Stop Siren Dispatched", Toast.LENGTH_SHORT).show()
        }

        binding.btnSendMessage.setOnClickListener {
            val messageText = binding.etWarningMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                viewModel.sendRemoteCommand(CommandType.MESSAGE, mapOf("message" to messageText))
                binding.etWarningMessage.text.clear()
                Toast.makeText(requireContext(), "Notice Message Dispatched", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Please enter a message text", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBlockApp.setOnClickListener {
            val packageName = binding.etBlockedPackage.text.toString().trim()
            if (packageName.isNotEmpty()) {
                viewModel.sendRemoteCommand(CommandType.BLOCK_APP, mapOf("packages" to packageName))
                binding.etBlockedPackage.text.clear()
                Toast.makeText(requireContext(), "Block App Dispatched", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Please enter a package name", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearBlocked.setOnClickListener {
            viewModel.sendRemoteCommand(CommandType.BLOCK_APP, mapOf("packages" to "[]"))
            Toast.makeText(requireContext(), "Clear Block List Dispatched", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ControlsUiState.Loading -> {
                        // Optional loading indicator or disable button interactions
                        binding.tvActiveChildLabel.text = "Loading..."
                    }
                    is ControlsUiState.Success -> {
                        cachedChildrenList = state.children
                        val names = state.children.map { it.name }

                        spinnerAdapter?.apply {
                            clear()
                            addAll(names)
                            notifyDataSetChanged()
                        }

                        state.selectedChild?.let { activeChild ->
                            val activeIdx = state.children.indexOfFirst { it.id == activeChild.id }
                            if (activeIdx >= 0 && activeIdx != binding.spinnerChildren.selectedItemPosition) {
                                binding.spinnerChildren.setSelection(activeIdx)
                            }
                            binding.tvActiveChildLabel.text = "Active: ${activeChild.name}"
                        }

                        state.errorMsg?.let { msg ->
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        }

                        updateFeedbackLogs(state.commandsList)
                    }
                    is ControlsUiState.Error -> {
                        binding.tvActiveChildLabel.text = "Error Loading Profiles"
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateFeedbackLogs(commands: List<RemoteCommand>) {
        if (commands.isEmpty()) {
            binding.tvFeedbackLog.text = "No commands transmitted in this session yet."
            return
        }

        val logBuilder = StringBuilder()
        commands.take(5).forEach { cmd ->
            val status = if (cmd.executed) {
                "🟢 SUCCESS (Executed)"
            } else {
                "🟡 PENDING (Transmitted)"
            }
            val payloadDetails = if (cmd.payload.isNotEmpty()) " (${cmd.payload.values.firstOrNull()})" else ""
            logBuilder.append("• ${cmd.command.name}$payloadDetails — $status\n")
        }
        binding.tvFeedbackLog.text = logBuilder.toString().trim()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

package com.guardianshield.parent.ui.monitoring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import com.guardianshield.parent.databinding.FragmentMonitoringBinding
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.ui.monitoring.adapters.CallLogAdapter
import com.guardianshield.parent.ui.monitoring.adapters.ContactAdapter
import com.guardianshield.parent.ui.monitoring.adapters.SmsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MonitoringFragment : Fragment() {

    private var _binding: FragmentMonitoringBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MonitoringViewModel by viewModels()

    private val smsAdapter = SmsAdapter()
    private val callAdapter = CallLogAdapter()
    private val contactAdapter = ContactAdapter()

    private var spinnerAdapter: ArrayAdapter<String>? = null
    private var cachedChildrenList = emptyList<Child>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonitoringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPagerAndTabs()
        setupSpinner()
        observeViewModel()
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPager.adapter = MonitoringPagerAdapter(smsAdapter, callAdapter, contactAdapter)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "SMS PREVIEWS"
                1 -> "CALL LOGS"
                2 -> "CONTACTS"
                else -> ""
            }
        }.attach()
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is MonitoringUiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.panelEmptyState.visibility = View.GONE
                    }
                    is MonitoringUiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        
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

                        smsAdapter.submitList(state.smsPreviews)
                        callAdapter.submitList(state.callLogs)
                        contactAdapter.submitList(state.contacts)

                        val currentTab = binding.viewPager.currentItem
                        val isEmpty = when (currentTab) {
                            0 -> state.smsPreviews.isEmpty()
                            1 -> state.callLogs.isEmpty()
                            2 -> state.contacts.isEmpty()
                            else -> false
                        }

                        if (state.selectedChild == null) {
                            binding.panelEmptyState.visibility = View.VISIBLE
                            binding.tvEmptyReason.text = "No linked child profiles found. Add a child from the dashboard first."
                        } else if (isEmpty) {
                            binding.panelEmptyState.visibility = View.VISIBLE
                            binding.tvEmptyReason.text = "No captured data has been uploaded yet for this child."
                        } else {
                            binding.panelEmptyState.visibility = View.GONE
                        }
                    }
                    is MonitoringUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.panelEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyReason.text = state.message
                    }
                }
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val state = viewModel.uiState.value
                if (state is MonitoringUiState.Success) {
                    val isEmpty = when (position) {
                        0 -> state.smsPreviews.isEmpty()
                        1 -> state.callLogs.isEmpty()
                        2 -> state.contacts.isEmpty()
                        else -> false
                    }
                    if (isEmpty) {
                        binding.panelEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyReason.text = "No captured data has been uploaded yet for this child."
                    } else {
                        binding.panelEmptyState.visibility = View.GONE
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class MonitoringPagerAdapter(
    private val smsAdapter: RecyclerView.Adapter<*>,
    private val callAdapter: RecyclerView.Adapter<*>,
    private val contactAdapter: RecyclerView.Adapter<*>
) : RecyclerView.Adapter<MonitoringPagerAdapter.PageViewHolder>() {

    override fun getItemCount(): Int = 3

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val recyclerView = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(parent.context)
            setPadding(0, 12, 0, 12)
            clipToPadding = false
        }
        return PageViewHolder(recyclerView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val targetAdapter = when (position) {
            0 -> smsAdapter
            1 -> callAdapter
            2 -> contactAdapter
            else -> throw IllegalArgumentException()
        }
        holder.recyclerView.adapter = targetAdapter
    }

    class PageViewHolder(val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView)
}

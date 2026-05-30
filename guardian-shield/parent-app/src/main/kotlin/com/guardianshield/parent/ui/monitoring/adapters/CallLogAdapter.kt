package com.guardianshield.parent.ui.monitoring.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardianshield.parent.databinding.ItemCallLogBinding
import com.guardianshield.parent.domain.models.CallLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogAdapter : ListAdapter<CallLog, CallLogAdapter.CallViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val binding = ItemCallLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CallViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CallViewHolder(
        private val binding: ItemCallLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(call: CallLog) {
            binding.tvCallName.text = call.contactName ?: "Unknown"
            binding.tvCallPhone.text = call.phoneNumber

            // Format duration e.g. "3m 45s"
            val durationText = formatDuration(call.durationSeconds)
            binding.tvCallType.text = "${call.callType} • $durationText"

            // Adjust styles/colors depending on CallType
            when (call.callType) {
                "INCOMING" -> {
                    binding.viewCallIndicator.setBackgroundColor(Color.parseColor("#10B981"))
                    binding.tvCallType.setTextColor(Color.parseColor("#10B981"))
                }
                "OUTGOING" -> {
                    binding.viewCallIndicator.setBackgroundColor(Color.parseColor("#3B82F6"))
                    binding.tvCallType.setTextColor(Color.parseColor("#3B82F6"))
                }
                "MISSED" -> {
                    binding.viewCallIndicator.setBackgroundColor(Color.parseColor("#EF4444"))
                    binding.tvCallType.setTextColor(Color.parseColor("#EF4444"))
                }
                else -> {
                    binding.viewCallIndicator.setBackgroundColor(Color.parseColor("#94A3B8"))
                    binding.tvCallType.setTextColor(Color.parseColor("#94A3B8"))
                }
            }

            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            binding.tvCallTime.text = sdf.format(Date(call.timestamp))
        }

        private fun formatDuration(seconds: Int): String {
            return if (seconds >= 60) {
                val min = seconds / 60
                val sec = seconds % 60
                "${min}m ${sec}s"
            } else {
                "${seconds}s"
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CallLog>() {
        override fun areItemsTheSame(oldItem: CallLog, newItem: CallLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallLog, newItem: CallLog): Boolean {
            return oldItem == newItem
        }
    }
}

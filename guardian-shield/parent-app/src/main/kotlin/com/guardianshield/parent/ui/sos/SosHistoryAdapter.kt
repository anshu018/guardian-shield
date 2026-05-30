package com.guardianshield.parent.ui.sos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardianshield.parent.databinding.ItemSosHistoryBinding
import com.guardianshield.parent.domain.models.SosEvent
import java.text.SimpleDateFormat
import java.util.Locale

class SosHistoryAdapter : ListAdapter<SosEvent, SosHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSosHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemSosHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        private val sdf = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())

        fun bind(event: SosEvent) {
            binding.tvHistoryStatus.text = if (event.active) "Emergency Active SOS" else "SOS Alarm Resolved"
            binding.tvHistoryTime.text = sdf.format(event.triggeredAt)
            binding.tvHistoryCoordinates.text = String.format(Locale.US, "%.4f, %.4f", event.lat, event.lng)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SosEvent>() {
        override fun areItemsTheSame(oldItem: SosEvent, newItem: SosEvent): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SosEvent, newItem: SosEvent): Boolean = oldItem == newItem
    }
}

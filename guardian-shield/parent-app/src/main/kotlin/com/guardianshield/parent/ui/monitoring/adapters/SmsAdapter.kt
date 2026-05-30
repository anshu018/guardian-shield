package com.guardianshield.parent.ui.monitoring.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardianshield.parent.databinding.ItemSmsPreviewBinding
import com.guardianshield.parent.domain.models.SmsPreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsAdapter : ListAdapter<SmsPreview, SmsAdapter.SmsViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val binding = ItemSmsPreviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SmsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SmsViewHolder(
        private val binding: ItemSmsPreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(sms: SmsPreview) {
            binding.tvSmsSender.text = sms.contactName ?: "Unknown"
            binding.tvSmsPhone.text = sms.phoneNumber
            binding.tvSmsBody.text = sms.messageBody
            
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            binding.tvSmsTime.text = sdf.format(Date(sms.timestamp))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SmsPreview>() {
        override fun areItemsTheSame(oldItem: SmsPreview, newItem: SmsPreview): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SmsPreview, newItem: SmsPreview): Boolean {
            return oldItem == newItem
        }
    }
}

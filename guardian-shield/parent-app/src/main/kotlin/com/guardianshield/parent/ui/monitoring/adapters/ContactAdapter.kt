package com.guardianshield.parent.ui.monitoring.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardianshield.parent.databinding.ItemContactProfileBinding
import com.guardianshield.parent.domain.models.ContactProfile

class ContactAdapter : ListAdapter<ContactProfile, ContactAdapter.ContactViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactProfileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ContactViewHolder(
        private val binding: ItemContactProfileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: ContactProfile) {
            binding.tvContactName.text = contact.name
            binding.tvContactPhone.text = contact.phoneNumber

            // Character avatar
            val firstChar = contact.name.trim().firstOrNull()?.toString() ?: "#"
            binding.tvAvatarChar.text = firstChar.uppercase()

            // Dial button action
            binding.btnDialShortcut.setOnClickListener {
                try {
                    val context = itemView.context
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${contact.phoneNumber}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ContactProfile>() {
        override fun areItemsTheSame(oldItem: ContactProfile, newItem: ContactProfile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ContactProfile, newItem: ContactProfile): Boolean {
            return oldItem == newItem
        }
    }
}

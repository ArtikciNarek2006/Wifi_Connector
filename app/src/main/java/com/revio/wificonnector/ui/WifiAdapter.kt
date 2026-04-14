package com.revio.wificonnector.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revio.wificonnector.databinding.ItemWifiNetworkBinding
import com.revio.wificonnector.domain.WifiNetwork

class WifiAdapter(private val onConnectClick: (WifiNetwork) -> Unit) :
    ListAdapter<WifiNetwork, WifiAdapter.WifiViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val binding = ItemWifiNetworkBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WifiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WifiViewHolder(private val binding: ItemWifiNetworkBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(network: WifiNetwork) {
            binding.tvSsid.text = network.ssid
            binding.tvPassword.text = "Password: ${network.preSharedKey}"
            binding.btnConnect.setOnClickListener { onConnectClick(network) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<WifiNetwork>() {
        override fun areItemsTheSame(oldItem: WifiNetwork, newItem: WifiNetwork): Boolean {
            return oldItem.ssid == newItem.ssid
        }

        override fun areContentsTheSame(oldItem: WifiNetwork, newItem: WifiNetwork): Boolean {
            return oldItem == newItem
        }
    }
}

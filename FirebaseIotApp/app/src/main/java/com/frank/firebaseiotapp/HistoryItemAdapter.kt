package com.frank.firebaseiotapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val value: Int,
    val timestamp: Long,
    val source: String
)

class HistoryItemAdapter : RecyclerView.Adapter<HistoryItemAdapter.ViewHolder>() {

    private val items = mutableListOf<HistoryItem>()

    fun updateItems(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems.sortedByDescending { it.timestamp })
        notifyDataSetChanged()
    }

    fun addItem(item: HistoryItem) {
        items.add(0, item)
        notifyItemInserted(0)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_history_item_adapter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvValue: TextView = itemView.findViewById(R.id.tvValue)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvSource: TextView = itemView.findViewById(R.id.tvSource)

        fun bind(item: HistoryItem) {
            tvValue.text = "Value: ${item.value}"
            tvTime.text = formatTime(item.timestamp)
            tvSource.text = "From: ${item.source}"
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}

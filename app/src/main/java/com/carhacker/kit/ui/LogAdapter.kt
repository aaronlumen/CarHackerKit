package com.carhacker.kit.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
    
    private val logs = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    data class LogEntry(
        val timestamp: Long,
        val message: String
    )
    
    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(android.R.id.text1)
        val tvMessage: TextView = view.findViewById(android.R.id.text2)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return LogViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = logs[position]
        holder.tvTimestamp.text = dateFormat.format(Date(entry.timestamp))
        holder.tvTimestamp.textSize = 10f
        holder.tvMessage.text = entry.message
        holder.tvMessage.textSize = 12f
        
        // Color based on message type
        val color = when {
            entry.message.startsWith("✓") -> 0xFF4CAF50.toInt() // Green
            entry.message.startsWith("❌") -> 0xFFF44336.toInt() // Red
            entry.message.startsWith("⚠") -> 0xFFFF9800.toInt() // Orange
            entry.message.startsWith("TX:") -> 0xFF2196F3.toInt() // Blue
            entry.message.startsWith("RX:") -> 0xFF9C27B0.toInt() // Purple
            else -> 0xFF000000.toInt() // Black
        }
        holder.tvMessage.setTextColor(color)
    }
    
    override fun getItemCount(): Int = logs.size
    
    fun add(message: String) {
        logs.add(LogEntry(System.currentTimeMillis(), message))
        notifyItemInserted(logs.size - 1)
    }
    
    fun clear() {
        val size = logs.size
        logs.clear()
        notifyItemRangeRemoved(0, size)
    }
    
    fun getFullLog(): String {
        return logs.joinToString("\n") { entry ->
            "[${dateFormat.format(Date(entry.timestamp))}] ${entry.message}"
        }
    }
}

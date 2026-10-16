package com.inacap.centinelaiot.ui.audit

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.inacap.centinelaiot.R
import com.inacap.centinelaiot.data.database.model.AuditLog
import com.inacap.centinelaiot.databinding.ItemAuditLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditLogAdapter(private var logs: List<AuditLog> = emptyList()) :
    RecyclerView.Adapter<AuditLogAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

    fun updateLogs(newLogs: List<AuditLog>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAuditLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    inner class ViewHolder(private val binding: ItemAuditLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: AuditLog) {
            val context = binding.root.context
            binding.tvEventType.text = log.eventType
            binding.tvDetails.text = log.details
            binding.tvUser.text = "Operador / Origen: ${log.username}"
            binding.tvTimestamp.text = dateFormat.format(Date(log.timestamp))

            binding.tvSeverityBadge.text = log.severity
            when (log.severity.uppercase()) {
                "CRITICAL" -> {
                    val color = ContextCompat.getColor(context, R.color.status_alarm)
                    binding.tvSeverityBadge.setTextColor(color)
                    binding.tvSeverityBadge.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_alarm)
                }
                "WARNING" -> {
                    val color = ContextCompat.getColor(context, R.color.status_warning)
                    binding.tvSeverityBadge.setTextColor(color)
                    binding.tvSeverityBadge.backgroundTintList = ColorStateList.valueOf(color)
                }
                else -> {
                    val color = ContextCompat.getColor(context, R.color.status_safe)
                    binding.tvSeverityBadge.setTextColor(color)
                    binding.tvSeverityBadge.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_safe)
                }
            }
        }
    }
}

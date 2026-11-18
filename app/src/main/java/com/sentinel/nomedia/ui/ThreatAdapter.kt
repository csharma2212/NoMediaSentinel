package com.sentinel.nomedia.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sentinel.nomedia.R
import com.sentinel.nomedia.intelligence.AppThreatReport
import com.sentinel.nomedia.intelligence.ThreatLevel

class ThreatAdapter(
    private val context: Context,
    private var items: List<AppThreatReport> = emptyList()
) : RecyclerView.Adapter<ThreatAdapter.ViewHolder>() {

    fun updateData(newItems: List<AppThreatReport>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_threat_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, context)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvThreatAppName: TextView = itemView.findViewById(R.id.tvThreatAppName)
        private val tvThreatPackage: TextView = itemView.findViewById(R.id.tvThreatPackage)
        private val tvThreatBadge: TextView = itemView.findViewById(R.id.tvThreatBadge)
        private val tvThreatSummary: TextView = itemView.findViewById(R.id.tvThreatSummary)
        private val tvThreatOnlineFindings: TextView = itemView.findViewById(R.id.tvThreatOnlineFindings)
        private val btnSearchWeb: MaterialButton = itemView.findViewById(R.id.btnSearchWeb)
        private val btnManageApp: MaterialButton = itemView.findViewById(R.id.btnManageApp)

        fun bind(report: AppThreatReport, context: Context) {
            tvThreatAppName.text = report.appName
            tvThreatPackage.text = report.packageName
            tvThreatSummary.text = report.summary

            if (report.icon != null) {
                ivAppIcon.setImageDrawable(report.icon)
            } else {
                ivAppIcon.setImageResource(R.drawable.ic_policeman_badge)
            }

            when (report.threatLevel) {
                ThreatLevel.HIGH -> {
                    tvThreatBadge.text = "HIGH RISK"
                    tvThreatBadge.setTextColor(Color.parseColor("#C62828"))
                    tvThreatBadge.setBackgroundColor(Color.parseColor("#FFCDD2"))
                }
                ThreatLevel.SUSPICIOUS -> {
                    tvThreatBadge.text = "SUSPICIOUS"
                    tvThreatBadge.setTextColor(Color.parseColor("#E65100"))
                    tvThreatBadge.setBackgroundColor(Color.parseColor("#FFE0B2"))
                }
                ThreatLevel.MODERATE -> {
                    tvThreatBadge.text = "MODERATE"
                    tvThreatBadge.setTextColor(Color.parseColor("#F57F17"))
                    tvThreatBadge.setBackgroundColor(Color.parseColor("#FFF9C4"))
                }
                ThreatLevel.LOW -> {
                    tvThreatBadge.text = "LOW RISK"
                    tvThreatBadge.setTextColor(Color.parseColor("#2E7D32"))
                    tvThreatBadge.setBackgroundColor(Color.parseColor("#C8E6C9"))
                }
            }

            if (report.onlineFindings.isNotEmpty()) {
                tvThreatOnlineFindings.visibility = View.VISIBLE
                tvThreatOnlineFindings.text = "Online Findings:\n• " + report.onlineFindings.joinToString("\n• ")
            } else {
                tvThreatOnlineFindings.visibility = View.GONE
            }

            btnSearchWeb.setOnClickListener {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(report.searchUrl))
                context.startActivity(browserIntent)
            }

            btnManageApp.setOnClickListener {
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${report.packageName}")
                }
                context.startActivity(appSettingsIntent)
            }
        }
    }
}

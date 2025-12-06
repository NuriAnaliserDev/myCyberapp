package com.example.cyberapp

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

data class Anomaly(val timestamp: String, val description: String, val rawJson: String)

class AnomalyAdapter(
    private val anomalies: MutableList<Anomaly>,
    private val listener: OnAnomalyInteractionListener
) : RecyclerView.Adapter<AnomalyAdapter.AnomalyViewHolder>() {

    interface OnAnomalyInteractionListener {
        fun onMarkAsNormal(anomaly: Anomaly, position: Int)
        fun onBlockIp(ip: String)
        fun onUninstallApp(packageName: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnomalyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.anomaly_item, parent, false)
        return AnomalyViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnomalyViewHolder, position: Int) {
        val anomaly = anomalies[position]
        holder.bind(anomaly, listener)
    }

    override fun getItemCount() = anomalies.size

    class AnomalyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampView: TextView = itemView.findViewById(R.id.anomaly_timestamp)
        private val descriptionView: TextView = itemView.findViewById(R.id.anomaly_description)
        private val mainContent: LinearLayout = itemView.findViewById(R.id.main_content)
        private val detailsLayout: LinearLayout = itemView.findViewById(R.id.details_layout)
        private val detailsTextView: TextView = itemView.findViewById(R.id.anomaly_details_text)
        private val expandArrow: ImageView = itemView.findViewById(R.id.expand_arrow)
        private val anomalyIcon: ImageView = itemView.findViewById(R.id.anomaly_icon)
        
        private val blockIpButton: Button = itemView.findViewById(R.id.block_ip_button)
        private val uninstallAppButton: Button = itemView.findViewById(R.id.uninstall_app_button)
        private val markNormalButton: Button = itemView.findViewById(R.id.mark_normal_button)

        fun bind(anomaly: Anomaly, listener: OnAnomalyInteractionListener) {
            timestampView.text = anomaly.timestamp
            descriptionView.text = anomaly.description

            val details = parseAnomalyDetails(anomaly.rawJson)
            detailsTextView.text = "Tafsilotlar: ${details.detailsText}"
            
            blockIpButton.isVisible = details.ipToBlock != null
            uninstallAppButton.isVisible = details.packageName != null

            // Set App Icon
            try {
                if (details.packageName != null) {
                    val icon = itemView.context.packageManager.getApplicationIcon(details.packageName)
                    anomalyIcon.setImageDrawable(icon)
                    anomalyIcon.clearColorFilter()
                } else {
                    setDefaultIcon()
                }
            } catch (e: PackageManager.NameNotFoundException) {
                setDefaultIcon()
            }

            mainContent.setOnClickListener {
                val isVisible = detailsLayout.isVisible
                detailsLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
                expandArrow.rotation = if (isVisible) 90f else 0f // Correct rotation for right arrow
            }

            blockIpButton.setOnClickListener { details.ipToBlock?.let { ip -> listener.onBlockIp(ip) } }
            uninstallAppButton.setOnClickListener { details.packageName?.let { pkg -> listener.onUninstallApp(pkg) } }
            markNormalButton.setOnClickListener { listener.onMarkAsNormal(anomaly, adapterPosition) }
        }

        private fun setDefaultIcon() {
            anomalyIcon.setImageResource(R.drawable.ic_shield_check) // Using a valid drawable
            anomalyIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.neon_red))
        }

        private fun parseAnomalyDetails(rawJson: String): AnomalyDetails {
            return try {
                val json = JSONObject(rawJson)
                val description = json.optString("description", "No details available.")
                val ip = json.optString("dest_ip", null)
                val app = json.optString("app", null)
                AnomalyDetails(description, ip, app)
            } catch (e: Exception) {
                AnomalyDetails("Could not parse details.", null, null)
            }
        }

        private data class AnomalyDetails(val detailsText: String, val ipToBlock: String?, val packageName: String?)
    }
}

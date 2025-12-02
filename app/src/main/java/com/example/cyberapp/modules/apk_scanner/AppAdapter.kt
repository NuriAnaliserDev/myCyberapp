package com.example.cyberapp.modules.apk_scanner

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.R

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val riskScore: Int,
    val sourceDir: String,
    val analysisWarnings: List<String>
)

class AppAdapter(private val apps: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
        val tvRiskScore: TextView = view.findViewById(R.id.tvRiskScore)
        val tvPermissions: TextView = view.findViewById(R.id.tvPermissions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.tvAppName.text = app.name
        holder.tvPackageName.text = app.packageName
        holder.ivIcon.setImageDrawable(app.icon)

        if (app.riskScore > 0) {
            holder.tvRiskScore.text = "RISK: ${app.riskScore}"
            holder.tvRiskScore.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
            holder.tvPermissions.visibility = View.VISIBLE
            holder.tvPermissions.text = "Warnings: ${app.analysisWarnings.joinToString(", ")}"
        } else {
            holder.tvRiskScore.text = "SAFE"
            holder.tvRiskScore.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
            holder.tvPermissions.visibility = View.GONE
        }
    }

    override fun getItemCount() = apps.size
}

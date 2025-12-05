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
    // val icon: Drawable removed to prevent OOM
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
        
        // Lazy load icon to prevent OOM
        try {
            val pm = holder.itemView.context.packageManager
            val icon = pm.getApplicationIcon(app.packageName)
            holder.ivIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        if (app.riskScore > 0) {
            holder.tvRiskScore.text = "RISK: ${app.riskScore}"
            holder.tvRiskScore.background.setTint(holder.itemView.context.getColor(R.color.neon_red))
            holder.tvPermissions.visibility = View.VISIBLE
            holder.tvPermissions.text = "Warnings: ${app.analysisWarnings.joinToString(", ")}"
        } else {
            holder.tvRiskScore.text = "SAFE"
            holder.tvRiskScore.background.setTint(holder.itemView.context.getColor(R.color.safe_green))
            holder.tvPermissions.visibility = View.GONE
        }
    }

    override fun getItemCount() = apps.size
}

package com.example.cyberapp

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppInfo(val name: String, val packageName: String, val icon: Drawable, var riskScore: Int = 0)

class AppAdapter(private val apps: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.appName.text = app.name
        holder.appPackageName.text = app.packageName
        holder.appIcon.setImageDrawable(app.icon)
        holder.riskScore.text = app.riskScore.toString()
    }

    override fun getItemCount() = apps.size

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val appPackageName: TextView = itemView.findViewById(R.id.app_package_name)
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val riskScore: TextView = itemView.findViewById(R.id.risk_score)
    }
}

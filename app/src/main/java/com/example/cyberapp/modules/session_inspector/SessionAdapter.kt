package com.example.cyberapp.modules.session_inspector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.R

data class Session(
    val deviceName: String,
    val location: String,
    val ipAddress: String,
    val status: String,
    val isCurrent: Boolean = false,
    val isCurrentDevice: Boolean = false
)

class SessionAdapter(
    private val sessions: MutableList<Session>,
    private val onTerminateClick: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.tv_device_name)
        val location: TextView = view.findViewById(R.id.tv_location)
        val status: TextView = view.findViewById(R.id.tv_status)
        val btnTerminate: View = view.findViewById(R.id.btn_terminate)
        val iconDevice: android.widget.ImageView = view.findViewById(R.id.icon_device)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        holder.deviceName.text = session.deviceName
        holder.location.text = "${session.location} • ${session.ipAddress}"
        holder.status.text = session.status

        if (session.isCurrent) {
            holder.status.text = holder.itemView.context.getString(R.string.active_now_this_device)
            holder.status.setTextColor(holder.itemView.context.getColor(R.color.safe_green))
            holder.btnTerminate.visibility = View.GONE
            holder.iconDevice.setColorFilter(holder.itemView.context.getColor(R.color.primary_blue))
        } else {
            holder.status.text = holder.itemView.context.getString(R.string.active)
            holder.status.setTextColor(holder.itemView.context.getColor(R.color.text_secondary))
            holder.btnTerminate.visibility = View.VISIBLE
            holder.iconDevice.setColorFilter(holder.itemView.context.getColor(R.color.text_secondary))
            
            holder.btnTerminate.setOnClickListener {
                onTerminateClick(session)
            }
        }
    }

    override fun getItemCount() = sessions.size
}

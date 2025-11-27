package com.example.cyberapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Endi har bir anomaliya o'zi haqidagi to'liq JSON ma'lumotini olib yuradi
data class Anomaly(val timestamp: String, val description: String, val rawJson: String)

class AnomalyAdapter(
    private val anomalies: MutableList<Anomaly>,
    private val listener: OnAnomalyInteractionListener
) : RecyclerView.Adapter<AnomalyAdapter.AnomalyViewHolder>() {

    interface OnAnomalyInteractionListener {
        fun onMarkAsNormal(anomaly: Anomaly, position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnomalyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.anomaly_item, parent, false)
        return AnomalyViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnomalyViewHolder, position: Int) {
        val anomaly = anomalies[position]
        holder.timestampView.text = anomaly.timestamp
        holder.descriptionView.text = anomaly.description

        holder.markAsNormalButton.setOnClickListener {
            listener.onMarkAsNormal(anomaly, position)
        }
    }

    override fun getItemCount() = anomalies.size

    class AnomalyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestampView: TextView = itemView.findViewById(R.id.anomaly_timestamp)
        val descriptionView: TextView = itemView.findViewById(R.id.anomaly_description)
        val markAsNormalButton: Button = itemView.findViewById(R.id.mark_normal_button)
    }
}

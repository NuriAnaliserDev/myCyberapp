package com.example.cyberapp.modules.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.R
import com.example.cyberapp.database.AppDatabase
import com.example.cyberapp.database.ScanHistoryEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_history)

        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = HistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val dao = AppDatabase.getDatabase(this).scanHistoryDao()

        lifecycleScope.launch {
            dao.getAllHistory().collectLatest { list ->
                if (list.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(list)
                }
            }
        }
    }

    class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        private var items: List<ScanHistoryEntity> = emptyList()

        fun submitList(newItems: List<ScanHistoryEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scan_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvUrl: TextView = itemView.findViewById(R.id.tvUrl)
            private val tvVerdict: TextView = itemView.findViewById(R.id.tvVerdict)
            private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

            fun bind(item: ScanHistoryEntity) {
                tvUrl.text = item.url
                tvVerdict.text = item.verdict.uppercase()
                
                if (item.verdict == "safe") {
                    tvVerdict.setTextColor(itemView.context.getColor(R.color.neon_green))
                } else {
                    tvVerdict.setTextColor(itemView.context.getColor(R.color.neon_red))
                }

                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                tvDate.text = sdf.format(Date(item.timestamp))
            }
        }
    }
}

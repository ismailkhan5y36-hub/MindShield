package com.mindshield.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mindshield.app.model.AppUsageInfo

class AppUsageAdapter(
    private var items: List<AppUsageInfo>
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvAppDuration: TextView = itemView.findViewById(R.id.tvAppDuration)
        val pbAppUsage: ProgressBar = itemView.findViewById(R.id.pbAppUsage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvAppName.text = item.appName
        holder.tvAppDuration.text = item.usageFormatted
        holder.pbAppUsage.progress = item.percentage
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<AppUsageInfo>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}

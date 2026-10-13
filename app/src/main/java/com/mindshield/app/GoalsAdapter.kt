package com.mindshield.app

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mindshield.app.model.GoalItem

class GoalsAdapter(
    private var items: List<GoalItem>,
    private val onToggle: (GoalItem) -> Unit,
    private val onDelete: (GoalItem) -> Unit
) : RecyclerView.Adapter<GoalsAdapter.GoalViewHolder>() {

    class GoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cbGoal: CheckBox = itemView.findViewById(R.id.cbGoal)
        val tvGoalTitle: TextView = itemView.findViewById(R.id.tvGoalTitle)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteGoal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GoalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_goal, parent, false)
        return GoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: GoalViewHolder, position: Int) {
        val item = items[position]
        holder.tvGoalTitle.text = item.title

        // Remove listener temporarily to avoid trigger during binding
        holder.cbGoal.setOnCheckedChangeListener(null)
        holder.cbGoal.isChecked = item.isCompleted

        if (item.isCompleted) {
            holder.tvGoalTitle.paintFlags = holder.tvGoalTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvGoalTitle.alpha = 0.5f
        } else {
            holder.tvGoalTitle.paintFlags = holder.tvGoalTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvGoalTitle.alpha = 1.0f
        }

        holder.cbGoal.setOnClickListener {
            onToggle(item)
        }

        holder.btnDelete.setOnClickListener {
            onDelete(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<GoalItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}

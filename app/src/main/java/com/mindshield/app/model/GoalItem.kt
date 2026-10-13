package com.mindshield.app.model

data class GoalItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    var isCompleted: Boolean = false,
    val date: String = ""
)

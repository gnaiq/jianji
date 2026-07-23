package com.example.jianji.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String = "💳",
    val balance: Double = 0.0,
    val isDefault: Boolean = false
)

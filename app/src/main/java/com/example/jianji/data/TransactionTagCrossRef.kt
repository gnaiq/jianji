package com.example.jianji.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 交易 ↔ 标签 多对多关联表（§11）。
 */
@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class TransactionTagCrossRef(
    val transactionId: Long,
    val tagId: Long
)

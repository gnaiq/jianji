package com.example.jianji.data.local.entity

/**
 * 账户余额聚合结果（SQL 下推计算，见 [TransactionDao.observeAccountBalances]）。
 * balanceCents 为 Long 分，避免浮点累加误差（修复 B3-4 的进一步下推）。
 */
data class AccountBalance(
    val accountId: Long,
    val balanceCents: Long
)

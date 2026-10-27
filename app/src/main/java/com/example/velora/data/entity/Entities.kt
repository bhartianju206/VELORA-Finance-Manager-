package com.example.velora.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AccountType {
    CASH, BANK, UPI, CUSTOM
}

enum class TransactionType {
    INCOME, EXPENSE, TRANSFER, LEND, BORROW, LEND_REPAYMENT, BORROW_REPAYMENT, ADJUSTMENT
}

enum class UdhaarType {
    LENT, BORROWED
}

enum class UdhaarStatus {
    PENDING, PARTIALLY_SETTLED, SETTLED
}

enum class ReminderType {
    BILL, UDHAAR, SUBSCRIPTION, RECHARGE, CUSTOM
}

enum class RecurrenceInterval {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val name: String,
    val type: AccountType,
    val openingBalancePaise: Long,
    val currentBalancePaise: Long,
    val colorHex: String = "#10B981",
    val isArchived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val type: TransactionType,
    val amountPaise: Long,
    val accountId: String,
    val destinationAccountId: String? = null,
    val category: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val isReconciled: Boolean = false,
    val referenceId: String? = null
)

@Entity(tableName = "udhaar")
data class UdhaarEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val personName: String,
    val type: UdhaarType,
    val originalAmountPaise: Long,
    val outstandingAmountPaise: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val notes: String = "",
    val status: UdhaarStatus = UdhaarStatus.PENDING
)

@Entity(tableName = "udhaar_payments")
data class UdhaarPaymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val udhaarId: String,
    val userId: String,
    val amountPaise: Long,
    val accountId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val category: String, // "__OVERALL__" or category name
    val monthYear: String, // "YYYY-MM"
    val limitPaise: Long,
    val alertThresholdPercent: Int = 80
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val amountPaise: Long,
    val category: String,
    val dueDate: Long,
    val type: ReminderType = ReminderType.BILL,
    val isCompleted: Boolean = false,
    val isSnoozed: Boolean = false,
    val snoozedUntil: Long? = null
)

@Entity(tableName = "recurring_payments")
data class RecurringPaymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val amountPaise: Long,
    val category: String,
    val interval: RecurrenceInterval = RecurrenceInterval.MONTHLY,
    val accountId: String,
    val nextDueDate: Long,
    val lastProcessedDate: Long? = null,
    val isActive: Boolean = true
)

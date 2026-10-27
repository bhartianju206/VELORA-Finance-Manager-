package com.example.velora.data.repository

import androidx.room.withTransaction
import com.example.velora.data.database.VeloraDatabase
import com.example.velora.data.entity.AccountEntity
import com.example.velora.data.entity.AccountType
import com.example.velora.data.entity.BudgetEntity
import com.example.velora.data.entity.RecurringPaymentEntity
import com.example.velora.data.entity.ReminderEntity
import com.example.velora.data.entity.TransactionEntity
import com.example.velora.data.entity.TransactionType
import com.example.velora.data.entity.UdhaarEntity
import com.example.velora.data.entity.UdhaarPaymentEntity
import com.example.velora.data.entity.UdhaarStatus
import com.example.velora.data.entity.UdhaarType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class VeloraRepository(private val database: VeloraDatabase) {

    private val accountDao = database.accountDao()
    private val transactionDao = database.transactionDao()
    private val udhaarDao = database.udhaarDao()
    private val budgetDao = database.budgetDao()
    private val reminderDao = database.reminderDao()
    private val recurringDao = database.recurringDao()

    // Accounts
    fun getAccounts(userId: String): Flow<List<AccountEntity>> = accountDao.getAccounts(userId)

    suspend fun getAccountsSync(userId: String): List<AccountEntity> = accountDao.getAccountsSync(userId)

    suspend fun createAccount(
        userId: String,
        name: String,
        type: AccountType,
        openingBalancePaise: Long,
        colorHex: String = "#10B981"
    ): AccountEntity {
        val account = AccountEntity(
            userId = userId,
            name = name,
            type = type,
            openingBalancePaise = openingBalancePaise,
            currentBalancePaise = openingBalancePaise,
            colorHex = colorHex
        )
        accountDao.insertAccount(account)
        return account
    }

    suspend fun ensureDefaultAccounts(userId: String) {
        val existing = accountDao.getAccountsSync(userId)
        if (existing.isEmpty()) {
            val defaults = listOf(
                AccountEntity(
                    userId = userId,
                    name = "Cash in Hand",
                    type = AccountType.CASH,
                    openingBalancePaise = 0L, // ₹0.00 initial balance
                    currentBalancePaise = 0L,
                    colorHex = "#10B981"
                ),
                AccountEntity(
                    userId = userId,
                    name = "Primary Bank",
                    type = AccountType.BANK,
                    openingBalancePaise = 0L, // ₹0.00 initial balance
                    currentBalancePaise = 0L,
                    colorHex = "#38BDF8"
                ),
                AccountEntity(
                    userId = userId,
                    name = "UPI / Wallet",
                    type = AccountType.UPI,
                    openingBalancePaise = 0L, // ₹0.00 initial balance
                    currentBalancePaise = 0L,
                    colorHex = "#F59E0B"
                )
            )
            accountDao.insertAccounts(defaults)
        }
    }

    suspend fun updateAccount(account: AccountEntity) {
        accountDao.updateAccount(account)
    }

    suspend fun updateAccountDetails(
        accountId: String,
        newName: String,
        newType: AccountType,
        newOpeningBalancePaise: Long,
        colorHex: String? = null
    ): Result<AccountEntity> = runCatching {
        database.withTransaction {
            val account = accountDao.getAccountById(accountId) ?: error("Account not found")
            val openingDiff = newOpeningBalancePaise - account.openingBalancePaise
            val newCurrentBalance = account.currentBalancePaise + openingDiff
            val updated = account.copy(
                name = newName.trim(),
                type = newType,
                openingBalancePaise = newOpeningBalancePaise,
                currentBalancePaise = newCurrentBalance,
                colorHex = colorHex ?: account.colorHex
            )
            accountDao.updateAccount(updated)
            updated
        }
    }

    suspend fun resetLegacyDemoBalances(userId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val accounts = accountDao.getAccountsSync(userId)
            accounts.forEach { acc ->
                var shouldReset = false
                if (acc.name == "Cash in Hand" && acc.openingBalancePaise == 250000L) shouldReset = true
                if (acc.name == "Primary Bank" && acc.openingBalancePaise == 4500000L) shouldReset = true
                if (acc.name == "UPI / Wallet" && acc.openingBalancePaise == 350000L) shouldReset = true

                if (shouldReset) {
                    val diff = 0L - acc.openingBalancePaise
                    val newCurrent = Math.max(0L, acc.currentBalancePaise + diff)
                    val updated = acc.copy(
                        openingBalancePaise = 0L,
                        currentBalancePaise = newCurrent
                    )
                    accountDao.updateAccount(updated)
                }
            }
        }
    }

    // Transactions
    fun getActiveTransactions(userId: String): Flow<List<TransactionEntity>> =
        transactionDao.getActiveTransactions(userId)

    fun getTrashTransactions(userId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTrashTransactions(userId)

    suspend fun deposit(
        userId: String,
        accountId: String,
        amountPaise: Long,
        category: String,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> = runCatching {
        require(amountPaise > 0) { "Deposit amount must be positive" }
        database.withTransaction {
            val account = accountDao.getAccountById(accountId) ?: error("Account not found")
            val newBalance = account.currentBalancePaise + amountPaise
            accountDao.updateBalance(accountId, newBalance)

            val tx = TransactionEntity(
                userId = userId,
                type = TransactionType.INCOME,
                amountPaise = amountPaise,
                accountId = accountId,
                category = category,
                note = note,
                timestamp = timestamp
            )
            transactionDao.insertTransaction(tx)
            tx
        }
    }

    suspend fun withdraw(
        userId: String,
        accountId: String,
        amountPaise: Long,
        category: String,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> = runCatching {
        require(amountPaise > 0) { "Withdrawal amount must be positive" }
        database.withTransaction {
            val account = accountDao.getAccountById(accountId) ?: error("Account not found")
            val newBalance = account.currentBalancePaise - amountPaise
            accountDao.updateBalance(accountId, newBalance)

            val tx = TransactionEntity(
                userId = userId,
                type = TransactionType.EXPENSE,
                amountPaise = amountPaise,
                accountId = accountId,
                category = category,
                note = note,
                timestamp = timestamp
            )
            transactionDao.insertTransaction(tx)
            tx
        }
    }

    suspend fun transfer(
        userId: String,
        fromAccountId: String,
        toAccountId: String,
        amountPaise: Long,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> = runCatching {
        require(amountPaise > 0) { "Transfer amount must be positive" }
        require(fromAccountId != toAccountId) { "Source and destination accounts must be different" }
        database.withTransaction {
            val fromAccount = accountDao.getAccountById(fromAccountId) ?: error("Source account not found")
            val toAccount = accountDao.getAccountById(toAccountId) ?: error("Destination account not found")

            accountDao.updateBalance(fromAccountId, fromAccount.currentBalancePaise - amountPaise)
            accountDao.updateBalance(toAccountId, toAccount.currentBalancePaise + amountPaise)

            val tx = TransactionEntity(
                userId = userId,
                type = TransactionType.TRANSFER,
                amountPaise = amountPaise,
                accountId = fromAccountId,
                destinationAccountId = toAccountId,
                category = "Transfer",
                note = note,
                timestamp = timestamp
            )
            transactionDao.insertTransaction(tx)
            tx
        }
    }

    suspend fun adjustBalance(
        userId: String,
        accountId: String,
        newBalancePaise: Long,
        reason: String,
        timestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> = runCatching {
        database.withTransaction {
            val account = accountDao.getAccountById(accountId) ?: error("Account not found")
            val diff = newBalancePaise - account.currentBalancePaise
            accountDao.updateBalance(accountId, newBalancePaise)

            val tx = TransactionEntity(
                userId = userId,
                type = TransactionType.ADJUSTMENT,
                amountPaise = diff,
                accountId = accountId,
                category = "Reconciliation",
                note = "Balance adjustment: $reason",
                timestamp = timestamp,
                isReconciled = true
            )
            transactionDao.insertTransaction(tx)
            tx
        }
    }

    // Udhaar (Money Lent & Money Borrowed)
    fun getAllUdhaar(userId: String): Flow<List<UdhaarEntity>> = udhaarDao.getAllUdhaar(userId)

    fun getPaymentsForUdhaar(udhaarId: String): Flow<List<UdhaarPaymentEntity>> =
        udhaarDao.getPaymentsForUdhaar(udhaarId)

    suspend fun recordLend(
        userId: String,
        personName: String,
        amountPaise: Long,
        fromAccountId: String,
        dueDate: Long? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Result<UdhaarEntity> = runCatching {
        require(amountPaise > 0) { "Amount must be positive" }
        database.withTransaction {
            val account = accountDao.getAccountById(fromAccountId) ?: error("Account not found")
            accountDao.updateBalance(fromAccountId, account.currentBalancePaise - amountPaise)

            val udhaar = UdhaarEntity(
                userId = userId,
                personName = personName.trim(),
                type = UdhaarType.LENT,
                originalAmountPaise = amountPaise,
                outstandingAmountPaise = amountPaise,
                createdAt = timestamp,
                dueDate = dueDate,
                notes = notes,
                status = UdhaarStatus.PENDING
            )
            udhaarDao.insertUdhaar(udhaar)

            val tx = TransactionEntity(
                userId = userId,
                type = TransactionType.LEND,
                amountPaise = amountPaise,
                accountId = fromAccountId,
                category = "Udhaar: Lent",
                note = "Lent to $personName. ${if (notes.isNotEmpty()) "($notes)" else ""}",
                timestamp = timestamp,
                referenceId = udhaar.id
            )
            transactionDao.insertTransaction(tx)
            udhaar
        }
    }

    suspend fun recordBorrow(
        userId: String,
        personName: String,
        amountPaise: Long,
        toAccountId: String,
        dueDate: Long? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Result<UdhaarEntity> = runCatching {
        require(amountPaise > 0) { "Amount must be positive" }
        database.withTransaction {
            val account = accountDao.getAccountById(toAccountId) ?: error("Account not found")
            accountDao.updateBalance(toAccountId, account.currentBalancePaise + amountPaise)

            val udhaar = UdhaarEntity(
                userId = userId,
                personName = personName.trim(),
                type = UdhaarType.BORROWED,
                originalAmountPaise = amountPaise,
                outstandingAmountPaise = amountPaise,
                createdAt = timestamp,
                dueDate = dueDate,
                notes = notes,
                status = UdhaarStatus.PENDING
            )
            udhaarDao.insertUdhaar(udhaar)

            val tx = TransactionEntity(
                userId = userId,
                type = TransactionType.BORROW,
                amountPaise = amountPaise,
                accountId = toAccountId,
                category = "Udhaar: Borrowed",
                note = "Borrowed from $personName. ${if (notes.isNotEmpty()) "($notes)" else ""}",
                timestamp = timestamp,
                referenceId = udhaar.id
            )
            transactionDao.insertTransaction(tx)
            udhaar
        }
    }

    suspend fun recordUdhaarRepayment(
        userId: String,
        udhaarId: String,
        amountPaise: Long,
        accountId: String,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        require(amountPaise > 0) { "Repayment amount must be positive" }
        database.withTransaction {
            val udhaar = udhaarDao.getUdhaarById(udhaarId) ?: error("Udhaar record not found")
            val account = accountDao.getAccountById(accountId) ?: error("Account not found")

            val actualRepayment = Math.min(amountPaise, udhaar.outstandingAmountPaise)
            val newOutstanding = udhaar.outstandingAmountPaise - actualRepayment
            val newStatus = if (newOutstanding <= 0L) UdhaarStatus.SETTLED else UdhaarStatus.PARTIALLY_SETTLED

            val updatedUdhaar = udhaar.copy(
                outstandingAmountPaise = newOutstanding,
                status = newStatus
            )
            udhaarDao.updateUdhaar(updatedUdhaar)

            val payment = UdhaarPaymentEntity(
                udhaarId = udhaarId,
                userId = userId,
                amountPaise = actualRepayment,
                accountId = accountId,
                timestamp = timestamp,
                note = note
            )
            udhaarDao.insertPayment(payment)

            if (udhaar.type == UdhaarType.LENT) {
                // Recovering loan principal increases liquid funds, not ordinary income
                accountDao.updateBalance(accountId, account.currentBalancePaise + actualRepayment)
                val tx = TransactionEntity(
                    userId = userId,
                    type = TransactionType.LEND_REPAYMENT,
                    amountPaise = actualRepayment,
                    accountId = accountId,
                    category = "Udhaar: Recovered",
                    note = "Received repayment from ${udhaar.personName}. ${if (note.isNotEmpty()) "($note)" else ""}",
                    timestamp = timestamp,
                    referenceId = udhaarId
                )
                transactionDao.insertTransaction(tx)
            } else {
                // Repaying borrowed money decreases liquid funds, not ordinary expense
                accountDao.updateBalance(accountId, account.currentBalancePaise - actualRepayment)
                val tx = TransactionEntity(
                    userId = userId,
                    type = TransactionType.BORROW_REPAYMENT,
                    amountPaise = actualRepayment,
                    accountId = accountId,
                    category = "Udhaar: Repaid",
                    note = "Repaid to ${udhaar.personName}. ${if (note.isNotEmpty()) "($note)" else ""}",
                    timestamp = timestamp,
                    referenceId = udhaarId
                )
                transactionDao.insertTransaction(tx)
            }
        }
    }

    // Soft delete / Trash & Restore
    suspend fun moveToTrash(transactionId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val tx = transactionDao.getTransactionById(transactionId) ?: error("Transaction not found")
            if (tx.isDeleted) return@withTransaction

            // Revert balance change
            val account = accountDao.getAccountById(tx.accountId)
            if (account != null) {
                val revertedBalance = when (tx.type) {
                    TransactionType.INCOME, TransactionType.BORROW, TransactionType.LEND_REPAYMENT ->
                        account.currentBalancePaise - tx.amountPaise
                    TransactionType.EXPENSE, TransactionType.LEND, TransactionType.BORROW_REPAYMENT ->
                        account.currentBalancePaise + tx.amountPaise
                    TransactionType.TRANSFER -> {
                        tx.destinationAccountId?.let { destId ->
                            val dest = accountDao.getAccountById(destId)
                            if (dest != null) {
                                accountDao.updateBalance(destId, dest.currentBalancePaise - tx.amountPaise)
                            }
                        }
                        account.currentBalancePaise + tx.amountPaise
                    }
                    TransactionType.ADJUSTMENT -> account.currentBalancePaise - tx.amountPaise
                }
                accountDao.updateBalance(tx.accountId, revertedBalance)
            }
            transactionDao.setDeleted(transactionId, true)
        }
    }

    suspend fun restoreFromTrash(transactionId: String): Result<Unit> = runCatching {
        database.withTransaction {
            val tx = transactionDao.getTransactionById(transactionId) ?: error("Transaction not found")
            if (!tx.isDeleted) return@withTransaction

            // Re-apply balance change
            val account = accountDao.getAccountById(tx.accountId)
            if (account != null) {
                val reappliedBalance = when (tx.type) {
                    TransactionType.INCOME, TransactionType.BORROW, TransactionType.LEND_REPAYMENT ->
                        account.currentBalancePaise + tx.amountPaise
                    TransactionType.EXPENSE, TransactionType.LEND, TransactionType.BORROW_REPAYMENT ->
                        account.currentBalancePaise - tx.amountPaise
                    TransactionType.TRANSFER -> {
                        tx.destinationAccountId?.let { destId ->
                            val dest = accountDao.getAccountById(destId)
                            if (dest != null) {
                                accountDao.updateBalance(destId, dest.currentBalancePaise + tx.amountPaise)
                            }
                        }
                        account.currentBalancePaise - tx.amountPaise
                    }
                    TransactionType.ADJUSTMENT -> account.currentBalancePaise + tx.amountPaise
                }
                accountDao.updateBalance(tx.accountId, reappliedBalance)
            }
            transactionDao.setDeleted(transactionId, false)
        }
    }

    suspend fun permanentlyDeleteTransaction(transactionId: String) {
        transactionDao.permanentlyDelete(transactionId)
    }

    suspend fun emptyTrash(userId: String) {
        transactionDao.emptyTrash(userId)
    }

    // Budgets
    fun getBudgetsForMonth(userId: String, monthYear: String): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(userId, monthYear)

    suspend fun setBudget(
        userId: String,
        category: String,
        monthYear: String,
        limitPaise: Long,
        alertThresholdPercent: Int = 80
    ) {
        val budget = BudgetEntity(
            userId = userId,
            category = category,
            monthYear = monthYear,
            limitPaise = limitPaise,
            alertThresholdPercent = alertThresholdPercent
        )
        budgetDao.insertBudget(budget)
    }

    suspend fun deleteBudget(budgetId: String) {
        budgetDao.deleteBudget(budgetId)
    }

    // Reminders
    fun getReminders(userId: String): Flow<List<ReminderEntity>> = reminderDao.getReminders(userId)

    suspend fun addReminder(reminder: ReminderEntity) {
        reminderDao.insertReminder(reminder)
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        reminderDao.updateReminder(reminder)
    }

    suspend fun deleteReminder(id: String) {
        reminderDao.deleteReminder(id)
    }

    // Recurring Payments
    fun getRecurringPayments(userId: String): Flow<List<RecurringPaymentEntity>> =
        recurringDao.getRecurringPayments(userId)

    suspend fun addRecurring(recurring: RecurringPaymentEntity) {
        recurringDao.insertRecurring(recurring)
    }

    suspend fun updateRecurring(recurring: RecurringPaymentEntity) {
        recurringDao.updateRecurring(recurring)
    }

    suspend fun deleteRecurring(id: String) {
        recurringDao.deleteRecurring(id)
    }

    suspend fun processRecurringPayment(
        userId: String,
        recurringId: String,
        timestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> = runCatching {
        database.withTransaction {
            val list = recurringDao.getAllRecurringSync(userId)
            val item = list.firstOrNull { it.id == recurringId } ?: error("Recurring schedule not found")

            val txResult = withdraw(
                userId = userId,
                accountId = item.accountId,
                amountPaise = item.amountPaise,
                category = item.category,
                note = "Recurring payment: ${item.title}",
                timestamp = timestamp
            ).getOrThrow()

            // Calculate next due date
            val nextDate = item.nextDueDate + when (item.interval) {
                com.example.velora.data.entity.RecurrenceInterval.DAILY -> 86400000L
                com.example.velora.data.entity.RecurrenceInterval.WEEKLY -> 7 * 86400000L
                com.example.velora.data.entity.RecurrenceInterval.MONTHLY -> 30L * 86400000L
                com.example.velora.data.entity.RecurrenceInterval.YEARLY -> 365L * 86400000L
            }
            recurringDao.updateRecurring(
                item.copy(
                    nextDueDate = nextDate,
                    lastProcessedDate = timestamp
                )
            )
            txResult
        }
    }

    // Account deletion with cascade
    suspend fun deleteUserAllData(userId: String) {
        database.withTransaction {
            accountDao.deleteAllForUser(userId)
            transactionDao.deleteAllForUser(userId)
            udhaarDao.deleteAllForUser(userId)
            budgetDao.deleteAllForUser(userId)
            reminderDao.deleteAllForUser(userId)
            recurringDao.deleteAllForUser(userId)
        }
    }
}

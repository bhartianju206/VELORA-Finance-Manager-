package com.example.velora.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.velora.data.entity.AccountEntity
import com.example.velora.data.entity.BudgetEntity
import com.example.velora.data.entity.RecurringPaymentEntity
import com.example.velora.data.entity.ReminderEntity
import com.example.velora.data.entity.TransactionEntity
import com.example.velora.data.entity.UdhaarEntity
import com.example.velora.data.entity.UdhaarPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE userId = :userId AND isArchived = 0 ORDER BY name ASC")
    fun getAccounts(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE userId = :userId AND isArchived = 0")
    suspend fun getAccountsSync(userId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET currentBalancePaise = :balance, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun updateBalance(accountId: String, balance: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getActiveTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND isDeleted = 1 ORDER BY timestamp DESC")
    fun getTrashTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAllTransactionsSync(userId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isDeleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: String, deleted: Boolean)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun permanentlyDelete(id: String)

    @Query("DELETE FROM transactions WHERE userId = :userId AND isDeleted = 1")
    suspend fun emptyTrash(userId: String)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface UdhaarDao {
    @Query("SELECT * FROM udhaar WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllUdhaar(userId: String): Flow<List<UdhaarEntity>>

    @Query("SELECT * FROM udhaar WHERE userId = :userId")
    suspend fun getAllUdhaarSync(userId: String): List<UdhaarEntity>

    @Query("SELECT * FROM udhaar WHERE id = :id LIMIT 1")
    suspend fun getUdhaarById(id: String): UdhaarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUdhaar(udhaar: UdhaarEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUdhaars(udhaars: List<UdhaarEntity>)

    @Update
    suspend fun updateUdhaar(udhaar: UdhaarEntity)

    @Query("DELETE FROM udhaar WHERE id = :id")
    suspend fun deleteUdhaar(id: String)

    @Query("SELECT * FROM udhaar_payments WHERE udhaarId = :udhaarId ORDER BY timestamp DESC")
    fun getPaymentsForUdhaar(udhaarId: String): Flow<List<UdhaarPaymentEntity>>

    @Query("SELECT * FROM udhaar_payments WHERE userId = :userId")
    suspend fun getAllPaymentsSync(userId: String): List<UdhaarPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: UdhaarPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<UdhaarPaymentEntity>)

    @Query("DELETE FROM udhaar_payments WHERE udhaarId = :udhaarId")
    suspend fun deletePaymentsForUdhaar(udhaarId: String)

    @Query("DELETE FROM udhaar WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :userId AND monthYear = :monthYear")
    fun getBudgetsForMonth(userId: String, monthYear: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE userId = :userId")
    suspend fun getAllBudgetsSync(userId: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: String)

    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY dueDate ASC")
    fun getReminders(userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE userId = :userId")
    suspend fun getAllRemindersSync(userId: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("DELETE FROM reminders WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_payments WHERE userId = :userId ORDER BY nextDueDate ASC")
    fun getRecurringPayments(userId: String): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments WHERE userId = :userId")
    suspend fun getAllRecurringSync(userId: String): List<RecurringPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(recurring: RecurringPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringList(list: List<RecurringPaymentEntity>)

    @Update
    suspend fun updateRecurring(recurring: RecurringPaymentEntity)

    @Query("DELETE FROM recurring_payments WHERE id = :id")
    suspend fun deleteRecurring(id: String)

    @Query("DELETE FROM recurring_payments WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

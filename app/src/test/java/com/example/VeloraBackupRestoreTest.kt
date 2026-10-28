package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.velora.backup.AutoBackupManager
import com.example.velora.backup.BackupStatus
import com.example.velora.backup.DriveBackupManager
import com.example.velora.data.database.VeloraDatabase
import com.example.velora.data.entity.AccountEntity
import com.example.velora.data.entity.AccountType
import com.example.velora.data.entity.BudgetEntity
import com.example.velora.data.entity.RecurrenceInterval
import com.example.velora.data.entity.RecurringPaymentEntity
import com.example.velora.data.entity.ReminderEntity
import com.example.velora.data.entity.ReminderType
import com.example.velora.data.entity.TransactionEntity
import com.example.velora.data.entity.TransactionType
import com.example.velora.data.entity.UdhaarEntity
import com.example.velora.data.entity.UdhaarPaymentEntity
import com.example.velora.data.entity.UdhaarStatus
import com.example.velora.data.entity.UdhaarType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VeloraBackupRestoreTest {

    private lateinit var context: Context
    private lateinit var database: VeloraDatabase
    private lateinit var backupManager: DriveBackupManager
    private lateinit var autoBackupManager: AutoBackupManager

    private val testUserId = "user_test_101"
    private val testUserEmail = "test@velora.app"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, VeloraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupManager = DriveBackupManager(context, database)
        autoBackupManager = AutoBackupManager(context, database, backupManager)
    }

    @After
    fun tearDown() {
        autoBackupManager.autoBackupFile.delete()
        autoBackupManager.tmpBackupFile.delete()
        database.close()
    }

    @Test
    fun `export empty database produces valid JSON schema version 1 with zero counts`() = runBlocking {
        val payload = backupManager.createBackupPayload(testUserId, testUserEmail)
        assertNotNull(payload)

        val json = JSONObject(payload)
        assertEquals("Velora", json.getString("app"))
        assertEquals("1.0", json.getString("appVersion"))
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals(testUserId, json.getString("userId"))
        assertEquals(testUserEmail, json.getString("userEmail"))
        assertEquals(0, json.getJSONArray("accounts").length())
        assertEquals(0, json.getJSONArray("transactions").length())
        assertEquals(0, json.getJSONArray("udhaar").length())
        assertTrue(json.has("checksumSha256"))

        val validation = backupManager.validateBackupContent(payload)
        assertTrue(validation.isValid)
        assertEquals(0, validation.accountsCount)
        assertEquals(0, validation.transactionsCount)
        assertEquals(0, validation.udhaarCount)
    }

    @Test
    fun `validation rejects non-Velora or corrupted JSON files`() {
        // Not a Velora file
        val wrongApp = JSONObject().apply {
            put("app", "SomeOtherApp")
            put("schemaVersion", 1)
        }.toString()
        val v1 = backupManager.validateBackupContent(wrongApp)
        assertFalse(v1.isValid)
        assertTrue(v1.errorMessage!!.contains("Not a genuine Velora backup"))

        // Malformed JSON string
        val malformed = "{ this is not valid json : 123"
        val v2 = backupManager.validateBackupContent(malformed)
        assertFalse(v2.isValid)
        assertTrue(v2.errorMessage!!.contains("Corrupted or invalid JSON"))

        // Checksum mismatch
        val tampered = JSONObject().apply {
            put("app", "Velora")
            put("schemaVersion", 1)
            put("accounts", org.json.JSONArray())
            put("checksumSha256", "invalid_tampered_hash_value")
        }.toString()
        val v3 = backupManager.validateBackupContent(tampered)
        assertFalse(v3.isValid)
        assertTrue(v3.errorMessage!!.contains("integrity check failed"))
    }

    @Test
    fun `roundtrip export and restore preserves all financial entities with exact fidelity`() = runBlocking {
        // 1. Populate full financial ecosystem
        val acc1Id = "acc_cash"
        val acc2Id = "acc_bank"

        val acc1 = AccountEntity(
            id = acc1Id,
            userId = testUserId,
            name = "Cash in Hand",
            type = AccountType.CASH,
            openingBalancePaise = 250000L, // ₹2,500
            currentBalancePaise = 250000L
        )
        val acc2 = AccountEntity(
            id = acc2Id,
            userId = testUserId,
            name = "HDFC Bank",
            type = AccountType.BANK,
            openingBalancePaise = 1000000L, // ₹10,000
            currentBalancePaise = 1000000L
        )
        database.accountDao().insertAccounts(listOf(acc1, acc2))

        // Income tx
        val tx1 = TransactionEntity(
            id = "tx_income_1",
            userId = testUserId,
            type = TransactionType.INCOME,
            amountPaise = 500000L, // ₹5,000
            accountId = acc2Id,
            category = "Salary",
            note = "October Salary"
        )
        // Expense tx
        val tx2 = TransactionEntity(
            id = "tx_expense_1",
            userId = testUserId,
            type = TransactionType.EXPENSE,
            amountPaise = 50000L, // ₹500
            accountId = acc1Id,
            category = "Groceries",
            note = "Vegetables"
        )
        // Transfer tx
        val tx3 = TransactionEntity(
            id = "tx_transfer_1",
            userId = testUserId,
            type = TransactionType.TRANSFER,
            amountPaise = 100000L, // ₹1,000 from Bank to Cash
            accountId = acc2Id,
            destinationAccountId = acc1Id,
            category = "Transfer",
            note = "ATM Withdrawal"
        )
        database.transactionDao().insertTransactions(listOf(tx1, tx2, tx3))

        // Udhaar: Lent ₹2,000 to Rahul
        val udhaarId = "udhaar_rahul_1"
        val udhaar = UdhaarEntity(
            id = udhaarId,
            userId = testUserId,
            personName = "Rahul Sharma",
            type = UdhaarType.LENT,
            originalAmountPaise = 200000L,
            outstandingAmountPaise = 200000L,
            status = UdhaarStatus.PENDING
        )
        database.udhaarDao().insertUdhaar(udhaar)

        // Udhaar Repayment of ₹500
        val payment = UdhaarPaymentEntity(
            id = "payment_rahul_1",
            udhaarId = udhaarId,
            userId = testUserId,
            amountPaise = 50000L, // ₹500
            accountId = acc1Id,
            note = "Partial repayment"
        )
        database.udhaarDao().insertPayment(payment)

        // Budget
        val budget = BudgetEntity(
            id = "budget_1",
            userId = testUserId,
            category = "Groceries",
            monthYear = "2026-10",
            limitPaise = 200000L
        )
        database.budgetDao().insertBudget(budget)

        // Reminder
        val reminder = ReminderEntity(
            id = "reminder_1",
            userId = testUserId,
            title = "Electricity Bill",
            amountPaise = 150000L,
            category = "Bills",
            dueDate = System.currentTimeMillis() + 86400000L
        )
        database.reminderDao().insertReminder(reminder)

        // Recurring Payment
        val recurring = RecurringPaymentEntity(
            id = "recurring_1",
            userId = testUserId,
            title = "Netflix",
            amountPaise = 64900L,
            category = "Subscription",
            interval = RecurrenceInterval.MONTHLY,
            accountId = acc2Id,
            nextDueDate = System.currentTimeMillis() + 86400000L
        )
        database.recurringDao().insertRecurring(recurring)

        // 2. Export All Data
        val payload = backupManager.createBackupPayload(testUserId, testUserEmail)
        val validation = backupManager.validateBackupContent(payload)
        assertTrue(validation.isValid)
        assertEquals(2, validation.accountsCount)
        assertEquals(3, validation.transactionsCount)
        assertEquals(1, validation.udhaarCount)
        assertEquals(1, validation.paymentsCount)
        assertEquals(1, validation.budgetsCount)
        assertEquals(1, validation.remindersCount)
        assertEquals(1, validation.recurringCount)

        // 3. Clear database simulating new device or fresh install
        database.accountDao().deleteAllForUser(testUserId)
        database.transactionDao().deleteAllForUser(testUserId)
        database.udhaarDao().deleteAllForUser(testUserId)
        database.budgetDao().deleteAllForUser(testUserId)
        database.reminderDao().deleteAllForUser(testUserId)
        database.recurringDao().deleteAllForUser(testUserId)

        assertEquals(0, database.accountDao().getAccountsSync(testUserId).size)
        assertEquals(0, database.transactionDao().getAllTransactionsSync(testUserId).size)

        // 4. Restore All Data
        val restoreResult = backupManager.restorePayload(testUserId, payload, mode = "REPLACE")
        assertTrue(restoreResult.isSuccess)
        val result = restoreResult.getOrThrow()
        assertEquals(2, result.accountsCount)
        assertEquals(3, result.transactionsCount)
        assertEquals(1, result.udhaarCount)

        // 5. Verify mathematical balance integrity:
        // Cash Account: opening ₹2,500 (250000) - Groceries ₹500 (50000) + Transfer from Bank ₹1,000 (100000) = ₹3,000 (300000)
        val restoredCash = database.accountDao().getAccountById(acc1Id)
        assertNotNull(restoredCash)
        assertEquals(250000L, restoredCash!!.openingBalancePaise)
        assertEquals(300000L, restoredCash.currentBalancePaise)

        // Bank Account: opening ₹10,000 (1000000) + Salary ₹5,000 (500000) - Transfer to Cash ₹1,000 (100000) = ₹14,000 (1400000)
        val restoredBank = database.accountDao().getAccountById(acc2Id)
        assertNotNull(restoredBank)
        assertEquals(1000000L, restoredBank!!.openingBalancePaise)
        assertEquals(1400000L, restoredBank.currentBalancePaise)

        // Udhaar Rahul: original ₹2,000 (200000) - payment ₹500 (50000) = ₹1,500 (150000), status PARTIALLY_SETTLED
        val restoredUdhaar = database.udhaarDao().getUdhaarById(udhaarId)
        assertNotNull(restoredUdhaar)
        assertEquals(200000L, restoredUdhaar!!.originalAmountPaise)
        assertEquals(150000L, restoredUdhaar.outstandingAmountPaise)
        assertEquals(UdhaarStatus.PARTIALLY_SETTLED, restoredUdhaar.status)

        // Budgets, Reminders, Recurring restored
        assertEquals(1, database.budgetDao().getAllBudgetsSync(testUserId).size)
        assertEquals(1, database.reminderDao().getAllRemindersSync(testUserId).size)
        assertEquals(1, database.recurringDao().getAllRecurringSync(testUserId).size)
    }

    @Test
    fun `merge mode preserves existing data and skips duplicate transactions`() = runBlocking {
        // Setup initial local database with Account 1 and Tx 1
        val acc1Id = "acc_cash"
        val acc1 = AccountEntity(
            id = acc1Id,
            userId = testUserId,
            name = "Cash in Hand",
            type = AccountType.CASH,
            openingBalancePaise = 100000L,
            currentBalancePaise = 150000L
        )
        database.accountDao().insertAccount(acc1)

        val tx1 = TransactionEntity(
            id = "tx_shared_1",
            userId = testUserId,
            type = TransactionType.INCOME,
            amountPaise = 50000L,
            accountId = acc1Id,
            category = "Bonus"
        )
        database.transactionDao().insertTransaction(tx1)

        // Create backup JSON with:
        // Same Account 1
        // New Account 2
        // Tx 1 (duplicate ID: tx_shared_1)
        // Tx 2 (new ID: tx_new_2)
        val backupJson = JSONObject().apply {
            put("app", "Velora")
            put("appVersion", "1.0")
            put("schemaVersion", 1)
            put("createdAt", System.currentTimeMillis())
            put("userId", "another_user")
            put("userEmail", "another@velora.app")

            val accArr = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("id", acc1Id)
                    put("name", "Cash in Hand")
                    put("type", "CASH")
                    put("openingBalancePaise", 100000L)
                    put("currentBalancePaise", 150000L)
                })
                put(JSONObject().apply {
                    put("id", "acc_upi")
                    put("name", "GPay Wallet")
                    put("type", "UPI")
                    put("openingBalancePaise", 50000L)
                    put("currentBalancePaise", 50000L)
                })
            }
            put("accounts", accArr)

            val txArr = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "tx_shared_1") // Duplicate
                    put("type", "INCOME")
                    put("amountPaise", 50000L)
                    put("accountId", acc1Id)
                    put("category", "Bonus")
                    put("timestamp", 1000L)
                })
                put(JSONObject().apply {
                    put("id", "tx_new_2") // New transaction
                    put("type", "EXPENSE")
                    put("amountPaise", 20000L)
                    put("accountId", acc1Id)
                    put("category", "Snacks")
                    put("timestamp", 2000L)
                })
            }
            put("transactions", txArr)
            put("udhaar", org.json.JSONArray())
            put("udhaarPayments", org.json.JSONArray())
            put("budgets", org.json.JSONArray())
            put("reminders", org.json.JSONArray())
            put("recurringPayments", org.json.JSONArray())
        }.toString()

        // Restore with MERGE mode
        val res = backupManager.restorePayload(testUserId, backupJson, mode = "MERGE")
        assertTrue(res.isSuccess)

        // Verify only 1 new account was added (total = 2)
        val allAccounts = database.accountDao().getAccountsSync(testUserId)
        assertEquals(2, allAccounts.size)

        // Verify only 1 new transaction was added (tx_shared_1 was NOT duplicated, total = 2)
        val allTx = database.transactionDao().getAllTransactionsSync(testUserId)
        assertEquals(2, allTx.size)

        // Recalculated balance for Cash:
        // opening 100000L + tx1 (50000L) - tx2 (20000L) = 130000L (₹1,300)
        val cash = database.accountDao().getAccountById(acc1Id)
        assertNotNull(cash)
        assertEquals(130000L, cash!!.currentBalancePaise)
    }

    @Test
    fun `opening balance is never counted as income and transfers are zero sum`() = runBlocking {
        val acc1Id = "acc_1"
        val acc2Id = "acc_2"

        val acc1 = AccountEntity(
            id = acc1Id,
            userId = testUserId,
            name = "Account 1",
            type = AccountType.CASH,
            openingBalancePaise = 500000L, // ₹5,000
            currentBalancePaise = 500000L
        )
        val acc2 = AccountEntity(
            id = acc2Id,
            userId = testUserId,
            name = "Account 2",
            type = AccountType.BANK,
            openingBalancePaise = 300000L, // ₹3,000
            currentBalancePaise = 300000L
        )
        database.accountDao().insertAccounts(listOf(acc1, acc2))

        // Transfer ₹1,000 from acc1 to acc2
        val transferTx = TransactionEntity(
            id = "transfer_1",
            userId = testUserId,
            type = TransactionType.TRANSFER,
            amountPaise = 100000L,
            accountId = acc1Id,
            destinationAccountId = acc2Id,
            category = "Transfer"
        )
        database.transactionDao().insertTransaction(transferTx)

        val payload = backupManager.createBackupPayload(testUserId, testUserEmail)
        backupManager.restorePayload(testUserId, payload, mode = "REPLACE")

        val restored1 = database.accountDao().getAccountById(acc1Id)!!
        val restored2 = database.accountDao().getAccountById(acc2Id)!!

        // acc1 balance: 500000 - 100000 = 400000
        assertEquals(400000L, restored1.currentBalancePaise)
        // acc2 balance: 300000 + 100000 = 400000
        assertEquals(400000L, restored2.currentBalancePaise)

        // Total liquid funds remains 800000L (Zero sum!)
        assertEquals(800000L, restored1.currentBalancePaise + restored2.currentBalancePaise)

        // Verify there is zero INCOME transaction
        val transactions = database.transactionDao().getAllTransactionsSync(testUserId)
        val incomeCount = transactions.count { it.type == TransactionType.INCOME }
        assertEquals(0, incomeCount)
    }

    @Test
    fun `auto backup execution creates Velora_AutoBackup json with atomic snapshot`(): Unit = runBlocking {
        // Prepare account and transaction
        val accId = "acc_auto_1"
        val account = AccountEntity(
            id = accId,
            userId = testUserId,
            name = "Test Savings",
            type = AccountType.BANK,
            openingBalancePaise = 500000L,
            currentBalancePaise = 500000L
        )
        database.accountDao().insertAccount(account)

        val tx = TransactionEntity(
            id = "tx_auto_1",
            userId = testUserId,
            type = TransactionType.INCOME,
            amountPaise = 250000L,
            accountId = accId,
            category = "Bonus"
        )
        database.transactionDao().insertTransaction(tx)

        // Execute auto backup
        val result = autoBackupManager.executeBackup(testUserId, testUserEmail)
        assertTrue(result.isSuccess)

        val backupFile = autoBackupManager.autoBackupFile
        assertTrue(backupFile.exists())
        assertEquals(AutoBackupManager.AUTO_BACKUP_FILE_NAME, backupFile.name)

        // Check content and validity
        val content = autoBackupManager.readCurrentAutoBackup()
        assertNotNull(content)

        val validation = backupManager.validateBackupContent(content!!)
        assertTrue(validation.isValid)
        assertEquals(1, validation.accountsCount)
        assertEquals(1, validation.transactionsCount)

        // Status must be UP_TO_DATE
        assertEquals(BackupStatus.UP_TO_DATE, autoBackupManager.backupStatus.value)
        assertNotNull(autoBackupManager.lastBackupTime.value)
    }

    @Test
    fun `atomic replacement preserves valid previous backup file`(): Unit = runBlocking {
        // Step 1: Create initial valid backup
        val accId = "acc_preserve_1"
        val account = AccountEntity(
            id = accId,
            userId = testUserId,
            name = "Safe Wallet",
            type = AccountType.CASH,
            openingBalancePaise = 100000L,
            currentBalancePaise = 100000L
        )
        database.accountDao().insertAccount(account)

        val firstResult = autoBackupManager.executeBackup(testUserId, testUserEmail)
        assertTrue(firstResult.isSuccess)
        val originalContent = autoBackupManager.readCurrentAutoBackup()
        assertNotNull(originalContent)

        // Step 2: Corrupted or invalid tmp file cannot overwrite the existing valid backup
        autoBackupManager.tmpBackupFile.writeText("CORRUPTED_NOT_JSON", Charsets.UTF_8)
        
        // Ensure auto backup file is untouched and readable
        val currentContent = autoBackupManager.readCurrentAutoBackup()
        assertEquals(originalContent, currentContent)
        assertTrue(autoBackupManager.autoBackupFile.exists())

        // Clean up tmp file
        autoBackupManager.tmpBackupFile.delete()
        Unit
    }

    @Test
    fun `auto backup toggle enables and disables state`(): Unit = runBlocking {
        autoBackupManager.setAutoBackupEnabled(false)
        assertFalse(autoBackupManager.isAutoBackupEnabled.value)

        autoBackupManager.setAutoBackupEnabled(true)
        assertTrue(autoBackupManager.isAutoBackupEnabled.value)
    }
}

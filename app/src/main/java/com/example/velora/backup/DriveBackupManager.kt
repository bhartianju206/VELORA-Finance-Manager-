package com.example.velora.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BackupMetadata(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val transactionCount: Int,
    val accountCount: Int,
    val isCloud: Boolean
)

data class BackupValidationResult(
    val isValid: Boolean,
    val schemaVersion: Int,
    val createdAt: Long,
    val accountsCount: Int,
    val transactionsCount: Int,
    val udhaarCount: Int,
    val paymentsCount: Int = 0,
    val budgetsCount: Int = 0,
    val remindersCount: Int = 0,
    val recurringCount: Int = 0,
    val categoriesCount: Int = 0,
    val totalBalancePaise: Long = 0L,
    val errorMessage: String? = null
)

data class RestoreResult(
    val accountsCount: Int,
    val transactionsCount: Int,
    val udhaarCount: Int,
    val paymentsCount: Int,
    val budgetsCount: Int,
    val remindersCount: Int,
    val recurringCount: Int,
    val mode: String
)

class DriveBackupManager(
    private val context: Context,
    private val database: VeloraDatabase
) {

    private val okHttpClient = OkHttpClient.Builder().build()

    private val backupDir = File(context.filesDir, "velora_backups").apply { mkdirs() }
    private val snapshotDir = File(context.filesDir, "velora_safety_snapshots").apply { mkdirs() }

    /**
     * Builds complete versioned JSON backup payload for the given user.
     * Includes all accounts, transactions, transfers, Udhaar records, repayments,
     * budgets, reminders, recurring definitions, categories, and monthly closing summary.
     */
    suspend fun createBackupPayload(userId: String, userEmail: String): String = withContext(Dispatchers.IO) {
        val accounts = database.accountDao().getAccountsSync(userId)
        val transactions = database.transactionDao().getAllTransactionsSync(userId)
        val udhaar = database.udhaarDao().getAllUdhaarSync(userId)
        val payments = database.udhaarDao().getAllPaymentsSync(userId)
        val budgets = database.budgetDao().getAllBudgetsSync(userId)
        val reminders = database.reminderDao().getAllRemindersSync(userId)
        val recurring = database.recurringDao().getAllRecurringSync(userId)

        // Compile unique categories
        val standardCategories = setOf(
            "Food & Dining", "Shopping", "Groceries", "Bills", "Transport",
            "Entertainment", "Health", "Salary", "Investment", "Transfer",
            "Udhaar: Lent", "Udhaar: Borrowed", "Udhaar: Recovered", "Udhaar: Repaid", "Reconciliation"
        )
        val userCategories = (
            transactions.map { it.category } +
            budgets.map { it.category } +
            reminders.map { it.category } +
            recurring.map { it.category }
        ).filter { it.isNotBlank() && it != "__OVERALL__" }.toSet()
        val allCategories = (standardCategories + userCategories).toList().sorted()

        // Balance & closing summary calculations
        val totalLiquidPaise = accounts.sumOf { it.currentBalancePaise }
        val totalReceivablesPaise = udhaar
            .filter { it.type == UdhaarType.LENT && it.status != UdhaarStatus.SETTLED }
            .sumOf { it.outstandingAmountPaise }
        val totalPayablesPaise = udhaar
            .filter { it.type == UdhaarType.BORROWED && it.status != UdhaarStatus.SETTLED }
            .sumOf { it.outstandingAmountPaise }
        val netPositionPaise = totalLiquidPaise + totalReceivablesPaise - totalPayablesPaise

        val json = JSONObject()
        json.put("app", "Velora")
        json.put("appVersion", "1.0")
        json.put("schemaVersion", 1)
        json.put("createdAt", System.currentTimeMillis())
        json.put("userId", userId)
        json.put("userEmail", userEmail)

        // Summary
        val exportSummary = JSONObject().apply {
            put("totalAccounts", accounts.size)
            put("totalTransactions", transactions.size)
            put("totalUdhaarRecords", udhaar.size)
            put("totalUdhaarPayments", payments.size)
            put("totalBudgets", budgets.size)
            put("totalReminders", reminders.size)
            put("totalRecurring", recurring.size)
            put("totalLiquidBalancePaise", totalLiquidPaise)
        }
        json.put("exportSummary", exportSummary)

        val closingSummary = JSONObject().apply {
            put("totalLiquidBalancePaise", totalLiquidPaise)
            put("totalReceivablesPaise", totalReceivablesPaise)
            put("totalPayablesPaise", totalPayablesPaise)
            put("netPositionPaise", netPositionPaise)
        }
        json.put("closingSummary", closingSummary)

        // Categories
        val categoriesArray = JSONArray()
        allCategories.forEach { categoriesArray.put(it) }
        json.put("categories", categoriesArray)

        // Accounts
        val accountsArray = JSONArray()
        for (a in accounts) {
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("userId", a.userId)
            obj.put("name", a.name)
            obj.put("type", a.type.name)
            obj.put("openingBalancePaise", a.openingBalancePaise)
            obj.put("currentBalancePaise", a.currentBalancePaise)
            obj.put("colorHex", a.colorHex)
            obj.put("isArchived", a.isArchived)
            obj.put("updatedAt", a.updatedAt)
            accountsArray.put(obj)
        }
        json.put("accounts", accountsArray)

        // Transactions
        val transactionsArray = JSONArray()
        for (t in transactions) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("userId", t.userId)
            obj.put("type", t.type.name)
            obj.put("amountPaise", t.amountPaise)
            obj.put("accountId", t.accountId)
            obj.put("destinationAccountId", t.destinationAccountId ?: "")
            obj.put("category", t.category)
            obj.put("note", t.note)
            obj.put("timestamp", t.timestamp)
            obj.put("isDeleted", t.isDeleted)
            obj.put("isReconciled", t.isReconciled)
            obj.put("referenceId", t.referenceId ?: "")
            transactionsArray.put(obj)
        }
        json.put("transactions", transactionsArray)

        // Udhaar
        val udhaarArray = JSONArray()
        for (u in udhaar) {
            val obj = JSONObject()
            obj.put("id", u.id)
            obj.put("userId", u.userId)
            obj.put("personName", u.personName)
            obj.put("type", u.type.name)
            obj.put("originalAmountPaise", u.originalAmountPaise)
            obj.put("outstandingAmountPaise", u.outstandingAmountPaise)
            obj.put("createdAt", u.createdAt)
            if (u.dueDate != null) obj.put("dueDate", u.dueDate)
            obj.put("notes", u.notes)
            obj.put("status", u.status.name)
            udhaarArray.put(obj)
        }
        json.put("udhaar", udhaarArray)

        // Udhaar Payments
        val paymentsArray = JSONArray()
        for (p in payments) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("udhaarId", p.udhaarId)
            obj.put("userId", p.userId)
            obj.put("amountPaise", p.amountPaise)
            obj.put("accountId", p.accountId)
            obj.put("timestamp", p.timestamp)
            obj.put("note", p.note)
            paymentsArray.put(obj)
        }
        json.put("udhaarPayments", paymentsArray)

        // Budgets
        val budgetsArray = JSONArray()
        for (b in budgets) {
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("userId", b.userId)
            obj.put("category", b.category)
            obj.put("monthYear", b.monthYear)
            obj.put("limitPaise", b.limitPaise)
            obj.put("alertThresholdPercent", b.alertThresholdPercent)
            budgetsArray.put(obj)
        }
        json.put("budgets", budgetsArray)

        // Reminders
        val remindersArray = JSONArray()
        for (r in reminders) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("userId", r.userId)
            obj.put("title", r.title)
            obj.put("amountPaise", r.amountPaise)
            obj.put("category", r.category)
            obj.put("dueDate", r.dueDate)
            obj.put("type", r.type.name)
            obj.put("isCompleted", r.isCompleted)
            obj.put("isSnoozed", r.isSnoozed)
            if (r.snoozedUntil != null) obj.put("snoozedUntil", r.snoozedUntil)
            remindersArray.put(obj)
        }
        json.put("reminders", remindersArray)

        // Recurring Payments
        val recurringArray = JSONArray()
        for (rc in recurring) {
            val obj = JSONObject()
            obj.put("id", rc.id)
            obj.put("userId", rc.userId)
            obj.put("title", rc.title)
            obj.put("amountPaise", rc.amountPaise)
            obj.put("category", rc.category)
            obj.put("interval", rc.interval.name)
            obj.put("accountId", rc.accountId)
            obj.put("nextDueDate", rc.nextDueDate)
            if (rc.lastProcessedDate != null) obj.put("lastProcessedDate", rc.lastProcessedDate)
            obj.put("isActive", rc.isActive)
            recurringArray.put(obj)
        }
        json.put("recurringPayments", recurringArray)

        // Checksum
        val contentWithoutChecksum = json.toString()
        val checksum = calculateSha256(contentWithoutChecksum)
        json.put("checksumSha256", checksum)

        json.toString(2)
    }

    /**
     * Writes full JSON backup payload to an Android Storage Access Framework Uri.
     */
    suspend fun exportToUri(uri: Uri, userId: String, userEmail: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = createBackupPayload(userId, userEmail)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(payload.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: error("Unable to open output stream for selected file")
            payload
        }
    }

    /**
     * Reads text content from an Android Storage Access Framework Uri.
     */
    suspend fun readFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Unable to open input stream for selected file")
        }
    }

    /**
     * Validates a backup string (integrity, schema, checksum).
     */
    fun validateBackupContent(content: String): BackupValidationResult {
        return try {
            val json = JSONObject(content)
            if (!json.has("app") || json.getString("app") != "Velora") {
                return BackupValidationResult(
                    isValid = false,
                    schemaVersion = 0,
                    createdAt = 0,
                    accountsCount = 0,
                    transactionsCount = 0,
                    udhaarCount = 0,
                    errorMessage = "Invalid backup format: Not a genuine Velora backup file."
                )
            }
            val schema = json.getInt("schemaVersion")
            if (schema > 1) {
                return BackupValidationResult(
                    isValid = false,
                    schemaVersion = schema,
                    createdAt = 0,
                    accountsCount = 0,
                    transactionsCount = 0,
                    udhaarCount = 0,
                    errorMessage = "Unsupported schema version $schema. Please update Velora to restore."
                )
            }

            val storedChecksum = json.optString("checksumSha256", "")
            if (storedChecksum.isNotEmpty()) {
                val copy = JSONObject(content)
                copy.remove("checksumSha256")
                val computed = calculateSha256(copy.toString())
                if (computed != storedChecksum) {
                    // Checksum mismatch might indicate manual editing or corruption
                    return BackupValidationResult(
                        isValid = false,
                        schemaVersion = schema,
                        createdAt = json.optLong("createdAt", 0),
                        accountsCount = 0,
                        transactionsCount = 0,
                        udhaarCount = 0,
                        errorMessage = "Backup integrity check failed: Checksum mismatch."
                    )
                }
            }

            val accountsArray = json.optJSONArray("accounts")
            val accountsCount = accountsArray?.length() ?: 0
            val txArray = json.optJSONArray("transactions")
            val txCount = txArray?.length() ?: 0
            val udhaarArray = json.optJSONArray("udhaar")
            val udhaarCount = udhaarArray?.length() ?: 0
            val paymentsArray = json.optJSONArray("udhaarPayments")
            val paymentsCount = paymentsArray?.length() ?: 0
            val budgetsArray = json.optJSONArray("budgets")
            val budgetsCount = budgetsArray?.length() ?: 0
            val remindersArray = json.optJSONArray("reminders")
            val remindersCount = remindersArray?.length() ?: 0
            val recurringArray = json.optJSONArray("recurringPayments")
            val recurringCount = recurringArray?.length() ?: 0
            val categoriesArray = json.optJSONArray("categories")
            val categoriesCount = categoriesArray?.length() ?: 0

            var totalBalance = 0L
            if (accountsArray != null) {
                for (i in 0 until accountsArray.length()) {
                    val acc = accountsArray.getJSONObject(i)
                    totalBalance += acc.optLong("currentBalancePaise", 0L)
                }
            }

            val createdAt = json.optLong("createdAt", System.currentTimeMillis())

            BackupValidationResult(
                isValid = true,
                schemaVersion = schema,
                createdAt = createdAt,
                accountsCount = accountsCount,
                transactionsCount = txCount,
                udhaarCount = udhaarCount,
                paymentsCount = paymentsCount,
                budgetsCount = budgetsCount,
                remindersCount = remindersCount,
                recurringCount = recurringCount,
                categoriesCount = categoriesCount,
                totalBalancePaise = totalBalance,
                errorMessage = null
            )
        } catch (e: Exception) {
            BackupValidationResult(
                isValid = false,
                schemaVersion = 0,
                createdAt = 0,
                accountsCount = 0,
                transactionsCount = 0,
                udhaarCount = 0,
                errorMessage = "Corrupted or invalid JSON: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Creates an automatic safety snapshot before restore.
     */
    suspend fun createSafetySnapshot(userId: String, userEmail: String): File = withContext(Dispatchers.IO) {
        val payload = createBackupPayload(userId, userEmail)
        val file = File(snapshotDir, "snapshot_before_restore_${System.currentTimeMillis()}.json")
        file.writeText(payload)
        file
    }

    /**
     * Transactionally restores backup payload into Room database.
     * Mode: "REPLACE" (or "REPLACE_ALL") vs "MERGE"
     * Performs strict mathematical recalculation of all account balances and Udhaar statuses.
     */
    suspend fun restorePayload(
        userId: String,
        content: String,
        mode: String = "REPLACE"
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val validation = validateBackupContent(content)
            if (!validation.isValid) {
                error(validation.errorMessage ?: "Validation failed")
            }

            val isReplaceMode = mode.equals("REPLACE", ignoreCase = true) || mode.equals("REPLACE_ALL", ignoreCase = true)
            val json = JSONObject(content)

            database.withTransaction {
                // If in MERGE mode, retrieve existing record IDs
                val existingAccounts = if (!isReplaceMode) database.accountDao().getAccountsSync(userId) else emptyList()
                val existingTransactions = if (!isReplaceMode) database.transactionDao().getAllTransactionsSync(userId) else emptyList()
                val existingUdhaar = if (!isReplaceMode) database.udhaarDao().getAllUdhaarSync(userId) else emptyList()
                val existingPayments = if (!isReplaceMode) database.udhaarDao().getAllPaymentsSync(userId) else emptyList()
                val existingBudgets = if (!isReplaceMode) database.budgetDao().getAllBudgetsSync(userId) else emptyList()
                val existingReminders = if (!isReplaceMode) database.reminderDao().getAllRemindersSync(userId) else emptyList()
                val existingRecurring = if (!isReplaceMode) database.recurringDao().getAllRecurringSync(userId) else emptyList()

                val existingAccIds = existingAccounts.map { it.id }.toSet()
                val existingTxIds = existingTransactions.map { it.id }.toSet()
                val existingUdhaarIds = existingUdhaar.map { it.id }.toSet()
                val existingPaymentIds = existingPayments.map { it.id }.toSet()
                val existingBudgetIds = existingBudgets.map { it.id }.toSet()
                val existingReminderIds = existingReminders.map { it.id }.toSet()
                val existingRecurringIds = existingRecurring.map { it.id }.toSet()

                if (isReplaceMode) {
                    // Wipe current user data cleanly
                    database.accountDao().deleteAllForUser(userId)
                    database.transactionDao().deleteAllForUser(userId)
                    database.udhaarDao().deleteAllForUser(userId)
                    database.budgetDao().deleteAllForUser(userId)
                    database.reminderDao().deleteAllForUser(userId)
                    database.recurringDao().deleteAllForUser(userId)
                }

                var restoredAccCount = 0
                var restoredTxCount = 0
                var restoredUdhaarCount = 0
                var restoredPaymentsCount = 0
                var restoredBudgetsCount = 0
                var restoredRemindersCount = 0
                var restoredRecurringCount = 0

                // 1. Restore Accounts
                val accountsArray = json.optJSONArray("accounts")
                if (accountsArray != null) {
                    for (i in 0 until accountsArray.length()) {
                        val obj = accountsArray.getJSONObject(i)
                        val id = obj.getString("id")
                        if (isReplaceMode || id !in existingAccIds) {
                            val acc = AccountEntity(
                                id = id,
                                userId = userId,
                                name = obj.getString("name"),
                                type = runCatching { AccountType.valueOf(obj.getString("type")) }.getOrDefault(AccountType.CUSTOM),
                                openingBalancePaise = obj.getLong("openingBalancePaise"),
                                currentBalancePaise = obj.getLong("currentBalancePaise"),
                                colorHex = obj.optString("colorHex", "#10B981"),
                                isArchived = obj.optBoolean("isArchived", false),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                            database.accountDao().insertAccount(acc)
                            restoredAccCount++
                        }
                    }
                }

                // 2. Restore Transactions (with stable ID duplicate prevention)
                val txArray = json.optJSONArray("transactions")
                if (txArray != null) {
                    for (i in 0 until txArray.length()) {
                        val obj = txArray.getJSONObject(i)
                        val id = obj.getString("id")
                        if (isReplaceMode || id !in existingTxIds) {
                            val rawDest = obj.optString("destinationAccountId", "")
                            val rawRef = obj.optString("referenceId", "")
                            val tx = TransactionEntity(
                                id = id,
                                userId = userId,
                                type = runCatching { TransactionType.valueOf(obj.getString("type")) }.getOrDefault(TransactionType.EXPENSE),
                                amountPaise = obj.getLong("amountPaise"),
                                accountId = obj.getString("accountId"),
                                destinationAccountId = if (rawDest.isNotBlank() && rawDest != "null") rawDest else null,
                                category = obj.optString("category", "General"),
                                note = obj.optString("note", ""),
                                timestamp = obj.getLong("timestamp"),
                                isDeleted = obj.optBoolean("isDeleted", false),
                                isReconciled = obj.optBoolean("isReconciled", false),
                                referenceId = if (rawRef.isNotBlank() && rawRef != "null") rawRef else null
                            )
                            database.transactionDao().insertTransaction(tx)
                            restoredTxCount++
                        }
                    }
                }

                // 3. Restore Udhaar
                val udhaarArray = json.optJSONArray("udhaar")
                if (udhaarArray != null) {
                    for (i in 0 until udhaarArray.length()) {
                        val obj = udhaarArray.getJSONObject(i)
                        val id = obj.getString("id")
                        if (isReplaceMode || id !in existingUdhaarIds) {
                            val u = UdhaarEntity(
                                id = id,
                                userId = userId,
                                personName = obj.getString("personName"),
                                type = runCatching { UdhaarType.valueOf(obj.getString("type")) }.getOrDefault(UdhaarType.LENT),
                                originalAmountPaise = obj.getLong("originalAmountPaise"),
                                outstandingAmountPaise = obj.getLong("outstandingAmountPaise"),
                                createdAt = obj.getLong("createdAt"),
                                dueDate = if (obj.has("dueDate") && !obj.isNull("dueDate")) obj.getLong("dueDate") else null,
                                notes = obj.optString("notes", ""),
                                status = runCatching { UdhaarStatus.valueOf(obj.getString("status")) }.getOrDefault(UdhaarStatus.PENDING)
                            )
                            database.udhaarDao().insertUdhaar(u)
                            restoredUdhaarCount++
                        }
                    }
                }

                // 4. Restore Udhaar Payments
                val paymentsArray = json.optJSONArray("udhaarPayments")
                if (paymentsArray != null) {
                    for (i in 0 until paymentsArray.length()) {
                        val obj = paymentsArray.getJSONObject(i)
                        val id = obj.getString("id")
                        if (isReplaceMode || id !in existingPaymentIds) {
                            val p = UdhaarPaymentEntity(
                                id = id,
                                udhaarId = obj.getString("udhaarId"),
                                userId = userId,
                                amountPaise = obj.getLong("amountPaise"),
                                accountId = obj.getString("accountId"),
                                timestamp = obj.getLong("timestamp"),
                                note = obj.optString("note", "")
                            )
                            database.udhaarDao().insertPayment(p)
                            restoredPaymentsCount++
                        }
                    }
                }

                // 5. Restore Budgets
                val budgetsArray = json.optJSONArray("budgets")
                if (budgetsArray != null) {
                    for (i in 0 until budgetsArray.length()) {
                        val obj = budgetsArray.getJSONObject(i)
                        val id = obj.getString("id")
                        if (isReplaceMode || id !in existingBudgetIds) {
                            val b = BudgetEntity(
                                id = id,
                                userId = userId,
                                category = obj.getString("category"),
                                monthYear = obj.getString("monthYear"),
                                limitPaise = obj.getLong("limitPaise"),
                                alertThresholdPercent = obj.optInt("alertThresholdPercent", 80)
                            )
                            database.budgetDao().insertBudget(b)
                            restoredBudgetsCount++
                        }
                    }
                }

                // 6. Restore Reminders
                val remindersArray = json.optJSONArray("reminders")
                if (remindersArray != null) {
                    for (i in 0 until remindersArray.length()) {
                        val obj = remindersArray.getJSONObject(i)
                        val id = obj.getString("id")
                        if (isReplaceMode || id !in existingReminderIds) {
                            val r = ReminderEntity(
                                id = id,
                                userId = userId,
                                title = obj.getString("title"),
                                amountPaise = obj.getLong("amountPaise"),
                                category = obj.getString("category"),
                                dueDate = obj.getLong("dueDate"),
                                type = runCatching { ReminderType.valueOf(obj.getString("type")) }.getOrDefault(ReminderType.BILL),
                                isCompleted = obj.optBoolean("isCompleted", false),
                                isSnoozed = obj.optBoolean("isSnoozed", false),
                                snoozedUntil = if (obj.has("snoozedUntil") && !obj.isNull("snoozedUntil")) obj.getLong("snoozedUntil") else null
                            )
                            database.reminderDao().insertReminder(r)
                            restoredRemindersCount++
                        }
                    }
                }

                // 7. Restore Recurring Payments
                val recurringArray = json.optJSONArray("recurringPayments")
                if (recurringArray != null) {
                    for (i in 0 until recurringArray.length()) {
                        val obj = recurringArray.getJSONObject(i)
                        val id = obj.getString("id")
                        if (isReplaceMode || id !in existingRecurringIds) {
                            val rc = RecurringPaymentEntity(
                                id = id,
                                userId = userId,
                                title = obj.getString("title"),
                                amountPaise = obj.getLong("amountPaise"),
                                category = obj.getString("category"),
                                interval = runCatching { RecurrenceInterval.valueOf(obj.getString("interval")) }.getOrDefault(RecurrenceInterval.MONTHLY),
                                accountId = obj.getString("accountId"),
                                nextDueDate = obj.getLong("nextDueDate"),
                                lastProcessedDate = if (obj.has("lastProcessedDate") && !obj.isNull("lastProcessedDate")) obj.getLong("lastProcessedDate") else null,
                                isActive = obj.optBoolean("isActive", true)
                            )
                            database.recurringDao().insertRecurring(rc)
                            restoredRecurringCount++
                        }
                    }
                }

                // =========================================================================
                // 8. MANDATORY MATHEMATICAL INTEGRITY RECALCULATION
                // =========================================================================
                // Recalculate each account's current balance from opening balance and valid transactions
                val allCurrentAccounts = database.accountDao().getAccountsSync(userId)
                val allCurrentTransactions = database.transactionDao().getAllTransactionsSync(userId)

                for (account in allCurrentAccounts) {
                    var calculatedBalance = account.openingBalancePaise
                    for (tx in allCurrentTransactions) {
                        if (tx.isDeleted) continue
                        when (tx.type) {
                            TransactionType.INCOME -> {
                                if (tx.accountId == account.id) {
                                    calculatedBalance += tx.amountPaise
                                }
                            }
                            TransactionType.EXPENSE -> {
                                if (tx.accountId == account.id) {
                                    calculatedBalance -= tx.amountPaise
                                }
                            }
                            TransactionType.TRANSFER -> {
                                if (tx.accountId == account.id) {
                                    calculatedBalance -= tx.amountPaise
                                }
                                if (tx.destinationAccountId == account.id) {
                                    calculatedBalance += tx.amountPaise
                                }
                            }
                            TransactionType.LEND -> {
                                if (tx.accountId == account.id) {
                                    calculatedBalance -= tx.amountPaise
                                }
                            }
                            TransactionType.BORROW -> {
                                if (tx.accountId == account.id) {
                                    calculatedBalance += tx.amountPaise
                                }
                            }
                            TransactionType.LEND_REPAYMENT -> {
                                if (tx.accountId == account.id) {
                                    calculatedBalance += tx.amountPaise
                                }
                            }
                            TransactionType.BORROW_REPAYMENT -> {
                                if (tx.accountId == account.id) {
                                    calculatedBalance -= tx.amountPaise
                                }
                            }
                            TransactionType.ADJUSTMENT -> {
                                if (tx.accountId == account.id) {
                                    calculatedBalance += tx.amountPaise
                                }
                            }
                        }
                    }
                    database.accountDao().updateBalance(account.id, calculatedBalance)
                }

                // Recalculate Udhaar outstanding balance and status from repayments
                val allCurrentUdhaar = database.udhaarDao().getAllUdhaarSync(userId)
                val allCurrentPayments = database.udhaarDao().getAllPaymentsSync(userId)

                for (u in allCurrentUdhaar) {
                    val repaymentsForUdhaar = allCurrentPayments.filter { it.udhaarId == u.id }
                    val totalRepaid = repaymentsForUdhaar.sumOf { it.amountPaise }
                    val outstanding = Math.max(0L, u.originalAmountPaise - totalRepaid)
                    val status = when {
                        outstanding <= 0L -> UdhaarStatus.SETTLED
                        outstanding < u.originalAmountPaise -> UdhaarStatus.PARTIALLY_SETTLED
                        else -> UdhaarStatus.PENDING
                    }
                    val updatedUdhaar = u.copy(
                        outstandingAmountPaise = outstanding,
                        status = status
                    )
                    database.udhaarDao().updateUdhaar(updatedUdhaar)
                }

                RestoreResult(
                    accountsCount = restoredAccCount,
                    transactionsCount = restoredTxCount,
                    udhaarCount = restoredUdhaarCount,
                    paymentsCount = restoredPaymentsCount,
                    budgetsCount = restoredBudgetsCount,
                    remindersCount = restoredRemindersCount,
                    recurringCount = restoredRecurringCount,
                    mode = if (isReplaceMode) "REPLACE" else "MERGE"
                )
            }
        }
    }

    /**
     * Creates a local backup file and returns its metadata.
     */
    suspend fun performLocalBackup(userId: String, userEmail: String): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = createBackupPayload(userId, userEmail)
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "velora_backup_$dateStr.json"
            val file = File(backupDir, fileName)
            file.writeText(payload)

            val parsed = JSONObject(payload)
            val txCount = parsed.getJSONArray("transactions").length()
            val accCount = parsed.getJSONArray("accounts").length()

            BackupMetadata(
                fileId = file.absolutePath,
                fileName = fileName,
                sizeBytes = file.length(),
                createdAt = parsed.getLong("createdAt"),
                transactionCount = txCount,
                accountCount = accCount,
                isCloud = false
            )
        }
    }

    /**
     * Lists existing local backups.
     */
    suspend fun listLocalBackups(): List<BackupMetadata> = withContext(Dispatchers.IO) {
        backupDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    val content = file.readText()
                    val json = JSONObject(content)
                    BackupMetadata(
                        fileId = file.absolutePath,
                        fileName = file.name,
                        sizeBytes = file.length(),
                        createdAt = json.optLong("createdAt", file.lastModified()),
                        transactionCount = json.optJSONArray("transactions")?.length() ?: 0,
                        accountCount = json.optJSONArray("accounts")?.length() ?: 0,
                        isCloud = false
                    )
                }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /**
     * Uploads backup to Google Drive REST API using provided OAuth Bearer token.
     */
    suspend fun uploadToGoogleDrive(
        accessToken: String,
        userId: String,
        userEmail: String
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = createBackupPayload(userId, userEmail)
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "velora_backup_$dateStr.json"

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("description", "Velora Financial Backup for $userEmail")
                put("mimeType", "application/json")
            }.toString()

            val boundary = "==VeloraDriveBoundary${System.currentTimeMillis()}=="
            val multipartBody = buildString {
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadataJson)
                append("\r\n--$boundary\r\n")
                append("Content-Type: application/json\r\n\r\n")
                append(payload)
                append("\r\n--$boundary--\r\n")
            }

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("Google Drive API error: ${response.code} ${response.message}")
            }

            val responseBody = response.body?.string() ?: ""
            val respJson = JSONObject(responseBody)
            val driveFileId = respJson.getString("id")

            val parsed = JSONObject(payload)
            BackupMetadata(
                fileId = driveFileId,
                fileName = fileName,
                sizeBytes = payload.toByteArray().size.toLong(),
                createdAt = parsed.getLong("createdAt"),
                transactionCount = parsed.getJSONArray("transactions").length(),
                accountCount = parsed.getJSONArray("accounts").length(),
                isCloud = true
            )
        }
    }

    /**
     * Lists Velora backups stored in user's Google Drive.
     */
    suspend fun listGoogleDriveBackups(accessToken: String): Result<List<BackupMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = "name contains 'velora_backup' and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,size,createdTime)"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("Google Drive API error: ${response.code} ${response.message}")
            }

            val respJson = JSONObject(response.body?.string() ?: "{}")
            val filesArray = respJson.optJSONArray("files") ?: JSONArray()
            val list = mutableListOf<BackupMetadata>()

            for (i in 0 until filesArray.length()) {
                val f = filesArray.getJSONObject(i)
                list.add(
                    BackupMetadata(
                        fileId = f.getString("id"),
                        fileName = f.getString("name"),
                        sizeBytes = f.optLong("size", 0L),
                        createdAt = System.currentTimeMillis(),
                        transactionCount = 0,
                        accountCount = 0,
                        isCloud = true
                    )
                )
            }
            list
        }
    }

    /**
     * Downloads backup file from Google Drive.
     */
    suspend fun downloadFromGoogleDrive(accessToken: String, fileId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("Failed to download from Drive: ${response.code}")
            }
            response.body?.string() ?: error("Empty response")
        }
    }

    private fun calculateSha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

package com.example.velora.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.velora.data.database.VeloraDatabase
import com.example.velora.data.entity.AccountEntity
import com.example.velora.data.entity.BudgetEntity
import com.example.velora.data.entity.RecurringPaymentEntity
import com.example.velora.data.entity.ReminderEntity
import com.example.velora.data.entity.TransactionEntity
import com.example.velora.data.entity.UdhaarEntity
import com.example.velora.data.entity.UdhaarPaymentEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val errorMessage: String? = null
)

class DriveBackupManager(
    private val context: Context,
    private val database: VeloraDatabase
) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder().build()

    private val backupDir = File(context.filesDir, "velora_backups").apply { mkdirs() }
    private val snapshotDir = File(context.filesDir, "velora_safety_snapshots").apply { mkdirs() }

    /**
     * Builds complete JSON backup payload for the current user.
     */
    suspend fun createBackupPayload(userId: String, userEmail: String): String = withContext(Dispatchers.IO) {
        val accounts = database.accountDao().getAccountsSync(userId)
        val transactions = database.transactionDao().getAllTransactionsSync(userId)
        val udhaar = database.udhaarDao().getAllUdhaarSync(userId)
        val payments = database.udhaarDao().getAllPaymentsSync(userId)
        val budgets = database.budgetDao().getAllBudgetsSync(userId)
        val reminders = database.reminderDao().getAllRemindersSync(userId)
        val recurring = database.recurringDao().getAllRecurringSync(userId)

        val json = JSONObject()
        json.put("app", "Velora")
        json.put("appVersion", "1.0")
        json.put("schemaVersion", 1)
        json.put("createdAt", System.currentTimeMillis())
        json.put("userId", userId)
        json.put("userEmail", userEmail)

        // Convert lists to JSON
        json.put("accounts", serializeList(accounts))
        json.put("transactions", serializeList(transactions))
        json.put("udhaar", serializeList(udhaar))
        json.put("udhaarPayments", serializeList(payments))
        json.put("budgets", serializeList(budgets))
        json.put("reminders", serializeList(reminders))
        json.put("recurringPayments", serializeList(recurring))

        // Checksum
        val contentWithoutChecksum = json.toString()
        val checksum = calculateSha256(contentWithoutChecksum)
        json.put("checksumSha256", checksum)

        json.toString(2)
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
     * Validates a backup string (integrity, schema, checksum).
     */
    fun validateBackupContent(content: String): BackupValidationResult {
        return try {
            val json = JSONObject(content)
            if (!json.has("app") || json.getString("app") != "Velora") {
                return BackupValidationResult(false, 0, 0, 0, 0, 0, "Invalid backup format: Not a Velora backup.")
            }
            val schema = json.getInt("schemaVersion")
            if (schema > 1) {
                return BackupValidationResult(false, schema, 0, 0, 0, 0, "Unsupported schema version $schema.")
            }

            val storedChecksum = json.optString("checksumSha256", "")
            if (storedChecksum.isNotEmpty()) {
                val copy = JSONObject(content)
                copy.remove("checksumSha256")
                val computed = calculateSha256(copy.toString())
                if (computed != storedChecksum) {
                    return BackupValidationResult(false, schema, 0, 0, 0, 0, "Backup integrity compromised: Checksum mismatch.")
                }
            }

            val accountsCount = json.optJSONArray("accounts")?.length() ?: 0
            val txCount = json.optJSONArray("transactions")?.length() ?: 0
            val udhaarCount = json.optJSONArray("udhaar")?.length() ?: 0
            val createdAt = json.optLong("createdAt", System.currentTimeMillis())

            BackupValidationResult(true, schema, createdAt, accountsCount, txCount, udhaarCount, null)
        } catch (e: Exception) {
            BackupValidationResult(false, 0, 0, 0, 0, 0, "Corrupted file: ${e.localizedMessage}")
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
     * Mode: "REPLACE" or "MERGE"
     */
    suspend fun restorePayload(
        userId: String,
        content: String,
        mode: String = "REPLACE"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val validation = validateBackupContent(content)
            if (!validation.isValid) {
                error(validation.errorMessage ?: "Validation failed")
            }

            val json = JSONObject(content)

            database.withTransaction {
                if (mode == "REPLACE") {
                    // Wipe current user data cleanly
                    database.accountDao().deleteAllForUser(userId)
                    database.transactionDao().deleteAllForUser(userId)
                    database.udhaarDao().deleteAllForUser(userId)
                    database.budgetDao().deleteAllForUser(userId)
                    database.reminderDao().deleteAllForUser(userId)
                    database.recurringDao().deleteAllForUser(userId)
                }

                // Restore Accounts
                val accountsArray = json.optJSONArray("accounts")
                if (accountsArray != null) {
                    for (i in 0 until accountsArray.length()) {
                        val obj = accountsArray.getJSONObject(i)
                        val acc = AccountEntity(
                            id = obj.getString("id"),
                            userId = userId, // Map to active authenticated user
                            name = obj.getString("name"),
                            type = com.example.velora.data.entity.AccountType.valueOf(obj.getString("type")),
                            openingBalancePaise = obj.getLong("openingBalancePaise"),
                            currentBalancePaise = obj.getLong("currentBalancePaise"),
                            colorHex = obj.optString("colorHex", "#10B981"),
                            isArchived = obj.optBoolean("isArchived", false),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                        database.accountDao().insertAccount(acc)
                    }
                }

                // Restore Transactions
                val txArray = json.optJSONArray("transactions")
                if (txArray != null) {
                    for (i in 0 until txArray.length()) {
                        val obj = txArray.getJSONObject(i)
                        val tx = TransactionEntity(
                            id = obj.getString("id"),
                            userId = userId,
                            type = com.example.velora.data.entity.TransactionType.valueOf(obj.getString("type")),
                            amountPaise = obj.getLong("amountPaise"),
                            accountId = obj.getString("accountId"),
                            destinationAccountId = obj.optString("destinationAccountId").ifEmpty { null },
                            category = obj.getString("category"),
                            note = obj.optString("note", ""),
                            timestamp = obj.getLong("timestamp"),
                            isDeleted = obj.optBoolean("isDeleted", false),
                            isReconciled = obj.optBoolean("isReconciled", false),
                            referenceId = obj.optString("referenceId").ifEmpty { null }
                        )
                        database.transactionDao().insertTransaction(tx)
                    }
                }

                // Restore Udhaar
                val udhaarArray = json.optJSONArray("udhaar")
                if (udhaarArray != null) {
                    for (i in 0 until udhaarArray.length()) {
                        val obj = udhaarArray.getJSONObject(i)
                        val u = UdhaarEntity(
                            id = obj.getString("id"),
                            userId = userId,
                            personName = obj.getString("personName"),
                            type = com.example.velora.data.entity.UdhaarType.valueOf(obj.getString("type")),
                            originalAmountPaise = obj.getLong("originalAmountPaise"),
                            outstandingAmountPaise = obj.getLong("outstandingAmountPaise"),
                            createdAt = obj.getLong("createdAt"),
                            dueDate = if (obj.has("dueDate") && !obj.isNull("dueDate")) obj.getLong("dueDate") else null,
                            notes = obj.optString("notes", ""),
                            status = com.example.velora.data.entity.UdhaarStatus.valueOf(obj.getString("status"))
                        )
                        database.udhaarDao().insertUdhaar(u)
                    }
                }

                // Restore Udhaar Payments
                val paymentsArray = json.optJSONArray("udhaarPayments")
                if (paymentsArray != null) {
                    for (i in 0 until paymentsArray.length()) {
                        val obj = paymentsArray.getJSONObject(i)
                        val p = UdhaarPaymentEntity(
                            id = obj.getString("id"),
                            udhaarId = obj.getString("udhaarId"),
                            userId = userId,
                            amountPaise = obj.getLong("amountPaise"),
                            accountId = obj.getString("accountId"),
                            timestamp = obj.getLong("timestamp"),
                            note = obj.optString("note", "")
                        )
                        database.udhaarDao().insertPayment(p)
                    }
                }

                // Restore Budgets
                val budgetsArray = json.optJSONArray("budgets")
                if (budgetsArray != null) {
                    for (i in 0 until budgetsArray.length()) {
                        val obj = budgetsArray.getJSONObject(i)
                        val b = BudgetEntity(
                            id = obj.getString("id"),
                            userId = userId,
                            category = obj.getString("category"),
                            monthYear = obj.getString("monthYear"),
                            limitPaise = obj.getLong("limitPaise"),
                            alertThresholdPercent = obj.optInt("alertThresholdPercent", 80)
                        )
                        database.budgetDao().insertBudget(b)
                    }
                }

                // Restore Reminders
                val remindersArray = json.optJSONArray("reminders")
                if (remindersArray != null) {
                    for (i in 0 until remindersArray.length()) {
                        val obj = remindersArray.getJSONObject(i)
                        val r = ReminderEntity(
                            id = obj.getString("id"),
                            userId = userId,
                            title = obj.getString("title"),
                            amountPaise = obj.getLong("amountPaise"),
                            category = obj.getString("category"),
                            dueDate = obj.getLong("dueDate"),
                            type = com.example.velora.data.entity.ReminderType.valueOf(obj.getString("type")),
                            isCompleted = obj.optBoolean("isCompleted", false)
                        )
                        database.reminderDao().insertReminder(r)
                    }
                }

                // Restore Recurring
                val recurringArray = json.optJSONArray("recurringPayments")
                if (recurringArray != null) {
                    for (i in 0 until recurringArray.length()) {
                        val obj = recurringArray.getJSONObject(i)
                        val rc = RecurringPaymentEntity(
                            id = obj.getString("id"),
                            userId = userId,
                            title = obj.getString("title"),
                            amountPaise = obj.getLong("amountPaise"),
                            category = obj.getString("category"),
                            interval = com.example.velora.data.entity.RecurrenceInterval.valueOf(obj.getString("interval")),
                            accountId = obj.getString("accountId"),
                            nextDueDate = obj.getLong("nextDueDate"),
                            lastProcessedDate = if (obj.has("lastProcessedDate") && !obj.isNull("lastProcessedDate")) obj.getLong("lastProcessedDate") else null,
                            isActive = obj.optBoolean("isActive", true)
                        )
                        database.recurringDao().insertRecurring(rc)
                    }
                }
            }
        }
    }

    /**
     * Uploads backup to Google Drive REST API using provided OAuth Bearer token.
     * Uses multipart upload with private drive.file metadata.
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
            val filesArray = respJson.optJSONArray("files") ?: org.json.JSONArray()
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

    private inline fun <reified T> serializeList(list: List<T>): org.json.JSONArray {
        val adapter = moshi.adapter(Array<T>::class.java)
        val jsonStr = adapter.toJson(list.toTypedArray())
        return org.json.JSONArray(jsonStr)
    }

    private fun calculateSha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

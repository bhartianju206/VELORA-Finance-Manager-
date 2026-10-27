package com.example.velora.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.velora.data.database.VeloraDatabase
import com.example.velora.data.entity.AccountType
import com.example.velora.data.entity.TransactionEntity
import com.example.velora.data.entity.TransactionType
import com.example.velora.util.CurrencyHelper
import com.example.velora.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CsvPreviewItem(
    val dateStr: String,
    val type: String,
    val category: String,
    val amountFormatted: String,
    val note: String,
    val isDuplicate: Boolean
)

data class CsvImportPreview(
    val totalRecords: Int,
    val newRecords: Int,
    val duplicateRecords: Int,
    val items: List<CsvPreviewItem>,
    val parsedTransactions: List<TransactionEntity>
)

class CsvManager(
    private val context: Context,
    private val database: VeloraDatabase
) {

    private val exportDir = File(context.filesDir, "velora_csv_exports").apply { mkdirs() }

    /**
     * Exports active transactions for user into a CSV file.
     */
    suspend fun exportTransactionsToCsv(userId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val transactions = database.transactionDao().getAllTransactionsSync(userId)
                .filter { !it.isDeleted }
            val accountsMap = database.accountDao().getAccountsSync(userId)
                .associateBy { it.id }

            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "velora_transactions_$timestampStr.csv")

            file.bufferedWriter().use { writer ->
                // CSV Header
                writer.write("TransactionID,Date,Type,Category,AmountRupees,AmountPaise,AccountName,Note,IsReconciled\n")
                for (tx in transactions) {
                    val accName = accountsMap[tx.accountId]?.name ?: "Unknown"
                    val dateFormatted = DateUtils.formatDateTime(tx.timestamp)
                    val rupees = tx.amountPaise / 100.0
                    val cleanNote = tx.note.replace("\"", "\"\"").replace("\n", " ")

                    writer.write(
                        "\"${tx.id}\",\"$dateFormatted\",\"${tx.type.name}\",\"${tx.category}\",${"%.2f".format(Locale.US, rupees)},${tx.amountPaise},\"$accName\",\"$cleanNote\",${tx.isReconciled}\n"
                    )
                }
            }
            file
        }
    }

    /**
     * Parses and generates a safe preview of a CSV string before import.
     */
    suspend fun previewCsv(userId: String, csvContent: String): Result<CsvImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val lines = csvContent.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) error("CSV file is empty")

            val header = lines.first().lowercase()
            if (!header.contains("type") || !header.contains("amount")) {
                error("Invalid CSV structure: Required columns 'Type' and 'Amount' not found.")
            }

            val existingTx = database.transactionDao().getAllTransactionsSync(userId)
            val existingIds = existingTx.map { it.id }.toSet()
            val accounts = database.accountDao().getAccountsSync(userId)
            val defaultAccountId = accounts.firstOrNull()?.id ?: run {
                val newAcc = database.accountDao().insertAccount(
                    com.example.velora.data.entity.AccountEntity(
                        userId = userId,
                        name = "Imported Account",
                        type = AccountType.CUSTOM,
                        openingBalancePaise = 0L,
                        currentBalancePaise = 0L
                    )
                )
                database.accountDao().getAccountsSync(userId).first().id
            }

            val items = mutableListOf<CsvPreviewItem>()
            val parsedList = mutableListOf<TransactionEntity>()
            var dupCount = 0

            for (i in 1 until lines.size) {
                val line = lines[i]
                val tokens = parseCsvLine(line)
                if (tokens.size < 4) continue

                // Check if has TransactionID in column 0
                val idCandidate = tokens.getOrNull(0)?.trim() ?: UUID.randomUUID().toString()
                val id = if (idCandidate.length > 20 && !idCandidate.contains(" ")) idCandidate else UUID.randomUUID().toString()
                val isDup = existingIds.contains(id)
                if (isDup) dupCount++

                val typeStr = tokens.getOrNull(2) ?: "EXPENSE"
                val type = runCatching { TransactionType.valueOf(typeStr.uppercase()) }.getOrDefault(TransactionType.EXPENSE)
                val category = tokens.getOrNull(3)?.trim() ?: "General"

                val amountPaise = tokens.getOrNull(5)?.toLongOrNull()
                    ?: run {
                        val rupeesStr = tokens.getOrNull(4) ?: "0"
                        CurrencyHelper.parseToPaise(rupeesStr) ?: 0L
                    }

                val note = tokens.getOrNull(7) ?: ""
                val dateStr = tokens.getOrNull(1) ?: DateUtils.formatDateTime(System.currentTimeMillis())

                items.add(
                    CsvPreviewItem(
                        dateStr = dateStr,
                        type = type.name,
                        category = category,
                        amountFormatted = CurrencyHelper.formatPaise(amountPaise),
                        note = note,
                        isDuplicate = isDup
                    )
                )

                if (!isDup) {
                    parsedList.add(
                        TransactionEntity(
                            id = id,
                            userId = userId,
                            type = type,
                            amountPaise = amountPaise,
                            accountId = defaultAccountId,
                            category = category,
                            note = note,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            CsvImportPreview(
                totalRecords = items.size,
                newRecords = parsedList.size,
                duplicateRecords = dupCount,
                items = items,
                parsedTransactions = parsedList
            )
        }
    }

    /**
     * Confirms and imports transactions with balance adjustments.
     */
    suspend fun executeImport(userId: String, transactionsToImport: List<TransactionEntity>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            database.withTransaction {
                for (tx in transactionsToImport) {
                    val account = database.accountDao().getAccountById(tx.accountId)
                    if (account != null) {
                        val newBal = when (tx.type) {
                            TransactionType.INCOME, TransactionType.LEND_REPAYMENT, TransactionType.BORROW ->
                                account.currentBalancePaise + tx.amountPaise
                            TransactionType.EXPENSE, TransactionType.LEND, TransactionType.BORROW_REPAYMENT ->
                                account.currentBalancePaise - tx.amountPaise
                            else -> account.currentBalancePaise
                        }
                        database.accountDao().updateBalance(tx.accountId, newBal)
                    }
                    database.transactionDao().insertTransaction(tx)
                }
                transactionsToImport.size
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}

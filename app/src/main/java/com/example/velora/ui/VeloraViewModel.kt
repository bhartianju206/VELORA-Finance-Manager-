package com.example.velora.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.velora.backup.BackupMetadata
import com.example.velora.backup.BackupValidationResult
import com.example.velora.backup.CsvImportPreview
import com.example.velora.backup.CsvManager
import com.example.velora.backup.DriveBackupManager
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
import com.example.velora.data.repository.AuthRepository
import com.example.velora.data.repository.AuthState
import com.example.velora.data.repository.VeloraRepository
import com.example.velora.data.repository.VeloraUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TransactionFilter {
    ALL, INCOME, EXPENSE, TRANSFER, UDHAAR
}

enum class SortOrder {
    NEWEST, OLDEST, HIGHEST_AMOUNT, LOWEST_AMOUNT
}

class VeloraViewModel(application: Application) : AndroidViewModel(application) {

    val database = VeloraDatabase.getInstance(application)
    val repository = VeloraRepository(database)
    val authRepository = AuthRepository(application)
    val driveBackupManager = DriveBackupManager(application, database)
    val csvManager = CsvManager(application, database)

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Unauthenticated)

    val isBalanceHidden: StateFlow<Boolean> = authRepository.hideBalancePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(TransactionFilter.ALL)
    val selectedFilter: StateFlow<TransactionFilter> = _selectedFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    // Google Drive state
    private val _driveAccessToken = MutableStateFlow<String?>(null)
    val driveAccessToken: StateFlow<String?> = _driveAccessToken.asStateFlow()

    private val _isBackupLoading = MutableStateFlow(false)
    val isBackupLoading: StateFlow<Boolean> = _isBackupLoading.asStateFlow()

    private val _localBackups = MutableStateFlow<List<BackupMetadata>>(emptyList())
    val localBackups: StateFlow<List<BackupMetadata>> = _localBackups.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<Long?>(null)
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    private val _legacyDemoDismissed = MutableStateFlow(false)
    val legacyDemoDismissed: StateFlow<Boolean> = _legacyDemoDismissed.asStateFlow()

    init {
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Authenticated) {
                    repository.ensureDefaultAccounts(state.user.id)
                    loadBackups()
                } else {
                    _isAppUnlocked.value = false
                }
            }
        }
    }

    fun unlockApp() {
        _isAppUnlocked.value = true
    }

    fun toggleHideBalance() {
        viewModelScope.launch {
            authRepository.setHideBalancePreference(!isBalanceHidden.value)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: TransactionFilter) {
        _selectedFilter.value = filter
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    // Accounts
    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            repository.getAccounts(state.user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All active transactions
    @OptIn(ExperimentalCoroutinesApi::class)
    val allActiveTransactions: StateFlow<List<TransactionEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            repository.getActiveTransactions(state.user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered transactions for History screen
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allActiveTransactions,
        searchQuery,
        selectedFilter,
        sortOrder
    ) { txList, query, filter, sort ->
        val q = query.trim().lowercase()
        var result = txList.filter { tx ->
            val matchQuery = if (q.isEmpty()) true else {
                tx.category.lowercase().contains(q) ||
                        tx.note.lowercase().contains(q) ||
                        tx.amountPaise.toString().contains(q)
            }
            val matchFilter = when (filter) {
                TransactionFilter.ALL -> true
                TransactionFilter.INCOME -> tx.type == TransactionType.INCOME
                TransactionFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
                TransactionFilter.TRANSFER -> tx.type == TransactionType.TRANSFER
                TransactionFilter.UDHAAR -> tx.type in listOf(
                    TransactionType.LEND,
                    TransactionType.BORROW,
                    TransactionType.LEND_REPAYMENT,
                    TransactionType.BORROW_REPAYMENT
                )
            }
            matchQuery && matchFilter
        }

        result = when (sort) {
            SortOrder.NEWEST -> result.sortedByDescending { it.timestamp }
            SortOrder.OLDEST -> result.sortedBy { it.timestamp }
            SortOrder.HIGHEST_AMOUNT -> result.sortedByDescending { it.amountPaise }
            SortOrder.LOWEST_AMOUNT -> result.sortedBy { it.amountPaise }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Trash transactions
    @OptIn(ExperimentalCoroutinesApi::class)
    val trashTransactions: StateFlow<List<TransactionEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            repository.getTrashTransactions(state.user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Udhaar
    @OptIn(ExperimentalCoroutinesApi::class)
    val udhaarList: StateFlow<List<UdhaarEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            repository.getAllUdhaar(state.user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Budgets
    val currentMonthYear: String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentBudgets: StateFlow<List<BudgetEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            repository.getBudgetsForMonth(state.user.id, currentMonthYear)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reminders
    @OptIn(ExperimentalCoroutinesApi::class)
    val reminders: StateFlow<List<ReminderEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            repository.getReminders(state.user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recurring
    @OptIn(ExperimentalCoroutinesApi::class)
    val recurringPayments: StateFlow<List<RecurringPaymentEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            repository.getRecurringPayments(state.user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Actions
    fun deposit(accountId: String, amountPaise: Long, category: String, note: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.deposit(user.id, accountId, amountPaise, category, note)
                .onSuccess { _userMessage.emit("Deposited successfully") }
                .onFailure { _userMessage.emit("Failed: ${it.localizedMessage}") }
        }
    }

    fun withdraw(accountId: String, amountPaise: Long, category: String, note: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.withdraw(user.id, accountId, amountPaise, category, note)
                .onSuccess { _userMessage.emit("Expense recorded") }
                .onFailure { _userMessage.emit("Failed: ${it.localizedMessage}") }
        }
    }

    fun transfer(fromAccountId: String, toAccountId: String, amountPaise: Long, note: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.transfer(user.id, fromAccountId, toAccountId, amountPaise, note)
                .onSuccess { _userMessage.emit("Transferred successfully") }
                .onFailure { _userMessage.emit("Transfer failed: ${it.localizedMessage}") }
        }
    }

    fun adjustBalance(accountId: String, newBalancePaise: Long, reason: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.adjustBalance(user.id, accountId, newBalancePaise, reason)
                .onSuccess { _userMessage.emit("Account reconciled") }
                .onFailure { _userMessage.emit("Failed: ${it.localizedMessage}") }
        }
    }

    fun addAccount(name: String, type: AccountType, openingBalancePaise: Long, colorHex: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.createAccount(user.id, name, type, openingBalancePaise, colorHex)
            _userMessage.emit("Account created")
        }
    }

    fun editAccount(accountId: String, name: String, type: AccountType, openingBalancePaise: Long) {
        viewModelScope.launch {
            repository.updateAccountDetails(accountId, name, type, openingBalancePaise)
                .onSuccess { _userMessage.emit("Account updated successfully") }
                .onFailure { _userMessage.emit("Failed to update: ${it.localizedMessage}") }
        }
    }

    fun dismissLegacyDemoWarning() {
        _legacyDemoDismissed.value = true
    }

    fun resetLegacyDemoBalances() {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.resetLegacyDemoBalances(user.id)
                .onSuccess {
                    _userMessage.emit("Sample balances reset to ₹0")
                    _legacyDemoDismissed.value = true
                }
                .onFailure { _userMessage.emit("Error: ${it.localizedMessage}") }
        }
    }

    // Udhaar
    fun lendMoney(personName: String, amountPaise: Long, fromAccountId: String, dueDate: Long?, notes: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.recordLend(user.id, personName, amountPaise, fromAccountId, dueDate, notes)
                .onSuccess { _userMessage.emit("Udhaar recorded: Lent to $personName") }
                .onFailure { _userMessage.emit("Error: ${it.localizedMessage}") }
        }
    }

    fun borrowMoney(personName: String, amountPaise: Long, toAccountId: String, dueDate: Long?, notes: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.recordBorrow(user.id, personName, amountPaise, toAccountId, dueDate, notes)
                .onSuccess { _userMessage.emit("Udhaar recorded: Borrowed from $personName") }
                .onFailure { _userMessage.emit("Error: ${it.localizedMessage}") }
        }
    }

    fun recordUdhaarRepayment(udhaarId: String, amountPaise: Long, accountId: String, note: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.recordUdhaarRepayment(user.id, udhaarId, amountPaise, accountId, note)
                .onSuccess { _userMessage.emit("Repayment recorded successfully") }
                .onFailure { _userMessage.emit("Failed: ${it.localizedMessage}") }
        }
    }

    // Trash & Permanent Deletion
    fun moveToTrash(transactionId: String) {
        viewModelScope.launch {
            repository.moveToTrash(transactionId)
                .onSuccess { _userMessage.emit("Moved to Trash") }
                .onFailure { _userMessage.emit("Failed: ${it.localizedMessage}") }
        }
    }

    fun restoreFromTrash(transactionId: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(transactionId)
                .onSuccess { _userMessage.emit("Transaction restored") }
                .onFailure { _userMessage.emit("Failed: ${it.localizedMessage}") }
        }
    }

    fun permanentlyDeleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.permanentlyDeleteTransaction(transactionId)
            _userMessage.emit("Permanently deleted")
        }
    }

    fun emptyTrash() {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.emptyTrash(user.id)
            _userMessage.emit("Trash emptied")
        }
    }

    // Budgets
    fun setBudget(category: String, limitPaise: Long) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.setBudget(user.id, category, currentMonthYear, limitPaise)
            _userMessage.emit("Budget updated")
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            repository.deleteBudget(budgetId)
            _userMessage.emit("Budget removed")
        }
    }

    // Reminders
    fun addReminder(title: String, amountPaise: Long, category: String, dueDate: Long, type: com.example.velora.data.entity.ReminderType) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.addReminder(
                ReminderEntity(
                    userId = user.id,
                    title = title,
                    amountPaise = amountPaise,
                    category = category,
                    dueDate = dueDate,
                    type = type
                )
            )
            _userMessage.emit("Reminder scheduled")
        }
    }

    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            repository.deleteReminder(id)
            _userMessage.emit("Reminder deleted")
        }
    }

    // Recurring
    fun addRecurring(title: String, amountPaise: Long, category: String, interval: com.example.velora.data.entity.RecurrenceInterval, accountId: String, nextDueDate: Long) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.addRecurring(
                RecurringPaymentEntity(
                    userId = user.id,
                    title = title,
                    amountPaise = amountPaise,
                    category = category,
                    interval = interval,
                    accountId = accountId,
                    nextDueDate = nextDueDate
                )
            )
            _userMessage.emit("Recurring payment added")
        }
    }

    fun deleteRecurring(id: String) {
        viewModelScope.launch {
            repository.deleteRecurring(id)
            _userMessage.emit("Recurring payment removed")
        }
    }

    fun processRecurring(recurringId: String) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            repository.processRecurringPayment(user.id, recurringId)
                .onSuccess { _userMessage.emit("Payment confirmed & recorded") }
                .onFailure { _userMessage.emit("Failed: ${it.localizedMessage}") }
        }
    }

    // Backups
    fun loadBackups() {
        viewModelScope.launch {
            _localBackups.value = driveBackupManager.listLocalBackups()
            _lastBackupTime.value = _localBackups.value.firstOrNull()?.createdAt
        }
    }

    fun performBackup(cloudToken: String? = null) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _isBackupLoading.value = true
            try {
                if (cloudToken != null) {
                    driveBackupManager.uploadToGoogleDrive(cloudToken, user.id, user.email)
                        .onSuccess {
                            _lastBackupTime.value = it.createdAt
                            _userMessage.emit("Google Drive backup completed successfully!")
                        }
                        .onFailure {
                            _userMessage.emit("Drive upload failed: ${it.localizedMessage}")
                        }
                } else {
                    driveBackupManager.performLocalBackup(user.id, user.email)
                        .onSuccess {
                            _lastBackupTime.value = it.createdAt
                            _userMessage.emit("Encrypted backup saved locally!")
                            loadBackups()
                        }
                        .onFailure {
                            _userMessage.emit("Backup failed: ${it.localizedMessage}")
                        }
                }
            } finally {
                _isBackupLoading.value = false
            }
        }
    }

    fun exportAllDataToUri(uri: Uri, onComplete: ((Boolean, String) -> Unit)? = null) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _isBackupLoading.value = true
            try {
                driveBackupManager.exportToUri(uri, user.id, user.email)
                    .onSuccess {
                        _lastBackupTime.value = System.currentTimeMillis()
                        _userMessage.emit("Financial backup exported successfully!")
                        loadBackups()
                        onComplete?.invoke(true, "Backup exported successfully")
                    }
                    .onFailure { err ->
                        _userMessage.emit("Export failed: ${err.localizedMessage}")
                        onComplete?.invoke(false, err.localizedMessage ?: "Export failed")
                    }
            } finally {
                _isBackupLoading.value = false
            }
        }
    }

    fun inspectBackupFromUri(uri: Uri, onResult: (BackupValidationResult, String) -> Unit) {
        viewModelScope.launch {
            _isBackupLoading.value = true
            try {
                driveBackupManager.readFromUri(uri)
                    .onSuccess { content ->
                        val validation = driveBackupManager.validateBackupContent(content)
                        onResult(validation, content)
                    }
                    .onFailure { err ->
                        val failedValidation = BackupValidationResult(
                            isValid = false,
                            schemaVersion = 0,
                            createdAt = 0,
                            accountsCount = 0,
                            transactionsCount = 0,
                            udhaarCount = 0,
                            errorMessage = err.localizedMessage ?: "Failed to read file"
                        )
                        onResult(failedValidation, "")
                    }
            } finally {
                _isBackupLoading.value = false
            }
        }
    }

    fun restoreBackupContent(content: String, mode: String = "REPLACE", onComplete: (Boolean, String) -> Unit) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _isBackupLoading.value = true
            try {
                driveBackupManager.createSafetySnapshot(user.id, user.email)
                driveBackupManager.restorePayload(user.id, content, mode)
                    .onSuccess { res ->
                        val msg = if (mode.contains("MERGE", ignoreCase = true)) {
                            "Data merged: ${res.accountsCount} accounts, ${res.transactionsCount} txs"
                        } else {
                            "All data restored: ${res.accountsCount} accounts, ${res.transactionsCount} txs"
                        }
                        _userMessage.emit(msg)
                        loadBackups()
                        onComplete(true, msg)
                    }
                    .onFailure {
                        _userMessage.emit("Restore failed: ${it.localizedMessage}")
                        onComplete(false, it.localizedMessage ?: "Restore failed")
                    }
            } catch (e: Exception) {
                _userMessage.emit("Restore error: ${e.localizedMessage}")
                onComplete(false, e.localizedMessage ?: "Unknown error")
            } finally {
                _isBackupLoading.value = false
            }
        }
    }

    fun restoreBackupFile(file: File, mode: String = "REPLACE", onComplete: (Boolean, String) -> Unit) {
        try {
            val content = file.readText()
            restoreBackupContent(content, mode, onComplete)
        } catch (e: Exception) {
            onComplete(false, e.localizedMessage ?: "Failed to read local file")
        }
    }

    fun setDriveAccessToken(token: String?) {
        _driveAccessToken.value = token
    }
}

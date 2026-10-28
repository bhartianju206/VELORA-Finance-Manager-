package com.example.velora.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.velora.data.database.VeloraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

private val Context.backupDataStore by preferencesDataStore(name = "velora_autobackup_prefs")

enum class BackupStatus {
    UP_TO_DATE,
    PENDING,
    FAILED
}

class AutoBackupManager(
    private val context: Context,
    private val database: VeloraDatabase,
    private val driveBackupManager: DriveBackupManager
) {

    companion object {
        const val AUTO_BACKUP_FILE_NAME = "Velora_AutoBackup.json"
        const val AUTO_BACKUP_TMP_NAME = "Velora_AutoBackup.json.tmp"

        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("key_auto_backup_enabled")
        private val KEY_BACKUP_PENDING = booleanPreferencesKey("key_backup_pending")
        private val KEY_LAST_BACKUP_TIME = longPreferencesKey("key_last_backup_time")
        private val KEY_EXTERNAL_BACKUP_URI = stringPreferencesKey("key_external_backup_uri")
        private val KEY_LAST_ERROR = stringPreferencesKey("key_last_backup_error")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupMutex = Mutex()
    private val triggerChannel = Channel<Pair<String, String>>(Channel.CONFLATED)

    val autoBackupFile: File get() = File(context.filesDir, AUTO_BACKUP_FILE_NAME)
    val tmpBackupFile: File get() = File(context.filesDir, AUTO_BACKUP_TMP_NAME)

    private val _backupStatus = MutableStateFlow(BackupStatus.UP_TO_DATE)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<Long?>(null)
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    private val _isAutoBackupEnabled = MutableStateFlow(true)
    val isAutoBackupEnabled: StateFlow<Boolean> = _isAutoBackupEnabled.asStateFlow()

    private val _externalBackupUri = MutableStateFlow<String?>(null)
    val externalBackupUri: StateFlow<String?> = _externalBackupUri.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        // Load initial preferences from DataStore
        scope.launch {
            try {
                val prefs = context.backupDataStore.data.first()
                _isAutoBackupEnabled.value = prefs[KEY_AUTO_BACKUP_ENABLED] ?: true
                val lastTime = prefs[KEY_LAST_BACKUP_TIME] ?: 0L
                if (lastTime > 0L) {
                    _lastBackupTime.value = lastTime
                } else if (autoBackupFile.exists()) {
                    _lastBackupTime.value = autoBackupFile.lastModified()
                }
                _externalBackupUri.value = prefs[KEY_EXTERNAL_BACKUP_URI]?.takeIf { it.isNotBlank() }
                val isPending = prefs[KEY_BACKUP_PENDING] ?: false
                val lastErr = prefs[KEY_LAST_ERROR]
                if (!lastErr.isNullOrBlank()) {
                    _lastErrorMessage.value = lastErr
                    _backupStatus.value = BackupStatus.FAILED
                } else if (isPending) {
                    _backupStatus.value = BackupStatus.PENDING
                } else {
                    _backupStatus.value = BackupStatus.UP_TO_DATE
                }
            } catch (_: Exception) {}
        }

        // Background debounced backup processing loop
        scope.launch {
            for (request in triggerChannel) {
                // Debounce rapid consecutive mutations by 350ms
                delay(350)
                // Drain any additional triggers that arrived during debounce delay
                var latest = request
                while (true) {
                    val next = triggerChannel.tryReceive().getOrNull() ?: break
                    latest = next
                }
                executeBackup(latest.first, latest.second)
            }
        }
    }

    /**
     * Called after every successful financial database change has committed.
     * Centralized trigger point that debounces rapid changes and avoids race conditions.
     */
    fun onDatabaseChanged(userId: String, userEmail: String) {
        if (!_isAutoBackupEnabled.value) return
        _backupStatus.value = BackupStatus.PENDING

        scope.launch {
            try {
                context.backupDataStore.edit { prefs ->
                    prefs[KEY_BACKUP_PENDING] = true
                }
            } catch (_: Exception) {}
            triggerChannel.trySend(Pair(userId, userEmail))
        }
    }

    /**
     * Immediately checks if a backup request was left pending from a previous process death,
     * or if the initial Velora_AutoBackup.json is missing, and runs it reliably.
     */
    fun checkAndRetryPendingBackup(userId: String, userEmail: String) {
        scope.launch {
            try {
                val prefs = context.backupDataStore.data.first()
                val isPending = prefs[KEY_BACKUP_PENDING] ?: false
                if (isPending || !autoBackupFile.exists()) {
                    _backupStatus.value = BackupStatus.PENDING
                    triggerChannel.trySend(Pair(userId, userEmail))
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Executes backup snapshot synchronously under a mutex, with atomic file replacement.
     */
    suspend fun executeBackup(userId: String, userEmail: String): Result<File> = backupMutex.withLock {
        _isProcessing.value = true
        _backupStatus.value = BackupStatus.PENDING

        runCatching {
            // 1. Generate snapshot from Room database (Room is primary source of truth)
            val payload = driveBackupManager.createBackupPayload(userId, userEmail)

            // 2. Safe atomic replacement step A: Write to temporary file
            tmpBackupFile.writeText(payload, Charsets.UTF_8)

            // 3. Safe atomic replacement step B: Validate new snapshot integrity
            val validation = driveBackupManager.validateBackupContent(payload)
            if (!validation.isValid) {
                tmpBackupFile.delete()
                error(validation.errorMessage ?: "Validation of generated backup failed")
            }

            // 4. Safe atomic replacement step C: Replace previous valid backup file atomically
            if (autoBackupFile.exists()) {
                if (!tmpBackupFile.renameTo(autoBackupFile)) {
                    // Fallback for filesystem edge cases
                    autoBackupFile.delete()
                    if (!tmpBackupFile.renameTo(autoBackupFile)) {
                        autoBackupFile.writeText(payload, Charsets.UTF_8)
                        tmpBackupFile.delete()
                    }
                }
            } else {
                if (!tmpBackupFile.renameTo(autoBackupFile)) {
                    autoBackupFile.writeText(payload, Charsets.UTF_8)
                    tmpBackupFile.delete()
                }
            }

            // 5. Update user-selected external backup document if configured
            val extUriStr = _externalBackupUri.value
            if (!extUriStr.isNullOrBlank()) {
                updateExternalDocument(Uri.parse(extUriStr), payload)
            }

            // 6. Update success state
            val now = System.currentTimeMillis()
            _lastBackupTime.value = now
            _backupStatus.value = BackupStatus.UP_TO_DATE
            _lastErrorMessage.value = null

            context.backupDataStore.edit { prefs ->
                prefs[KEY_BACKUP_PENDING] = false
                prefs[KEY_LAST_BACKUP_TIME] = now
                prefs.remove(KEY_LAST_ERROR)
            }

            autoBackupFile
        }.onFailure { ex ->
            _backupStatus.value = BackupStatus.FAILED
            _lastErrorMessage.value = ex.localizedMessage ?: "Backup failed"
            runCatching {
                context.backupDataStore.edit { prefs ->
                    prefs[KEY_BACKUP_PENDING] = true
                    prefs[KEY_LAST_ERROR] = ex.localizedMessage ?: "Backup failed"
                }
            }
        }.also {
            _isProcessing.value = false
        }
    }

    /**
     * Safely updates the user-selected external document using Storage Access Framework.
     * Handles revoked permissions and unavailable providers gracefully without crashing
     * or corrupting the private local auto-backup.
     */
    private fun updateExternalDocument(uri: Uri, payload: String) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outStream ->
                outStream.write(payload.toByteArray(Charsets.UTF_8))
                outStream.flush()
            } ?: throw IOException("Document provider refused output stream")
        } catch (e: SecurityException) {
            _lastErrorMessage.value = "External backup permission revoked. Please re-select backup location."
        } catch (e: Exception) {
            _lastErrorMessage.value = "External backup write failed: ${e.localizedMessage}"
        }
    }

    /**
     * Toggles automatic backup on or off.
     */
    fun setAutoBackupEnabled(enabled: Boolean, userId: String? = null, userEmail: String? = null) {
        _isAutoBackupEnabled.value = enabled
        scope.launch {
            try {
                context.backupDataStore.edit { prefs ->
                    prefs[KEY_AUTO_BACKUP_ENABLED] = enabled
                }
            } catch (_: Exception) {}
            if (enabled && userId != null && userEmail != null) {
                onDatabaseChanged(userId, userEmail)
            }
        }
    }

    /**
     * Configures the external document URI chosen by user via Storage Access Framework.
     */
    fun setExternalBackupUri(uriString: String?, userId: String? = null, userEmail: String? = null) {
        _externalBackupUri.value = uriString
        scope.launch {
            try {
                context.backupDataStore.edit { prefs ->
                    if (uriString != null) {
                        prefs[KEY_EXTERNAL_BACKUP_URI] = uriString
                    } else {
                        prefs.remove(KEY_EXTERNAL_BACKUP_URI)
                    }
                }
            } catch (_: Exception) {}
            if (uriString != null && userId != null && userEmail != null) {
                onDatabaseChanged(userId, userEmail)
            }
        }
    }

    /**
     * Reads the current contents of the automatic backup file.
     */
    fun readCurrentAutoBackup(): String? {
        return if (autoBackupFile.exists()) {
            runCatching { autoBackupFile.readText(Charsets.UTF_8) }.getOrNull()
        } else {
            null
        }
    }
}

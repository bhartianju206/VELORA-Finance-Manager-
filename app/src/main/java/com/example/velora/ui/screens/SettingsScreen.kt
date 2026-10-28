package com.example.velora.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.velora.backup.BackupValidationResult
import com.example.velora.data.repository.AuthState
import com.example.velora.ui.VeloraViewModel
import com.example.velora.ui.theme.AmoledBackground
import com.example.velora.ui.theme.AmoledBorder
import com.example.velora.ui.theme.AmoledCard
import com.example.velora.ui.theme.AmoledSurface
import com.example.velora.ui.theme.EmeraldDark
import com.example.velora.ui.theme.EmeraldMint
import com.example.velora.ui.theme.EmeraldPrimary
import com.example.velora.ui.theme.ExpenseCoral
import com.example.velora.ui.theme.TextMuted
import com.example.velora.ui.theme.TextPrimary
import com.example.velora.ui.theme.TextSecondary
import com.example.velora.ui.theme.WarningGold
import com.example.velora.util.CurrencyHelper
import com.example.velora.util.DateUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: VeloraViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isHidden by viewModel.isBalanceHidden.collectAsStateWithLifecycle()
    val isAppLockEnabled by viewModel.authRepository.isAppLockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val isBackupLoading by viewModel.isBackupLoading.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSetupGuideDialog by remember { mutableStateOf(false) }

    var backupToRestorePreview by remember { mutableStateOf<Pair<String, BackupValidationResult>?>(null) }
    var restoreMode by remember { mutableStateOf("REPLACE") }

    val user = (authState as? AuthState.Authenticated)?.user

    // Storage Access Framework Launchers
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportAllDataToUri(uri)
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.inspectBackupFromUri(uri) { validation, content ->
                backupToRestorePreview = Pair(content, validation)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Settings & Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Account isolation, PIN lock & data portability", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }

        // Profile Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(EmeraldPrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user?.displayName ?: "User", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text(user?.email ?: "", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (user?.isGoogleUser == true) "Google Account" else "Email/Password Account",
                            fontSize = 11.sp,
                            color = EmeraldMint,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Privacy & Preferences
        item {
            Text("SECURITY & PREFERENCES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Hide Balances Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide Balances by Default", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                            Text("Mask financial numbers for privacy in public", fontSize = 12.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isHidden,
                            onCheckedChange = { viewModel.toggleHideBalance() },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary, checkedTrackColor = EmeraldDark)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // App Lock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App PIN Lock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                            Text("Require 4-digit security PIN on app launch", fontSize = 12.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isAppLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPinDialog = true
                                } else {
                                    scope.launch { viewModel.authRepository.setAppLock(false) }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary, checkedTrackColor = EmeraldDark)
                        )
                    }
                }
            }
        }

        // Data Backup & Portability Section
        item {
            Text("DATA BACKUP & PORTABILITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth().testTag("data_portability_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Export or Restore All Data", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Export a single portable velora_backup.json file containing all your transactions, accounts, Udhaar loans and budgets, or restore it on any device.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                exportJsonLauncher.launch("velora_backup_$dateStr.json")
                            },
                            modifier = Modifier.weight(1f).testTag("settings_export_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            enabled = !isBackupLoading
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF003822))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export All", color = Color(0xFF003822), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                importJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            modifier = Modifier.weight(1f).testTag("settings_import_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldMint),
                            enabled = !isBackupLoading
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldMint)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import & Restore", color = EmeraldMint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Cloud & External Setup Guide
        item {
            Text("EXTERNAL CLOUD INTEGRATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Cloud Setup & Credentials Guide", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Step-by-step instructions for adding google-services.json, Firebase Authentication, and Google Cloud Drive OAuth client.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showSetupGuideDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Cloud Setup Instructions")
                    }
                }
            }
        }

        // Account Actions
        item {
            Text("ACCOUNT ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            scope.launch { viewModel.authRepository.logout() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmoledBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out from Velora", color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ExpenseCoral, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Account & Financial Data", color = ExpenseCoral, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Set PIN Dialog
    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showPinDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Set Security PIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                        label = { Text("4-digit PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                        label = { Text("Confirm 4-digit PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(error!!, color = ExpenseCoral, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { showPinDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (pin.length != 4) {
                                    error = "PIN must be exactly 4 digits"
                                    return@Button
                                }
                                if (pin != confirmPin) {
                                    error = "PINs do not match"
                                    return@Button
                                }
                                scope.launch {
                                    viewModel.authRepository.setAppLock(true, pin)
                                    showPinDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Save PIN", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Wipe All Financial Data?", fontWeight = FontWeight.Bold, color = ExpenseCoral) },
            text = {
                Text(
                    "This will permanently delete all your accounts, transactions, Udhaar records, and budgets from this device.\n\n" +
                            "This action cannot be undone unless you have a saved velora_backup.json export.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        user?.let { u ->
                            scope.launch {
                                viewModel.repository.deleteUserAllData(u.id)
                                viewModel.authRepository.logout()
                                showDeleteConfirmDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral)
                ) {
                    Text("Delete Everything", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Setup Guide Dialog
    if (showSetupGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSetupGuideDialog = false },
            title = { Text("Cloud Setup Guide", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "1. Firebase Authentication:\n" +
                                "• Go to console.firebase.google.com\n" +
                                "• Create a new Firebase project (e.g. 'Velora-Finance')\n" +
                                "• Register Android app with package: com.aistudio.velora.kxmpzq\n" +
                                "• Download google-services.json into /app directory\n" +
                                "• Enable Email/Password in Authentication providers\n\n" +
                                "2. Google Sign-In & Google Drive:\n" +
                                "• Enable Google Provider in Firebase Auth\n" +
                                "• Enable Google Drive API in Google Cloud Console\n" +
                                "• Add OAuth Client ID with your SHA-1 key credential\n\n" +
                                "Note: The current app runs with complete local offline database persistence, integer paise accounting, and full JSON backup/restore.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSetupGuideDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Close", color = Color(0xFF003822))
                }
            }
        )
    }

    // Restore Preview & Confirmation Dialog
    if (backupToRestorePreview != null) {
        val (content, validation) = backupToRestorePreview!!

        Dialog(onDismissRequest = { backupToRestorePreview = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth().testTag("settings_restore_preview_dialog")
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Backup Preview & Validation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (!validation.isValid) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseCoral, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verification Failed", color = ExpenseCoral, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(validation.errorMessage ?: "Corrupted file.", color = TextSecondary, fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { backupToRestorePreview = null },
                            colors = ButtonDefaults.buttonColors(containerColor = AmoledBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close", color = TextPrimary)
                        }
                    } else {
                        DetailRow("Backup Date", DateUtils.formatDateTime(validation.createdAt))
                        DetailRow("Schema Version", "v${validation.schemaVersion}")
                        DetailRow("SHA-256 Checksum", "Verified & Untampered")
                        DetailRow("Accounts found", "${validation.accountsCount}")
                        DetailRow("Transactions found", "${validation.transactionsCount}")
                        DetailRow("Udhaar loans", "${validation.udhaarCount} (repayments: ${validation.paymentsCount})")
                        DetailRow("Budgets & Reminders", "${validation.budgetsCount} budgets, ${validation.remindersCount} reminders")
                        DetailRow("Liquid Balance", CurrencyHelper.formatPaise(validation.totalBalancePaise))

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Select Restoration Mode:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (restoreMode == "REPLACE") ExpenseCoral.copy(alpha = 0.15f) else AmoledCard
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { restoreMode = "REPLACE" }
                                    .border(
                                        width = if (restoreMode == "REPLACE") 1.5.dp else 0.dp,
                                        color = if (restoreMode == "REPLACE") ExpenseCoral else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .testTag("settings_mode_replace_btn")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Replace All Data", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (restoreMode == "REPLACE") ExpenseCoral else TextPrimary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Safe overwrite with rollback protection", fontSize = 10.sp, color = TextMuted)
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (restoreMode == "MERGE") EmeraldPrimary.copy(alpha = 0.15f) else AmoledCard
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { restoreMode = "MERGE" }
                                    .border(
                                        width = if (restoreMode == "MERGE") 1.5.dp else 0.dp,
                                        color = if (restoreMode == "MERGE") EmeraldPrimary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .testTag("settings_mode_merge_btn")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Merge Data", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (restoreMode == "MERGE") EmeraldMint else TextPrimary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Keep existing, skip duplicates", fontSize = 10.sp, color = TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (restoreMode == "REPLACE") {
                            Text(
                                "⚠️ Warning: Replacing all data safely clears current user records before importing this backup. An automated local safety snapshot is captured prior to restore for instant rollback.",
                                fontSize = 11.sp,
                                color = WarningGold,
                                lineHeight = 15.sp
                            )
                        } else {
                            Text(
                                "ℹ️ Merge preserves all existing records, skips transactions with matching IDs to prevent duplicates, and recomputes all account balances consistently.",
                                fontSize = 11.sp,
                                color = EmeraldMint,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = { backupToRestorePreview = null }) {
                                Text("Cancel", color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    viewModel.restoreBackupContent(content, restoreMode) { success, msg ->
                                        backupToRestorePreview = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (restoreMode == "REPLACE") ExpenseCoral else EmeraldPrimary
                                ),
                                modifier = Modifier.testTag("settings_confirm_restore_btn"),
                                enabled = !isBackupLoading
                            ) {
                                if (isBackupLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                } else {
                                    Text(
                                        if (restoreMode == "REPLACE") "Confirm & Replace" else "Confirm & Merge",
                                        color = if (restoreMode == "REPLACE") Color.White else Color(0xFF003822),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

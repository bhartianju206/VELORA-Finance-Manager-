package com.example.velora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import com.example.velora.backup.BackupMetadata
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
import com.example.velora.util.DateUtils
import java.io.File

@Composable
fun BackupSyncScreen(
    viewModel: VeloraViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val isBackupLoading by viewModel.isBackupLoading.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackupTime.collectAsStateWithLifecycle()
    val driveAccessToken by viewModel.driveAccessToken.collectAsStateWithLifecycle()

    var showDriveSetupDialog by remember { mutableStateOf(false) }
    var showTokenInputDialog by remember { mutableStateOf(false) }
    var backupToRestorePreview by remember { mutableStateOf<Pair<File, BackupValidationResult>?>(null) }
    var restoreMode by remember { mutableStateOf("REPLACE") }
    var autoBackupEnabled by remember { mutableStateOf(true) }

    val userEmail = (authState as? AuthState.Authenticated)?.user?.email ?: "User"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
            .testTag("backup_sync_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Backup & Restore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Private Google Drive & Offline Snapshots", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                IconButton(onClick = { showDriveSetupDialog = true }) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "Setup Guide", tint = EmeraldMint)
                }
            }
        }

        // Decorative Security & Cloud Artwork Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ill_cloud_backup),
                        contentDescription = "Cloud Backup Security Illustration",
                        modifier = Modifier.size(52.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Private Cloud & Offline Backups",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Encrypted snapshots stored directly into your own Google Drive and local storage with SHA-256 integrity.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Google Drive Status Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (driveAccessToken != null) EmeraldPrimary.copy(alpha = 0.2f) else AmoledBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (driveAccessToken != null) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = if (driveAccessToken != null) EmeraldPrimary else TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (driveAccessToken != null) "Google Drive Connected" else "Google Drive Not Connected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    if (driveAccessToken != null) userEmail else "Private backup scope: drive.file",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (driveAccessToken != null) {
                        OutlinedButton(
                            onClick = { viewModel.setDriveAccessToken(null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseCoral)
                        ) {
                            Text("Disconnect Google Drive")
                        }
                    } else {
                        Button(
                            onClick = { showTokenInputDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("connect_drive_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Google Drive", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Backup Action Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Last Successful Backup", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (lastBackup != null) DateUtils.formatDateTime(lastBackup!!) else "No backup yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lastBackup != null) EmeraldMint else TextMuted
                            )
                        }

                        Button(
                            onClick = { viewModel.performBackup(driveAccessToken) },
                            enabled = !isBackupLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.testTag("backup_now_button")
                        ) {
                            if (isBackupLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF003822))
                            } else {
                                Text("Backup Now", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Automatic Daily Backup", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Sync when connected to Wi-Fi / Network", fontSize = 11.sp, color = TextMuted)
                        }

                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { autoBackupEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldDark
                            )
                        )
                    }
                }
            }
        }

        // Available Backups List
        item {
            Text("AVAILABLE BACKUP VERSIONS (${localBackups.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        if (localBackups.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No saved backup files yet. Tap 'Backup Now' above.", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(localBackups) { b ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(b.fileName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                            Text(
                                "${DateUtils.formatDateTime(b.createdAt)} • ${b.sizeBytes / 1024} KB",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Text("${b.transactionCount} transactions, ${b.accountCount} accounts", fontSize = 11.sp, color = EmeraldMint)
                        }

                        Button(
                            onClick = {
                                val file = File(b.fileId)
                                if (file.exists()) {
                                    val content = file.readText()
                                    val validation = viewModel.driveBackupManager.validateBackupContent(content)
                                    backupToRestorePreview = Pair(file, validation)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmoledBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restore", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Google Drive Setup Guide Modal
    if (showDriveSetupDialog) {
        AlertDialog(
            onDismissRequest = { showDriveSetupDialog = false },
            title = { Text("Google Drive API Setup", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Velora uses the official Google Drive REST API with minimal scope (drive.file) to privately store your encrypted backups in your own Google Drive.\n\n" +
                                "To connect Google Drive in production:\n" +
                                "1. Go to Google Cloud Console\n" +
                                "2. Enable Google Drive API\n" +
                                "3. Configure OAuth Consent Screen\n" +
                                "4. Add Android OAuth Client with package name: com.aistudio.velora.kxmpzq\n" +
                                "5. Velora isolates backups so other apps cannot access them.\n\n" +
                                "Offline local snapshots are fully functional and store the identical JSON schema with SHA-256 checksum integrity verification.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDriveSetupDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Understood", color = Color(0xFF003822))
                }
            }
        )
    }

    // Token input modal for testing Drive REST token
    if (showTokenInputDialog) {
        var tokenInput by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showTokenInputDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Connect Google Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Enter an OAuth 2.0 Access Token or select Simulated Cloud Mode for local evaluation:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("OAuth Bearer Token (Optional)") },
                        placeholder = { Text("ya29.a0A...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AmoledBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { showTokenInputDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                val token = tokenInput.trim().ifEmpty { "SIMULATED_TEST_TOKEN_2026" }
                                viewModel.setDriveAccessToken(token)
                                showTokenInputDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Connect", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Restore Safety Preview & Confirmation Modal
    if (backupToRestorePreview != null) {
        val (file, validation) = backupToRestorePreview!!

        Dialog(onDismissRequest = { backupToRestorePreview = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Restore Safety Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!validation.isValid) {
                        Text("Integrity Check Failed: ${validation.errorMessage}", color = ExpenseCoral, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { backupToRestorePreview = null },
                            colors = ButtonDefaults.buttonColors(containerColor = AmoledBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close")
                        }
                    } else {
                        DetailRow("Backup Date", DateUtils.formatDateTime(validation.createdAt))
                        DetailRow("Schema Version", "v${validation.schemaVersion}")
                        DetailRow("Integrity Status", "Verified SHA-256 Checksum")
                        DetailRow("Accounts to restore", "${validation.accountsCount}")
                        DetailRow("Transactions to restore", "${validation.transactionsCount}")
                        DetailRow("Udhaar records", "${validation.udhaarCount}")

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Restoration Mode:", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { restoreMode = "REPLACE" },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (restoreMode == "REPLACE") ExpenseCoral.copy(alpha = 0.2f) else Color.Transparent
                                )
                            ) {
                                Text("Replace Data", fontSize = 11.sp, color = if (restoreMode == "REPLACE") ExpenseCoral else TextSecondary)
                            }
                            OutlinedButton(
                                onClick = { restoreMode = "MERGE" },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (restoreMode == "MERGE") EmeraldPrimary.copy(alpha = 0.2f) else Color.Transparent
                                )
                            ) {
                                Text("Merge Data", fontSize = 11.sp, color = if (restoreMode == "MERGE") EmeraldPrimary else TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "* A local safety snapshot will be automatically captured prior to restoration for full rollback protection.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = { backupToRestorePreview = null }) {
                                Text("Cancel", color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    viewModel.restoreBackupFile(file, restoreMode) { success, msg ->
                                        backupToRestorePreview = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                modifier = Modifier.testTag("confirm_restore_btn")
                            ) {
                                Text("Restore Now", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

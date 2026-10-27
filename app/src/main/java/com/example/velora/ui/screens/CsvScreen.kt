package com.example.velora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Text
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
import com.example.velora.backup.CsvImportPreview
import com.example.velora.data.repository.AuthState
import com.example.velora.ui.VeloraViewModel
import com.example.velora.ui.theme.AmoledBackground
import com.example.velora.ui.theme.AmoledBorder
import com.example.velora.ui.theme.AmoledCard
import com.example.velora.ui.theme.AmoledSurface
import com.example.velora.ui.theme.EmeraldMint
import com.example.velora.ui.theme.EmeraldPrimary
import com.example.velora.ui.theme.ExpenseCoral
import com.example.velora.ui.theme.TextMuted
import com.example.velora.ui.theme.TextPrimary
import com.example.velora.ui.theme.TextSecondary
import com.example.velora.ui.theme.WarningGold
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CsvScreen(
    viewModel: VeloraViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: ""

    var exportedFile by remember { mutableStateOf<File?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .testTag("csv_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("CSV Export & Import", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Standard spreadsheet exchange", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Export Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Export All Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Generates a complete RFC-compliant CSV containing Transaction IDs, timestamps, categories, amounts in INR and integer paise, accounts, and notes.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            isExporting = true
                            scope.launch {
                                viewModel.csvManager.exportTransactionsToCsv(currentUserId)
                                    .onSuccess {
                                        exportedFile = it
                                        statusMessage = "Exported to: ${it.name}"
                                    }
                                    .onFailure {
                                        statusMessage = "Export failed: ${it.localizedMessage}"
                                    }
                                isExporting = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("export_csv_btn")
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF003822))
                        } else {
                            Text("Export Transactions CSV", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (exportedFile != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldMint, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saved locally: ${exportedFile!!.name} (${exportedFile!!.length()} bytes)", fontSize = 12.sp, color = EmeraldMint)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Import Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Import CSV Spreadsheet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Safely import transactions with duplicate detection, preview before committing, and automatic local safety snapshots.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth().testTag("import_csv_btn")
                    ) {
                        Text("Paste or Select CSV Content", color = Color(0xFF082F49), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(statusMessage!!, fontSize = 13.sp, color = EmeraldMint, fontWeight = FontWeight.Medium)
            }
        }
    }

    // CSV Input & Preview Modal
    if (showImportDialog) {
        var rawCsv by remember { mutableStateOf(
            "TransactionID,Date,Type,Category,AmountRupees,AmountPaise,AccountName,Note,IsReconciled\n" +
                    "tx-sample-01,24 Oct 2026,EXPENSE,Groceries,450.00,45000,Cash in Hand,Supermarket,false\n" +
                    "tx-sample-02,25 Oct 2026,INCOME,Freelance,8500.00,850000,Primary Bank,Website design,false"
        ) }
        var isParsing by remember { mutableStateOf(false) }
        var parseError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showImportDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth().height(520.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Import CSV Content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Paste raw CSV text below to parse & preview:", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = rawCsv,
                        onValueChange = {
                            rawCsv = it
                            parseError = null
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = AmoledBorder
                        )
                    )

                    if (parseError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(parseError!!, color = ExpenseCoral, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { showImportDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                isParsing = true
                                scope.launch {
                                    viewModel.csvManager.previewCsv(currentUserId, rawCsv)
                                        .onSuccess {
                                            importPreview = it
                                            showImportDialog = false
                                        }
                                        .onFailure {
                                            parseError = it.localizedMessage
                                        }
                                    isParsing = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Text("Preview Import", color = Color(0xFF082F49), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Import Preview Confirmation Modal
    if (importPreview != null) {
        val preview = importPreview!!
        Dialog(onDismissRequest = { importPreview = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth().height(480.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("CSV Import Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total parsed: ${preview.totalRecords}", fontSize = 12.sp, color = TextSecondary)
                        Text("New records: ${preview.newRecords}", fontSize = 12.sp, color = EmeraldMint, fontWeight = FontWeight.Bold)
                        if (preview.duplicateRecords > 0) {
                            Text("Duplicates skipped: ${preview.duplicateRecords}", fontSize = 12.sp, color = WarningGold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(preview.items) { item ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = AmoledCard)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(item.category, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextPrimary)
                                        Text("${item.type} • ${item.dateStr}", fontSize = 10.sp, color = TextMuted)
                                    }
                                    Text(item.amountFormatted, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldMint)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { importPreview = null }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.csvManager.executeImport(currentUserId, preview.parsedTransactions)
                                        .onSuccess { count ->
                                            statusMessage = "Successfully imported $count transactions!"
                                            importPreview = null
                                        }
                                        .onFailure {
                                            statusMessage = "Import execution failed: ${it.localizedMessage}"
                                        }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Confirm & Import", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

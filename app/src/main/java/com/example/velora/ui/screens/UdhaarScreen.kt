package com.example.velora.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.velora.data.entity.AccountEntity
import com.example.velora.data.entity.UdhaarEntity
import com.example.velora.data.entity.UdhaarStatus
import com.example.velora.data.entity.UdhaarType
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
import com.example.velora.util.CurrencyHelper
import com.example.velora.util.DateUtils

@Composable
fun UdhaarScreen(viewModel: VeloraViewModel) {
    val udhaarList by viewModel.udhaarList.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val isHidden by viewModel.isBalanceHidden.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Money I Lent, 1: Money I Borrowed
    var showAddUdhaarDialog by remember { mutableStateOf(false) }
    var selectedUdhaarForRepay by remember { mutableStateOf<UdhaarEntity?>(null) }

    val lentRecords = udhaarList.filter { it.type == UdhaarType.LENT }
    val borrowedRecords = udhaarList.filter { it.type == UdhaarType.BORROWED }

    val currentRecords = if (selectedTab == 0) lentRecords else borrowedRecords

    val totalOriginalPaise = currentRecords.sumOf { it.originalAmountPaise }
    val totalOutstandingPaise = currentRecords.filter { it.status != UdhaarStatus.SETTLED }.sumOf { it.outstandingAmountPaise }
    val totalSettledPaise = totalOriginalPaise - totalOutstandingPaise

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .testTag("udhaar_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text("Udhaar Ledger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Track loans, receivables & payables accurately", fontSize = 12.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs: Lent vs Borrowed
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AmoledCard,
                contentColor = EmeraldPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = if (selectedTab == 0) EmeraldPrimary else WarningGold
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Money I Lent (${lentRecords.count { it.status != UdhaarStatus.SETTLED }})",
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedTab == 0) EmeraldPrimary else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Money I Owe (${borrowedRecords.count { it.status != UdhaarStatus.SETTLED }})",
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedTab == 1) WarningGold else TextSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (selectedTab == 0) "TOTAL OUTSTANDING RECEIVABLE" else "TOTAL OUTSTANDING PAYABLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalOutstandingPaise),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (selectedTab == 0) EmeraldMint else WarningGold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Settled so far", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalSettledPaise),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Udhaar List
            if (currentRecords.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Handshake,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (selectedTab == 0) "No money lent recorded" else "No borrowed money recorded",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap the '+' button below to record an Udhaar entry", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(currentRecords, key = { it.id }) { udhaar ->
                        UdhaarItemCard(
                            udhaar = udhaar,
                            isHidden = isHidden,
                            onRepayClick = { selectedUdhaarForRepay = udhaar }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showAddUdhaarDialog = true },
            containerColor = if (selectedTab == 0) EmeraldPrimary else WarningGold,
            contentColor = Color(0xFF003822),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 90.dp)
                .testTag("add_udhaar_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Udhaar")
        }
    }

    // Add Udhaar Dialog
    if (showAddUdhaarDialog) {
        AddUdhaarDialog(
            isLent = selectedTab == 0,
            accounts = accounts,
            onDismiss = { showAddUdhaarDialog = false },
            onConfirm = { name, amt, accId, notes ->
                if (selectedTab == 0) {
                    viewModel.lendMoney(name, amt, accId, null, notes)
                } else {
                    viewModel.borrowMoney(name, amt, accId, null, notes)
                }
            }
        )
    }

    // Repay / Settle Udhaar Dialog
    if (selectedUdhaarForRepay != null) {
        val u = selectedUdhaarForRepay!!
        RepayUdhaarDialog(
            udhaar = u,
            accounts = accounts,
            onDismiss = { selectedUdhaarForRepay = null },
            onConfirm = { amt, accId, note ->
                viewModel.recordUdhaarRepayment(u.id, amt, accId, note)
            }
        )
    }
}

@Composable
fun UdhaarItemCard(
    udhaar: UdhaarEntity,
    isHidden: Boolean,
    onRepayClick: () -> Unit
) {
    val progress = if (udhaar.originalAmountPaise > 0) {
        1f - (udhaar.outstandingAmountPaise.toFloat() / udhaar.originalAmountPaise.toFloat())
    } else 1f

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (udhaar.type == UdhaarType.LENT) EmeraldPrimary.copy(alpha = 0.15f) else WarningGold.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = if (udhaar.type == UdhaarType.LENT) EmeraldPrimary else WarningGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(udhaar.personName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Text(DateUtils.formatDate(udhaar.createdAt), fontSize = 11.sp, color = TextMuted)
                    }
                }

                // Status Badge
                val (statusBg, statusFg, statusText) = when (udhaar.status) {
                    UdhaarStatus.PENDING -> Triple(WarningGold.copy(alpha = 0.2f), WarningGold, "Pending")
                    UdhaarStatus.PARTIALLY_SETTLED -> Triple(Color(0xFF38BDF8).copy(alpha = 0.2f), Color(0xFF38BDF8), "Partial")
                    UdhaarStatus.SETTLED -> Triple(EmeraldPrimary.copy(alpha = 0.2f), EmeraldMint, "Settled")
                }

                Box(
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(statusText, color = statusFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Outstanding", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(udhaar.outstandingAmountPaise),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (udhaar.status == UdhaarStatus.SETTLED) TextMuted else TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Original Total", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(udhaar.originalAmountPaise),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (udhaar.type == UdhaarType.LENT) EmeraldPrimary else WarningGold,
                trackColor = AmoledBorder
            )

            if (udhaar.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Note: ${udhaar.notes}", fontSize = 12.sp, color = TextMuted)
            }

            if (udhaar.status != UdhaarStatus.SETTLED) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRepayClick,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (udhaar.type == UdhaarType.LENT) EmeraldPrimary else WarningGold
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (udhaar.type == UdhaarType.LENT) "Record Repayment Received" else "Record Repayment Made",
                        color = Color(0xFF003822),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AddUdhaarDialog(
    isLent: Boolean,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amountPaise: Long, accountId: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AmoledSurface),
            modifier = Modifier.fillMaxWidth().testTag("add_udhaar_dialog")
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isLent) "Record Money Lent" else "Record Money Borrowed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Person Name") },
                    placeholder = { Text("e.g. Rahul Sharma") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("udhaar_person_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isLent) EmeraldPrimary else WarningGold,
                        unfocusedBorderColor = AmoledBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("e.g. 2000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("udhaar_amount_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isLent) EmeraldPrimary else WarningGold,
                        unfocusedBorderColor = AmoledBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    if (isLent) "Given From Account:" else "Received Into Account:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts) { acc ->
                        FilterChip(
                            selected = acc.id == selectedAccountId,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text(acc.name, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Reason") },
                    placeholder = { Text("e.g. For dinner bill split") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isLent) EmeraldPrimary else WarningGold,
                        unfocusedBorderColor = AmoledBorder
                    )
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText!!, color = ExpenseCoral, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val amt = CurrencyHelper.parseToPaise(amountText)
                            if (name.isBlank()) {
                                errorText = "Please enter person name"
                                return@Button
                            }
                            if (amt == null || amt <= 0) {
                                errorText = "Please enter a valid amount"
                                return@Button
                            }
                            onConfirm(name, amt, selectedAccountId, notes)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLent) EmeraldPrimary else WarningGold
                        ),
                        modifier = Modifier.testTag("confirm_add_udhaar_btn")
                    ) {
                        Text("Record Udhaar", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RepayUdhaarDialog(
    udhaar: UdhaarEntity,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (amountPaise: Long, accountId: String, note: String) -> Unit
) {
    val isLent = udhaar.type == UdhaarType.LENT
    var amountText by remember { mutableStateOf((udhaar.outstandingAmountPaise / 100.0).toString()) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AmoledSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (isLent) "Record Repayment from ${udhaar.personName}" else "Record Repayment to ${udhaar.personName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Outstanding: ${CurrencyHelper.formatPaise(udhaar.outstandingAmountPaise)}",
                    color = EmeraldMint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = { Text("Repayment Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("repay_amount_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = AmoledBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    if (isLent) "Deposit repayment into:" else "Deduct repayment from:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts) { acc ->
                        FilterChip(
                            selected = acc.id == selectedAccountId,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text(acc.name, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("e.g. Received via GPay") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = AmoledBorder
                    )
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText!!, color = ExpenseCoral, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val amt = CurrencyHelper.parseToPaise(amountText)
                            if (amt == null || amt <= 0) {
                                errorText = "Please enter valid repayment amount"
                                return@Button
                            }
                            if (amt > udhaar.outstandingAmountPaise) {
                                errorText = "Amount cannot exceed outstanding balance (${CurrencyHelper.formatPaise(udhaar.outstandingAmountPaise)})"
                                return@Button
                            }
                            onConfirm(amt, selectedAccountId, note)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.testTag("confirm_repay_button")
                    ) {
                        Text("Confirm", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

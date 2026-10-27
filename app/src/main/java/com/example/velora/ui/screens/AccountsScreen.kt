package com.example.velora.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.HelpOutline
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.velora.data.entity.AccountEntity
import com.example.velora.data.entity.AccountType
import com.example.velora.ui.VeloraViewModel
import com.example.velora.ui.theme.AmoledBackground
import com.example.velora.ui.theme.AmoledBorder
import com.example.velora.ui.theme.AmoledCard
import com.example.velora.ui.theme.AmoledCardElevated
import com.example.velora.ui.theme.AmoledSurface
import com.example.velora.ui.theme.EmeraldMint
import com.example.velora.ui.theme.EmeraldPrimary
import com.example.velora.ui.theme.ExpenseCoral
import com.example.velora.ui.theme.TextMuted
import com.example.velora.ui.theme.TextPrimary
import com.example.velora.ui.theme.TextSecondary
import com.example.velora.ui.theme.WarningGold
import com.example.velora.util.CurrencyHelper

@Composable
fun AccountsScreen(
    viewModel: VeloraViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val isHidden by viewModel.isBalanceHidden.collectAsStateWithLifecycle()
    val legacyDemoDismissed by viewModel.legacyDemoDismissed.collectAsStateWithLifecycle()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var accountToReconcile by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }

    val hasLegacyDemoSeed = !legacyDemoDismissed && accounts.any {
        (it.name == "Primary Bank" && it.openingBalancePaise == 4500000L && it.currentBalancePaise == 4500000L) ||
        (it.name == "Cash in Hand" && it.openingBalancePaise == 250000L && it.currentBalancePaise == 250000L) ||
        (it.name == "UPI / Wallet" && it.openingBalancePaise == 350000L && it.currentBalancePaise == 350000L)
    }

    val totalBalance = accounts.sumOf { it.currentBalancePaise }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .testTag("accounts_screen")
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
                    Text("Accounts & Balances", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Reconcile and manage your liquid funds", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total summary card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL LIQUID FUNDS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalBalance),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = EmeraldMint
                        )
                    }

                    OutlinedButton(
                        onClick = { showTransferDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Transfer")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (hasLegacyDemoSeed) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCardElevated),
                    border = BorderStroke(1.dp, WarningGold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("legacy_demo_warning_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = WarningGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sample Balances Review", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Initial sample opening balances (₹51,000 total) were detected in your accounts. If these are sample figures and not your real funds, you can reset them to ₹0 with one tap.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.resetLegacyDemoBalances() },
                                colors = ButtonDefaults.buttonColors(containerColor = WarningGold),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reset to ₹0", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.dismissLegacyDemoWarning() },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Keep Balances", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("ALL ACCOUNTS (${accounts.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(accounts, key = { it.id }) { acc ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AmoledCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                val icon = when (acc.type) {
                                    AccountType.CASH -> Icons.Default.Payments
                                    AccountType.BANK -> Icons.Default.AccountBalance
                                    AccountType.UPI -> Icons.Default.AccountBalanceWallet
                                    AccountType.CUSTOM -> Icons.Default.MoreHoriz
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(EmeraldPrimary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(acc.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                                    Text("Type: ${acc.type.name} • Opening: ${CurrencyHelper.formatPaise(acc.openingBalancePaise)}", fontSize = 11.sp, color = TextMuted)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(acc.currentBalancePaise),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (acc.currentBalancePaise >= 0) TextPrimary else ExpenseCoral
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { accountToEdit = acc },
                                        modifier = Modifier.size(28.dp).testTag("edit_account_btn_${acc.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Account", tint = TextSecondary, modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Reconcile",
                                        color = EmeraldMint,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { accountToReconcile = acc }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddAccountDialog = true },
            containerColor = EmeraldPrimary,
            contentColor = Color(0xFF003822),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_account_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Account")
        }
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        var name by remember { mutableStateOf("") }
        var balanceText by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(AccountType.BANK) }
        var error by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showAddAccountDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Add New Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account Name") },
                        placeholder = { Text("e.g. SBI Savings, Paytm Wallet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AmoledBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = { Text("Opening Balance (₹)") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_account_opening_balance"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AmoledBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Money you already had in this account before tracking with Velora. This is not recorded as new income.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Account Type", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AccountType.BANK, AccountType.UPI, AccountType.CASH, AccountType.CUSTOM).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error!!, color = ExpenseCoral, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { showAddAccountDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    error = "Enter account name"
                                    return@Button
                                }
                                val paise = CurrencyHelper.parseToPaise(balanceText) ?: 0L
                                viewModel.addAccount(name, selectedType, paise, "#10B981")
                                showAddAccountDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Create", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Edit Account Dialog
    if (accountToEdit != null) {
        val acc = accountToEdit!!
        var editName by remember(acc) { mutableStateOf(acc.name) }
        var editOpeningText by remember(acc) {
            val amt = acc.openingBalancePaise / 100.0
            mutableStateOf(if (amt % 1.0 == 0.0) amt.toLong().toString() else amt.toString())
        }
        var editType by remember(acc) { mutableStateOf(acc.type) }
        var editError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { accountToEdit = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Edit Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Account Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AmoledBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editOpeningText,
                        onValueChange = { editOpeningText = it },
                        label = { Text("Opening Balance (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_account_opening_balance"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AmoledBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Money you already had before tracking with Velora. Changing this updates your current balance by the difference without altering your past transaction history.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Account Type", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AccountType.BANK, AccountType.UPI, AccountType.CASH, AccountType.CUSTOM).forEach { type ->
                            FilterChip(
                                selected = editType == type,
                                onClick = { editType = type },
                                label = { Text(type.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (editError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(editError!!, color = ExpenseCoral, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { accountToEdit = null }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (editName.isBlank()) {
                                    editError = "Enter account name"
                                    return@Button
                                }
                                val paise = CurrencyHelper.parseToPaise(editOpeningText)
                                if (paise == null) {
                                    editError = "Enter a valid opening balance"
                                    return@Button
                                }
                                viewModel.editAccount(acc.id, editName, editType, paise)
                                accountToEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Save Changes", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Reconcile Dialog
    if (accountToReconcile != null) {
        val acc = accountToReconcile!!
        var newBalanceText by remember { mutableStateOf((acc.currentBalancePaise / 100.0).toString()) }
        var reason by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { accountToReconcile = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Reconcile ${acc.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Current Recorded: ${CurrencyHelper.formatPaise(acc.currentBalancePaise)}", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newBalanceText,
                        onValueChange = { newBalanceText = it },
                        label = { Text("Actual New Balance (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AmoledBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason for Adjustment") },
                        placeholder = { Text("e.g. Bank interest credited / Missed cash expense") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AmoledBorder
                        )
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error!!, color = ExpenseCoral, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { accountToReconcile = null }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val paise = CurrencyHelper.parseToPaise(newBalanceText)
                                if (paise == null) {
                                    error = "Enter a valid amount"
                                    return@Button
                                }
                                viewModel.adjustBalance(acc.id, paise, reason.ifBlank { "Manual reconciliation" })
                                accountToReconcile = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Confirm Adjustment", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showTransferDialog) {
        TransferDialog(
            accounts = accounts,
            onDismiss = { showTransferDialog = false },
            onConfirm = { fromId, toId, amt, note -> viewModel.transfer(fromId, toId, amt, note) }
        )
    }
}

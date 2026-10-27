package com.example.velora.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.velora.data.entity.AccountEntity
import com.example.velora.data.entity.AccountType
import com.example.velora.data.entity.TransactionEntity
import com.example.velora.data.entity.TransactionType
import com.example.velora.data.entity.UdhaarStatus
import com.example.velora.data.entity.UdhaarType
import com.example.velora.ui.VeloraViewModel
import com.example.velora.ui.theme.AmoledBackground
import com.example.velora.ui.theme.AmoledBorder
import com.example.velora.ui.theme.AmoledCard
import com.example.velora.ui.theme.AmoledCardElevated
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

@Composable
fun HomeScreen(
    viewModel: VeloraViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToUdhaar: () -> Unit,
    onNavigateToMenu: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transactions by viewModel.allActiveTransactions.collectAsStateWithLifecycle()
    val udhaarList by viewModel.udhaarList.collectAsStateWithLifecycle()
    val isHidden by viewModel.isBalanceHidden.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackupTime.collectAsStateWithLifecycle()

    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }

    // Calculated liquid balance (strictly from actual accounts)
    val totalLiquidPaise = accounts.sumOf { it.currentBalancePaise }

    // Money Lent (Receivables) - only pending/partial outstanding
    val totalLentReceivablePaise = udhaarList
        .filter { it.type == UdhaarType.LENT && it.status != UdhaarStatus.SETTLED }
        .sumOf { it.outstandingAmountPaise }

    // Money Borrowed (Payables) - only pending/partial outstanding
    val totalBorrowedPayablePaise = udhaarList
        .filter { it.type == UdhaarType.BORROWED && it.status != UdhaarStatus.SETTLED }
        .sumOf { it.outstandingAmountPaise }

    // Estimated Net Position: Liquid Funds + Receivables - Payables
    val estimatedNetPositionPaise = totalLiquidPaise + totalLentReceivablePaise - totalBorrowedPayablePaise

    // Today's spending
    val todaySpendingPaise = transactions
        .filter { it.type == TransactionType.EXPENSE && DateUtils.isToday(it.timestamp) }
        .sumOf { it.amountPaise }

    // Month's income & expenses
    val now = System.currentTimeMillis()
    val monthIncomePaise = transactions
        .filter { it.type == TransactionType.INCOME && DateUtils.isSameMonth(it.timestamp, now) }
        .sumOf { it.amountPaise }
    val monthExpensePaise = transactions
        .filter { it.type == TransactionType.EXPENSE && DateUtils.isSameMonth(it.timestamp, now) }
        .sumOf { it.amountPaise }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.velora_logo),
                        contentDescription = "Velora Logo",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("VELORA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = EmeraldMint)
                        Text("Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                // Backup Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(AmoledCard, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        if (lastBackup != null) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                        contentDescription = "Backup status",
                        tint = if (lastBackup != null) EmeraldPrimary else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (lastBackup != null) "Backed Up" else "Local",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Hero: Total Liquid Balance Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("liquid_balance_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(EmeraldPrimary.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL LIQUID BALANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = TextSecondary)
                            IconButton(
                                onClick = { viewModel.toggleHideBalance() },
                                modifier = Modifier.size(28.dp).testTag("toggle_hide_balance_btn")
                            ) {
                                Icon(
                                    if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Balance",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isHidden) "₹ ••••••••" else CurrencyHelper.formatPaise(totalLiquidPaise),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Estimated Net Position row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AmoledCardElevated, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Estimated Net Position:", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                if (isHidden) "₹ •••••" else CurrencyHelper.formatPaise(estimatedNetPositionPaise),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (estimatedNetPositionPaise >= 0) EmeraldMint else ExpenseCoral
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Shortcuts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionButton(
                    icon = Icons.Default.ArrowDownward,
                    label = "Deposit",
                    color = EmeraldPrimary,
                    onClick = { showDepositDialog = true },
                    testTag = "home_deposit_btn"
                )
                QuickActionButton(
                    icon = Icons.Default.ArrowUpward,
                    label = "Expense",
                    color = ExpenseCoral,
                    onClick = { showWithdrawDialog = true },
                    testTag = "home_expense_btn"
                )
                QuickActionButton(
                    icon = Icons.Default.SwapHoriz,
                    label = "Transfer",
                    color = Color(0xFF38BDF8),
                    onClick = { showTransferDialog = true },
                    testTag = "home_transfer_btn"
                )
                QuickActionButton(
                    icon = Icons.Default.Handshake,
                    label = "Udhaar",
                    color = WarningGold,
                    onClick = onNavigateToUdhaar,
                    testTag = "home_udhaar_btn"
                )
            }
        }

        // Month Summary Mini-Cards (Income vs Expense vs Today)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryPillCard(
                    title = "This Month Income",
                    amountStr = if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(monthIncomePaise, false),
                    color = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                SummaryPillCard(
                    title = "This Month Spend",
                    amountStr = if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(monthExpensePaise, false),
                    color = ExpenseCoral,
                    modifier = Modifier.weight(1f)
                )
                SummaryPillCard(
                    title = "Today's Spend",
                    amountStr = if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(todaySpendingPaise, false),
                    color = WarningGold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Accounts Overview (Cash, Bank, UPI)
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onNavigateToMenu) {
                        Text("Manage", color = EmeraldPrimary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(accounts) { acc ->
                        AccountPill(
                            account = acc,
                            isHidden = isHidden
                        )
                    }
                }
            }
        }

        // Udhaar Snapshot (Lent vs Borrowed)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToUdhaar() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Money I Lent (Receivable)", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalLentReceivablePaise),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldMint
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(AmoledBorder)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text("Money I Owe (Payable)", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalBorrowedPayablePaise),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseCoral
                        )
                    }
                }
            }
        }

        // Recent Transactions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNavigateToHistory) {
                    Text("See All", color = EmeraldPrimary, fontSize = 13.sp)
                }
            }
        }

        val recentTx = transactions.take(5)
        if (recentTx.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ill_empty_receipt),
                            contentDescription = "No transactions",
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No transactions recorded yet", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Use Deposit or Expense buttons above to start tracking.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(recentTx) { tx ->
                TransactionRowItem(tx = tx, isHidden = isHidden)
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Dialogs
    if (showDepositDialog) {
        DepositDialog(
            accounts = accounts,
            onDismiss = { showDepositDialog = false },
            onConfirm = { accId, amt, cat, note -> viewModel.deposit(accId, amt, cat, note) }
        )
    }

    if (showWithdrawDialog) {
        WithdrawDialog(
            accounts = accounts,
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { accId, amt, cat, note -> viewModel.withdraw(accId, amt, cat, note) }
        )
    }

    if (showTransferDialog) {
        TransferDialog(
            accounts = accounts,
            onDismiss = { showTransferDialog = false },
            onConfirm = { fromId, toId, amt, note -> viewModel.transfer(fromId, toId, amt, note) }
        )
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SummaryPillCard(
    title: String,
    amountStr: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(amountStr, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
fun AccountPill(
    account: AccountEntity,
    isHidden: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        modifier = Modifier.width(150.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (account.type) {
                    AccountType.CASH -> Icons.Default.Payments
                    AccountType.BANK -> Icons.Default.AccountBalance
                    AccountType.UPI -> Icons.Default.AccountBalanceWallet
                    AccountType.CUSTOM -> Icons.Default.MoreHoriz
                }
                Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(account.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(account.currentBalancePaise),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (account.currentBalancePaise >= 0) TextPrimary else ExpenseCoral
            )
        }
    }
}

@Composable
fun TransactionRowItem(
    tx: TransactionEntity,
    isHidden: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val (color, prefix, icon) = when (tx.type) {
                    TransactionType.INCOME, TransactionType.LEND_REPAYMENT ->
                        Triple(EmeraldPrimary, "+", Icons.Default.ArrowDownward)
                    TransactionType.EXPENSE, TransactionType.BORROW_REPAYMENT ->
                        Triple(ExpenseCoral, "-", Icons.Default.ArrowUpward)
                    TransactionType.TRANSFER ->
                        Triple(Color(0xFF38BDF8), "", Icons.Default.SwapHoriz)
                    TransactionType.LEND ->
                        Triple(WarningGold, "Lent ", Icons.Default.Handshake)
                    TransactionType.BORROW ->
                        Triple(WarningGold, "Borrowed ", Icons.Default.Handshake)
                    TransactionType.ADJUSTMENT ->
                        Triple(TextSecondary, "", Icons.Default.MoreHoriz)
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(tx.category, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (tx.note.isNotEmpty()) {
                        Text(tx.note, fontSize = 12.sp, color = TextMuted, maxLines = 1)
                    }
                    Text(DateUtils.formatDateTime(tx.timestamp), fontSize = 10.sp, color = TextMuted)
                }
            }

            val isIncomeLike = tx.type in listOf(TransactionType.INCOME, TransactionType.LEND_REPAYMENT)
            val isExpenseLike = tx.type in listOf(TransactionType.EXPENSE, TransactionType.BORROW_REPAYMENT)
            val amtColor = when {
                isIncomeLike -> EmeraldPrimary
                isExpenseLike -> ExpenseCoral
                else -> TextPrimary
            }

            Text(
                if (isHidden) "₹ •••" else {
                    val sign = if (isIncomeLike) "+" else if (isExpenseLike) "-" else ""
                    "$sign${CurrencyHelper.formatPaise(tx.amountPaise)}"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = amtColor
            )
        }
    }
}

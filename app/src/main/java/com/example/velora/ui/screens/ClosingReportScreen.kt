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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.velora.data.entity.TransactionType
import com.example.velora.data.entity.UdhaarStatus
import com.example.velora.data.entity.UdhaarType
import com.example.velora.ui.VeloraViewModel
import com.example.velora.ui.theme.AmoledBackground
import com.example.velora.ui.theme.AmoledBorder
import com.example.velora.ui.theme.AmoledCard
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
fun ClosingReportScreen(
    viewModel: VeloraViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transactions by viewModel.allActiveTransactions.collectAsStateWithLifecycle()
    val udhaarList by viewModel.udhaarList.collectAsStateWithLifecycle()
    val isHidden by viewModel.isBalanceHidden.collectAsStateWithLifecycle()

    val now = System.currentTimeMillis()
    val monthTransactions = transactions.filter { DateUtils.isSameMonth(it.timestamp, now) }

    // Liquid funds & account totals
    val totalOpeningPaise = accounts.sumOf { it.openingBalancePaise }
    val totalClosingLiquidPaise = accounts.sumOf { it.currentBalancePaise }

    // Cash flow categories for month
    val incomePaise = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
    val expensePaise = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
    val transfersPaise = monthTransactions.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amountPaise }

    // Debt principal movements
    val lentOutPaise = monthTransactions.filter { it.type == TransactionType.LEND }.sumOf { it.amountPaise }
    val loanRecoveriesPaise = monthTransactions.filter { it.type == TransactionType.LEND_REPAYMENT }.sumOf { it.amountPaise }
    val borrowedPaise = monthTransactions.filter { it.type == TransactionType.BORROW }.sumOf { it.amountPaise }
    val borrowedRepaymentsPaise = monthTransactions.filter { it.type == TransactionType.BORROW_REPAYMENT }.sumOf { it.amountPaise }

    // Outstanding balances (Balance Sheet)
    val totalReceivablesPaise = udhaarList
        .filter { it.type == UdhaarType.LENT && it.status != UdhaarStatus.SETTLED }
        .sumOf { it.outstandingAmountPaise }
    val totalPayablesPaise = udhaarList
        .filter { it.type == UdhaarType.BORROWED && it.status != UdhaarStatus.SETTLED }
        .sumOf { it.outstandingAmountPaise }

    val netPositionPaise = totalClosingLiquidPaise + totalReceivablesPaise - totalPayablesPaise

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
            .testTag("closing_report_screen"),
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
                    Text("Monthly Closing Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Formal balance sheet for ${DateUtils.formatMonthYear(now)}", fontSize = 12.sp, color = EmeraldMint)
                }
            }
        }

        // Net Position Hero
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("ESTIMATED NET FINANCIAL POSITION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (isHidden) "₹ •••••" else CurrencyHelper.formatPaise(netPositionPaise),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = if (netPositionPaise >= 0) EmeraldMint else ExpenseCoral
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "= Liquid Funds (${CurrencyHelper.formatPaise(totalClosingLiquidPaise)}) + Receivables (${CurrencyHelper.formatPaise(totalReceivablesPaise)}) - Payables (${CurrencyHelper.formatPaise(totalPayablesPaise)})",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Section 1: Liquid Cash Flow
        item {
            Text("1. LIQUID CASH FLOW SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ReportRow("Total Opening Balance", CurrencyHelper.formatPaise(totalOpeningPaise), TextPrimary)
                    ReportRow("Ordinary Income", "+${CurrencyHelper.formatPaise(incomePaise)}", EmeraldPrimary)
                    ReportRow("Ordinary Expenses", "-${CurrencyHelper.formatPaise(expensePaise)}", ExpenseCoral)
                    ReportRow("Internal Account Transfers", CurrencyHelper.formatPaise(transfersPaise) + " (Zero-sum)", Color(0xFF38BDF8))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AmoledBorder)
                    ReportRow("Current Liquid Balance", CurrencyHelper.formatPaise(totalClosingLiquidPaise), TextPrimary, isBold = true)
                }
            }
        }

        // Section 2: Debt & Loan Movements
        item {
            Text("2. DEBT & UDHAAR PRINCIPAL MOVEMENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ReportRow("Money Lent Out (Reduced Liquid)", "-${CurrencyHelper.formatPaise(lentOutPaise)}", WarningGold)
                    ReportRow("Loan Principal Recovered (Added Liquid)", "+${CurrencyHelper.formatPaise(loanRecoveriesPaise)}", EmeraldMint)
                    ReportRow("Money Borrowed (Added Liquid)", "+${CurrencyHelper.formatPaise(borrowedPaise)}", WarningGold)
                    ReportRow("Borrowed Money Repaid (Reduced Liquid)", "-${CurrencyHelper.formatPaise(borrowedRepaymentsPaise)}", ExpenseCoral)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AmoledBorder)
                    Text(
                        "*Note: Principal recoveries and borrowings adjust liquid balance directly and are strictly kept separate from ordinary revenue and expense.",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Section 3: Accounts Breakdown
        item {
            Text("3. CLOSING ACCOUNTS STATEMENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        items(accounts) { acc ->
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
                    Column {
                        Text(acc.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Opening: ${CurrencyHelper.formatPaise(acc.openingBalancePaise)}", fontSize = 11.sp, color = TextMuted)
                    }
                    Text(
                        if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(acc.currentBalancePaise),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (acc.currentBalancePaise >= 0) TextPrimary else ExpenseCoral
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun ReportRow(label: String, value: String, valueColor: Color, isBold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontSize = 13.sp, color = valueColor, fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

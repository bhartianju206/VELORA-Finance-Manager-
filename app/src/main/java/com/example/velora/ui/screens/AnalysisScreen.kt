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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import java.util.Calendar

@Composable
fun AnalysisScreen(
    viewModel: VeloraViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val transactions by viewModel.allActiveTransactions.collectAsStateWithLifecycle()
    val isHidden by viewModel.isBalanceHidden.collectAsStateWithLifecycle()

    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, -1)
    val lastMonthTime = cal.timeInMillis

    // Current month transactions
    val currentMonthExpenses = transactions.filter {
        it.type == TransactionType.EXPENSE && DateUtils.isSameMonth(it.timestamp, now)
    }
    val currentMonthIncome = transactions.filter {
        it.type == TransactionType.INCOME && DateUtils.isSameMonth(it.timestamp, now)
    }

    // Previous month expenses
    val lastMonthExpenses = transactions.filter {
        it.type == TransactionType.EXPENSE && DateUtils.isSameMonth(it.timestamp, lastMonthTime)
    }

    val totalCurrentExpense = currentMonthExpenses.sumOf { it.amountPaise }
    val totalCurrentIncome = currentMonthIncome.sumOf { it.amountPaise }
    val totalLastExpense = lastMonthExpenses.sumOf { it.amountPaise }

    // Category breakdown
    val categoryTotals = currentMonthExpenses.groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amountPaise } }
        .toList()
        .sortedByDescending { it.second }

    // Top 5 largest expenses
    val largestExpenses = currentMonthExpenses.sortedByDescending { it.amountPaise }.take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
            .testTag("analysis_screen"),
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
                    Text("Spending Analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Breakdown and trends from saved records", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }

        // Current Month vs Last Month Comparison
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("MONTH OVER MONTH TREND", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("This Month Spending", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalCurrentExpense),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Last Month Spending", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalLastExpense),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Safe comparison without misleading math
                    val comparisonNote = when {
                        totalLastExpense == 0L && totalCurrentExpense == 0L -> "No expense records in either month"
                        totalLastExpense == 0L -> "First month of tracked spending"
                        totalCurrentExpense > totalLastExpense -> {
                            val diff = totalCurrentExpense - totalLastExpense
                            val pct = ((diff.toFloat() / totalLastExpense.toFloat()) * 100).toInt()
                            "Spending increased by ${CurrencyHelper.formatPaise(diff)} (+${pct}%)"
                        }
                        totalCurrentExpense < totalLastExpense -> {
                            val diff = totalLastExpense - totalCurrentExpense
                            val pct = ((diff.toFloat() / totalLastExpense.toFloat()) * 100).toInt()
                            "Spending reduced by ${CurrencyHelper.formatPaise(diff)} (-${pct}%)"
                        }
                        else -> "Spending is identical to last month"
                    }

                    val isIncrease = totalCurrentExpense > totalLastExpense && totalLastExpense > 0L

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isIncrease) ExpenseCoral else EmeraldMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            comparisonNote,
                            fontSize = 12.sp,
                            color = if (isIncrease) ExpenseCoral else EmeraldMint,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Income vs Expense comparison
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Earned this Month", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalCurrentIncome),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldMint
                        )
                    }

                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Spent this Month", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalCurrentExpense),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseCoral
                        )
                    }
                }
            }
        }

        // Category Breakdown
        item {
            Text("CATEGORY BREAKDOWN (${categoryTotals.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
        }

        if (categoryTotals.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No expenses recorded for current month", color = TextSecondary)
                    }
                }
            }
        } else {
            val colors = listOf(
                EmeraldPrimary, Color(0xFF38BDF8), WarningGold,
                Color(0xFFA78BFA), Color(0xFFF43F5E), Color(0xFF34D399)
            )

            items(categoryTotals) { (cat, amount) ->
                val pct = if (totalCurrentExpense > 0) ((amount.toFloat() / totalCurrentExpense.toFloat()) * 100).toInt() else 0
                val colorIndex = Math.abs(cat.hashCode()) % colors.size
                val catColor = colors[colorIndex]

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(catColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cat, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            }
                            Text(
                                if (isHidden) "₹ •••" else "${CurrencyHelper.formatPaise(amount)} ($pct%)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { if (totalCurrentExpense > 0) (amount.toFloat() / totalCurrentExpense.toFloat()) else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = catColor,
                            trackColor = AmoledBorder
                        )
                    }
                }
            }
        }

        // Top Expenses
        if (largestExpenses.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("LARGEST TRANSACTIONS THIS MONTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
            }

            items(largestExpenses) { tx ->
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
                        Column {
                            Text(tx.category, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                            if (tx.note.isNotEmpty()) {
                                Text(tx.note, fontSize = 12.sp, color = TextMuted)
                            }
                            Text(DateUtils.formatDateTime(tx.timestamp), fontSize = 10.sp, color = TextMuted)
                        }
                        Text(
                            if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(tx.amountPaise),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ExpenseCoral
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

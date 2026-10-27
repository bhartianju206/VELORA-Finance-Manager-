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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.velora.data.entity.BudgetEntity
import com.example.velora.data.entity.TransactionType
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
fun BudgetsScreen(
    viewModel: VeloraViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val budgets by viewModel.currentBudgets.collectAsStateWithLifecycle()
    val transactions by viewModel.allActiveTransactions.collectAsStateWithLifecycle()
    val isHidden by viewModel.isBalanceHidden.collectAsStateWithLifecycle()

    var showAddBudgetDialog by remember { mutableStateOf(false) }

    // Spending for current month: Only ordinary EXPENSE transactions
    val now = System.currentTimeMillis()
    val currentMonthExpenses = transactions.filter {
        it.type == TransactionType.EXPENSE && DateUtils.isSameMonth(it.timestamp, now)
    }

    val totalSpentInMonth = currentMonthExpenses.sumOf { it.amountPaise }
    val overallBudget = budgets.firstOrNull { it.category == "__OVERALL__" }
    val categoryBudgets = budgets.filter { it.category != "__OVERALL__" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .testTag("budgets_screen")
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
                    Text("Budgets & Targets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(DateUtils.formatMonthYear(now), fontSize = 12.sp, color = EmeraldMint)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Overall Monthly Budget Hero Card
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
                        Text("OVERALL MONTHLY BUDGET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        if (overallBudget != null) {
                            IconButton(
                                onClick = { viewModel.deleteBudget(overallBudget.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (overallBudget == null) {
                        Text("No overall budget set for this month", color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showAddBudgetDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                        ) {
                            Text("Set Monthly Budget")
                        }
                    } else {
                        val limit = overallBudget.limitPaise
                        val remaining = limit - totalSpentInMonth
                        val percentage = if (limit > 0) ((totalSpentInMonth.toFloat() / limit.toFloat()) * 100).toInt() else 0
                        val isOver = totalSpentInMonth > limit
                        val isWarning = percentage >= overallBudget.alertThresholdPercent

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isHidden) "₹ •••" else CurrencyHelper.formatPaise(totalSpentInMonth),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isOver) ExpenseCoral else if (isWarning) WarningGold else EmeraldMint
                            )
                            Text(
                                "of ${CurrencyHelper.formatPaise(limit)} ($percentage%)",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Bottom)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { (totalSpentInMonth.toFloat() / limit.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (isOver) ExpenseCoral else if (isWarning) WarningGold else EmeraldPrimary,
                            trackColor = AmoledBorder
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isOver) "Exceeded by ${CurrencyHelper.formatPaise(Math.abs(remaining))}"
                                else "Remaining: ${CurrencyHelper.formatPaise(remaining)}",
                                fontSize = 12.sp,
                                color = if (isOver) ExpenseCoral else EmeraldMint,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isWarning && !isOver) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Near limit (${overallBudget.alertThresholdPercent}%)", fontSize = 11.sp, color = WarningGold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("CATEGORY BUDGETS (${categoryBudgets.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMint, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (categoryBudgets.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No category-specific budgets set", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(categoryBudgets, key = { it.id }) { b ->
                        val spent = currentMonthExpenses
                            .filter { it.category.equals(b.category, ignoreCase = true) }
                            .sumOf { it.amountPaise }
                        val pct = if (b.limitPaise > 0) ((spent.toFloat() / b.limitPaise.toFloat()) * 100).toInt() else 0
                        val isOver = spent > b.limitPaise

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AmoledCard),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(b.category, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                    IconButton(
                                        onClick = { viewModel.deleteBudget(b.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${CurrencyHelper.formatPaise(spent)} spent",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isOver) ExpenseCoral else TextPrimary
                                    )
                                    Text("Limit: ${CurrencyHelper.formatPaise(b.limitPaise)} ($pct%)", fontSize = 12.sp, color = TextSecondary)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { (spent.toFloat() / b.limitPaise.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = if (isOver) ExpenseCoral else EmeraldPrimary,
                                    trackColor = AmoledBorder
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddBudgetDialog = true },
            containerColor = EmeraldPrimary,
            contentColor = Color(0xFF003822),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_budget_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Budget")
        }
    }

    if (showAddBudgetDialog) {
        var category by remember { mutableStateOf("") }
        var isOverall by remember { mutableStateOf(false) }
        var limitText by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showAddBudgetDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Set Budget Target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isOverall = true
                                category = "__OVERALL__"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isOverall) EmeraldPrimary.copy(alpha = 0.2f) else Color.Transparent
                            )
                        ) {
                            Text("Overall Monthly", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                isOverall = false
                                category = "Food & Dining"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (!isOverall) EmeraldPrimary.copy(alpha = 0.2f) else Color.Transparent
                            )
                        ) {
                            Text("Category Specific", fontSize = 12.sp)
                        }
                    }

                    if (!isOverall) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = AmoledBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Budget Limit (₹)") },
                        placeholder = { Text("e.g. 15000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        OutlinedButton(onClick = { showAddBudgetDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val paise = CurrencyHelper.parseToPaise(limitText)
                                if (paise == null || paise <= 0) {
                                    error = "Enter a valid budget limit"
                                    return@Button
                                }
                                val finalCat = if (isOverall) "__OVERALL__" else category.trim()
                                if (finalCat.isBlank()) {
                                    error = "Enter category name"
                                    return@Button
                                }
                                viewModel.setBudget(finalCat, paise)
                                showAddBudgetDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Save Budget", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

package com.example.velora.ui.screens

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.velora.data.entity.TransactionEntity
import com.example.velora.data.entity.TransactionType
import com.example.velora.ui.SortOrder
import com.example.velora.ui.TransactionFilter
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
fun HistoryScreen(viewModel: VeloraViewModel) {
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val trashTransactions by viewModel.trashTransactions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isHidden by viewModel.isBalanceHidden.collectAsStateWithLifecycle()

    var showTrashModal by remember { mutableStateOf(false) }
    var selectedTxForDetail by remember { mutableStateOf<TransactionEntity?>(null) }
    var txToDeleteWithConfirm by remember { mutableStateOf<TransactionEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
            .testTag("history_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title and Trash Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transaction History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu Trigger
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(AmoledSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest First", color = TextPrimary) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.NEWEST)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest First", color = TextPrimary) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.OLDEST)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Highest Amount", color = TextPrimary) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.HIGHEST_AMOUNT)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Lowest Amount", color = TextPrimary) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.LOWEST_AMOUNT)
                                showSortMenu = false
                            }
                        )
                    }
                }

                // Trash Button with badge
                IconButton(
                    onClick = { showTrashModal = true },
                    modifier = Modifier.testTag("trash_bin_button")
                ) {
                    Box {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Trash Bin", tint = TextSecondary)
                        if (trashTransactions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(ExpenseCoral, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search by category, note, amount...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("history_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = AmoledBorder
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filters (All, Income, Expense, Transfer, Udhaar)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf(
                TransactionFilter.ALL to "All",
                TransactionFilter.INCOME to "Income",
                TransactionFilter.EXPENSE to "Expense",
                TransactionFilter.TRANSFER to "Transfer",
                TransactionFilter.UDHAAR to "Udhaar"
            )
            items(filters) { (f, label) ->
                FilterChip(
                    selected = selectedFilter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary,
                        selectedLabelColor = Color(0xFF003822)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transaction List
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ill_empty_receipt),
                        contentDescription = "No transactions",
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No transactions found", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Try clearing filters or search criteria", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(transactions, key = { it.id }) { tx ->
                    HistoryItemCard(
                        tx = tx,
                        isHidden = isHidden,
                        onClick = { selectedTxForDetail = tx },
                        onDeleteClick = { txToDeleteWithConfirm = tx }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Transaction Detail Modal
    if (selectedTxForDetail != null) {
        val tx = selectedTxForDetail!!
        Dialog(onDismissRequest = { selectedTxForDetail = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Transaction Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    DetailRow("Type", tx.type.name)
                    DetailRow("Category", tx.category)
                    DetailRow("Amount", CurrencyHelper.formatPaise(tx.amountPaise))
                    DetailRow("Date", DateUtils.formatDateTime(tx.timestamp))
                    if (tx.note.isNotEmpty()) {
                        DetailRow("Note", tx.note)
                    }
                    DetailRow("Transaction ID", tx.id.take(12) + "...")

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedTxForDetail = null
                                txToDeleteWithConfirm = tx
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseCoral)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Move to Trash")
                        }

                        Button(
                            onClick = { selectedTxForDetail = null },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Done", color = Color(0xFF003822))
                        }
                    }
                }
            }
        }
    }

    // Confirm Move to Trash Dialog
    if (txToDeleteWithConfirm != null) {
        val tx = txToDeleteWithConfirm!!
        AlertDialog(
            onDismissRequest = { txToDeleteWithConfirm = null },
            title = { Text("Move to Trash?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Moving this transaction to Trash will adjust your account balance accordingly. You can restore it anytime from the Trash Bin.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.moveToTrash(tx.id)
                        txToDeleteWithConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral)
                ) {
                    Text("Move to Trash", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { txToDeleteWithConfirm = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Trash Bin Modal (Restore & Permanent Delete)
    if (showTrashModal) {
        Dialog(onDismissRequest = { showTrashModal = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                modifier = Modifier.fillMaxWidth().height(500.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ExpenseCoral)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Trash Bin (${trashTransactions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showTrashModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (trashTransactions.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Trash is empty", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(trashTransactions) { tx ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = AmoledCard)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tx.category, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                            Text(CurrencyHelper.formatPaise(tx.amountPaise), fontSize = 12.sp, color = ExpenseCoral)
                                        }
                                        Row {
                                            IconButton(onClick = { viewModel.restoreFromTrash(tx.id) }) {
                                                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = EmeraldPrimary)
                                            }
                                            IconButton(onClick = { viewModel.permanentlyDeleteTransaction(tx.id) }) {
                                                Icon(Icons.Default.DeleteForever, contentDescription = "Permanent Delete", tint = ExpenseCoral)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.emptyTrash() },
                            colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Empty Entire Trash", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    tx: TransactionEntity,
    isHidden: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val (color, icon) = when (tx.type) {
                    TransactionType.INCOME, TransactionType.LEND_REPAYMENT -> Pair(EmeraldPrimary, Icons.Default.ArrowDownward)
                    TransactionType.EXPENSE, TransactionType.BORROW_REPAYMENT -> Pair(ExpenseCoral, Icons.Default.ArrowUpward)
                    TransactionType.TRANSFER -> Pair(Color(0xFF38BDF8), Icons.Default.SwapHoriz)
                    TransactionType.LEND, TransactionType.BORROW -> Pair(WarningGold, Icons.Default.Handshake)
                    TransactionType.ADJUSTMENT -> Pair(TextSecondary, Icons.Default.MoreHoriz)
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
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

            Row(verticalAlignment = Alignment.CenterVertically) {
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

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

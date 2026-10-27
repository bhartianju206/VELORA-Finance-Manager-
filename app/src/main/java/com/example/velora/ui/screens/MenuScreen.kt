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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.velora.ui.theme.AmoledBackground
import com.example.velora.ui.theme.AmoledCard
import com.example.velora.ui.theme.EmeraldMint
import com.example.velora.ui.theme.EmeraldPrimary
import com.example.velora.ui.theme.ExpenseCoral
import com.example.velora.ui.theme.TextMuted
import com.example.velora.ui.theme.TextPrimary
import com.example.velora.ui.theme.TextSecondary
import com.example.velora.ui.theme.WarningGold

enum class MenuDestination {
    ACCOUNTS,
    BUDGETS,
    ANALYSIS,
    CLOSING_REPORT,
    REMINDERS_RECURRING,
    BACKUP_SYNC,
    CSV_EXPORT_IMPORT,
    SETTINGS_SECURITY
}

@Composable
fun MenuScreen(
    onNavigate: (MenuDestination) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
            .testTag("menu_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Financial Hub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Advanced accounting, analysis & backup tools", fontSize = 12.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))
        }

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
                        painter = painterResource(id = R.drawable.velora_crystal_badge),
                        contentDescription = "Velora Emblem",
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("VELORA VAULT", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = EmeraldMint)
                        Text("Your Money. Your Control. Offline & Private.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }

        item {
            Text("ACCOUNTS & PLANNING", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = EmeraldMint)
        }

        item {
            MenuItemCard(
                title = "Accounts & Transfers",
                subtitle = "Manage Cash, Bank, UPI & balance reconciliation",
                icon = Icons.Default.AccountBalance,
                iconColor = EmeraldPrimary,
                onClick = { onNavigate(MenuDestination.ACCOUNTS) }
            )
        }

        item {
            MenuItemCard(
                title = "Budgets & Spending Limits",
                subtitle = "Monthly targets & warning alerts",
                icon = Icons.Default.Savings,
                iconColor = WarningGold,
                onClick = { onNavigate(MenuDestination.BUDGETS) }
            )
        }

        item {
            MenuItemCard(
                title = "Spending Analysis",
                subtitle = "Category breakdown & monthly trends",
                icon = Icons.Default.PieChart,
                iconColor = Color(0xFF38BDF8),
                onClick = { onNavigate(MenuDestination.ANALYSIS) }
            )
        }

        item {
            MenuItemCard(
                title = "Monthly Closing Report",
                subtitle = "Complete balance sheet, receivables & net position",
                icon = Icons.Default.Assessment,
                iconColor = Color(0xFFA78BFA),
                onClick = { onNavigate(MenuDestination.CLOSING_REPORT) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("AUTOMATION & SCHEDULES", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = EmeraldMint)
        }

        item {
            MenuItemCard(
                title = "Reminders & Recurring Payments",
                subtitle = "Due dates, bill reminders & recurring schedules",
                icon = Icons.Default.NotificationsActive,
                iconColor = WarningGold,
                onClick = { onNavigate(MenuDestination.REMINDERS_RECURRING) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("DATA & SECURITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = EmeraldMint)
        }

        item {
            MenuItemCard(
                title = "Google Drive Backup & Sync",
                subtitle = "Private cloud backup, local snapshots & safe restore",
                icon = Icons.Default.CloudUpload,
                iconColor = EmeraldPrimary,
                onClick = { onNavigate(MenuDestination.BACKUP_SYNC) }
            )
        }

        item {
            MenuItemCard(
                title = "CSV Export & Import",
                subtitle = "Export transaction sheet or import with validation",
                icon = Icons.Default.Description,
                iconColor = Color(0xFF38BDF8),
                onClick = { onNavigate(MenuDestination.CSV_EXPORT_IMPORT) }
            )
        }

        item {
            MenuItemCard(
                title = "Settings & Security",
                subtitle = "App PIN lock, account profile & Firebase guide",
                icon = Icons.Default.Security,
                iconColor = ExpenseCoral,
                onClick = { onNavigate(MenuDestination.SETTINGS_SECURITY) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun MenuItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, fontSize = 12.sp, color = TextMuted)
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

package com.example.velora.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.velora.data.repository.AuthState
import com.example.velora.ui.screens.AccountsScreen
import com.example.velora.ui.screens.AnalysisScreen
import com.example.velora.ui.screens.AuthScreen
import com.example.velora.ui.screens.BackupSyncScreen
import com.example.velora.ui.screens.BudgetsScreen
import com.example.velora.ui.screens.ClosingReportScreen
import com.example.velora.ui.screens.CsvScreen
import com.example.velora.ui.screens.HistoryScreen
import com.example.velora.ui.screens.HomeScreen
import com.example.velora.ui.screens.MenuDestination
import com.example.velora.ui.screens.MenuScreen
import com.example.velora.ui.screens.RemindersScreen
import com.example.velora.ui.screens.SettingsScreen
import com.example.velora.ui.screens.UdhaarScreen
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import com.example.R
import com.example.velora.ui.screens.SplashScreen

enum class MainDestination {
    HOME,
    HISTORY,
    UDHAAR,
    MENU
}

@Composable
fun VeloraApp(
    viewModel: VeloraViewModel = viewModel()
) {
    var showSplash by remember { mutableStateOf(true) }
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showSplash) {
        SplashScreen(onSplashFinished = { showSplash = false })
        return
    }

    when (val state = authState) {
        is AuthState.Unauthenticated -> {
            AuthScreen(
                authRepository = viewModel.authRepository,
                onAuthSuccess = { /* Automatically handled via DataStore Flow */ }
            )
        }
        is AuthState.Authenticated -> {
            if (state.isLocked && !isAppUnlocked) {
                // PIN Lock Screen
                AppLockPinScreen(
                    onVerifyPin = { pin -> viewModel.authRepository.verifyPin(pin) },
                    onUnlocked = { viewModel.unlockApp() }
                )
            } else {
                MainAppScaffold(viewModel = viewModel, snackbarHostState = snackbarHostState)
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    viewModel: VeloraViewModel,
    snackbarHostState: SnackbarHostState
) {
    var currentMainDestination by remember { mutableStateOf(MainDestination.HOME) }
    var currentMenuSubDestination by remember { mutableStateOf<MenuDestination?>(null) }

    // Intercept back button when inside a menu sub-screen
    if (currentMenuSubDestination != null) {
        BackHandler {
            currentMenuSubDestination = null
        }
    } else if (currentMainDestination != MainDestination.HOME) {
        BackHandler {
            currentMainDestination = MainDestination.HOME
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Only display primary bottom nav when on main destinations
            if (currentMenuSubDestination == null) {
                NavigationBar(
                    containerColor = AmoledSurface,
                    contentColor = TextPrimary,
                    modifier = Modifier.navigationBarsPadding().testTag("bottom_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentMainDestination == MainDestination.HOME,
                        onClick = { currentMainDestination = MainDestination.HOME },
                        icon = {
                            Icon(
                                if (currentMainDestination == MainDestination.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF003822),
                            selectedTextColor = EmeraldMint,
                            indicatorColor = EmeraldPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )

                    NavigationBarItem(
                        selected = currentMainDestination == MainDestination.HISTORY,
                        onClick = { currentMainDestination = MainDestination.HISTORY },
                        icon = {
                            Icon(
                                if (currentMainDestination == MainDestination.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "History"
                            )
                        },
                        label = { Text("History", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF003822),
                            selectedTextColor = EmeraldMint,
                            indicatorColor = EmeraldPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )

                    NavigationBarItem(
                        selected = currentMainDestination == MainDestination.UDHAAR,
                        onClick = { currentMainDestination = MainDestination.UDHAAR },
                        icon = {
                            Icon(
                                if (currentMainDestination == MainDestination.UDHAAR) Icons.Filled.Handshake else Icons.Outlined.Handshake,
                                contentDescription = "Udhaar"
                            )
                        },
                        label = { Text("Udhaar", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF003822),
                            selectedTextColor = EmeraldMint,
                            indicatorColor = EmeraldPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )

                    NavigationBarItem(
                        selected = currentMainDestination == MainDestination.MENU,
                        onClick = { currentMainDestination = MainDestination.MENU },
                        icon = {
                            Icon(
                                if (currentMainDestination == MainDestination.MENU) Icons.Filled.Menu else Icons.Outlined.Menu,
                                contentDescription = "Menu"
                            )
                        },
                        label = { Text("Menu", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF003822),
                            selectedTextColor = EmeraldMint,
                            indicatorColor = EmeraldPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        },
        containerColor = AmoledBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            if (currentMenuSubDestination != null) {
                when (currentMenuSubDestination!!) {
                    MenuDestination.ACCOUNTS -> AccountsScreen(viewModel = viewModel, onBack = { currentMenuSubDestination = null })
                    MenuDestination.BUDGETS -> BudgetsScreen(viewModel = viewModel, onBack = { currentMenuSubDestination = null })
                    MenuDestination.ANALYSIS -> AnalysisScreen(viewModel = viewModel, onBack = { currentMenuSubDestination = null })
                    MenuDestination.CLOSING_REPORT -> ClosingReportScreen(viewModel = viewModel, onBack = { currentMenuSubDestination = null })
                    MenuDestination.REMINDERS_RECURRING -> RemindersScreen(viewModel = viewModel, onBack = { currentMenuSubDestination = null })
                    MenuDestination.BACKUP_SYNC -> BackupSyncScreen(viewModel = viewModel, onBack = { currentMenuSubDestination = null })
                    MenuDestination.CSV_EXPORT_IMPORT -> CsvScreen(viewModel = viewModel, onBack = { currentMenuSubDestination = null })
                    MenuDestination.SETTINGS_SECURITY -> SettingsScreen(viewModel = viewModel, onBack = { currentMenuSubDestination = null })
                }
            } else {
                when (currentMainDestination) {
                    MainDestination.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToHistory = { currentMainDestination = MainDestination.HISTORY },
                        onNavigateToUdhaar = { currentMainDestination = MainDestination.UDHAAR },
                        onNavigateToMenu = {
                            currentMainDestination = MainDestination.MENU
                            currentMenuSubDestination = MenuDestination.ACCOUNTS
                        }
                    )
                    MainDestination.HISTORY -> HistoryScreen(viewModel = viewModel)
                    MainDestination.UDHAAR -> UdhaarScreen(viewModel = viewModel)
                    MainDestination.MENU -> MenuScreen(
                        onNavigate = { dest -> currentMenuSubDestination = dest }
                    )
                }
            }
        }
    }
}

@Composable
fun AppLockPinScreen(
    onVerifyPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AmoledCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.velora_logo),
                    contentDescription = "Velora Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Velora App Locked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Enter security PIN to access your finance records", fontSize = 12.sp, color = TextSecondary)

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6) {
                            pin = it
                            errorText = null
                        }
                    },
                    label = { Text("Security PIN") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = AmoledBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_pin_input")
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText!!, color = ExpenseCoral, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val ok = onVerifyPin(pin)
                            if (ok) {
                                onUnlocked()
                            } else {
                                errorText = "Incorrect PIN. Please try again."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Unlock Velora", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

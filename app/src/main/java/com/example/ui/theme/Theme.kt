package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VeloraDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF003822),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = EmeraldLight,
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF0F3B2C),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = WarningGold,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = WarningGoldContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = AmoledBackground,
    onBackground = TextPrimary,
    surface = AmoledSurface,
    onSurface = TextPrimary,
    surfaceVariant = AmoledCard,
    onSurfaceVariant = TextSecondary,
    outline = AmoledBorder,
    outlineVariant = Color(0xFF16231E),
    error = ExpenseCoral,
    onError = Color.White,
    errorContainer = ExpenseCoralContainer,
    onErrorContainer = Color(0xFFFFD1D9)
)

@Composable
fun VeloraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VeloraDarkColorScheme,
        typography = Typography,
        content = content
    )
}

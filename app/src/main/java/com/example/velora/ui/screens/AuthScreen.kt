package com.example.velora.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.velora.data.repository.AuthRepository
import com.example.velora.ui.theme.AmoledBackground
import com.example.velora.ui.theme.AmoledBorder
import com.example.velora.ui.theme.AmoledCard
import com.example.velora.ui.theme.EmeraldDark
import com.example.velora.ui.theme.EmeraldMint
import com.example.velora.ui.theme.EmeraldPrimary
import com.example.velora.ui.theme.ExpenseCoral
import com.example.velora.ui.theme.TextMuted
import com.example.velora.ui.theme.TextPrimary
import com.example.velora.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Login, 1: Register, 2: Reset

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showGoogleConfigDialog by remember { mutableStateOf(false) }
    var webClientIdInput by remember { mutableStateOf("") }
    var isCopiedSha by remember { mutableStateOf(false) }

    fun executeGoogleSignIn(clientId: String) {
        isLoading = true
        errorMessage = null
        successMessage = null
        scope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetSignInWithGoogleOption.Builder(clientId.trim())
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    authRepository.signInWithGoogleIdToken(idToken)
                        .onSuccess {
                            onAuthSuccess()
                        }
                        .onFailure {
                            errorMessage = "Firebase Auth failed: ${it.localizedMessage}"
                        }
                } else {
                    errorMessage = "Unexpected credential format returned from Google Sign-In."
                }
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the account chooser
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.javaClass.simpleName
                if (msg.contains("10") || msg.contains("12500") || msg.contains("DEVELOPER_ERROR")) {
                    errorMessage = "Google Sign-In Error (10): Ensure SHA-1 fingerprint is registered in Firebase Console and Google Auth provider is enabled."
                } else {
                    errorMessage = "Google Sign-In failed: $msg"
                }
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
    ) {
        // High-fidelity AMOLED Login Background with emerald ambient glows
        Image(
            painter = painterResource(id = R.drawable.bg_login),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.85f)
        )

        // Subtle gradient vignette overlay for optimal form contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AmoledBackground.copy(alpha = 0.6f),
                            AmoledBackground.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Official Velora Logo Asset
            Image(
                painter = painterResource(id = R.drawable.velora_logo),
                contentDescription = "Velora Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "VELORA",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
                color = TextPrimary
            )
            Text(
                "Your Money. Your Control.",
                style = MaterialTheme.typography.bodyMedium,
                color = EmeraldMint,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Auth Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledCard.copy(alpha = 0.94f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = EmeraldPrimary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = EmeraldPrimary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                errorMessage = null
                                successMessage = null
                            },
                            text = { Text("Log In", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                errorMessage = null
                                successMessage = null
                            },
                            text = { Text("Register", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = {
                                selectedTab = 2
                                errorMessage = null
                                successMessage = null
                            },
                            text = { Text("Reset", fontWeight = FontWeight.SemiBold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (selectedTab == 1) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldPrimary) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = AmoledBorder
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = EmeraldPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AmoledBorder
                        )
                    )

                    if (selectedTab != 2) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            label = { Text(if (selectedTab == 0) "Password" else "Create Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldPrimary) },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = TextMuted
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = AmoledBorder
                            )
                        )
                    }

                    if (selectedTab == 1 || selectedTab == 2) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text(if (selectedTab == 1) "Confirm Password" else "New Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldPrimary) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_confirm_password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = AmoledBorder
                            )
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            errorMessage!!,
                            color = ExpenseCoral,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (successMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            successMessage!!,
                            color = EmeraldMint,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            successMessage = null
                            isLoading = true
                            scope.launch {
                                try {
                                    when (selectedTab) {
                                        0 -> {
                                            authRepository.login(email, password)
                                                .onSuccess { onAuthSuccess() }
                                                .onFailure { errorMessage = it.localizedMessage }
                                        }
                                        1 -> {
                                            if (password != confirmPassword) {
                                                errorMessage = "Passwords do not match."
                                                return@launch
                                            }
                                            authRepository.register(email, password, displayName)
                                                .onSuccess { onAuthSuccess() }
                                                .onFailure { errorMessage = it.localizedMessage }
                                        }
                                        2 -> {
                                            authRepository.resetPassword(email, confirmPassword)
                                                .onSuccess {
                                                    successMessage = "Password updated! You can now log in."
                                                    selectedTab = 0
                                                }
                                                .onFailure { errorMessage = it.localizedMessage }
                                        }
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF003822))
                        } else {
                            Text(
                                when (selectedTab) {
                                    0 -> "Log In"
                                    1 -> "Create Account"
                                    else -> "Reset Password"
                                },
                                color = Color(0xFF003822),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real Functional Google Sign-In button with Google G branding
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val detectedClientId = authRepository.resolveWebClientId()
                                if (detectedClientId.isNullOrBlank()) {
                                    showGoogleConfigDialog = true
                                } else {
                                    executeGoogleSignIn(detectedClientId)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmoledBorder),
                        enabled = !isLoading
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Official Google G indicator
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("G", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF4285F4))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Continue with Google", fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = {
                            scope.launch {
                                webClientIdInput = authRepository.resolveWebClientId() ?: ""
                                showGoogleConfigDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("google_auth_setup_button")
                    ) {
                        Text("Firebase & Google Sign-In Setup / Keys", color = TextMuted, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Demo credentials button
                    TextButton(
                        onClick = {
                            email = "alex@velora.finance"
                            password = "Password123"
                            if (selectedTab == 1) {
                                displayName = "Alex Turner"
                                confirmPassword = "Password123"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("quick_demo_button")
                    ) {
                        Text("Fill Demo Credentials (Instant Access)", color = EmeraldMint, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Real Google Sign-In & Firebase Configuration Dialog
        if (showGoogleConfigDialog) {
            AlertDialog(
                onDismissRequest = { showGoogleConfigDialog = false },
                title = { Text("Google Sign-In & Firebase Setup", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "Velora authenticates using official Android Credential Manager and Firebase Authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Firebase Project: velora-finance-manager", color = EmeraldMint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Package: com.aistudio.velora.kxmpzq", color = TextMuted, fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("SHA-1 Fingerprint (Required in Firebase Console):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AmoledBackground),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AmoledBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "2D:6D:3E:FB:A6:66:BA:F0:0A:73:66:A3:4F:B8:7A:E2:87:7F:36:74",
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = EmeraldMint
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("Velora SHA1", "2D:6D:3E:FB:A6:66:BA:F0:0A:73:66:A3:4F:B8:7A:E2:87:7F:36:74")
                                        clipboard?.setPrimaryClip(clip)
                                        isCopiedSha = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isCopiedSha) EmeraldDark else EmeraldPrimary)
                                ) {
                                    Text(if (isCopiedSha) "✓ SHA-1 Copied!" else "Copy SHA-1 Fingerprint", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Firebase Console Steps:\n" +
                                    "1. In Authentication → Sign-in method: Enable 'Google'.\n" +
                                    "2. In Project Settings → Your apps: Add the SHA-1 fingerprint above.\n" +
                                    "3. Paste your Web Client ID below (ends with .apps.googleusercontent.com):",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = webClientIdInput,
                            onValueChange = { webClientIdInput = it },
                            label = { Text("OAuth Web Client ID") },
                            placeholder = { Text("e.g. 675344395387-xxx.apps.googleusercontent.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("web_client_id_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = AmoledBorder
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (webClientIdInput.isNotBlank()) {
                                scope.launch {
                                    authRepository.saveWebClientId(webClientIdInput.trim())
                                    showGoogleConfigDialog = false
                                    executeGoogleSignIn(webClientIdInput.trim())
                                }
                            } else {
                                showGoogleConfigDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        enabled = webClientIdInput.isNotBlank()
                    ) {
                        Text("Connect & Sign In", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoogleConfigDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}

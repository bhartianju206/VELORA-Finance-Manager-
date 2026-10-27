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
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
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
    var showAuthErrorDialog by remember { mutableStateOf(false) }
    var authErrorTitle by remember { mutableStateOf("Sign-In Failed") }
    var authErrorDetail by remember { mutableStateOf("") }
    var webClientIdInput by remember { mutableStateOf("") }
    var isCopiedSha by remember { mutableStateOf(false) }

    fun executeGoogleSignIn(clientId: String) {
        isLoading = true
        errorMessage = null
        successMessage = null
        scope.launch {
            try {
                val cleanClientId = clientId.trim()
                Log.i("VeloraAuth", "1/4: Initiating Google Sign-In with serverClientId=$cleanClientId")
                val credentialManager = CredentialManager.create(context)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(cleanClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val siwgOption = GetSignInWithGoogleOption.Builder(cleanClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .addCredentialOption(siwgOption)
                    .build()

                Log.d("VeloraAuth", "2/4: Prompting account chooser via CredentialManager...")
                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                Log.i("VeloraAuth", "3/4: Credential returned. Class=${credential.javaClass.simpleName}, type=${credential.type}")

                val idToken: String? = when {
                    credential is CustomCredential && (
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
                    ) -> {
                        try {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            Log.d("VeloraAuth", "Parsed GoogleIdTokenCredential for user: ${googleIdTokenCredential.id}")
                            googleIdTokenCredential.idToken
                        } catch (e: Exception) {
                            Log.e("VeloraAuth", "Error parsing GoogleIdTokenCredential bundle", e)
                            credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                ?: credential.data.getString("id_token")
                        }
                    }
                    credential is CustomCredential -> {
                        Log.w("VeloraAuth", "CustomCredential type '${credential.type}' not standard. Checking bundle keys...")
                        credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                            ?: credential.data.getString("id_token")
                    }
                    else -> {
                        Log.e("VeloraAuth", "Non-custom credential class received: ${credential.javaClass.name}")
                        null
                    }
                }

                if (idToken.isNullOrBlank()) {
                    val err = "Unable to retrieve Google ID token from response (type: ${credential.type})."
                    Log.e("VeloraAuth", err)
                    errorMessage = err
                    authErrorTitle = "Google ID Token Missing"
                    authErrorDetail = err
                    showAuthErrorDialog = true
                    return@launch
                }

                Log.i("VeloraAuth", "4/4: Exchanging ID token (length=${idToken.length}) with Firebase Authentication...")
                authRepository.signInWithGoogleIdToken(idToken)
                    .onSuccess { user ->
                        Log.i("VeloraAuth", "✓ Google Sign-In & Firebase Auth SUCCESS! User: ${user.email} (UID: ${user.id})")
                        onAuthSuccess()
                    }
                    .onFailure { error ->
                        Log.e("VeloraAuth", "✗ Firebase signInWithCredential failed", error)
                        val fMsg = error.localizedMessage ?: error.javaClass.simpleName
                        val cleanMsg = when {
                            fMsg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
                            fMsg.contains("disabled for this Firebase project", ignoreCase = true) -> {
                                "Google Sign-In is disabled in your Firebase project.\n\nTo fix:\n1. Open Firebase Console (velora-finance-manager)\n2. Navigate to Build → Authentication → Sign-in method\n3. Click 'Google' and toggle Enable to ON\n4. Set a Project support email and Save"
                            }
                            fMsg.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                            fMsg.contains("invalid token", ignoreCase = true) -> {
                                "Invalid Google credential token. The token audience does not match your Firebase Web Client ID ($cleanClientId) or has expired."
                            }
                            else -> {
                                "Firebase Authentication failed: $fMsg"
                            }
                        }
                        errorMessage = cleanMsg
                        authErrorTitle = "Firebase Authentication Failed"
                        authErrorDetail = cleanMsg
                        showAuthErrorDialog = true
                    }

            } catch (e: GetCredentialCancellationException) {
                Log.w("VeloraAuth", "Google account selection was dismissed or cancelled by user (${e.message})")
                // User dismissed the chooser; retain login state and do not show jarring error modal
            } catch (e: Exception) {
                Log.e("VeloraAuth", "CredentialManager getCredential failed with exception: ${e.javaClass.name}: ${e.message}", e)
                val msg = e.localizedMessage ?: e.javaClass.simpleName
                val cleanMsg = when {
                    msg.contains("10") || msg.contains("12500") || msg.contains("DEVELOPER_ERROR", ignoreCase = true) -> {
                        "Google Sign-In Error (Code 10 - DEVELOPER_ERROR):\n\n" +
                        "Google Play Services rejected the sign-in request due to a signature/configuration mismatch:\n\n" +
                        "1. SHA-1 Fingerprint:\nEnsure SHA-1 ${AuthRepository.DEBUG_SHA1} is added under your Android app in Firebase Console.\n\n" +
                        "2. Keystore Match:\nVerify the installed APK was signed with the official debug keystore (re-install the latest APK built with restored debug key).\n\n" +
                        "3. Google Auth Provider:\nEnsure 'Google' is turned ON in Firebase Console > Authentication > Sign-in method."
                    }
                    msg.contains("16") || msg.contains("CANCELLED", ignoreCase = true) -> {
                        "Google Sign-In request was cancelled."
                    }
                    msg.contains("NoCredentialException", ignoreCase = true) -> {
                        "No Google accounts found on this device. Please log into a Google account in Android system settings."
                    }
                    else -> {
                        "Google Sign-In failed: $msg\n(${e.javaClass.simpleName})"
                    }
                }
                errorMessage = cleanMsg
                authErrorTitle = "Google Sign-In Failed"
                authErrorDetail = cleanMsg
                showAuthErrorDialog = true
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

                    // Prominent Google Sign-In Error Banner right beneath the button so it is never missed
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ExpenseCoral.copy(alpha = 0.12f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ExpenseCoral.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().testTag("auth_error_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Error",
                                        tint = ExpenseCoral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Authentication Alert", color = ExpenseCoral, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    errorMessage!!,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                webClientIdInput = authRepository.resolveWebClientId() ?: AuthRepository.WEB_CLIENT_ID
                                                showGoogleConfigDialog = true
                                            }
                                        }
                                    ) {
                                        Text("Fix / Verify Keys", color = EmeraldMint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = {
                            scope.launch {
                                webClientIdInput = authRepository.resolveWebClientId() ?: AuthRepository.WEB_CLIENT_ID
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

        // Dedicated Detailed Error Dialog on Sign-In Failure
        if (showAuthErrorDialog) {
            AlertDialog(
                onDismissRequest = { showAuthErrorDialog = false },
                title = { Text(authErrorTitle, fontWeight = FontWeight.Bold, color = ExpenseCoral) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            authErrorDetail,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = TextPrimary
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAuthErrorDialog = false
                            scope.launch {
                                webClientIdInput = authRepository.resolveWebClientId() ?: AuthRepository.WEB_CLIENT_ID
                                showGoogleConfigDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Check Setup / Keys", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAuthErrorDialog = false }) {
                        Text("Dismiss", color = TextMuted)
                    }
                }
            )
        }

        // Real Google Sign-In & Firebase Configuration Dialog
        if (showGoogleConfigDialog) {
            val installedSha = remember { AuthRepository.getInstalledApkSha1(context) }
            val isShaMatch = AuthRepository.REGISTERED_SHA1S.any { it.equals(installedSha, ignoreCase = true) }

            AlertDialog(
                onDismissRequest = { showGoogleConfigDialog = false },
                title = { Text("Google Sign-In & Firebase Status", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "Velora authenticates with Google Identity Services & Firebase Authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Firebase Project: ${AuthRepository.FIREBASE_PROJECT_ID}", color = EmeraldMint, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Package Name: ${context.packageName}", color = TextMuted, fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Runtime APK Signature SHA-1:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isShaMatch) EmeraldDark.copy(alpha = 0.3f) else ExpenseCoral.copy(alpha = 0.15f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isShaMatch) EmeraldPrimary else ExpenseCoral
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    installedSha,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = if (isShaMatch) EmeraldMint else ExpenseCoral
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isShaMatch) "✓ MATCHES Firebase Registered Key" else "⚠️ Key mismatch! Must match Firebase SHA-1.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isShaMatch) EmeraldMint else ExpenseCoral
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Firebase Registered SHA-1:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AmoledBackground),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AmoledBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    AuthRepository.DEBUG_SHA1,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = EmeraldMint
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("Velora SHA1", AuthRepository.DEBUG_SHA1)
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
                            "Firebase Console Requirements:\n" +
                                    "1. Authentication → Sign-in method: Enable 'Google'.\n" +
                                    "2. Project Settings → Your apps: Ensure SHA-1 is added.\n" +
                                    "3. Web Client ID (serverClientId):",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

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
                            val targetId = webClientIdInput.trim().ifEmpty { AuthRepository.WEB_CLIENT_ID }
                            scope.launch {
                                authRepository.saveWebClientId(targetId)
                                showGoogleConfigDialog = false
                                executeGoogleSignIn(targetId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Connect & Sign In", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoogleConfigDialog = false }) {
                        Text("Close", color = TextMuted)
                    }
                }
            )
        }
    }
}

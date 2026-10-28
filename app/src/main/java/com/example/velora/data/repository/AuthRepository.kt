package com.example.velora.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.velora.util.awaitTask
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "velora_auth_prefs")

data class VeloraUser(
    val id: String,
    val email: String,
    val displayName: String,
    val isGoogleUser: Boolean = false
)

sealed interface AuthState {
    object Unauthenticated : AuthState
    data class Authenticated(val user: VeloraUser, val isLocked: Boolean = false) : AuthState
}

data class FirebaseConfigInfo(
    val projectId: String,
    val packageName: String,
    val webClientId: String,
    val registeredSha1s: List<String>
)

class AuthRepository(private val context: Context) {

    companion object {
        private const val TAG = "VeloraAuth"
        const val DEBUG_SHA1 = "5C:0C:85:77:ED:69:B1:D1:B5:91:AD:25:91:F6:F4:5D:09:AE:76:2A"
        val REGISTERED_SHA1S = setOf(
            "5C:0C:85:77:ED:69:B1:D1:B5:91:AD:25:91:F6:F4:5D:09:AE:76:2A",
            "2D:6D:3E:FB:A6:66:BA:F0:0A:73:66:A3:4F:B8:7A:E2:87:7F:36:74",
            "3C:83:D2:49:17:75:E5:C6:55:98:6B:B9:EF:9E:2D:47:4F:DC:9E:B4",
            "08:09:50:96:62:E0:1D:16:DC:83:30:32:38:86:93:CF:52:89:05:87"
        )
        const val FIREBASE_PROJECT_ID = "velora-finance-manager"
        const val FIREBASE_PROJECT_NUMBER = "675344395387"
        const val FIREBASE_STORAGE_BUCKET = "velora-finance-manager.firebasestorage.app"
        const val FIREBASE_APP_ID = "1:675344395387:android:32f8c582f4f2288cc0c6de"
        const val FIREBASE_API_KEY = "AIzaSyBJl24vuSonX_sDYBdF6cOdqpYh9PeRI8c"
        const val WEB_CLIENT_ID = "675344395387-9l7v727lcq2u56udcm5m0du6pntf1rcv.apps.googleusercontent.com"

        @Synchronized
        fun ensureFirebaseInitialized(context: Context): Boolean {
            val appContext = context.applicationContext ?: context
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                return true
            }

            // 1. Attempt standard automatic initialization from resources
            try {
                val autoApp = FirebaseApp.initializeApp(appContext)
                if (autoApp != null) {
                    Log.i(TAG, "✓ FirebaseApp successfully initialized via resources")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Automatic FirebaseApp.initializeApp(Context) threw: ${e.message}")
            }

            // 2. Attempt explicit programmatic initialization from assets/google-services.json
            try {
                val jsonString = appContext.assets.open("google-services.json").bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val projectInfo = root.optJSONObject("project_info")
                val projectId = projectInfo?.optString("project_id") ?: FIREBASE_PROJECT_ID
                val projectNumber = projectInfo?.optString("project_number") ?: FIREBASE_PROJECT_NUMBER
                val storageBucket = projectInfo?.optString("storage_bucket") ?: FIREBASE_STORAGE_BUCKET

                val clients = root.optJSONArray("client")
                val client = clients?.optJSONObject(0)
                val clientInfo = client?.optJSONObject("client_info")
                val appId = clientInfo?.optString("mobilesdk_app_id") ?: FIREBASE_APP_ID

                val apiKeys = client?.optJSONArray("api_key")
                val apiKey = apiKeys?.optJSONObject(0)?.optString("current_key") ?: FIREBASE_API_KEY

                val options = FirebaseOptions.Builder()
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setStorageBucket(storageBucket)
                    .setGcmSenderId(projectNumber)
                    .build()

                FirebaseApp.initializeApp(appContext, options)
                Log.i(TAG, "✓ FirebaseApp explicitly initialized from asset config (Project: $projectId)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Asset-based Firebase initialization failed: ${e.message}")
            }

            // 3. Fallback to hardcoded configuration options
            try {
                val fallbackOptions = FirebaseOptions.Builder()
                    .setApplicationId(FIREBASE_APP_ID)
                    .setApiKey(FIREBASE_API_KEY)
                    .setProjectId(FIREBASE_PROJECT_ID)
                    .setStorageBucket(FIREBASE_STORAGE_BUCKET)
                    .setGcmSenderId(FIREBASE_PROJECT_NUMBER)
                    .build()

                FirebaseApp.initializeApp(appContext, fallbackOptions)
                Log.i(TAG, "✓ FirebaseApp initialized with fallback options")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "All Firebase initialization attempts failed: ${e.message}", e)
                return false
            }
        }

        fun getFirebaseConfigInfo(context: Context): FirebaseConfigInfo {
            return try {
                val jsonString = context.assets.open("google-services.json").bufferedReader().use { it.readText() }
                val root = org.json.JSONObject(jsonString)
                val projectInfo = root.optJSONObject("project_info")
                val projectId = projectInfo?.optString("project_id") ?: FIREBASE_PROJECT_ID
                val clients = root.optJSONArray("client")
                val client = clients?.optJSONObject(0)
                val clientInfo = client?.optJSONObject("client_info")
                val androidClientInfo = clientInfo?.optJSONObject("android_client_info")
                val pkgName = androidClientInfo?.optString("package_name") ?: context.packageName

                val shaList = mutableListOf<String>()
                var foundWebClientId = WEB_CLIENT_ID

                val oauthClients = client?.optJSONArray("oauth_client")
                if (oauthClients != null) {
                    for (i in 0 until oauthClients.length()) {
                        val oauthObj = oauthClients.optJSONObject(i) ?: continue
                        val clientType = oauthObj.optInt("client_type")
                        if (clientType == 3) {
                            val cId = oauthObj.optString("client_id")
                            if (!cId.isNullOrBlank()) foundWebClientId = cId
                        } else if (clientType == 1) {
                            val androidInfo = oauthObj.optJSONObject("android_info")
                            val rawHash = androidInfo?.optString("certificate_hash")
                            if (!rawHash.isNullOrBlank()) {
                                val formatted = if (rawHash.contains(":")) {
                                    rawHash.uppercase()
                                } else {
                                    rawHash.chunked(2).joinToString(":").uppercase()
                                }
                                if (!shaList.contains(formatted)) {
                                    shaList.add(formatted)
                                }
                            }
                        }
                    }
                }
                if (shaList.isEmpty()) {
                    shaList.addAll(REGISTERED_SHA1S)
                }
                FirebaseConfigInfo(projectId, pkgName, foundWebClientId, shaList)
            } catch (e: Exception) {
                Log.w(TAG, "Could not load google-services.json from assets: ${e.message}")
                FirebaseConfigInfo(
                    projectId = FIREBASE_PROJECT_ID,
                    packageName = context.packageName,
                    webClientId = WEB_CLIENT_ID,
                    registeredSha1s = REGISTERED_SHA1S.toList()
                )
            }
        }

        fun getInstalledApkSha1(context: Context): String {
            return try {
                val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val packageInfo = context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                    )
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.GET_SIGNATURES
                    )
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }
                val cert = signatures?.firstOrNull()?.toByteArray()
                if (cert != null) {
                    val md = MessageDigest.getInstance("SHA-1")
                    val digest = md.digest(cert)
                    digest.joinToString(":") { "%02X".format(it) }
                } else {
                    "Unavailable"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private val KEY_USER_ID = stringPreferencesKey("auth_user_id")
    private val KEY_USER_EMAIL = stringPreferencesKey("auth_user_email")
    private val KEY_USER_NAME = stringPreferencesKey("auth_user_name")
    private val KEY_IS_GOOGLE = booleanPreferencesKey("auth_is_google")
    private val KEY_USERS_STORE = stringPreferencesKey("auth_local_users_store")
    private val KEY_APP_PIN = stringPreferencesKey("auth_app_pin")
    private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("auth_app_lock_enabled")
    private val KEY_HIDE_BALANCE = booleanPreferencesKey("auth_hide_balance")
    private val KEY_AUTO_BACKUP = booleanPreferencesKey("auth_auto_backup")
    private val KEY_GOOGLE_WEB_CLIENT_ID = stringPreferencesKey("auth_google_web_client_id")

    val authState: Flow<AuthState> = context.dataStore.data.map { prefs ->
        val userId = prefs[KEY_USER_ID]
        val email = prefs[KEY_USER_EMAIL]
        val name = prefs[KEY_USER_NAME]
        val isGoogle = prefs[KEY_IS_GOOGLE] ?: false
        val pin = prefs[KEY_APP_PIN]
        val isLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false

        if (userId != null && email != null) {
            val user = VeloraUser(
                id = userId,
                email = email,
                displayName = name ?: email.substringBefore("@"),
                isGoogleUser = isGoogle
            )
            AuthState.Authenticated(user = user, isLocked = isLockEnabled && !pin.isNullOrEmpty())
        } else {
            AuthState.Unauthenticated
        }
    }

    val hideBalancePreference: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIDE_BALANCE] ?: false
    }

    suspend fun setHideBalancePreference(hide: Boolean) {
        context.dataStore.edit { it[KEY_HIDE_BALANCE] = hide }
    }

    val isAppLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCK_ENABLED] ?: false
    }

    suspend fun setAppLock(enabled: Boolean, pin: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_LOCK_ENABLED] = enabled
            if (pin != null) {
                prefs[KEY_APP_PIN] = hashPassword(pin)
            } else if (!enabled) {
                prefs.remove(KEY_APP_PIN)
            }
        }
    }

    suspend fun verifyPin(enteredPin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[KEY_APP_PIN] ?: return true
        return storedHash == hashPassword(enteredPin)
    }

    suspend fun register(email: String, password: String, displayName: String): Result<VeloraUser> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        val prefs = context.dataStore.data.first()
        val usersMap = parseUsersMap(prefs[KEY_USERS_STORE])
        if (usersMap.containsKey(cleanEmail)) {
            return Result.failure(IllegalArgumentException("An account with this email already exists. Please log in."))
        }

        val userId = UUID.randomUUID().toString()
        val passwordHash = hashPassword(password)
        val name = displayName.trim().ifEmpty { cleanEmail.substringBefore("@") }
        val newStore = usersMap + (cleanEmail to "$userId|$passwordHash|$name|false")

        context.dataStore.edit { p ->
            p[KEY_USERS_STORE] = serializeUsersMap(newStore)
            p[KEY_USER_ID] = userId
            p[KEY_USER_EMAIL] = cleanEmail
            p[KEY_USER_NAME] = name
            p[KEY_IS_GOOGLE] = false
        }

        return Result.success(VeloraUser(userId, cleanEmail, name, false))
    }

    suspend fun login(email: String, password: String): Result<VeloraUser> {
        val cleanEmail = email.trim().lowercase()
        val prefs = context.dataStore.data.first()
        val usersMap = parseUsersMap(prefs[KEY_USERS_STORE])

        val record = usersMap[cleanEmail] ?: return Result.failure(
            IllegalArgumentException("No account found with this email. Please check your credentials or register.")
        )

        val parts = record.split("|")
        val userId = parts.getOrNull(0) ?: ""
        val storedHash = parts.getOrNull(1) ?: ""
        val name = parts.getOrNull(2) ?: cleanEmail.substringBefore("@")
        val isGoogle = parts.getOrNull(3)?.toBoolean() ?: false

        if (storedHash != hashPassword(password)) {
            return Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
        }

        context.dataStore.edit { p ->
            p[KEY_USER_ID] = userId
            p[KEY_USER_EMAIL] = cleanEmail
            p[KEY_USER_NAME] = name
            p[KEY_IS_GOOGLE] = isGoogle
        }

        return Result.success(VeloraUser(userId, cleanEmail, name, isGoogle))
    }

    suspend fun resolveWebClientId(): String? {
        val prefs = context.dataStore.data.first()
        val customClientId = prefs[KEY_GOOGLE_WEB_CLIENT_ID]
        if (!customClientId.isNullOrBlank()) {
            Log.d(TAG, "Using custom web client ID: $customClientId")
            return customClientId.trim()
        }
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            val resVal = context.getString(resId)
            if (resVal.isNotBlank() && !resVal.startsWith("YOUR_")) {
                Log.d(TAG, "Resolved web client ID from resources: $resVal")
                return resVal.trim()
            }
        }
        val parsedConfig = getFirebaseConfigInfo(context)
        Log.d(TAG, "Using parsed config web client ID: ${parsedConfig.webClientId}")
        return parsedConfig.webClientId
    }

    suspend fun saveWebClientId(clientId: String) {
        context.dataStore.edit { prefs ->
            if (clientId.isBlank()) {
                prefs.remove(KEY_GOOGLE_WEB_CLIENT_ID)
            } else {
                prefs[KEY_GOOGLE_WEB_CLIENT_ID] = clientId.trim()
            }
        }
    }

    fun ensureFirebaseInitialized(): Boolean = Companion.ensureFirebaseInitialized(context)

    suspend fun signInWithGoogleIdToken(idToken: String): Result<VeloraUser> {
        return try {
            val initialized = ensureFirebaseInitialized(context)
            if (!initialized || FirebaseApp.getApps(context).isEmpty()) {
                val err = "Default FirebaseApp is not initialized in this process. Please check Firebase configuration."
                Log.e(TAG, err)
                return Result.failure(IllegalStateException(err))
            }
            val auth = FirebaseAuth.getInstance()
            Log.d(TAG, "Exchanging Google ID token (length=${idToken.length}) with Firebase signInWithCredential...")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).awaitTask()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase user was null after authentication."))

            Log.i(TAG, "Firebase Auth confirmed! UID=${firebaseUser.uid}, Email=${firebaseUser.email}")

            val googleEmail = firebaseUser.email ?: "google-user@velora.finance"
            val googleName = firebaseUser.displayName ?: googleEmail.substringBefore("@")
            val firebaseUid = firebaseUser.uid

            // Check if account with this email was previously registered locally, to preserve existing data association
            val prefs = context.dataStore.data.first()
            val usersMap = parseUsersMap(prefs[KEY_USERS_STORE])
            val existing = usersMap[googleEmail.lowercase()]
            val userId = if (existing != null) {
                existing.split("|").firstOrNull() ?: firebaseUid
            } else {
                firebaseUid
            }

            val newStore = usersMap + (googleEmail.lowercase() to "$userId|FIREBASE_GOOGLE_AUTH|$googleName|true")

            context.dataStore.edit { p ->
                p[KEY_USERS_STORE] = serializeUsersMap(newStore)
                p[KEY_USER_ID] = userId
                p[KEY_USER_EMAIL] = googleEmail.lowercase()
                p[KEY_USER_NAME] = googleName
                p[KEY_IS_GOOGLE] = true
            }

            Log.i(TAG, "Velora user session persisted successfully for $userId ($googleEmail)")
            Result.success(VeloraUser(userId, googleEmail.lowercase(), googleName, true))
        } catch (e: Exception) {
            Log.e(TAG, "signInWithGoogleIdToken failed with exception: ${e.javaClass.name}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun loginWithGoogleAccount(googleEmail: String, googleName: String): Result<VeloraUser> {
        val cleanEmail = googleEmail.trim().lowercase()
        val prefs = context.dataStore.data.first()
        val usersMap = parseUsersMap(prefs[KEY_USERS_STORE])

        val existing = usersMap[cleanEmail]
        val userId = if (existing != null) {
            existing.split("|").firstOrNull() ?: UUID.randomUUID().toString()
        } else {
            UUID.randomUUID().toString()
        }

        val name = googleName.ifEmpty { cleanEmail.substringBefore("@") }
        val newStore = usersMap + (cleanEmail to "$userId|GOOGLE_AUTH|$name|true")

        context.dataStore.edit { p ->
            p[KEY_USERS_STORE] = serializeUsersMap(newStore)
            p[KEY_USER_ID] = userId
            p[KEY_USER_EMAIL] = cleanEmail
            p[KEY_USER_NAME] = name
            p[KEY_IS_GOOGLE] = true
        }

        return Result.success(VeloraUser(userId, cleanEmail, name, true))
    }

    suspend fun resetPassword(email: String, newPassword: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("New password must be at least 6 characters."))
        }
        val prefs = context.dataStore.data.first()
        val usersMap = parseUsersMap(prefs[KEY_USERS_STORE])
        val record = usersMap[cleanEmail] ?: return Result.failure(
            IllegalArgumentException("No account found for this email.")
        )
        val parts = record.split("|")
        val userId = parts.getOrNull(0) ?: ""
        val name = parts.getOrNull(2) ?: ""
        val isGoogle = parts.getOrNull(3) ?: "false"

        val updatedStore = usersMap + (cleanEmail to "$userId|${hashPassword(newPassword)}|$name|$isGoogle")
        context.dataStore.edit { it[KEY_USERS_STORE] = serializeUsersMap(updatedStore) }
        return Result.success(Unit)
    }

    suspend fun logout() {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance().signOut()
            }
        } catch (_: Exception) {
        }
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_IS_GOOGLE)
        }
    }

    suspend fun deleteAccount(): Result<String> {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance().currentUser?.delete()?.awaitTask()
            }
        } catch (_: Exception) {
        }
        val prefs = context.dataStore.data.first()
        val currentUserId = prefs[KEY_USER_ID] ?: return Result.failure(IllegalStateException("No logged in user"))
        val currentEmail = prefs[KEY_USER_EMAIL] ?: ""

        val usersMap = parseUsersMap(prefs[KEY_USERS_STORE])
        val updatedStore = usersMap - currentEmail

        context.dataStore.edit { p ->
            p[KEY_USERS_STORE] = serializeUsersMap(updatedStore)
            p.remove(KEY_USER_ID)
            p.remove(KEY_USER_EMAIL)
            p.remove(KEY_USER_NAME)
            p.remove(KEY_IS_GOOGLE)
            p.remove(KEY_APP_PIN)
            p.remove(KEY_APP_LOCK_ENABLED)
        }
        return Result.success(currentUserId)
    }

    private fun hashPassword(password: String): String {
        val salt = "VELORA_SECURE_SALT_2026"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + password).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseUsersMap(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            val idx = entry.indexOf("=")
            if (idx > 0) {
                entry.substring(0, idx) to entry.substring(idx + 1)
            } else null
        }.toMap()
    }

    private fun serializeUsersMap(map: Map<String, String>): String {
        return map.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}

package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.json.JSONObject

class VeloraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized()
    }

    private fun ensureFirebaseInitialized() {
        try {
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                Log.i(TAG, "✓ FirebaseApp already initialized automatically.")
                return
            }

            // Attempt 1: Standard automatic initialization from Google Services resources
            val autoApp = FirebaseApp.initializeApp(this)
            if (autoApp != null) {
                Log.i(TAG, "✓ FirebaseApp successfully initialized via Google Services plugin resources.")
                return
            }

            Log.w(TAG, "Automatic initialization returned null, falling back to asset config...")
            initFirebaseFromAssets()
        } catch (e: Exception) {
            Log.w(TAG, "Standard Firebase initialization encountered an exception: ${e.message}. Falling back to explicit config...", e)
            try {
                initFirebaseFromAssets()
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Failed to initialize FirebaseApp from asset config", fallbackError)
                initFirebaseFromHardcodedConstants()
            }
        }
    }

    private fun initFirebaseFromAssets() {
        val jsonString = assets.open("google-services.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonString)
        val projectInfo = root.optJSONObject("project_info")
        val projectId = projectInfo?.optString("project_id") ?: DEFAULT_PROJECT_ID
        val projectNumber = projectInfo?.optString("project_number") ?: DEFAULT_PROJECT_NUMBER
        val storageBucket = projectInfo?.optString("storage_bucket") ?: DEFAULT_STORAGE_BUCKET

        val clients = root.optJSONArray("client")
        val client = clients?.optJSONObject(0)
        val clientInfo = client?.optJSONObject("client_info")
        val appId = clientInfo?.optString("mobilesdk_app_id") ?: DEFAULT_APP_ID

        val apiKeys = client?.optJSONArray("api_key")
        val apiKey = apiKeys?.optJSONObject(0)?.optString("current_key") ?: DEFAULT_API_KEY

        val options = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
            .setStorageBucket(storageBucket)
            .setGcmSenderId(projectNumber)
            .build()

        FirebaseApp.initializeApp(this, options)
        Log.i(TAG, "✓ FirebaseApp successfully initialized explicitly (Project ID: $projectId, App ID: $appId)")
    }

    private fun initFirebaseFromHardcodedConstants() {
        try {
            val options = FirebaseOptions.Builder()
                .setApplicationId(DEFAULT_APP_ID)
                .setApiKey(DEFAULT_API_KEY)
                .setProjectId(DEFAULT_PROJECT_ID)
                .setStorageBucket(DEFAULT_STORAGE_BUCKET)
                .setGcmSenderId(DEFAULT_PROJECT_NUMBER)
                .build()

            FirebaseApp.initializeApp(this, options)
            Log.i(TAG, "✓ FirebaseApp successfully initialized from fallback constants (Project ID: $DEFAULT_PROJECT_ID)")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal: Unable to initialize FirebaseApp with fallback constants", e)
        }
    }

    companion object {
        private const val TAG = "VeloraApp"
        private const val DEFAULT_PROJECT_ID = "velora-finance-manager"
        private const val DEFAULT_PROJECT_NUMBER = "675344395387"
        private const val DEFAULT_STORAGE_BUCKET = "velora-finance-manager.firebasestorage.app"
        private const val DEFAULT_APP_ID = "1:675344395387:android:32f8c582f4f2288cc0c6de"
        private const val DEFAULT_API_KEY = "AIzaSyBJl24vuSonX_sDYBdF6cOdqpYh9PeRI8c"
    }
}

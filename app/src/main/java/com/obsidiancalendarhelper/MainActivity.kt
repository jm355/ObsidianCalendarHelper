package com.obsidiancalendarhelper

//todo organize imports
import FileParsingWorker
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.obsidiancalendarhelper.ui.theme.ObsidianCalendarHelperTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "Notification permission granted.")
                // You can now proceed with actions that require this permission
                // For example, re-trigger parsing if it was waiting for this
            } else {
                Log.w("MainActivity", "Notification permission denied.")
                // Inform the user that notifications will not work
            }
        }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // TIRAMISU is API 33
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("MainActivity", "Notification permission already granted.")
                    // You can proceed with scheduling notifications
                }
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    // Show an educational UI to the user explaining why the permission is needed
                    // Then, request the permission
                    Log.i("MainActivity", "Showing rationale for notification permission.")
                    // For simplicity, directly requesting here. In a real app, show a dialog.
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Directly request the permission
                    Log.d("MainActivity", "Requesting notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Permissions are not needed for pre-Tiramisu, notifications are enabled by default
            Log.d("MainActivity", "Notification permission not required for this API level.")
        }
    }


    fun processDirectoryAndScheduleReminders(directoryUri: Uri, context: Context) {
        // Check for notification permission first (for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted, request it or inform the user
                // In a real app, you'd call checkAndRequestNotificationPermission() from an Activity context
                Log.w("ProcessDir", "Notification permission not granted. Requesting it or inform user.")
                // If this function is called from a non-Activity context, you can't directly request.
                // Best to ensure permission is requested from UI before calling this.
                // For now, we'll log and proceed, but notifications might not show on API 33+
            }
        }


        val workManager = WorkManager.getInstance(context.applicationContext)

        val workData = Data.Builder()
            .putString(FileParsingWorker.KEY_DIRECTORY_URI, directoryUri.toString())
            .build()

        // For a one-time scan (e.g., after directory selection)
        val fileParseRequest = OneTimeWorkRequestBuilder<FileParsingWorker>()
            .setInputData(workData)
            .setConstraints(
                Constraints.Builder()
                    // .setRequiredNetworkType(NetworkType.CONNECTED) // If files are remote
                    .build()
            )
            .addTag("file_parse_one_time") // Optional tag for managing the work
            .build()

        workManager.enqueueUniqueWork(
            "uniqueFileParseWork", // Unique name for this work
            ExistingWorkPolicy.REPLACE, // Or APPEND, or KEEP
            fileParseRequest
        )
        Log.d("MainActivity", "Enqueued one-time file parsing work for $directoryUri")


        // To schedule periodic parsing (e.g., every 12 hours)
        // Be mindful of battery usage with frequent periodic work.
        /*
        val periodicFileParseRequest = PeriodicWorkRequestBuilder<FileParsingWorker>(
            12, TimeUnit.HOURS // Repeat interval
        )
            .setInputData(workData) // URI must be persistently stored if used here
            .setConstraints(
                Constraints.Builder()
                    // Add constraints like battery not low, device idle if appropriate
                    .build()
            )
            .addTag("file_parse_periodic")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "uniquePeriodicFileParseWork",
            ExistingPeriodicWorkPolicy.REPLACE, // Or KEEP
            periodicFileParseRequest
        )
        Log.d("MainActivity", "Enqueued periodic file parsing work.")
        */
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestNotificationPermission()

        // --- Load the saved URI ---
        val savedUri = AppPreferences.getSelectedDirectoryUri(applicationContext)
        // --- If a URI was saved, you might want to automatically trigger processing ---
        savedUri?.let {
            Log.d("MainActivity", "Found saved directory: $it. Processing...")
            // Ensure permissions are still valid before processing
            // (ContentResolver.persistedUriPermissions can be checked)
            // For simplicity, we'll assume permissions are valid if URI is present
            // and re-granted by the system. In a production app, you might want more robust checks.
            if (contentResolver.persistedUriPermissions.any { p -> p.uri == it && p.isReadPermission && p.isWritePermission }) {
                processDirectoryAndScheduleReminders(it, applicationContext)
            } else {
                Log.w("MainActivity", "Saved URI $it found, but permissions might have been revoked. Please re-select.")
                // Optionally clear the saved URI if permissions are lost
                AppPreferences.saveSelectedDirectoryUri(applicationContext, null)
            }
        }

        enableEdgeToEdge()
        setContent {
            ObsidianCalendarHelperTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Pass the loaded URI to the screen
                    val currentSavedUri = AppPreferences.getSelectedDirectoryUri(LocalContext.current.applicationContext)
                    DirectorySelectorScreen(
                        modifier = Modifier.padding(innerPadding),
                        initialDirectoryUri = currentSavedUri, // Pass loaded URI
                        onDirectorySelected = { uri ->
                            processDirectoryAndScheduleReminders(uri, applicationContext)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DirectorySelectorScreen(
    modifier: Modifier = Modifier,
    initialDirectoryUri: Uri?, // Add this to receive the loaded URI
    onDirectorySelected: (Uri) -> Unit
) {
    // Use the initialDirectoryUri if provided, otherwise null
    var selectedDirectoryUri by remember(initialDirectoryUri) { mutableStateOf(initialDirectoryUri) }
    val context = LocalContext.current

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    // Persist permission for application context
                    context.applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d("DirSelector", "Persistable URI permission granted for $uri")

                    // --- Save the URI ---
                    AppPreferences.saveSelectedDirectoryUri(context.applicationContext, uri)
                    selectedDirectoryUri = uri // Update local state for immediate UI feedback
                    // --------------------

                    onDirectorySelected(uri)

                } catch (e: SecurityException) {
                    Log.e("DirSelector", "Failed to take persistable URI permission for $uri", e)
                    // Handle error: Inform user they might need to re-select the directory later
                    AppPreferences.saveSelectedDirectoryUri(context.applicationContext, null) // Clear if failed
                    selectedDirectoryUri = null
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            // Optionally, you could pass selectedDirectoryUri to `launch()` if you want
            // the picker to start at the currently selected location (if any).
            // For now, null starts at a default location.
            directoryPickerLauncher.launch(null)
        }) {
            Text(if (selectedDirectoryUri != null) "Change Directory" else "Select Directory")
        }

        selectedDirectoryUri?.let { uri ->
            Text(
                "Selected Directory: ${uri.path}",
                modifier = Modifier.padding(top = 8.dp)
            )
            // You can add a button here to manually trigger parsing for the selected directory
            Button(
                onClick = { onDirectorySelected(uri) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Process Selected Directory")
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DirectorySelectorScreenPreview() {
    ObsidianCalendarHelperTheme {
        DirectorySelectorScreen(
            initialDirectoryUri = null,
            onDirectorySelected = {}
        )
    }
}

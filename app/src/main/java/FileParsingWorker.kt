import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.obsidiancalendarhelper.AppPreferences
import com.obsidiancalendarhelper.NotificationReceiver
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Data class to hold parsed event information (you'll customize this)
data class CalendarEvent(
    val title: String,
    val eventTimeMillis: Long,
    val content: String // For notification content
)

class FileParsingWorker(
    val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DIRECTORY_URI = "key_directory_uri"
        const val NOTIFICATION_CHANNEL_ID = "calendar_event_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Calendar Events"
        private const val TAG = "FileParsingWorker"
    }

    override suspend fun doWork(): Result {
        // Try to get URI from inputData first (for one-time or explicit passing)
        var directoryUriString = inputData.getString(KEY_DIRECTORY_URI)
        var directoryUri: Uri? = directoryUriString?.let { Uri.parse(it) }

        // If not found in inputData (e.g., for a purely periodic worker started without fresh data),
        // try to get it from SharedPreferences.
        if (directoryUri == null) {
            Log.d(TAG, "Directory URI not in inputData, trying SharedPreferences.")
            directoryUri = AppPreferences.getSelectedDirectoryUri(appContext)
        }

        if (directoryUri == null) {
            Log.e(TAG, "Directory URI not found in inputData or SharedPreferences. Cannot proceed.")
            return Result.failure()
        }

        // Check for persisted permissions before proceeding (important for periodic work)
        val hasPermissions = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == directoryUri && it.isReadPermission && it.isWritePermission // Adjust as needed
        }

        if (!hasPermissions) {
            Log.e(TAG, "Permissions for URI $directoryUri seem to have been revoked. Worker cannot access.")
            // Optionally, you could send a notification to the user to re-select the directory
            // or clear the saved URI.
            AppPreferences.saveSelectedDirectoryUri(appContext, null) // Clear problematic URI
            return Result.failure() // Or Result.success() if you don't want it to retry
        }


        Log.d(TAG, "Starting to parse files in directory: $directoryUri")
        createNotificationChannel()

        val documentTree = DocumentFile.fromTreeUri(appContext, directoryUri)
        if (documentTree == null || !documentTree.isDirectory) {
            Log.e(TAG, "Failed to access directory or it's not a directory.")
            return Result.failure()
        }

        documentTree.listFiles().forEach { documentFile ->
            if (documentFile.isFile && documentFile.name?.endsWith(".md") == true) {
                Log.d(TAG, "Processing file: ${documentFile.name}")
                try {
                    applicationContext.contentResolver.openInputStream(documentFile.uri)
                        ?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                val fileContent = reader.readText()
                                // --- Your Parsing Logic Goes Here ---
                                // This is a placeholder. Replace with your actual markdown parsing.
                                val events = parseMarkdownContent(
                                    fileContent,
                                    documentFile.name ?: "Unknown File"
                                )
                                // ------------------------------------
                                events.forEach { event ->
                                    if (event.eventTimeMillis > System.currentTimeMillis()) {
                                        scheduleNotification(event)
                                    } else {
                                        Log.d(
                                            TAG,
                                            "Event '${event.title}' is in the past. Skipping."
                                        )
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing file ${documentFile.name}: ${e.message}", e)
                    // Optionally, report this error or continue with other files
                }
            }
        }
        Log.d(TAG, "File parsing finished.")
        return Result.success()
    }

    // --- Placeholder for your Markdown Parsing Logic ---
    // You need to implement this function based on your markdown file structure
    /*
event file format:
---
title: test event
allDay: false
startTime: 10:33
endTime: 11:33
date: 2025-07-04
completed: null
---

multi-day event file format:
---
title: test multiday even
allDay: false
startTime: 09:00
endTime: 10:00
date: 2025-07-04
endDate: 2025-07-05
completed: null
---

all day event file format:
---
title: test all day event
allDay: true
date: 2025-07-06
completed: null
---

multi-all-day event file format:
---
title: test multi all day event
allDay: true
date: 2025-07-05
endDate: 2025-07-06
completed: null
---

recurring event file format (endless if endRecur is missing)
Days of week:
Sunday - U
Monday - M
Tuesday - T
Wednesday - W
Thursday - R
Friday - F
Saturday - S
---
title: test recurring never ends
allDay: false
startTime: 11:00
endTime: 13:00
type: recurring
daysOfWeek: [U,S,F,R,W,T,M]
startRecur: 2025-07-05
endRecur: 2025-07-20
---


task file format:
---
title: test task
allDay: false
startTime: 10:33
endTime: 11:33
date: 2025-07-04
completed: false
---

recurring tasks don't seem to work
https://github.com/obsidian-community/obsidian-full-calendar/issues/583
https://github.com/obsidian-community/obsidian-full-calendar/issues/486
     */
    private fun parseMarkdownContent(content: String, fileName: String): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        // Example: Look for lines like "EVENT: Buy Groceries @ 2024-07-28 14:30"
        val eventRegex = """EVENT:\s*(.+?)\s*@\s*(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2})""".toRegex()

        content.lines().forEach { line ->
            eventRegex.find(line)?.let { matchResult ->
                val title = matchResult.groupValues[1].trim()
                val dateTimeString = matchResult.groupValues[2].trim()
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val date = sdf.parse(dateTimeString)
                    if (date != null) {
                        events.add(CalendarEvent(title, date.time, "Reminder for: $title"))
                        Log.d(TAG, "Parsed event: $title at $dateTimeString")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse date-time for event: $line", e)
                }
            }
        }
        if (events.isEmpty()) Log.d(TAG, "No events found in $fileName")
        return events
    }
    // --------------------------------------------------

    private fun scheduleNotification(event: CalendarEvent) {
        val alarmManager =
            applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, NotificationReceiver::class.java).apply {
            action = "com.obsidiancalendarhelper.action.SHOW_NOTIFICATION" // Unique action
            putExtra(
                NotificationReceiver.EXTRA_NOTIFICATION_ID,
                event.hashCode()
            ) // Unique ID for the notification
            putExtra(NotificationReceiver.EXTRA_EVENT_TITLE, event.title)
            putExtra(NotificationReceiver.EXTRA_EVENT_CONTENT, event.content)
        }

        // Use a unique request code for each PendingIntent if you have multiple distinct alarms
        // event.hashCode() can be a simple way if event titles and times are reasonably unique
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            event.hashCode(), // Request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ensure we only schedule if the event time is in the future
        if (event.eventTimeMillis > System.currentTimeMillis()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    Log.w(
                        TAG,
                        "Cannot schedule exact alarms. Notification for '${event.title}' might be delayed."
                    )
                    // Consider guiding user to settings or using inexact alarms
                    alarmManager.setAndAllowWhileIdle( // Or setExactAndAllowWhileIdle for more precision if permission granted
                        AlarmManager.RTC_WAKEUP,
                        event.eventTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        event.eventTimeMillis,
                        pendingIntent
                    )
                }
                Log.d(
                    TAG,
                    "Scheduled notification for '${event.title}' at ${
                        SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()
                        ).format(event.eventTimeMillis)
                    }"
                )
            } catch (se: SecurityException) {
                Log.e(
                    TAG,
                    "SecurityException: Cannot schedule exact alarms. Check SCHEDULE_EXACT_ALARM permission.",
                    se
                )
                // Fallback or inform user
            }
        } else {
            Log.d(TAG, "Event '${event.title}' is in the past. Not scheduling.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // Adjust importance as needed
            ).apply {
                description = "Channel for calendar event reminders"
                // Configure other channel properties here (e.g., sound, vibration)
            }
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created.")
        }
    }
}
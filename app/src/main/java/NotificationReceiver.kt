package com.obsidiancalendarhelper // Or your app's package name

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_CONTENT = "extra_event_content"
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast to show notification")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Calendar Event"
        val eventContent = intent.getStringExtra(EXTRA_EVENT_CONTENT) ?: "You have a reminder."

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingActivityIntent = PendingIntent.getActivity(
            context,
            notificationId,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FileParsingWorker.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app's icon
            .setContentTitle(eventTitle)
            .setContentText(eventContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingActivityIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Notification displayed for event: $eventTitle (ID: $notificationId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying notification: ${e.message}", e)
        }
    }
}
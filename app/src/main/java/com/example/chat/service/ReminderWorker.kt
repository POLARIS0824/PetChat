package com.example.chat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.chat.MainActivity
import com.example.chat.R
import com.example.chat.data.dao.ReminderDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderDao: ReminderDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "reminder_channel"
        private const val WORK_TAG_PREFIX = "reminder_"

        fun schedule(context: Context, reminderId: Long, scheduledTimeMillis: Long) {
            val delay = scheduledTimeMillis - System.currentTimeMillis()
            if (delay <= 0) return

            val inputData = androidx.work.Data.Builder()
                .putLong("reminder_id", reminderId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("$WORK_TAG_PREFIX$reminderId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_TAG_PREFIX$reminderId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong("reminder_id", 0)
        if (reminderId == 0L) return Result.failure()

        val reminder = reminderDao.getById(reminderId) ?: return Result.failure()

        if (reminder.isCompleted) return Result.success()

        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, reminderId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(reminder.description)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(reminderId.toInt(), notification)

        reminderDao.markCompleted(reminderId)

        return Result.success()
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.reminder_channel_name)
        val descriptionText = context.getString(R.string.reminder_channel_desc)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

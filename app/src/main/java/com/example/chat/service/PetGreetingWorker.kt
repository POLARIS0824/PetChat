package com.example.chat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.chat.MainActivity
import com.example.chat.data.repository.ChatRepository
import com.example.chat.data.repository.dataStore
import com.example.chat.model.PetType
import com.example.chat.R
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

@HiltWorker
class PetGreetingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ChatRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "pet_greeting_channel"
        private const val NOTIFICATION_ID = 1
        private const val WORK_NAME = "pet_greeting_work"

        private val KEY_HOUR = intPreferencesKey("hour")
        private val KEY_MINUTE = intPreferencesKey("minute")
        private val KEY_PET_TYPE = stringPreferencesKey("pet_type")

        fun saveGreetingTime(context: Context, hourOfDay: Int, minute: Int) {
            runBlocking {
                context.dataStore.edit { prefs ->
                    prefs[KEY_HOUR] = hourOfDay
                    prefs[KEY_MINUTE] = minute
                }
            }
        }

        fun savePetType(context: Context, petType: PetType) {
            runBlocking {
                context.dataStore.edit { prefs ->
                    prefs[KEY_PET_TYPE] = petType.name
                }
            }
        }

        private fun getSavedPetType(context: Context): PetType {
            val name = runBlocking {
                context.dataStore.data.first()[KEY_PET_TYPE]
            }
            return PetType.entries.firstOrNull { it.name == name } ?: PetType.CAT
        }

        fun schedule(context: Context, hourOfDay: Int, minute: Int) {
            saveGreetingTime(context, hourOfDay, minute)

            val workManager = WorkManager.getInstance(context)
            val currentTime = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = currentTime
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
            }

            if (calendar.timeInMillis <= currentTime) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            val initialDelay = calendar.timeInMillis - currentTime
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PetGreetingWorker>(
                24, TimeUnit.HOURS
            ).setInitialDelay(
                initialDelay, TimeUnit.MILLISECONDS
            ).setConstraints(constraints).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val petType = getSavedPetType(context)
            val greeting = try {
                repository.getPetResponse(
                    petType,
                    "生成一句简短的问候语，表达对主人的思念或关心"
                )
            } catch (e: Exception) {
                context.getString(R.string.notification_fallback)
            }

            createNotificationChannel()

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(greeting)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.notification_channel_name)
        val descriptionText = context.getString(R.string.notification_channel_desc)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

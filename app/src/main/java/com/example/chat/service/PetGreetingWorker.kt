package com.example.chat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.*
import com.example.chat.MainActivity
import com.example.chat.PetChatRepository
import com.example.chat.data.ChatDatabase
import com.example.chat.model.PetTypes
import java.util.concurrent.TimeUnit

/**
 * 宠物问候的工作管理器
 * 负责定时发送宠物问候消息
 */
class PetGreetingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "pet_greeting_channel"
        private const val NOTIFICATION_ID = 1
        private const val WORK_NAME = "pet_greeting_work"

        /**
         * 设置定时问候任务
         * @param context 上下文
         * @param hourOfDay 每天问候的小时（24小时制）
         * @param minute 每天问候的分钟
         */
        fun saveGreetingTime(context: Context, hourOfDay: Int, minute: Int) {
            context.getSharedPreferences("pet_greeting", Context.MODE_PRIVATE).edit {
                putInt("hour", hourOfDay)
                putInt("minute", minute)
            }
        }

        fun schedule(context: Context, hourOfDay: Int, minute: Int) {
            // 保存配置
            saveGreetingTime(context, hourOfDay, minute)
            
            val workManager = WorkManager.getInstance(context)
            
            // 计算初始延迟
            val currentTime = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = currentTime
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
            }
            
            // 如果今天的时间已经过了，设置为明天
            if (calendar.timeInMillis <= currentTime) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            
            val initialDelay = calendar.timeInMillis - currentTime

            // 创建周期性工作请求
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // 需要网络连接
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PetGreetingWorker>(
                24, TimeUnit.HOURS // 每24小时执行一次
            ).setInitialDelay(
                initialDelay, TimeUnit.MILLISECONDS
            ).setConstraints(constraints)  // 添加约束
            .build()

            // 确保只有一个问候任务在运行
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        try {
            val repository = PetChatRepository.getInstance(
                ChatDatabase.getDatabase(context).chatDao()
            )
            
            // 建议添加错误处理和日志记录
            val greeting = try {
                repository.getPetResponse(
                    PetTypes.CAT,
                    "生成一句简短的问候语，表达对主人的思念或关心"
                )
            } catch (e: Exception) {
                // 如果AI调用失败，使用默认问候语
                "喵~ 想你了，主人！"
            }

            // 创建通知渠道（Android 8.0及以上需要）
            createNotificationChannel()

            // 在 doWork() 中添加通知点击行为
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            // 发送通知
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("来自宠物的问候")
                .setContentText(greeting)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)  // 添加点击行为
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            return Result.success()
        } catch (e: Exception) {
            // 建议添加日志记录
            e.printStackTrace()
            return Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "宠物问候"
            val descriptionText = "来自宠物的每日问候"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
} 
package com.example.chat.data.tools

import android.content.Context
import com.example.chat.data.dao.ReminderDao
import com.example.chat.data.entity.ReminderEntity
import com.example.chat.service.ReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderTool @Inject constructor(
    private val reminderDao: ReminderDao,
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "set_reminder"
    override val displayName = "设置提醒"
    override val description = "为主人设置定时提醒。支持相对时间(如5分钟后)和绝对时间(如下午3点)"

    override val parametersJson = """
        {
            "type": "object",
            "properties": {
                "description": {
                    "type": "string",
                    "description": "提醒的内容描述"
                },
                "delay_minutes": {
                    "type": "integer",
                    "description": "多少分钟后提醒，设置此字段时不需要设置time"
                },
                "time": {
                    "type": "string",
                    "description": "ISO8601格式的绝对时间，如2026-05-26T15:00:00"
                }
            },
            "required": ["description"]
        }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): ToolResult {
        return try {
            val args = json.decodeFromString<ReminderArgs>(arguments)
            val description = args.description ?: return ToolResult(false, "提醒内容不能为空", "提醒内容为空")

            val scheduledTime = when {
                args.delay_minutes != null && args.delay_minutes > 0 -> {
                    System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(args.delay_minutes.toLong())
                }
                !args.time.isNullOrBlank() -> {
                    try {
                        parseTime(args.time)
                    } catch (e: Exception) {
                        return ToolResult(false, "时间格式无效: ${args.time}", "时间格式无效")
                    }
                }
                else -> return ToolResult(false, "需要指定delay_minutes或time", "缺少时间参数")
            }

            if (scheduledTime <= System.currentTimeMillis()) {
                return ToolResult(false, "提醒时间不能是过去的时间", "提醒时间无效")
            }

            val reminder = ReminderEntity(
                description = description,
                scheduledTimeMillis = scheduledTime
            )
            reminderDao.insert(reminder)

            ReminderWorker.schedule(context, reminder.id, scheduledTime)

            val minutesUntil = TimeUnit.MILLISECONDS.toMinutes(scheduledTime - System.currentTimeMillis())
            ToolResult(
                success = true,
                content = "已设置提醒: $description，约${minutesUntil}分钟后通知",
                displayMessage = "已设置提醒"
            )
        } catch (e: Exception) {
            ToolResult(false, "设置提醒失败: ${e.message}", "提醒设置失败")
        }
    }

    private fun parseTime(timeStr: String): Long {
        return try {
            // Try with timezone suffix first
            java.time.Instant.parse(timeStr).toEpochMilli()
        } catch (e: Exception) {
            try {
                // Try with 'Z' appended
                java.time.Instant.parse("${timeStr}Z").toEpochMilli()
            } catch (e2: Exception) {
                // Treat as local datetime in system default zone
                val localDt = java.time.LocalDateTime.parse(timeStr)
                val zonedDt = localDt.atZone(java.time.ZoneId.systemDefault())
                zonedDt.toInstant().toEpochMilli()
            }
        }
    }

    @Serializable
    private data class ReminderArgs(
        val description: String? = null,
        val delay_minutes: Int? = null,
        val time: String? = null
    )
}

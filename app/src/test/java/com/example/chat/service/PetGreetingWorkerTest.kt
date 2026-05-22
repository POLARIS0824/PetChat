package com.example.chat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.chat.R
import com.example.chat.data.repository.ChatRepository
import com.example.chat.data.repository.dataStore
import com.example.chat.model.PetType
import com.example.chat.ui.notes.MainDispatcherRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class PetGreetingWorkerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val context: Context = mock()
    private val chatRepository: ChatRepository = mock()
    private val notificationManager: NotificationManager = mock()
    private val workerParams: WorkerParameters = mock()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var mockedLog: MockedStatic<Log>
    private lateinit var mockedPendingIntent: MockedStatic<PendingIntent>
    private lateinit var mockedDataStoreExtensions: MockedStatic<*>
    private lateinit var worker: PetGreetingWorker

    private val KEY_HOUR = intPreferencesKey("hour")
    private val KEY_MINUTE = intPreferencesKey("minute")
    private val KEY_PET_TYPE = stringPreferencesKey("pet_type")

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_greeting.preferences_pb") }
        )

        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.e(any(), any(), any()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(any(), any()) }.thenReturn(0)

        mockedPendingIntent = Mockito.mockStatic(PendingIntent::class.java)
        val mockPendingIntent = mock<PendingIntent>()
        mockedPendingIntent.`when`<PendingIntent> {
            PendingIntent.getActivity(any(), anyInt(), any(), anyInt())
        }.thenReturn(mockPendingIntent)

        // Mock the extension property Context.dataStore
        @Suppress("UNCHECKED_CAST")
        val extensionsClass = Class.forName("com.example.chat.data.repository.DataStoreExtensionsKt") as Class<Any>
        mockedDataStoreExtensions = Mockito.mockStatic(extensionsClass)
        mockedDataStoreExtensions.`when`<DataStore<Preferences>> {
            context.dataStore
        }.thenReturn(dataStore)

        // Mock notification manager and strings
        whenever(context.getSystemService(eq(Context.NOTIFICATION_SERVICE)))
            .thenReturn(notificationManager)
        whenever(context.packageName).thenReturn("com.example.chat")
        whenever(context.getString(eq(R.string.notification_fallback))).thenReturn("兜底问候语")
        whenever(context.getString(eq(R.string.notification_title))).thenReturn("宠物思念")
        whenever(context.getString(eq(R.string.notification_channel_name))).thenReturn("每日问候")
        whenever(context.getString(eq(R.string.notification_channel_desc))).thenReturn("每日定时的宠物问候语")

        worker = PetGreetingWorker(context, workerParams, chatRepository)
    }

    @After
    fun tearDown() {
        mockedLog.close()
        mockedPendingIntent.close()
        mockedDataStoreExtensions.close()
    }

    @Test
    fun testSaveGreetingTime() = runTest {
        PetGreetingWorker.saveGreetingTime(context, 8, 30)

        val prefs = dataStore.data.first()
        assertEquals(8, prefs[KEY_HOUR])
        assertEquals(30, prefs[KEY_MINUTE])
    }

    @Test
    fun testSavePetType() = runTest {
        PetGreetingWorker.savePetType(context, PetType.SHIBA)

        val prefs = dataStore.data.first()
        assertEquals("SHIBA", prefs[KEY_PET_TYPE])
    }

    @Test
    fun testDoWork_successWithGreeting() = runTest {
        // 模拟已保存宠物类型为 DOG
        dataStore.edit { prefs ->
            prefs[KEY_PET_TYPE] = "DOG"
        }

        // 模拟大模型成功返回宠物问候语
        val expectedGreeting = "主人，大白今天也很想你汪！"
        whenever(chatRepository.getPetResponse(eq(PetType.DOG), anyString()))
            .thenReturn(expectedGreeting)

        // Mock 构造 Intent 以免在非 Android 环境下抛出 setFlags 异常
        Mockito.mockConstruction(Intent::class.java) { mockIntent, _ ->
            whenever(mockIntent.setFlags(anyInt())).thenReturn(mockIntent)
        }.use { _ ->
            Mockito.mockConstruction(NotificationChannel::class.java).use { _ ->
                Mockito.mockConstruction(NotificationCompat.Builder::class.java) { mockBuilder, _ ->
                    whenever(mockBuilder.setSmallIcon(anyInt())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setContentTitle(any())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setContentText(any())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setPriority(anyInt())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setAutoCancel(any())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setContentIntent(any())).thenReturn(mockBuilder)
                    val mockNotification = mock<Notification>()
                    whenever(mockBuilder.build()).thenReturn(mockNotification)
                }.use { _ ->
                    val result = worker.doWork()

                    assertEquals(ListenableWorker.Result.success(), result)

                    // 验证读取了 DOG 的回复
                    verify(chatRepository).getPetResponse(eq(PetType.DOG), anyString())

                    // 验证创建了通知渠道并且发送了通知
                    verify(notificationManager).createNotificationChannel(any())
                    verify(notificationManager).notify(eq(1), any())
                }
            }
        }
    }

    @Test
    fun testDoWork_fallbackOnError() = runTest {
        // 模拟已保存宠物类型为 CAT，并且大模型调用发生异常
        dataStore.edit { prefs ->
            prefs[KEY_PET_TYPE] = "CAT"
        }
        whenever(chatRepository.getPetResponse(eq(PetType.CAT), anyString()))
            .thenThrow(RuntimeException("大模型不可用"))

        // Mock 构造 Intent 以免在非 Android 环境下抛出 setFlags 异常
        Mockito.mockConstruction(Intent::class.java) { mockIntent, _ ->
            whenever(mockIntent.setFlags(anyInt())).thenReturn(mockIntent)
        }.use { _ ->
            Mockito.mockConstruction(NotificationChannel::class.java).use { _ ->
                Mockito.mockConstruction(NotificationCompat.Builder::class.java) { mockBuilder, _ ->
                    whenever(mockBuilder.setSmallIcon(anyInt())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setContentTitle(any())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setContentText(any())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setPriority(anyInt())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setAutoCancel(any())).thenReturn(mockBuilder)
                    whenever(mockBuilder.setContentIntent(any())).thenReturn(mockBuilder)
                    val mockNotification = mock<Notification>()
                    whenever(mockBuilder.build()).thenReturn(mockNotification)
                }.use { _ ->
                    val result = worker.doWork()

                    // 即使调用大模型失败，也会使用兜底消息成功发出通知，不会直接抛出崩溃
                    assertEquals(ListenableWorker.Result.success(), result)

                    // 验证使用了兜底问候语
                    verify(context).getString(eq(R.string.notification_fallback))
                    
                    // 验证创建渠道与发出通知
                    verify(notificationManager).createNotificationChannel(any())
                    verify(notificationManager).notify(eq(1), any())
                }
            }
        }
    }
}

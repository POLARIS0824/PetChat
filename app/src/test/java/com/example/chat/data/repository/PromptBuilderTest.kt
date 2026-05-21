package com.example.chat.data.repository

import com.example.chat.data.dao.AnalysisDao
import com.example.chat.data.entity.ChatAnalysisEntity
import com.example.chat.model.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    // 实现一个简单的 FakeAnalysisDao
    private class FakeAnalysisDao : AnalysisDao {
        private val db = mutableListOf<ChatAnalysisEntity>()

        override suspend fun getLatestAnalysis(petType: String): ChatAnalysisEntity? {
            return db.filter { it.petType == petType }
                .maxByOrNull { it.timestamp }
        }

        override suspend fun insert(analysis: ChatAnalysisEntity) {
            db.add(analysis)
        }

        override fun insertBlocking(analysis: ChatAnalysisEntity) {
            db.add(analysis)
        }
    }

    @Test
    fun testBuild_noAnalysisAvailable() = runBlocking {
        val fakeDao = FakeAnalysisDao()
        val promptBuilder = PromptBuilder(fakeDao)

        // 当数据库中没有该宠物的用户画像时，应直接返回对应的 basePrompt
        val petType = PetType.CAT
        val prompt = promptBuilder.build(petType)

        val expectedBasePrompt = PromptConfig.prompts[petType] ?: ""
        assertEquals(expectedBasePrompt, prompt)
    }

    @Test
    fun testBuild_withAnalysisAvailable() = runBlocking {
        val fakeDao = FakeAnalysisDao()
        val petType = PetType.CAT
        
        // 预插入一条画像数据
        val analysis = ChatAnalysisEntity(
            petType = petType.name,
            summary = "用户喜欢调侃傲娇属性",
            preferences = "[\"喜欢鱼干\", \"讨厌被冷落\"]",
            patterns = "[\"经常主动发起对话\"]",
            timestamp = System.currentTimeMillis()
        )
        fakeDao.insert(analysis)

        val promptBuilder = PromptBuilder(fakeDao)
        val prompt = promptBuilder.build(petType)

        // 验证生成的 Prompt 是否包含基础 Prompt 以及用户画像的各项细节
        val expectedBasePrompt = PromptConfig.prompts[petType] ?: ""
        assertTrue(prompt.contains(expectedBasePrompt))
        assertTrue(prompt.contains("用户画像信息："))
        assertTrue(prompt.contains("总体分析：用户喜欢调侃傲娇属性"))
        assertTrue(prompt.contains("用户偏好：[\"喜欢鱼干\", \"讨厌被冷落\"]"))
        assertTrue(prompt.contains("互动模式：[\"经常主动发起对话\"]"))
        assertTrue(prompt.contains("请根据以上用户画像信息，调整你的回复风格和内容。"))
    }

    @Test
    fun testBuild_multipleAnalysesReturnsLatest() = runBlocking {
        val fakeDao = FakeAnalysisDao()
        val petType = PetType.DOG

        // 插入一条旧的画像
        val oldAnalysis = ChatAnalysisEntity(
            petType = petType.name,
            summary = "旧的分析",
            preferences = "旧偏好",
            patterns = "旧模式",
            timestamp = 1000L
        )
        fakeDao.insert(oldAnalysis)

        // 插入一条新的画像
        val newAnalysis = ChatAnalysisEntity(
            petType = petType.name,
            summary = "最新的分析内容",
            preferences = "最新偏好",
            patterns = "最新模式",
            timestamp = 2000L
        )
        fakeDao.insert(newAnalysis)

        val promptBuilder = PromptBuilder(fakeDao)
        val prompt = promptBuilder.build(petType)

        // 应该返回最新的分析内容而非旧的
        assertTrue(prompt.contains("总体分析：最新的分析内容"))
        assertFalse(prompt.contains("总体分析：旧的分析"))
    }
}

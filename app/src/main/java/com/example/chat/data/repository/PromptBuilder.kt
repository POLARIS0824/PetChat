package com.example.chat.data.repository

import com.example.chat.data.dao.AnalysisDao
import com.example.chat.model.PetTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptBuilder @Inject constructor(
    private val analysisDao: AnalysisDao,
) {
    suspend fun build(petType: PetTypes): String {
        val basePrompt = PromptConfig.prompts[petType] ?: ""
        val analysis = analysisDao.getLatestAnalysis(petType.name)
        return if (analysis != null) {
            """
            $basePrompt

            用户画像信息：
            总体分析：${analysis.summary}
            用户偏好：${analysis.preferences}
            互动模式：${analysis.patterns}

            请根据以上用户画像信息，调整你的回复风格和内容。
            """.trimIndent()
        } else {
            basePrompt
        }
    }
}

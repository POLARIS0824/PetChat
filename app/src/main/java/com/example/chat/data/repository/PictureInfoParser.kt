package com.example.chat.data.repository

import com.example.chat.model.PictureInfo
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PictureInfoParser @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    @Volatile
    private var lastPictureInfo: PictureInfo? = null

    fun consumeLastPictureInfo(): PictureInfo? = synchronized(lock) {
        val info = lastPictureInfo
        lastPictureInfo = null
        return info
    }

    internal fun setLastPictureInfo(info: PictureInfo?) {
        lastPictureInfo = info
    }

    fun extract(response: String): Pair<String, PictureInfo> {
        val systemNoteStart = response.indexOf("<system_note>")
        val systemNoteEnd = response.indexOf("</system_note>")

        return if (systemNoteStart != -1 && systemNoteEnd != -1) {
            val cleanResponse = response.substring(0, systemNoteStart).trim()
            val jsonStr = response.substring(systemNoteStart + 13, systemNoteEnd)
            try {
                Pair(cleanResponse, json.decodeFromString<PictureInfo>(jsonStr))
            } catch (e: Exception) {
                Pair(cleanResponse, PictureInfo(false, ""))
            }
        } else {
            Pair(response, PictureInfo(false, ""))
        }
    }
}

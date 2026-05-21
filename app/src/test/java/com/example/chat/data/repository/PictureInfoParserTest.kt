package com.example.chat.data.repository

import com.example.chat.model.PictureInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PictureInfoParserTest {

    private lateinit var parser: PictureInfoParser

    @Before
    fun setUp() {
        parser = PictureInfoParser()
    }

    @Test
    fun testExtract_happyPath() {
        val input = "这是一个非常可爱的猫咪！<system_note>{\"isPictureNeeded\":true,\"pictureDescription\":\"一只橘猫躺在沙发上，睡得很香\"}</system_note>"
        val (cleanResponse, pictureInfo) = parser.extract(input)

        assertEquals("这是一个非常可爱的猫咪！", cleanResponse)
        assertTrue(pictureInfo.isPictureNeeded)
        assertEquals("一只橘猫躺在沙发上，睡得很香", pictureInfo.pictureDescription)
    }

    @Test
    fun testExtract_noPictureNeeded() {
        val input = "今天天气真好喵~<system_note>{\"isPictureNeeded\":false,\"pictureDescription\":\"\"}</system_note>"
        val (cleanResponse, pictureInfo) = parser.extract(input)

        assertEquals("今天天气真好喵~", cleanResponse)
        assertFalse(pictureInfo.isPictureNeeded)
        assertEquals("", pictureInfo.pictureDescription)
    }

    @Test
    fun testExtract_noSystemNote() {
        val input = "单纯的文本回复，没有任何标签。"
        val (cleanResponse, pictureInfo) = parser.extract(input)

        assertEquals("单纯的文本回复，没有任何标签。", cleanResponse)
        assertFalse(pictureInfo.isPictureNeeded)
        assertEquals("", pictureInfo.pictureDescription)
    }

    @Test
    fun testExtract_malformedJson() {
        val input = "测试异常情况<system_note>{\"isPictureNeeded\":true, \"pictureDescription\"</system_note>"
        val (cleanResponse, pictureInfo) = parser.extract(input)

        // 依然能提取出纯文本，但 JSON 解析失败返回默认值
        assertEquals("测试异常情况", cleanResponse)
        assertFalse(pictureInfo.isPictureNeeded)
        assertEquals("", pictureInfo.pictureDescription)
    }

    @Test
    fun testConsumeLastPictureInfo() {
        val info = PictureInfo(isPictureNeeded = true, pictureDescription = "柴犬傻笑")
        
        parser.setLastPictureInfo(info)
        
        // 第一次消费，应该能拿到设置的 info
        val consumed = parser.consumeLastPictureInfo()
        assertNotNull(consumed)
        assertTrue(consumed!!.isPictureNeeded)
        assertEquals("柴犬傻笑", consumed.pictureDescription)

        // 第二次消费，应该为空（因为已经被消费并置空了）
        val consumedAgain = parser.consumeLastPictureInfo()
        assertNull(consumedAgain)
    }
}

package com.example.chat.data

import com.example.chat.model.PetType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun testFromPetType() {
        assertEquals("CAT", converters.fromPetType(PetType.CAT))
        assertEquals("DOG", converters.fromPetType(PetType.DOG))
        assertEquals("HAMSTER", converters.fromPetType(PetType.HAMSTER))
        assertEquals("SHIBA", converters.fromPetType(PetType.SHIBA))
    }

    @Test
    fun testToPetType_validValues() {
        assertEquals(PetType.CAT, converters.toPetType("CAT"))
        assertEquals(PetType.DOG, converters.toPetType("DOG"))
        assertEquals(PetType.HAMSTER, converters.toPetType("HAMSTER"))
        assertEquals(PetType.SHIBA, converters.toPetType("SHIBA"))
    }

    @Test
    fun testToPetType_invalidValueReturnsDefault() {
        // 当传入无效的字符串时，应默认返回 CAT
        assertEquals(PetType.CAT, converters.toPetType("UNKNOWN_PET"))
        assertEquals(PetType.CAT, converters.toPetType(""))
    }
}

package com.example.chat.data

import androidx.room.TypeConverter
import com.example.chat.model.PetType
import com.example.chat.model.petTypeFromStorage

class Converters {
    @TypeConverter
    fun fromPetType(value: PetType): String = value.name

    @TypeConverter
    fun toPetType(value: String): PetType = petTypeFromStorage(value)
}

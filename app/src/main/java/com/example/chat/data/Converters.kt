package com.example.chat.data

import androidx.room.TypeConverter
import com.example.chat.model.PetType

class Converters {
    @TypeConverter
    fun fromPetType(value: PetType): String = value.name

    @TypeConverter
    fun toPetType(value: String): PetType =
        PetType.entries.firstOrNull { it.name == value } ?: PetType.CAT
}

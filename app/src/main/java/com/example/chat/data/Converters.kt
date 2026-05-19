package com.example.chat.data

import androidx.room.TypeConverter
import com.example.chat.model.PetTypes

class Converters {
    @TypeConverter
    fun fromPetTypes(value: PetTypes): String = value.name

    @TypeConverter
    fun toPetTypes(value: String): PetTypes =
        PetTypes.entries.firstOrNull { it.name == value } ?: PetTypes.CAT
}

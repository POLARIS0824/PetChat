package com.example.chat.ui.cards

import androidx.lifecycle.ViewModel
import com.example.chat.R
import com.example.chat.model.Pet
import com.example.chat.model.PetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CardsViewModel @Inject constructor() : ViewModel() {
    private val _pets = MutableStateFlow<List<Pet>>(emptyList())
    val pets: StateFlow<List<Pet>> = _pets.asStateFlow()

    init {
        loadSamplePets()
    }

    private fun loadSamplePets() {
        _pets.value = listOf(
            Pet(
                name = "布丁",
                status = "懒洋洋地趴着，享受阳光中",
                imageRes = R.drawable.card_cat,
                initialRes = R.drawable.card_cat_inital,
                finalRes = R.drawable.card_cat_final,
                breed = "英短",
                age = "2岁",
                gender = "母",
                weight = "4kg",
                character = "慵懒，爱睡觉，吃货",
                hobby = "日光浴，吃鱼",
                petType = PetType.CAT
            ),
            Pet(
                name = "大白",
                status = "今天状态很好活力满满",
                imageRes = R.drawable.card_dog,
                initialRes = R.drawable.card_dog_inital,
                finalRes = R.drawable.card_dog_final,
                breed = "萨摩耶",
                age = "1岁",
                gender = "公",
                weight = "28kg",
                character = "活泼，粘人，爱笑",
                hobby = "追球，吃骨头",
                petType = PetType.DOG
            )
        )
    }

    fun addPet(pet: Pet) {
        _pets.update { it + pet }
    }

    fun removePet(pet: Pet) {
        _pets.update { list -> list.filter { it != pet } }
    }

    fun updatePet(oldPet: Pet, newPet: Pet) {
        _pets.update { list ->
            list.map { if (it == oldPet) newPet else it }
        }
    }
}

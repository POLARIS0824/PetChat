package com.example.chat.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.R
import com.example.chat.model.Pet
import com.example.chat.model.PetTypes
import kotlinx.coroutines.launch

class CardsViewModel : ViewModel() {
    private val _pets = mutableStateListOf<Pet>()
    val pets: List<Pet> = _pets

    init {
        loadSamplePets()
    }

    private fun loadSamplePets() {
        viewModelScope.launch {
            _pets.clear()
            _pets.addAll(
                listOf(
                    Pet(
                        name = "布丁",
                        status = "懒洋洋地趴着，享受阳光中",
                        imageRes = R.drawable.card_cat,
                        initalRes = R.drawable.card_cat_inital,
                        finalRes = R.drawable.card_cat_final,
                        breed = "英短",
                        age = "2岁",
                        gender = "母",
                        character = "慵懒，爱睡觉，吃货",
                        hobby = "日光浴，吃鱼",
                        petType = PetTypes.CAT
                    ),
                    Pet(
                        name = "大白",
                        status = "今天状态很好活力满满",
                        imageRes = R.drawable.card_dog,
                        initalRes = R.drawable.card_dog_inital,
                        finalRes = R.drawable.card_dog_final,
                        breed = "萨摩耶",
                        age = "1岁",
                        gender = "公",
                        character = "活泼，粘人，爱笑",
                        hobby = "追球，吃骨头",
                        petType = PetTypes.DOG
                    )
                )
            )
        }
    }
    
    // 添加新宠物
    fun addPet(pet: Pet) {
        _pets.add(pet)
    }
    
    // 删除宠物
    fun removePet(pet: Pet) {
        _pets.remove(pet)
    }
    
    // 更新宠物信息
    fun updatePet(oldPet: Pet, newPet: Pet) {
        val index = _pets.indexOf(oldPet)
        if (index != -1) {
            _pets[index] = newPet
        }
    }
}

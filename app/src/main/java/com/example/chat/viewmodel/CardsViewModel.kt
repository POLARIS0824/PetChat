package com.example.chat.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.R
import com.example.chat.model.Pet
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
                        name = "咪咪",
                        status = "可爱的猫咪",
                        imageRes = R.drawable.card_cat,
                        initalRes = R.drawable.card_cat_inital,
                        finalRes = R.drawable.card_cat_final,
                        breed = "英短",
                        age = "2岁",
                        gender = "母"
                    ),
                    Pet(
                        name = "旺财", 
                        status = "活泼的狗狗",
                        imageRes = R.drawable.card_dog,
                        initalRes = R.drawable.card_dog_inital,
                        finalRes = R.drawable.card_dog_final,
                        breed = "柴犬",
                        age = "1岁",
                        gender = "公"
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

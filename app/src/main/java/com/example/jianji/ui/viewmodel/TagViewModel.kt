package com.example.jianji.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 标签管理 ViewModel：标签 CRUD、标签-交易映射。
 */
class TagViewModel(
    private val tagRepo: TagRepository
) : ViewModel() {

    val tags: StateFlow<List<Tag>> = tagRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionTagMap: StateFlow<Map<Long, List<Tag>>> =
        tagRepo.observeAllCrossRefs().combine(tags) { cross, tagList ->
            val tagById = tagList.associateBy { it.id }
            cross.groupBy({ it.transactionId }, { tagById[it.tagId] })
                .mapValues { it.value.filterNotNull() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addTag(name: String, color: String, icon: String) {
        viewModelScope.launch { tagRepo.insert(Tag(name = name, color = color, icon = icon)) }
    }

    fun updateTag(tag: Tag) {
        viewModelScope.launch { tagRepo.update(tag) }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch { tagRepo.delete(tag) }
    }
}
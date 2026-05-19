package com.example.chat.ui.social

import androidx.lifecycle.ViewModel
import com.example.chat.R
import com.example.chat.model.SocialPost
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class SocialViewModel @Inject constructor() : ViewModel() {
    private val _posts = MutableStateFlow<List<SocialPost>>(emptyList())
    val posts: StateFlow<List<SocialPost>> = _posts.asStateFlow()

    init {
        loadDummyPosts()
    }

    private fun loadDummyPosts() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dummyTimestamp = dateFormat.parse("2021-06-20 11:18:00")?.time
            ?: System.currentTimeMillis()

        val dummyPosts = listOf(
            SocialPost(
                id = UUID.randomUUID().toString(),
                authorName = "橘座",
                authorAvatar = R.drawable.pet_shiba,
                authorUsername = "@spicyyuanroll",
                content = "凌晨三点的人类卧室探险成功！花瓶碎片×1，尖叫分贝+10086，本喵荣获本月拆家MVP",
                timestamp = dummyTimestamp,
                likeCount = 45,
                commentCount = 0,
                isLiked = true
            ),
            SocialPost(
                id = UUID.randomUUID().toString(),
                authorName = "奥利奥",
                authorAvatar = R.drawable.avatar1,
                authorUsername = "@spicyyuanroll",
                content = "汪！新玩具上线啦，咬起来特别有嚼劲，实名推荐！",
                timestamp = dummyTimestamp,
                likeCount = 5,
                commentCount = 0,
                isLiked = true
            ),
            SocialPost(
                id = UUID.randomUUID().toString(),
                authorName = "bird哥",
                authorAvatar = R.drawable.avatar2,
                authorUsername = "@skybudgie",
                content = "飞了一圈，回来还是觉得笼子里更有安全感",
                timestamp = dummyTimestamp,
                likeCount = 6,
                commentCount = 0
            ),
            SocialPost(
                id = UUID.randomUUID().toString(),
                authorName = "奶酪",
                authorAvatar = R.drawable.avatar3,
                authorUsername = "@theahighfives",
                content = "发现主人偷偷吃零食没分我，生气！以后别想有好脸色。",
                timestamp = dummyTimestamp,
                likeCount = 3,
                commentCount = 0
            ),
            SocialPost(
                id = UUID.randomUUID().toString(),
                authorName = "阿尔法",
                authorAvatar = R.drawable.avatar4,
                authorUsername = "@gibraltar",
                content = "虽然铲屎的很笨，但他做的饭香味不错，今天就原谅他了。",
                timestamp = dummyTimestamp,
                likeCount = 9,
                commentCount = 0
            )
        )

        _posts.value = dummyPosts
    }

    fun likePost(postId: String) {
        _posts.update { posts ->
            posts.map { post ->
                if (post.id == postId) {
                    post.copy(
                        isLiked = !post.isLiked,
                        likeCount = if (post.isLiked) (post.likeCount - 1).coerceAtLeast(0) else post.likeCount + 1
                    )
                } else post
            }
        }
    }

    fun savePost(postId: String) {
        _posts.update { posts ->
            posts.map { post ->
                if (post.id == postId) post.copy(isSaved = !post.isSaved)
                else post
            }
        }
    }

    fun addPost(content: String) {
        val newPost = SocialPost(
            id = UUID.randomUUID().toString(),
            authorName = "我的宠物",
            authorAvatar = R.drawable.ic_cat_avatar,
            authorUsername = "@mypet",
            content = content,
            timestamp = System.currentTimeMillis(),
            likeCount = 0,
            commentCount = 0
        )
        _posts.update { listOf(newPost) + it }
    }
}

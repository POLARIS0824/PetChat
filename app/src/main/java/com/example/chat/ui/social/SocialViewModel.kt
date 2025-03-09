package com.example.chat.ui.social

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.example.chat.R
import com.example.chat.model.SocialPost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SocialViewModel : ViewModel() {
    private val _posts = mutableStateListOf<SocialPost>()
    val posts: List<SocialPost> get() = _posts

    init {
        // 加载模拟数据
        loadDummyPosts()
    }

    private fun loadDummyPosts() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val dummyPosts = listOf(
            SocialPost(
                id = UUID.randomUUID().toString(),
                authorName = "橘座",
                authorAvatar = R.drawable.avatar1,
                authorUsername = "@spicyyuanroll",
                content = "凌晨三点的人类卧室探险成功！花瓶碎片×1，尖叫分贝+10086，本喵荣获本月拆家MVP",
                timestamp = dateFormat.parse("2021-06-20 11:18:00") ?: Date(),
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
                timestamp = dateFormat.parse("2021-06-20 11:18:00") ?: Date(),
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
                timestamp = dateFormat.parse("2021-06-20 11:18:00") ?: Date(),
                likeCount = 6,
                commentCount = 0
            ),
            SocialPost(
                id = UUID.randomUUID().toString(),
                authorName = "奶酪",
                authorAvatar = R.drawable.avatar3,
                authorUsername = "@theahighfives",
                content = "发现主人偷偷吃零食没分我，生气！以后别想有好脸色。",
                timestamp = dateFormat.parse("2021-06-20 11:18:00") ?: Date(),
                likeCount = 3,
                commentCount = 0
            ),
            SocialPost(
                id = UUID.randomUUID().toString(),
                authorName = "阿尔法",
                authorAvatar = R.drawable.avatar4,
                authorUsername = "@gibraltar",
                content = "虽然铲屎的很笨，但他做的饭香味不错，今天就原谅他了。",
                timestamp = dateFormat.parse("2021-06-20 11:18:00") ?: Date(),
                likeCount = 9,
                commentCount = 0
            )
        )

        _posts.addAll(dummyPosts)
    }

    fun likePost(postId: String) {
        val index = _posts.indexOfFirst { it.id == postId }
        if (index != -1) {
            val post = _posts[index]
            _posts[index] = post.copy(
                isLiked = !post.isLiked,
                likeCount = if (post.isLiked) post.likeCount - 1 else post.likeCount + 1
            )
        }
    }

    fun savePost(postId: String) {
        val index = _posts.indexOfFirst { it.id == postId }
        if (index != -1) {
            val post = _posts[index]
            _posts[index] = post.copy(isSaved = !post.isSaved)
        }
    }

    fun addPost(content: String) {
        val newPost = SocialPost(
            id = UUID.randomUUID().toString(),
            authorName = "我的宠物",
            authorAvatar = R.drawable.ic_cat_avatar,
            authorUsername = "@mypet",
            content = content,
            timestamp = Date(),
            likeCount = 0,
            commentCount = 0
        )
        _posts.add(0, newPost)
    }
}
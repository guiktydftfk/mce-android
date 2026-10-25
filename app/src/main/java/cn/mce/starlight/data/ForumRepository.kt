package cn.mce.starlight.data

// ---------------- 模型 ----------------

/** 版块 */
data class ForumBoard(
    val fid: Int,
    val name: String,
    val group: String,
    val description: String? = null,
)

/** 帖子摘要 */
data class ThreadSummary(
    val id: Int,
    val title: String,
    val author: String?,
    val avatarUrl: String?,
    val replies: Int?,
    val metaText: String?,
    val typeLabel: String?,
)

/** 列表页 */
data class ForumPage(
    val threads: List<ThreadSummary>,
    val page: Int,
    val hasNextPage: Boolean,
)

/** 楼层/帖子 */
data class Post(
    val pid: Int,
    val author: String,
    val avatarUrl: String?,
    val contentHtml: String,
    val contentText: String,
    val images: List<String>,
    val floorLabel: String?,
    val dateText: String?,
    val isOp: Boolean,
)

/** 帖子详情 */
data class ThreadDetail(
    val tid: Int,
    val title: String,
    val replies: Int?,
    val posts: List<Post>,
    val page: Int,
    val hasNextPage: Boolean,
    val fid: Int? = null,
    val formhash: String? = null,
    val rewardCount: Int? = null,
    val rewardUrl: String? = null,
)

// ---------------- 协议 ----------------

interface ForumRepository {
    suspend fun loadBoards(): List<ForumBoard>
    suspend fun loadThreads(fid: Int, page: Int, filter: String? = null): ForumPage
    suspend fun loadGuide(page: Int): ForumPage
    suspend fun loadThreadDetail(tid: Int, page: Int): ThreadDetail
}

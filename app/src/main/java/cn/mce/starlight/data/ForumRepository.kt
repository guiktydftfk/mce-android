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
    val summary: String? = null,
    val forumName: String? = null,
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
    val uid: Int? = null,
    val author: String,
    val avatarUrl: String?,
    val contentHtml: String,
    val contentText: String,
    val images: List<String>,
    val blocks: List<ContentBlock> = emptyList(),
    val floorLabel: String?,
    val dateText: String?,
    val isOp: Boolean,
)

/** 投票选项 */
data class PollOption(
    val id: String,
    val label: String,
    val votes: Int,
)

/** 帖子内投票（v0.3.3：支持直接参与投票） */
data class PollData(
    /** 提交表单 action（相对地址，如 forum.php?mod=misc&action=votepoll&fid=2&tid=38058...） */
    val action: String,
    val formhash: String,
    val multi: Boolean,
    val maxChoices: Int,
    val options: List<PollOption>,
    /** 已投过票：只读展示结果，不再显示提交按钮 */
    val voted: Boolean,
    /** 查看投票参与人入口 */
    val viewVoteUrl: String?,
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
    val poll: PollData? = null,
)

// ---------------- 协议 ----------------

interface ForumRepository {
    suspend fun loadBoards(): List<ForumBoard>
    suspend fun loadThreads(fid: Int, page: Int, filter: String? = null): ForumPage
    suspend fun loadGuide(view: String = "newthread", page: Int = 1): ForumPage
    suspend fun loadThreadDetail(tid: Int, page: Int, noCache: Boolean = false): ThreadDetail
}

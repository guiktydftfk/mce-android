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
    /** v0.4.6：阅读数（列表 meta 里可能给出，也可能没有；放最后保持构造参数位置兼容） */
    val views: Int? = null,
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
    /** 服务端给的百分比（poll_bar>poll_ct 的 width），缺失时由 UI 用票数比例计算 */
    val percent: Float? = null,
    /** 我在该选项上投过票（已投票页面 input[checked]） */
    val chosen: Boolean = false,
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
    /** 投票已结束/已关闭（与「已投票」区分，避免误显示"您已投过票"） */
    val closed: Boolean = false,
    /** 参与人数（div.ss_data「N 投票人数」） */
    val totalVoters: Int? = null,
    /** 累计票数（div.ss_data「N 累计投票数」） */
    val totalVotes: Int? = null,
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
    /** v0.4.1：网页端管理组可见的管理操作（普通用户为空，界面不显示入口） */
    /** v0.4.2：评分/打赏（电脑版帖子页 dl.rate 区块，手机模板不渲染） */
    val ratings: ThreadRatings? = null,
    /** v0.4.7：帖子阅读数（页头「查看 N」） */
    val views: Int? = null,
)

/** 帖子评分/打赏汇总 */
data class ThreadRatings(
    val count: Int,
    val total: String,
    val entries: List<RatingEntry>,
)

data class RatingEntry(
    val uid: Int?,
    val name: String,
    val avatarUrl: String?,
    val amount: String,
    val reason: String?,
)


// ---------------- 协议 ----------------

interface ForumRepository {
    suspend fun loadBoards(): List<ForumBoard>
    suspend fun loadThreads(fid: Int, page: Int, filter: String? = null): ForumPage
    suspend fun loadGuide(view: String = "newthread", page: Int = 1): ForumPage
    suspend fun loadThreadDetail(tid: Int, page: Int, noCache: Boolean = false): ThreadDetail
}

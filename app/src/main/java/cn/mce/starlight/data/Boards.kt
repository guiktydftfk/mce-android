package cn.mce.starlight.data

/**
 * 版块种子数据（实测：fid=2 是「综合交流」，不是私密版块）。
 */
object Boards {
    val all: List<ForumBoard> = listOf(
        ForumBoard(2, "综合交流", "全国交流", "可以畅所欲言的交流大厅"),
        ForumBoard(53, "通知公告", "全国交流", "团队通知、公告、重要信息"),
        ForumBoard(121, "小红龙频道", "全国交流", "公司新闻与科技数码资讯"),
        ForumBoard(159, "礼品中心", "星光大厅", "星光值兑换礼品"),
        ForumBoard(157, "荣誉中心", "星光大厅", "荣誉与表彰"),
    )

    val default: ForumBoard = all.first()
}

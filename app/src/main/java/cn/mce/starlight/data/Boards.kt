package cn.mce.starlight.data

/**
 * 版块种子数据。
 *
 * 说明：触屏模板的 forum.php?mod=index 是门户式卡片，不是稳定的版块树；
 * 完整版块目录在 api/mobile 里需要插件（站点未启用）。
 * 因此这里内置实测确认过的公开版块，后续可改成从 HTML 动态抓取。
 */
object Boards {
    val all: List<ForumBoard> = listOf(
        ForumBoard(53, "通知公告", "全国交流", "团队通知、公告、重要信息"),
        ForumBoard(121, "小红龙频道", "全国交流", "公司新闻与科技数码资讯"),
        ForumBoard(2, "私密版块", "全国交流", "仅登录可见"),
        ForumBoard(159, "礼品中心", "星光大厅", "星光值兑换礼品"),
        ForumBoard(157, "荣誉中心", "星光大厅", "荣誉与表彰"),
    )
}

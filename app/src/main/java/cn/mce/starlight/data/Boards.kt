package cn.mce.starlight.data

/**
 * 完整板块树（实测自 forum.php 桌面版，guest 可见）。
 *
 * gid=1  全国交流
 * gid=45 项目和活动
 * gid=39 全国大家庭（城市板块）
 * gid=91 星光大厅
 */
object Boards {

    val groups: List<Pair<String, List<ForumBoard>>> = listOf(
        "全国交流" to listOf(
            ForumBoard(53, "通知公告", "全国交流", "团队通知、公告、重要信息"),
            ForumBoard(2, "综合交流", "全国交流", "可以畅所欲言的交流大厅"),
            ForumBoard(121, "小红龙频道", "全国交流", "公司新闻与科技数码资讯"),
        ),
        "项目和活动" to listOf(
            ForumBoard(94, "社区主题活动", "项目和活动"),
            ForumBoard(186, "萌星营地", "项目和活动"),
            ForumBoard(37, "迎新大招募", "项目和活动"),
            ForumBoard(130, "新星报到厅", "项目和活动"),
            ForumBoard(132, "校园微星日", "项目和活动"),
            ForumBoard(111, "星宝见面会·分享会", "项目和活动"),
            ForumBoard(140, "巡店调研", "项目和活动"),
            ForumBoard(74, "资源·干货·技能", "项目和活动"),
            ForumBoard(123, "科技·电竞·数码", "项目和活动"),
            ForumBoard(152, "年度创作大赛", "项目和活动"),
            ForumBoard(131, "星汇峰会龙盾局", "项目和活动"),
            ForumBoard(46, "赋能青春·暑期训练营", "项目和活动"),
            ForumBoard(88, "未来之星·寒假训练营", "项目和活动"),
        ),
        "城市板块" to listOf(
            ForumBoard(40, "郑州", "城市板块"),
            ForumBoard(41, "西安", "城市板块"),
            ForumBoard(54, "兰州", "城市板块"),
            ForumBoard(81, "天津", "城市板块"),
            ForumBoard(72, "上海", "城市板块"),
            ForumBoard(52, "南京", "城市板块"),
            ForumBoard(73, "沧州", "城市板块"),
            ForumBoard(68, "肇庆", "城市板块"),
            ForumBoard(114, "北京", "城市板块"),
            ForumBoard(172, "成都", "城市板块"),
            ForumBoard(173, "徐州", "城市板块"),
            ForumBoard(176, "淮安", "城市板块"),
            ForumBoard(189, "石家庄", "城市板块"),
            ForumBoard(191, "晋中", "城市板块"),
            ForumBoard(193, "天水", "城市板块"),
            ForumBoard(192, "合肥", "城市板块"),
            ForumBoard(77, "更多城市", "城市板块"),
        ),
        "星光大厅" to listOf(
            ForumBoard(159, "礼品中心", "星光大厅", "星光值兑换礼品"),
            ForumBoard(157, "荣誉中心", "星光大厅", "荣誉与表彰"),
            ForumBoard(162, "习惯打卡", "星光大厅"),
            ForumBoard(169, "星光值加分", "星光大厅"),
        ),
    )

    /** 扁平列表（兼容旧调用） */
    val all: List<ForumBoard> = groups.flatMap { it.second }

    val default: ForumBoard = all.first { it.fid == 2 }

    fun byFid(fid: Int): ForumBoard? = all.firstOrNull { it.fid == fid }
}

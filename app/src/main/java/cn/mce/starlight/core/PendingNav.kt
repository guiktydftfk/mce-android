package cn.mce.starlight.core

/** v0.4.5：通知点击后待处理的导航（打开对应私信会话） */
object PendingNav {
    var pmTouid: Int = 0
    /** v0.4.7：分享链接打开的帖子 tid */
    var threadTid: Int = 0
    /** v0.4.8：从提醒点进来时直达的楼层 pid（0 = 不定位） */
    var floorPid: Int = 0
}

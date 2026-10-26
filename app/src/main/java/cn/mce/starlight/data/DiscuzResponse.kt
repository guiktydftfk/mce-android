package cn.mce.starlight.data

import org.jsoup.Jsoup

/**
 * Discuz 响应判定（v0.3.3 批 4）。
 *
 * 历史问题：回复 / 评分 / 收藏 / 资料保存 / 表单提交大多"只要 HTTP 200 就当成功"，
 * 而 Discuz 的失败提示（来路不正确、两次发表间隔少于 15 秒、不能给自己评分、无权操作…）
 * **同样返回 200**，于是失败被当成成功（回复失败也提示"回复成功"并清空输入框）。
 *
 * 判定策略（按顺序）：
 *  1. 显式失败证据（`showError(...)` / `alert_error` / "抱歉/错误/无权/不能…"）→ 失败，并带出原文
 *  2. 显式成功标记（`show_success` / "回复发布成功" / "已发布" / "收藏成功"…）→ 成功
 *  3. 两者都没有 → **未确认**，按失败处理并提示刷新确认（不再默认成功）
 */
object DiscuzResponse {
    private val SUCCESS_MARKERS = listOf(
        "show_success",
        "\u53d1\u5e03\u6210\u529f",      // 发布成功
        "\u5df2\u53d1\u5e03",            // 已发布
        "\u56de\u590d\u6210\u529f",      // 回复成功
        "\u56de\u590d\u53d1\u5e03\u6210\u529f", // 回复发布成功
        "\u53d1\u8868\u6210\u529f",      // 发表成功
        "\u8bc4\u5206\u6210\u529f",      // 评分成功
        "\u6536\u85cf\u6210\u529f",      // 收藏成功
        "\u5df2\u6536\u85cf",            // 已收藏
        "\u4fdd\u5b58\u6210\u529f",      // 保存成功
        "\u8bbe\u7f6e\u6210\u529f",      // 设置成功
        "\u66f4\u65b0\u6210\u529f",      // 更新成功
        "\u63d0\u4ea4\u6210\u529f",      // 提交成功
        "\u64cd\u4f5c\u6210\u529f",      // 操作成功
        "\u611f\u8c22\u60a8\u7684\u53c2\u4e0e", // 感谢您的参与
        "\u8f6c\u5165\u4e3b\u9898",      // 转入主题
    )

    private val FAILURE_HINTS = listOf(
        "\u62b1\u6b49",      // 抱歉
        "\u5bf9\u4e0d\u8d77", // 对不起
        "\u9519\u8bef",      // 错误
        "\u5931\u8d25",      // 失败
        "\u65e0\u6743",      // 无权
        "\u7981\u6b62",      // 禁止
        "\u4e0d\u80fd",      // 不能
        "\u4e0d\u5141\u8bb8", // 不允许
        "\u9700\u8981\u5ba1\u6838", // 需要审核
        "\u95f4\u9694\u5c11\u4e8e", // 间隔少于
        "\u9a8c\u8bc1\u7801", // 验证码
        "\u6765\u8def\u4e0d\u6b63\u786e", // 来路不正确
        "\u8bf7\u5148\u767b\u5f55", // 请先登录
        "\u5185\u5bb9\u8fc7\u77ed", // 内容过短
        "\u4e24\u6b21\u53d1\u8868", // 两次发表
    )

    private val showErrorRegex = Regex("""(?:show_error|showError)\(([^)]*)\)""")
    private val stringLiteralRegex = Regex("""['"]([^'"]*)['"]""")
    private val alertRegex = Regex(
        """<div[^>]*class="[^"]*alert_(?:error|info)[^"]*"[^>]*>(.*?)</div>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val messagePatterns = listOf(
        Regex("""(?:抱歉|对不起)[^。！!<\r\n]{0,80}"""),
        Regex("""您[^。！!<\r\n]{0,50}?(?:间隔|无权|不能|禁止|需要|无法|失败|评分|收藏|权限)[^。！!<\r\n]{0,50}"""),
    )

    /** 是否有显式成功标记 */
    fun isSuccess(html: String): Boolean = SUCCESS_MARKERS.any { html.contains(it) }

    /** 失败提示原文；没有明确失败证据时返回 null */
    fun failureMessage(html: String): String? {
        // Discuz AJAX 失败两种写法：
        //   showError('抱歉，…')            / show_error("抱歉，…")
        //   show_error("yahoo", "")         ← 字段校验失败（第一参数是字段名，消息可能为空）
        showErrorRegex.find(html)?.let { m ->
            val args = stringLiteralRegex.findAll(m.groupValues[1])
                .map { it.groupValues[1] }.toList()
            when (args.size) {
                // 双参数：第一个字段名，第二个提示（可能为空）
                2 -> {
                    val message = args[1].trim()
                    if (message.isNotBlank()) return clean(message)
                    val field = args[0].trim()
                    if (field.isNotBlank()) return "\u5b57\u6bb5\u300c$field\u300d\u6821\u9a8c\u672a\u901a\u8fc7"
                }
                // 单参数：就是失败原文
                1 -> args[0].trim().takeIf { it.isNotBlank() }?.let { return clean(it) }
            }
            return null
        }

        alertRegex.find(html)?.let { m ->
            Jsoup.parse(m.groupValues[1]).text().trim()
                .takeIf { it.isNotBlank() }?.let { return clean(it) }
        }

        val text = Jsoup.parse(html).text()
        for (pattern in messagePatterns) {
            pattern.find(text)?.let { return clean(it.value) }
        }
        return if (FAILURE_HINTS.any { text.contains(it) }) clean(text) else null
    }

    /** 去掉提示页附带的导航尾巴（"[ 点击这里返回上一页 ]" 之类） */
    private fun clean(message: String): String = message
        .substringBefore("\u70b9\u51fb\u8fd9\u91cc")       // 点击这里
        .substringBefore("\u8fd4\u56de\u4e0a\u4e00\u9875")   // 返回上一页
        .replace("[\u3000 ]*]", "")
        .trim()
        .take(60)

    /**
     * 综合判定：返回 (是否成功, 给用户看的提示)。
     * @param successText 判定成功时展示的文案
     */
    fun judge(
        html: String,
        successText: String,
        unknownText: String = "\u63d0\u4ea4\u7ed3\u679c\u672a\u786e\u8ba4\uff0c\u8bf7\u5237\u65b0\u67e5\u770b",
    ): Pair<Boolean, String> {
        if (isSuccess(html)) return true to successText
        val failure = failureMessage(html)
        if (failure != null) return false to failure
        return false to unknownText
    }
}

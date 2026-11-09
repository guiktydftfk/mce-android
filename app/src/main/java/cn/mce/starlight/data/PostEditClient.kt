package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * v0.4.6：原生编辑帖子/回复（第一步：编辑表单解析 + 提交）。
 *
 * 说明：Discuz 电脑版编辑页的正文在 `<textarea name="message">` 里是**原始 BBCode**
 * （含 [img]/[attach]/表格等），必须原样回传，否则会破坏排版。
 * 因此这里只负责「解析表单字段 + 原样带回」，不做正文转换。
 */
object PostEditClient {

    data class EditForm(
        val formhash: String,
        val posttime: String,
        val subject: String,
        val message: String,
    )

    /** 从编辑页 HTML 解析表单（缺失 message/formhash 视为失败） */
    fun parse(html: String): EditForm? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html)
        val formhash = doc.selectFirst("input[name=formhash]")?.attr("value")?.trim().orEmpty()
        val posttime = doc.selectFirst("input[name=posttime]")?.attr("value")?.trim().orEmpty()
        val subject = doc.selectFirst("input[name=subject]")?.attr("value")?.trim().orEmpty()
        val message = doc.selectFirst("textarea[name=message]")?.text()
            ?: doc.selectFirst("textarea[name=message]")?.html()
            ?: return null
        if (formhash.isBlank()) return null
        return EditForm(formhash, posttime, subject, message)
    }

    /** 组装提交 body（字段顺序与网页表单一致） */
    fun buildBody(form: EditForm, subject: String, message: String): String = buildString {
        append("formhash=").append(encode(form.formhash))
        if (form.posttime.isNotBlank()) append("&posttime=").append(encode(form.posttime))
        append("&subject=").append(encode(subject))
        append("&message=").append(encode(message))
        append("&editsubmit=yes")
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    /** 拉取编辑页并解析（供编辑弹窗预填 BBCode） */
    suspend fun loadForm(tid: Int, pid: Int): EditForm? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${Net.BASE}/forum.php?mod=post&action=edit&tid=$tid&pid=$pid&mobile=no"
            val page = GenericPageClient.get(url, userAgent = Net.DESKTOP_UA, isolateCookies = true)
            parse(page)
        }.getOrNull()
    }

    /** 拉取编辑页 → 解析 → 原样提交 */
    suspend fun submit(tid: Int, pid: Int, subject: String, message: String): PostActions.Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${Net.BASE}/forum.php?mod=post&action=edit&tid=$tid&pid=$pid&mobile=no"
                val page = GenericPageClient.get(url, userAgent = Net.DESKTOP_UA, isolateCookies = true)
                val form = parse(page) ?: return@withContext PostActions.Result(false, "编辑表单解析失败（可能未登录或帖子不可编辑）")
                val resp = GenericPageClient.httpPost(
                    url = "${Net.BASE}/forum.php?mod=post&action=edit&editsubmit=yes&mobile=no",
                    body = buildBody(form, subject, message),
                    cookie = null,
                    userAgent = Net.DESKTOP_UA,
                )
                val (ok, msg) = DiscuzResponse.judge(resp, "编辑")
                PostActions.Result(ok, msg)
            }.getOrElse { PostActions.Result(false, it.message) }
        }
}

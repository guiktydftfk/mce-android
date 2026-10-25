package cn.mce.starlight.data

import cn.mce.starlight.core.WebCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 原生登录（Discuz 表单 POST，无需 WebView）。
 *
 * 流程：
 *  1. GET 登录页 → 取 formhash + form action（含 loginhash）
 *  2. POST 用户名/密码 → Discuz 下发 auth Cookie（写入 CookieManager）
 *  3. GET 论坛首页校验是否出现「退出」链接
 */
object AuthClient {
    private const val BASE = "https://mce.msi.cn"

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    suspend fun login(username: String, password: String): Result =
        withContext(Dispatchers.IO) {
            if (username.isBlank() || password.isBlank()) {
                return@withContext Result.Failure("请输入用户名和密码")
            }

            val loginPage = runCatching {
                GenericPageClient.get("$BASE/member.php?mod=logging&action=login&mobile=2")
            }.getOrElse { return@withContext Result.Failure("网络错误：${it.message}") }

            val doc = Jsoup.parse(loginPage, BASE)
            val form = doc.selectFirst("form#loginform")
                ?: return@withContext Result.Failure("登录页结构异常，找不到登录表单")

            val action = form.attr("action").replace("&amp;", "&")
                .takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("http")) it else "$BASE/$it" }
                ?: return@withContext Result.Failure("登录表单缺少提交地址")

            val formhash = form.selectFirst("input[name=formhash]")?.attr("value").orEmpty()
            val referer = form.selectFirst("input[name=referer]")?.attr("value")
                ?.takeIf { it.isNotBlank() } ?: "$BASE/"

            val fields = listOf(
                "formhash" to formhash,
                "referer" to referer,
                "loginfield" to "username",
                "fastloginfield" to "username",
                "cookietime" to "2592000",
                "username" to username,
                "password" to password,
            )

            val body = fields.joinToString("&") {
                URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
            }

            val response = runCatching {
                GenericPageClient.httpPost(action, body, WebCookies.cookieHeader())
            }.getOrElse { return@withContext Result.Failure("网络错误：${it.message}") }

            // 校验：论坛首页是否出现退出链接
            val check = runCatching { GenericPageClient.get("$BASE/forum.php") }.getOrNull()
            val ok = check != null && (check.contains("action=logout") || check.contains("action=logout"))

            if (ok) {
                Result.Success
            } else {
                val msg = extractError(response)
                Result.Failure(msg ?: "登录失败：账号或密码不正确")
            }
        }

    /** 原生退出登录 */
    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val forumHtml = GenericPageClient.get("$BASE/forum.php")
            val formhash = Regex("""formhash=([a-z0-9]+)""")
                .find(forumHtml)?.groupValues?.get(1).orEmpty()
            if (formhash.isNotEmpty()) {
                GenericPageClient.get("$BASE/member.php?mod=logging&action=logout&formhash=$formhash")
            }
            cn.mce.starlight.core.WebCookies.clear()
            true
        }.getOrDefault(false)
    }

    private fun extractError(html: String): String? {
        val text = Jsoup.parse(html).text()
        val patterns = listOf(
            Regex("""登录失败[^。]{0,40}"""),
            Regex("""密码错误[^。]{0,30}"""),
            Regex("""(?:请|需要)[^。]{0,20}登录[^。]{0,20}"""),
            Regex("""验证码[^。]{0,20}"""),
        )
        for (p in patterns) {
            p.find(text)?.let { return it.value.take(60) }
        }
        return null
    }
}

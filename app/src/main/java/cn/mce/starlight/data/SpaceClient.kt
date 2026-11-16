package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * v0.4.8：个人空间扩展 —— 日志 / 相册 / 留言板 / 访客。
 *
 * 论坛手机模板没有这些子页，统一走电脑版个人空间：
 *   - 日志：home.php?mod=space&uid=X&do=blog&mobile=no
 *   - 相册：home.php?mod=space&uid=X&do=album&mobile=no
 *   - 留言板：home.php?mod=space&uid=X&do=wall&mobile=no
 *   - 访客：home.php?mod=space&uid=X&do=visitor&mobile=no
 *
 * 解析全部是启发式（不同模板结构差异大），空结果时 UI 提供「网页打开」兜底。
 */
object SpaceClient {

    data class BlogItem(val id: String, val title: String, val date: String?)
    data class AlbumItem(val id: String, val title: String, val cover: String?, val countText: String?)
    data class WallItem(val author: String?, val avatar: String?, val text: String, val time: String?)
    data class VisitorItem(val uid: Int, val name: String, val avatar: String?, val time: String?)

    private val dateRegex = Regex("""\d{4}-\d{1,2}-\d{1,2}(?:\s+\d{1,2}:\d{2})?""")
    private val relTimeRegex = Regex("""\d+\s*(?:秒|分钟|小时|天)前|昨天|前天""")

    private suspend fun fetchDesktop(uid: Int, doWhat: String): String? {
        val desktopUrl = "${Net.BASE}/home.php?mod=space&uid=$uid&do=$doWhat&mobile=no"
        val desktop = runCatching {
            GenericPageClient.get(desktopUrl, userAgent = Net.DESKTOP_UA, isolateCookies = true)
        }.getOrNull()
        if (!desktop.isNullOrBlank()) return desktop
        val mobileUrl = "${Net.BASE}/home.php?mod=space&uid=$uid&do=$doWhat"
        return runCatching { GenericPageClient.get(mobileUrl) }.getOrNull()
    }

    suspend fun loadBlogs(uid: Int): List<BlogItem> = withContext(Dispatchers.IO) {
        runCatching { fetchDesktop(uid, "blog")?.let { parseBlogs(it) } }.getOrNull().orEmpty()
    }

    suspend fun loadAlbums(uid: Int): List<AlbumItem> = withContext(Dispatchers.IO) {
        runCatching { fetchDesktop(uid, "album")?.let { parseAlbums(it) } }.getOrNull().orEmpty()
    }

    suspend fun loadWall(uid: Int): List<WallItem> = withContext(Dispatchers.IO) {
        runCatching { fetchDesktop(uid, "wall")?.let { parseWall(it) } }.getOrNull().orEmpty()
    }

    suspend fun loadVisitors(uid: Int): List<VisitorItem> = withContext(Dispatchers.IO) {
        runCatching { fetchDesktop(uid, "visitor")?.let { parseVisitors(it) } }.getOrNull().orEmpty()
    }

    // ---------------- 解析（internal 方便单测） ----------------

    internal fun parseBlogs(html: String, baseUrl: String = Net.BASE): List<BlogItem> {
        val doc = Jsoup.parse(html, baseUrl)
        val anchors = doc.select("a[href*=do=blog][href*=id=]")
            .ifEmpty { doc.select("a[href*=blog-]") }
        return anchors.mapNotNull { a ->
            val href = a.attr("href")
            val id = Regex("""[?&]id=(\d+)""").find(href)?.groupValues?.get(1)
                ?: Regex("""blog-(\d+)""").find(href)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val title = a.text().trim()
            if (title.isBlank()) return@mapNotNull null
            val container = a.parents().firstOrNull {
                it.tagName() in setOf("li", "dl", "dd", "div") && it.text().length <= 400
            } ?: a.parent()
            val raw = container?.text().orEmpty()
            val date = dateRegex.find(raw)?.value ?: relTimeRegex.find(raw)?.value
            BlogItem(id = id, title = title.take(60), date = date)
        }.distinctBy { it.id }
    }

    internal fun parseAlbums(html: String, baseUrl: String = Net.BASE): List<AlbumItem> {
        val doc = Jsoup.parse(html, baseUrl)
        val anchors = doc.select("a[href*=do=album][href*=id=]")
            .ifEmpty { doc.select("a[href*=album-]") }
        return anchors.mapNotNull { a ->
            val href = a.attr("href")
            val id = Regex("""[?&]id=(\d+)""").find(href)?.groupValues?.get(1)
                ?: Regex("""album-(\d+)""").find(href)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val directText = a.text().trim()
            if (directText.startsWith("\u67e5\u770b") || directText.isEmpty()) return@mapNotNull null
            val container = a.parents().firstOrNull {
                it.tagName() in setOf("li", "dl", "div") && it.text().length <= 300 &&
                    it.selectFirst("img[src]") != null
            } ?: a.parent()
            val containerText = container?.text().orEmpty()
            val cover = container?.selectFirst("img[src]")?.attr("src")?.takeIf { it.isNotBlank() }?.let(::absolutize)
            val count = Regex("""(\d+)\s*\u5f20""").find(containerText)?.value
            val title = directText.ifBlank {
                container?.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty()
            }
            if (title.isBlank()) return@mapNotNull null
            AlbumItem(id = id, title = title.take(60), cover = cover, countText = count)
        }.distinctBy { it.id }
    }

    internal fun parseWall(html: String, baseUrl: String = Net.BASE): List<WallItem> {
        val doc = Jsoup.parse(html, baseUrl)
        val candidates = doc.select("#wall_list li, .wall_list li, ul.comment_list li, div.wall_item, li.wall_item")
        val items = candidates.mapNotNull { li ->
            val authorLink = li.selectFirst("a[href*=uid=], a[href*=space]") ?: return@mapNotNull null
            val author = authorLink.text().trim().takeIf { it.isNotBlank() }
            val avatar = li.selectFirst("img[src*=avatar]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)
            var text = li.text().trim()
            author?.let { text = text.removePrefix(it).trim() }
            val time = dateRegex.find(text)?.value ?: relTimeRegex.find(text)?.value
            time?.let { text = text.replace(it, "").trim() }
            if (text.isBlank() || text.length < 2) return@mapNotNull null
            WallItem(author = author, avatar = avatar, text = text.take(300), time = time)
        }
        if (items.isNotEmpty()) return items
        // 兜底：评论式列表（div.comment / dl.comment）
        return doc.select("div.comment, dl.comment").mapNotNull { el ->
            val authorLink = el.selectFirst("a[href*=uid=]") ?: return@mapNotNull null
            val text = el.text().trim()
            if (text.length < 2) return@mapNotNull null
            WallItem(
                author = authorLink.text().trim(),
                avatar = el.selectFirst("img[src*=avatar]")?.attr("src")?.let(::absolutize),
                text = text.take(300),
                time = dateRegex.find(text)?.value ?: relTimeRegex.find(text)?.value,
            )
        }
    }

    internal fun parseVisitors(html: String, baseUrl: String = Net.BASE): List<VisitorItem> {
        val doc = Jsoup.parse(html, baseUrl)
        val containers = doc.select("#visitor_list li, .visitor_list li, ul.visitors li, li.visitor")
        val scoped = if (containers.isNotEmpty()) containers else doc.select("div[class*=visitor] a[href*=uid=]")
        return scoped.mapNotNull { el ->
            val anchor = if (el.tagName() == "a") el
            else el.select("a[href*=uid=]").firstOrNull { it.text().isNotBlank() } ?: return@mapNotNull null
            val uid = Regex("""uid=(\d+)""").find(anchor.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = anchor.text().trim().trim('[', ']', ' ')
            if (name.isBlank()) return@mapNotNull null
            val avatar = if (el.tagName() == "a") null else el.selectFirst("img[src*=avatar]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)
            val time = relTimeRegex.find(el.text())?.value ?: dateRegex.find(el.text())?.value
            VisitorItem(uid = uid, name = name, avatar = avatar, time = time)
        }.distinctBy { it.uid }.take(60)
    }

    private fun absolutize(src: String): String = Net.absolutize(src)
}

package cn.mce.starlight.core

import android.content.Context
import cn.mce.starlight.data.ForumPage
import cn.mce.starlight.data.ThreadSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 首页 / 版块列表的本地缓存（v0.3.10）。
 *
 * 解决两个卡顿点：
 *  1. **首次打开**：磁盘里有上次的列表就先渲染，网络请求在后台补（不再白屏骨架等 2~4 秒）；
 *  2. **从帖子返回**：打开帖子时 [cn.mce.starlight.ui.HomeFeedScreen] 会离开组合、状态被销毁，
 *     返回后旧实现整页重新拉取 → 又看到一次骨架屏；现在返回时直接命中内存/磁盘缓存秒开，再静默刷新。
 *
 * 只缓存「列表页 1」，体积极小（约 20~50 KB）；14 天过期，退出登录时清空。
 */
object FeedCache {
    private const val FILE_NAME = "feed_cache.json"
    private const val TTL_MS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_KEYS = 8

    data class Entry(val threads: List<ThreadSummary>, val hasNext: Boolean, val savedAt: Long)

    private var dir: File? = null
    private val memory = LinkedHashMap<String, Entry>()
    private var loaded = false

    fun init(context: Context) {
        dir = context.applicationContext.filesDir
        memory.clear()
        loaded = false
    }

    /** v0.4.1：后台预热（启动时在线程池里先把磁盘缓存读进内存，避免首帧在主线程解析 JSON） */
    @Synchronized
    fun prewarm() {
        ensureLoaded()
    }

    /** 是否已完成预热（未完成时调用方不要在主线程 get，以免解析卡首帧） */
    fun isReady(): Boolean = loaded
    /** 同步读内存；首次调用时顺带把磁盘缓存读进内存（文件很小，几毫秒） */
    @Synchronized
    fun get(key: String): Entry? {
        ensureLoaded()
        val e = memory[key] ?: return null
        if (System.currentTimeMillis() - e.savedAt > TTL_MS) {
            memory.remove(key)
            return null
        }
        return e
    }

    /** 列表首屏加载成功后写入（内存立即生效，磁盘异步写） */
    @Synchronized
    fun put(key: String, page: ForumPage) {
        if (page.page != 1) return
        memory[key] = Entry(page.threads, page.hasNextPage, System.currentTimeMillis())
        trim()
        persistAsync()
    }

    /** v0.4.0：内存紧张时只释放内存里的列表（磁盘缓存保留，下次仍能秒开） */
    @Synchronized
    fun trimMemory() {
        memory.clear()
        // 保留 loaded=true，避免下次 get 再读一遍磁盘；内存里没有就自然回落到网络
    }

    /** 退出登录时清空，避免串号 */
    @Synchronized
    fun clear() {
        memory.clear()
        runCatching { dir?.let { File(it, FILE_NAME).delete() } }
    }

    private fun trim() {
        while (memory.size > MAX_KEYS) {
            val first = memory.keys.firstOrNull() ?: return
            memory.remove(first)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val file = dir?.let { File(it, FILE_NAME) } ?: return
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            val keys = root.names() ?: return@runCatching
            for (i in 0 until keys.length()) {
                val key = keys.getString(i)
                val obj = root.optJSONObject(key) ?: continue
                val savedAt = obj.optLong("savedAt", 0L)
                if (System.currentTimeMillis() - savedAt > TTL_MS) continue
                val arr = obj.optJSONArray("threads") ?: continue
                memory[key] = Entry(readThreads(arr), obj.optBoolean("hasNext", false), savedAt)
            }
        }
    }

    private fun persistAsync() {
        val d = dir ?: return
        val snapshot = HashMap(memory)
        Thread {
            runCatching {
                val root = JSONObject()
                snapshot.forEach { (key, entry) ->
                    val obj = JSONObject()
                    obj.put("savedAt", entry.savedAt)
                    obj.put("hasNext", entry.hasNext)
                    val arr = JSONArray()
                    entry.threads.forEach { t ->
                        arr.put(
                            JSONObject().apply {
                                put("id", t.id)
                                put("title", t.title)
                                t.author?.let { put("author", it) }
                                t.avatarUrl?.let { put("avatarUrl", it) }
                                t.replies?.let { put("replies", it) }
                                t.metaText?.let { put("metaText", it) }
                                t.typeLabel?.let { put("typeLabel", it) }
                                t.summary?.let { put("summary", it) }
                                t.forumName?.let { put("forumName", it) }
                            },
                        )
                    }
                    obj.put("threads", arr)
                    root.put(key, obj)
                }
                File(d, FILE_NAME).writeText(root.toString())
            }
        }.apply { isDaemon = true }.start()
    }

    private fun readThreads(arr: JSONArray): List<ThreadSummary> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", 0)
            val title = o.optString("title", "")
            if (id <= 0 || title.isBlank()) continue
            add(
                ThreadSummary(
                    id = id,
                    title = title,
                    author = o.optString("author", "").takeIf { it.isNotBlank() },
                    avatarUrl = o.optString("avatarUrl", "").takeIf { it.isNotBlank() },
                    replies = if (o.has("replies")) o.optInt("replies") else null,
                    metaText = o.optString("metaText", "").takeIf { it.isNotBlank() },
                    typeLabel = o.optString("typeLabel", "").takeIf { it.isNotBlank() },
                    summary = o.optString("summary", "").takeIf { it.isNotBlank() },
                    forumName = o.optString("forumName", "").takeIf { it.isNotBlank() },
                ),
            )
        }
    }
}

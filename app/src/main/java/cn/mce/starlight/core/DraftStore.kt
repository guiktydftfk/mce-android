package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** 本地草稿（v0.3.3）：Discuz 手机模板没有草稿箱，存草稿只能由客户端自己保存 */
data class Draft(
    val id: Long,
    val fid: Int,
    val fidName: String,
    val title: String,
    val content: String,
    val pollMode: Boolean,
    val pollOptions: String,
    val maxChoices: String,
    val expireDays: String,
    val resultVisible: Boolean,
    val publicVoters: Boolean,
    val savedAt: Long,
)

/**
 * 草稿箱（本地持久化）。
 *
 * 旧实现点「存草稿」是把 `save=1` 塞进发帖 POST —— Discuz 手机模板不支持存草稿，
 * 结果草稿要么被丢弃、要么直接发布出去，用户根本找不到草稿箱。
 * 现在改为客户端本地保存：草稿只在「草稿箱」里出现，不会混进「我的主题」。
 */
object DraftStore {
    private const val PREFS = "mce_drafts"
    private const val KEY = "drafts"
    private const val MAX_DRAFTS = 30

    private var appContext: Context? = null

    var drafts by mutableStateOf<List<Draft>>(emptyList())
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        drafts = load()
        // v0.4.0：草稿含个人信息（正文/标题），改为加密存储；旧明文数据在后台迁移
        Thread {
            runCatching {
                if (prefs()?.getString(KEY, null)?.let { CryptoBox.isEncrypted(it) } == false) persist(drafts)
            }
        }.apply { isDaemon = true }.start()
    }

    fun get(id: Long): Draft? = drafts.firstOrNull { it.id == id }

    /** 新增或覆盖（同 id）一份草稿 */
    fun save(draft: Draft) {
        val next = (drafts.filterNot { it.id == draft.id } + draft)
            .sortedByDescending { it.savedAt }
            .take(MAX_DRAFTS)
        drafts = next
        persist(next)
    }

    fun delete(id: Long) {
        val next = drafts.filterNot { it.id == id }
        drafts = next
        persist(next)
    }

    fun clear() {
        drafts = emptyList()
        persist(emptyList())
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(): List<Draft> {
        val raw = CryptoBox.decrypt(prefs()?.getString(KEY, null)) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Draft(
                    id = o.optLong("id"),
                    fid = o.optInt("fid"),
                    fidName = o.optString("fidName"),
                    title = o.optString("title"),
                    content = o.optString("content"),
                    pollMode = o.optBoolean("pollMode"),
                    pollOptions = o.optString("pollOptions"),
                    maxChoices = o.optString("maxChoices"),
                    expireDays = o.optString("expireDays"),
                    resultVisible = o.optBoolean("resultVisible"),
                    publicVoters = o.optBoolean("publicVoters"),
                    savedAt = o.optLong("savedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Draft>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { d ->
                arr.put(
                    JSONObject()
                        .put("id", d.id)
                        .put("fid", d.fid)
                        .put("fidName", d.fidName)
                        .put("title", d.title)
                        .put("content", d.content)
                        .put("pollMode", d.pollMode)
                        .put("pollOptions", d.pollOptions)
                        .put("maxChoices", d.maxChoices)
                        .put("expireDays", d.expireDays)
                        .put("resultVisible", d.resultVisible)
                        .put("publicVoters", d.publicVoters)
                        .put("savedAt", d.savedAt),
                )
            }
            prefs()?.edit()?.putString(KEY, CryptoBox.encrypt(arr.toString()))?.apply()
        }
    }
}

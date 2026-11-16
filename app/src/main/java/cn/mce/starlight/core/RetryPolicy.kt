package cn.mce.starlight.core

import kotlinx.coroutines.delay

/**
 * v0.4.8：弱网发布重试策略。
 *
 * 只对「网络类失败」重试一次（连接失败 / 超时 / 网络较弱）；
 * 业务失败（权限、验证码、内容违规等）不重试，避免误导与重复提交。
 */
object RetryPolicy {

    private val netHints = Regex(
        "网络|超时|较弱|连接|timeout|failed to connect|SocketTimeout|UnknownHost|unable to resolve",
        RegexOption.IGNORE_CASE,
    )

    fun shouldRetry(message: String?): Boolean =
        message != null && netHints.containsMatchIn(message)

    /** 失败且属于网络问题时，延迟 900ms 重试一次；否则直接返回首次结果 */
    suspend fun <T> retryOnceOnNetwork(
        isOk: (T) -> Boolean,
        messageOf: (T) -> String?,
        block: suspend () -> T,
    ): T {
        val first = block()
        if (!isOk(first) && shouldRetry(messageOf(first))) {
            delay(900)
            return block()
        }
        return first
    }
}
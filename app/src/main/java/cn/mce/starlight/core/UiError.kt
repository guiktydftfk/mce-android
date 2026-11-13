package cn.mce.starlight.core

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * v0.4.7：弱网/失败提示统一化。
 * 全 App 的网络失败提示都走这里，避免各页面文案不一致（有的直接抛异常类名）。
 */
object UiError {
    fun friendly(t: Throwable?): String = when {
        t == null -> "加载失败，请重试"
        t is UnknownHostException -> "网络不可用，请检查网络后重试"
        t is SocketTimeoutException -> "网络超时，请稍后重试"
        t is ConnectException -> "无法连接服务器，请稍后重试"
        else -> {
            val msg = t.message.orEmpty()
            when {
                msg.contains("timed out", true) -> "网络超时，请稍后重试"
                msg.contains("Unable to resolve host", true) -> "网络不可用，请检查网络后重试"
                msg.contains("401") || msg.contains("403") -> "登录状态失效，请重新登录"
                msg.contains("500") || msg.contains("502") || msg.contains("503") -> "服务器繁忙，请稍后重试"
                msg.isBlank() -> "加载失败，请重试"
                else -> msg
            }
        }
    }
}

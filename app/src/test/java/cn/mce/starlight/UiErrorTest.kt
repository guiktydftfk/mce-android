package cn.mce.starlight.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** v0.4.7：弱网/失败提示统一化的映射规则单测 */
class UiErrorTest {

    @Test
    fun `空异常给出通用提示`() {
        assertEquals("加载失败，请重试", UiError.friendly(null))
    }

    @Test
    fun `域名解析失败提示检查网络`() {
        assertEquals("网络不可用，请检查网络后重试", UiError.friendly(UnknownHostException("host")))
    }

    @Test
    fun `超时提示稍后重试`() {
        assertEquals("网络超时，请稍后重试", UiError.friendly(SocketTimeoutException("timeout")))
    }

    @Test
    fun `连接失败提示无法连接服务器`() {
        assertEquals("无法连接服务器，请稍后重试", UiError.friendly(ConnectException("refused")))
    }

    @Test
    fun `登录失效提示重新登录`() {
        assertEquals("登录状态失效，请重新登录", UiError.friendly(RuntimeException("HTTP 401")))
    }

    @Test
    fun `服务器繁忙提示稍后再试`() {
        assertEquals("服务器繁忙，请稍后重试", UiError.friendly(RuntimeException("HTTP 503")))
    }

    @Test
    fun `其他异常保留原始消息`() {
        assertEquals("解析失败", UiError.friendly(RuntimeException("解析失败")))
    }

    @Test
    fun `空白消息回退通用提示`() {
        assertEquals("加载失败，请重试", UiError.friendly(RuntimeException("   ")))
    }

    @Test
    fun `英文超时消息也能识别`() {
        assertEquals("网络超时，请稍后重试", UiError.friendly(RuntimeException("request timed out")))
    }
}

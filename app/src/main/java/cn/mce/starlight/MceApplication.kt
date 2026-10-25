package cn.mce.starlight

import android.app.Application
import cn.mce.starlight.core.CookieJar
import cn.mce.starlight.core.WebCookies
import cn.mce.starlight.core.SettingsStore
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * 全局配置：
 * - 设置读取
 * - Coil 图片加载器注入 Cookie（附件图片需要登录态才能取到）
 */
class MceApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        CookieJar.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val cookieInterceptor = Interceptor { chain ->
            val request = chain.request()
            val isSite = request.url.host.contains("mce.msi.cn")
            val builder = request.newBuilder()
            if (isSite) {
                // 附件图片需要登录态 + Referer，否则服务端可能返回拒绝页
                WebCookies.cookieHeader(request.url.toString())?.let {
                    builder.header("Cookie", it)
                }
                builder.header("Referer", "https://mce.msi.cn/forum.php")
                builder.header(
                    "User-Agent",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 " +
                        "Mobile/15E148 Safari/604.1",
                )
            }
            chain.proceed(builder.build())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(cookieInterceptor)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}

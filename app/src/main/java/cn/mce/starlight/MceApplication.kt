package cn.mce.starlight

import android.app.Application
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
    }

    override fun newImageLoader(): ImageLoader {
        val cookieInterceptor = Interceptor { chain ->
            val request = chain.request()
            val cookie = WebCookies.cookieHeader(request.url.toString())
            val newRequest = if (!cookie.isNullOrBlank() && request.url.host.contains("mce.msi.cn")) {
                request.newBuilder().header("Cookie", cookie).build()
            } else {
                request
            }
            chain.proceed(newRequest)
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

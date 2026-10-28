package cn.mce.starlight

import android.app.Application
import cn.mce.starlight.core.CookieJar
import cn.mce.starlight.core.DebugDump
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

    /** v0.4.0：保留 ImageLoader 引用，系统内存告急时主动释放图片内存缓存 */
    private var imageLoaderRef: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        // 只做必要的同步初始化；ThumbCache 的 200 条记录解析已改为懒加载（见 ThumbCache.ensureLoaded）
        SettingsStore.init(this)
        CookieJar.init(this)
        // 兜底：清掉"强制电脑版模板"残留的 mobile Cookie（否则所有页面会按电脑版渲染、移动版解析全空）
        CookieJar.removeMobileCookies()
        cn.mce.starlight.data.ThumbCache.init(this)
        cn.mce.starlight.core.DraftStore.init(this)
        cn.mce.starlight.core.MessageCenter.init(this)
        cn.mce.starlight.core.PmReadStore.init(this)
        cn.mce.starlight.core.StarlightDailyStore.init(this)
        cn.mce.starlight.core.FeedCache.init(this)
        DebugDump.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val cookieInterceptor = Interceptor { chain ->
            val request = chain.request()
            // 精确匹配站点域名（旧写法 host.contains 会把 Cookie 发给 mce.msi.cn.evil.com）
            val host = request.url.host
            val isSite = host == "mce.msi.cn" || host.endsWith(".mce.msi.cn")
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

        // 只需要网络拦截器：它会跟随每次重定向重新加头（附件图常 301/302 跳转）。
        // 旧实现同时注册 application + network 两遍，每个请求白跑两遍。
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(cookieInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val loader = ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this).maxSizePercent(0.18).build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .build()
        imageLoaderRef = loader
        return loader
    }

    /**
     * v0.4.0：内存/资源占用优化——
     * 系统内存告急时释放图片内存缓存与列表内存缓存（磁盘缓存保留，界面不闪白）。
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            imageLoaderRef?.memoryCache?.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        }
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            cn.mce.starlight.core.FeedCache.trimMemory()
            cn.mce.starlight.data.ThreadImageCache.trimMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        imageLoaderRef?.memoryCache?.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        cn.mce.starlight.core.FeedCache.trimMemory()
        cn.mce.starlight.data.ThreadImageCache.trimMemory()
    }
}

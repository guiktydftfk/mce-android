package cn.mce.starlight.core

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * v0.4.3：设备标识（仅本地生成，不联网、不上传）。
 *
 * 目的：让用户反馈问题时，我们能用一个短码把「设备 + 论坛账号」对应起来。
 *
 * 格式：M-{论坛UID}-{4位哈希}   例：M-2782-3f9a（未登录时 UID 段为 anon）
 * 哈希输入：ANDROID_ID + 首次安装随机盐 + 包名 + 设备型号
 * 稳定性：同一设备同一账号，不卸载/清数据则固定；卸载重装或清数据会变。
 * 隐私：不采集 IMEI/MAC/序列号；只输出哈希后的短码。
 */
object DeviceIdentity {
    private const val PREFS = "mce_device_id"
    private const val KEY_SALT = "install_salt"

    /** 短哈希长度（4 位十六进制，配合 UID 足够区分） */
    private const val HASH_LEN = 4

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 生成/读取当前设备标识（形如 M-2782-3f9a） */
    fun current(): String {
        val ctx = appContext
        val hash = runCatching { shortHash(ctx) }.getOrNull() ?: "0000"
        val uid = SessionStore.uid
        return formatId(uid, hash)
    }

    /** 纯函数：格式化（便于单测） */
    fun formatId(uid: Int?, hash: String): String {
        val uidPart = if (uid != null && uid > 0) uid.toString() else "anon"
        val clean = hash.lowercase().filter { it.isLetterOrDigit() }.take(HASH_LEN).padEnd(HASH_LEN, '0')
        return "M-$uidPart-$clean"
    }

    private fun shortHash(context: Context?): String {
        val androidId = runCatching {
            Settings.Secure.getString(context?.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val salt = ensureSalt(context)
        val material = listOf(
            androidId,
            salt,
            context?.packageName.orEmpty(),
            android.os.Build.MODEL.orEmpty(),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(HASH_LEN)
    }

    @Synchronized
    private fun ensureSalt(context: Context?): String {
        val ctx = context ?: return "nosalt"
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_SALT, null)?.let { return CryptoBox.decrypt(it) ?: it }
        val fresh = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SALT, CryptoBox.encrypt(fresh)).apply()
        return fresh
    }
}

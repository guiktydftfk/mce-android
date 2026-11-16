package cn.mce.starlight.core

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent

/**
 * v0.4.8：应用锁（使用系统锁屏凭据解锁，不新增生物识别依赖）。
 *
 * 设备已设置锁屏（PIN/图案/密码/生物识别）时可用；否则设置里会提示不可启用。
 * 只做「打开应用时验证一次」与「切后台回来再验证」，不存储任何 PIN。
 */
object AppLock {

    fun isDeviceSecure(context: Context): Boolean =
        runCatching {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isDeviceSecure == true
        }.getOrDefault(false)

    /** 系统解锁确认页（返回 RESULT_OK 表示验证通过） */
    fun unlockIntent(context: Context, title: String = "解锁 MCE 星光班"): Intent? =
        runCatching {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                ?: return null
            km.createConfirmDeviceCredentialIntent(title, "请用设备锁屏密码 / 指纹解锁")
        }.getOrNull()
}
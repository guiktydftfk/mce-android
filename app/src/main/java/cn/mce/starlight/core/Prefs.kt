package cn.mce.starlight.core

import android.content.Context
import android.content.SharedPreferences

/**
 * v0.4.7：本地 SharedPreferences 访问统一入口。
 * 新代码不再各自 `context.getSharedPreferences(...)`，方便统一命名/迁移/清理。
 */
object Prefs {
    fun of(context: Context, name: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
}

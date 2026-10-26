# aMCE R8 规则（v0.3.3 批 6）
# Compose / Coil / OkHttp 都自带 consumer rules，这里只补 jsoup 与通用可读性配置。

# jsoup：HTML 解析库，保留以防反射/克隆相关裁剪
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Kotlin/协程元数据
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# 崩溃堆栈可读（混淆后仍保留行号与源文件名）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 应用数据模型（被 Compose 状态与 R8 内联同时引用，保守保留字段名便于调试）
-keep class cn.mce.starlight.data.** { *; }
-keep class cn.mce.starlight.core.** { *; }

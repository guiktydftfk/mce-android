package cn.mce.starlight.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * v0.4.7：宽屏（平板/横屏/分屏）标记。
 * 由 MainActivity 用 BoxWithConstraints 的真实窗口尺寸计算后提供，
 * 这样窗口旋转、分屏、自由窗口缩放时都能正确进入双栏，而不是依赖
 * LocalConfiguration.screenWidthDp（部分模拟器/自由窗口下该值不随窗口变化）。
 */
val LocalWideLayout = staticCompositionLocalOf { false }

/** v0.4.8：真实窗口宽度（dp），三栏判定用 */
val LocalWindowWidthDp = staticCompositionLocalOf { 0 }

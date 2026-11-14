package cn.mce.starlight

import cn.mce.starlight.core.WindowLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.4.7：平板/横屏双栏判定单测 */
class WindowLayoutTest {

    @Test
    fun `手机竖屏单栏`() {
        assertFalse(WindowLayout.isDualPane(411, 891))
    }

    @Test
    fun `小屏横屏（宽小于600）单栏`() {
        assertFalse(WindowLayout.isDualPane(599, 400))
    }

    @Test
    fun `手机横屏双栏`() {
        assertTrue(WindowLayout.isDualPane(891, 411))
    }

    @Test
    fun `平板竖屏双栏`() {
        assertTrue(WindowLayout.isDualPane(800, 1280))
    }

    @Test
    fun `平板横屏双栏`() {
        assertTrue(WindowLayout.isDualPane(1280, 800))
    }

    @Test
    fun `临界宽度600竖屏仍单栏`() {
        assertFalse(WindowLayout.isDualPane(600, 960))
    }

    @Test
    fun `折叠屏展开方屏双栏`() {
        assertTrue(WindowLayout.isDualPane(720, 720))
    }
}

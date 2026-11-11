package cn.mce.starlight.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.4.7：渲染层实体解码（含旧缓存里的双重转义） */
class HtmlTextTest {
    @Test
    fun decodesAstralNumericEntity() {
        assertEquals("七年了🥺🥺", HtmlText.decode("七年了&#129402;&#129402;"))
    }

    @Test
    fun decodesDoubleEscaped() {
        assertEquals("七年了🥺", HtmlText.decode("七年了&amp;#129402;"))
    }

    @Test
    fun plainTextUntouched() {
        assertEquals("普通标题", HtmlText.decode("普通标题"))
    }
}

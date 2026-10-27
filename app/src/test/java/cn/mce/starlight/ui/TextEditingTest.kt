package cn.mce.starlight.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.3.6：发帖页 / 回帖框共用的光标插入与 @ BBCode 生成 */
class TextEditingTest {

    @Test
    fun insertAtCursor_inMiddle_keepsOrderAndCursor() {
        val field = TextFieldValue("回复", TextRange(1))
        val out = field.insertAtCursor("{:5_176:}")
        assertEquals("回{:5_176:}复", out.text)
        assertEquals(10, out.selection.start)
        assertEquals(out.selection.start, out.selection.end)
    }

    @Test
    fun insertAtCursor_withSelection_replacesSelection() {
        val field = TextFieldValue("abcdef", TextRange(1, 4))
        val out = field.insertAtCursor("@Sera")
        // 选区 bcd 被替换成 @Sera，光标落在插入文本末尾
        assertEquals("a@Seraef", out.text)
        assertEquals(6, out.selection.start)
    }

    @Test
    fun insertAtCursor_emptyInsert_isNoop() {
        val field = TextFieldValue("abc", TextRange(2))
        val out = field.insertAtCursor("")
        assertEquals("abc", out.text)
        assertEquals(2, out.selection.start)
    }

    @Test
    fun wrapSelection_noSelection_insertsPlaceholderWithCursorInside() {
        val field = TextFieldValue("文字", TextRange(2))
        val out = field.wrapSelection("[b]", "[/b]", "加粗文字")
        assertEquals("文字[b]加粗文字[/b]", out.text)
        // 光标落在占位文本中间：2 + "[b]".length + "加粗文字".length
        assertEquals(2 + 3 + 4, out.selection.start)
    }

    @Test
    fun wrapSelection_withSelection_wrapsSelectedText() {
        val field = TextFieldValue("请加粗这里", TextRange(1, 4))
        val out = field.wrapSelection("[b]", "[/b]", "占位")
        assertEquals("请[b]加粗这[/b]里", out.text)
        // v0.3.10：包裹后保持「被包裹的文字」处于选中态，方便连续叠加颜色/斜体等样式
        assertEquals(1 + 3, out.selection.min)
        assertEquals(1 + 3 + 3, out.selection.max)
    }

    @Test
    fun mentionBbcode_withUid_usesDiscuzUrlBbcode() {
        assertEquals(
            "[url=home.php?mod=space&uid=42]@流云闲鹿[/url] ",
            mentionBbcode(42, "流云闲鹿"),
        )
    }

    @Test
    fun mentionBbcode_withoutUid_fallsBackToPlainMention() {
        assertEquals("@流云闲鹿 ", mentionBbcode(null, "流云闲鹿"))
        assertEquals("@流云闲鹿 ", mentionBbcode(0, "流云闲鹿"))
    }

    @Test
    fun mentionBbcode_blankName_returnsEmpty() {
        assertEquals("", mentionBbcode(42, "   "))
    }
}

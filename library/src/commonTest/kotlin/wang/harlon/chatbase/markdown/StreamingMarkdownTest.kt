package wang.harlon.chatbase.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StreamingMarkdownTest {

    @Test
    fun pending_equals_line_turns_previous_line_into_heading() {
        // 问题本身：流式切块恰好停在单独的 `=` 上，上一行被解析成一级标题
        assertIs<MdBlock.Heading>(parseMarkdown("\\end{pmatrix}\n=").blocks.single())
        assertIs<MdBlock.Heading>(parseMarkdown("以下几点：\n-").blocks.single())
    }

    @Test
    fun pending_underline_is_held_back() {
        assertEquals("\\end{pmatrix}\n", "\\end{pmatrix}\n=".withoutPendingSetextUnderline())
        assertEquals("以下几点：\n", "以下几点：\n-".withoutPendingSetextUnderline())
        assertEquals("标题\n", "标题\n   ===  ".withoutPendingSetextUnderline())
        assertEquals("标题\n", "标题\n---".withoutPendingSetextUnderline())
        assertIs<MdBlock.Paragraph>(parseMarkdown("\\end{pmatrix}\n=".withoutPendingSetextUnderline()).blocks.single())
    }

    @Test
    fun other_tails_are_kept() {
        listOf(
            "\\end{pmatrix}\n= \\left(", // 下划线行已有正文，不再可能是 setext
            "标题\n===\n", // 已换行结束，是定稿内容
            "标题\n    ===", // 4 个空格缩进不是 setext 下划线
            "标题\n=-", // 混用两种字符不是 setext 下划线
            "===", // 只有一行，上面没有可升格的段落
            "",
        ).forEach { assertEquals(it, it.withoutPendingSetextUnderline()) }
    }
}

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
        // 引用块里同样会升格
        val quote = assertIs<MdBlock.Quote>(parseMarkdown("> 引用\n> -").blocks.single())
        assertIs<MdBlock.Heading>(quote.blocks.single())
    }

    @Test
    fun pending_underline_is_held_back() {
        assertEquals("\\end{pmatrix}\n", "\\end{pmatrix}\n=".withoutPendingSetextUnderline())
        assertEquals("以下几点：\n", "以下几点：\n-".withoutPendingSetextUnderline())
        assertEquals("标题\n", "标题\n   ===  ".withoutPendingSetextUnderline())
        assertEquals("标题\n", "标题\n---".withoutPendingSetextUnderline())
        assertEquals("> 引用\n", "> 引用\n> -".withoutPendingSetextUnderline())
        assertEquals("- 列表\n  - 子项\n", "- 列表\n  - 子项\n    =".withoutPendingSetextUnderline())
        assertIs<MdBlock.Paragraph>(parseMarkdown("\\end{pmatrix}\n=".withoutPendingSetextUnderline()).blocks.single())
    }

    @Test
    fun other_tails_are_kept() {
        listOf(
            "\\end{pmatrix}\n= \\left(", // 下划线行已有正文，不再可能是 setext
            "标题\n===\n", // 已换行结束，是定稿内容
            "标题\n=-", // 混用两种字符不是 setext 下划线
            "===", // 只有一行，上面没有可升格的段落
            "",
        ).forEach { assertEquals(it, it.withoutPendingSetextUnderline()) }
    }

    @Test
    fun pending_display_math_is_cut_off() {
        listOf(
            "前文\n\n\$\$\n\\begin{pmatrix} a & b" to "前文\n\n",
            "前文\n\n\\[\nx = " to "前文\n\n",
            "前文\n\n\$\$" to "前文\n\n", // 只吐出了开头定界符
            "\$\$a\$\$\n\n\$\$\nb" to "\$\$a\$\$\n\n", // 前一个已闭合，截在后一个
        ).forEach { (source, head) ->
            val view = streamingMarkdown(source)
            assertEquals(head, view.markdown, source)
            assertEquals(true, view.pendingMath, source)
        }
    }

    @Test
    fun closed_or_non_math_dollars_are_kept() {
        listOf(
            "\$\$x\$\$ 后文",
            "前文\n\n\$\$\nx\n\$\$", // 已闭合
            "```\n\$\$\n", // 围栏代码块里
            "`\$\$` 是块级定界符", // 行内代码里
            "价格 \$\$ 很贵\n\n后文", // 跨空行，不是公式
        ).forEach { source ->
            val view = streamingMarkdown(source)
            assertEquals(source, view.markdown, source)
            assertEquals(false, view.pendingMath, source)
        }
    }

    @Test
    fun setext_hold_back_still_applies_without_pending_math() {
        val view = streamingMarkdown("\\end{pmatrix}\n=")
        assertEquals("\\end{pmatrix}\n", view.markdown)
        assertEquals(false, view.pendingMath)
    }
}

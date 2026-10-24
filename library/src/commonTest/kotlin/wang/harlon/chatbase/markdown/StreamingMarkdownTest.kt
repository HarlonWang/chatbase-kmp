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

    private fun stream(md: String) = parseMarkdown(md, streaming = true).blocks

    private val pending = MdBlock.Math("", pending = true)

    @Test
    fun unclosed_display_math_becomes_pending_block() {
        assertEquals(
            listOf(MdBlock.Paragraph(listOf(MdInline.Text("前文"))), MdBlock.Math("\\begin{pmatrix} a", pending = true)),
            stream("前文\n\n\$\$\n\\begin{pmatrix} a"),
        )
        assertEquals(MdBlock.Math("x =", pending = true), stream("前文\n\n\\[\nx = ").last())
        assertEquals(pending, stream("前文\n\n\$\$").last()) // 只吐出了开头定界符
        // 前一个已闭合，后一个未闭合
        assertEquals(listOf(MdBlock.Math("a"), MdBlock.Math("b", pending = true)), stream("\$\$a\$\$\n\n\$\$\nb"))
    }

    @Test
    fun pending_math_stays_in_its_container() {
        val quote = assertIs<MdBlock.Quote>(stream("> 说明\n>\n> \$\$x").single())
        assertEquals(listOf(MdBlock.Paragraph(listOf(MdInline.Text("说明"))), MdBlock.Math("x", pending = true)), quote.blocks)

        val list = assertIs<MdBlock.OrderedList>(stream("1. 步骤：\n\n   \$\$\n   x = ").single())
        assertEquals(MdBlock.Math("x =", pending = true), list.items.single().last())

        // 紧跟在正文下一行：和已闭合时一样，是段落里的块级公式
        val paragraph = assertIs<MdBlock.Paragraph>(stream("前文\n\$\$\nx").single())
        assertEquals(MdInline.Math("x", display = true, pending = true), paragraph.inline.last())
    }

    @Test
    fun unclosed_delimiters_not_at_line_start_stay_text() {
        listOf(
            "[文档](https://example.test/\\[)", // 链接地址里
            "价格 \$\$ 很贵", // 段落中间
            "```\n\$\$\n", // 围栏代码块里
            "`\$\$` 是块级定界符", // 行内代码里
            "\$\$\n前文\n\n后文", // 跨空行，不是公式
        ).forEach { md ->
            assertEquals(parseMarkdown(md), parseMarkdown(md, streaming = true), md)
        }
    }

    @Test
    fun non_streaming_keeps_unclosed_delimiters_as_text() {
        val blocks = parseMarkdown("前文\n\n\$\$\nx")
        assertEquals(2, blocks.blocks.size)
        assertIs<MdBlock.Paragraph>(blocks.blocks.last())
    }
}

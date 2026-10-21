package wang.harlon.chatbase.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 公式抽出与回填：和方言套件一样两端同跑。样本偏向真实模型输出——OpenAI 系模型爱用 `\(` `\[`，
 * 其他模型爱用 `$` `$$`，金额和代码里的 `$` 必须保持原样。
 */
class MarkdownMathTest {

    private fun parse(md: String): List<MdBlock> = parseMarkdown(md).blocks

    private fun text(s: String) = MdInline.Text(s)

    private fun inlineMath(latex: String) = MdInline.Math(latex, display = false)

    private fun paragraph(md: String): List<MdInline> = assertIs<MdBlock.Paragraph>(parse(md).single()).inline

    @Test
    fun backslash_paren_inline_survives_markdown_escapes() {
        // 不抽出的话 commonmark 会把 \( 吃成 (，把 _ 当强调
        assertEquals(
            listOf(text("面积是 "), inlineMath("\\frac{a_1}{2}"), text("。")),
            paragraph("面积是 \\(\\frac{a_1}{2}\\)。"),
        )
    }

    @Test
    fun backslash_bracket_alone_becomes_block_math() {
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(text("推导如下"))),
                MdBlock.Math("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"),
            ),
            parse("推导如下\n\n\\[ x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} \\]"),
        )
    }

    @Test
    fun double_dollar_multiline_block() {
        val blocks = parse("\$\$\n\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\end{pmatrix}\n\$\$\n\n后文")
        assertEquals(MdBlock.Math("\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\end{pmatrix}"), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(text("后文"))), blocks[1])
    }

    @Test
    fun double_dollar_inside_paragraph_stays_inline_display() {
        assertEquals(
            listOf(text("结论 "), MdInline.Math("E = mc^2", display = true), text(" 成立")),
            paragraph("结论 \$\$E = mc^2\$\$ 成立"),
        )
    }

    @Test
    fun single_dollar_inline() {
        assertEquals(
            listOf(text("令 "), inlineMath("x^2 + y^2 = r^2"), text(" 即可")),
            paragraph("令 \$x^2 + y^2 = r^2\$ 即可"),
        )
    }

    @Test
    fun currency_is_not_math() {
        assertEquals(listOf(text("价格从 \$5 涨到 \$10，涨了 \$5。")), paragraph("价格从 \$5 涨到 \$10，涨了 \$5。"))
        assertEquals(listOf(text("区间 \$5-\$10 之间")), paragraph("区间 \$5-\$10 之间"))
    }

    @Test
    fun prose_between_dollars_is_not_math() {
        assertEquals(listOf(text("he said \$this is money\$ ok")), paragraph("he said \$this is money\$ ok"))
    }

    @Test
    fun escaped_dollar_is_literal() {
        assertEquals(listOf(text("共 \$3，变量 "), inlineMath("n")), paragraph("共 \\\$3，变量 \$n\$"))
    }

    @Test
    fun math_in_list_item_and_heading() {
        val blocks = parse("## 公式 \$a+b\$\n\n- 第一项 \\(\\alpha\\)\n- 第二项")
        assertEquals(MdBlock.Heading(2, listOf(text("公式 "), inlineMath("a+b"))), blocks[0])
        val list = assertIs<MdBlock.BulletList>(blocks[1])
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph(listOf(text("第一项 "), inlineMath("\\alpha")))), list.items[0])
    }

    @Test
    fun math_inside_strong() {
        assertEquals(
            listOf(MdInline.Strong(listOf(text("结果 "), inlineMath("x=1")))),
            paragraph("**结果 \$x=1\$**"),
        )
    }

    @Test
    fun fenced_code_is_untouched() {
        val blocks = parse("```bash\necho \$HOME and \$PATH\n```")
        assertEquals(listOf<MdBlock>(MdBlock.CodeBlock("echo \$HOME and \$PATH", "bash")), blocks)
    }

    @Test
    fun inline_code_is_untouched() {
        assertEquals(
            listOf(text("变量 "), MdInline.Code("\$x\$"), text(" 与 "), inlineMath("y")),
            paragraph("变量 `\$x\$` 与 \$y\$"),
        )
    }

    @Test
    fun placeholder_inside_indented_code_falls_back_to_source() {
        // 缩进代码块不在抽出阶段识别，占位符进了代码字面量后要换回原文
        val blocks = parse("前文：\n\n    cost = \$a\$ + 1")
        assertEquals(MdBlock.CodeBlock("cost = \$a\$ + 1", ""), blocks[1])
    }

    /** 流式中间态：定界符还没闭合时按普通文本显示，不闪成半截公式。`\[` 留给 commonmark 转义成 `[`，与接入前一致。 */
    @Test
    fun unclosed_delimiters_stay_literal() {
        assertEquals(listOf(text("推导 [ x =")), paragraph("推导 \\[ x = "))
        assertEquals(listOf(text("推导 \$\$ x =")), paragraph("推导 \$\$ x = "))
        assertEquals(listOf(text("推导 \$x =")), paragraph("推导 \$x = "))
    }

    @Test
    fun plain_text_degrades_to_latex_source() {
        assertEquals("面积 \\frac{a}{2} 哈", paragraph("面积 \$\\frac{a}{2}\$ 哈").plainText())
    }

    @Test
    fun text_without_math_is_unchanged() {
        assertEquals(listOf(text("普通 "), MdInline.Strong(listOf(text("加粗")))), paragraph("普通 **加粗**"))
    }
}

package wang.harlon.chatbase.markdown

/**
 * SDK 自有的 Markdown AST：渲染层只认它，解析器是可替换件——
 * Android 映射自 commonmark-java，iOS 映射自 cmark-gfm，两端行为由 commonTest
 * 方言套件（MarkdownDialectTest）锁定。节点集合只覆盖渲染层实际消费的形态。
 */
data class MdDocument(val blocks: List<MdBlock>)

sealed interface MdBlock {
    data class Heading(val level: Int, val inline: List<MdInline>) : MdBlock
    data class Paragraph(val inline: List<MdInline>) : MdBlock

    /** 围栏与缩进代码块统一形态；缩进块 [language] 为空串。literal 不含末尾换行。 */
    data class CodeBlock(val literal: String, val language: String) : MdBlock

    data class BulletList(val items: List<List<MdBlock>>) : MdBlock
    data class OrderedList(val start: Int, val items: List<List<MdBlock>>) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data object ThematicBreak : MdBlock
    data class Table(val header: List<MdTableCell>, val rows: List<List<MdTableCell>>) : MdBlock

    /**
     * 独立成段的块级公式（`$$…$$` 或 `\[…\]` 单独占一段）。[latex] 不含定界符。
     * [pending] 为 true 表示流式中还没写到闭合串，[latex] 是已到达的半截内容，渲染层显示占位。
     */
    data class Math(val latex: String, val pending: Boolean = false) : MdBlock
}

data class MdTableCell(val alignment: MdAlign, val inline: List<MdInline>)

enum class MdAlign { START, CENTER, END }

sealed interface MdInline {
    data class Text(val literal: String) : MdInline
    data class Emphasis(val children: List<MdInline>) : MdInline
    data class Strong(val children: List<MdInline>) : MdInline
    data class Code(val literal: String) : MdInline
    data class Link(val destination: String, val children: List<MdInline>) : MdInline
    data class Image(val destination: String, val children: List<MdInline>) : MdInline
    data object SoftBreak : MdInline
    data object HardBreak : MdInline

    /**
     * 公式，[latex] 不含定界符。[display] 为 true 表示源串用的是块级定界符但混在段落文字里
     * （如列表项里的 `$$…$$`），渲染时按块级样式单独占一行。[pending] 同 [MdBlock.Math.pending]。
     */
    data class Math(val latex: String, val display: Boolean, val pending: Boolean = false) : MdInline
}

/**
 * 解析 Markdown（CommonMark + GFM 表格 + LaTeX 公式）。解析器线程安全、可重复调用。
 *
 * 公式必须在方言解析器之前抽出：`\(`、`\[` 会被当成反斜杠转义吃掉，`_`、`*` 会被当成强调。
 * 抽出与回填都在公共层（[extractMath] / [restoreMath]），两端方言解析器零改动，
 * commonTest 里的公式用例自然两端同跑。
 *
 * @param streaming 内容仍在流式追加：行首起头、到结尾都没有闭合串的块级公式按「还没写完」处理，
 *   解析成 pending 的公式节点并丢弃其后的内容，留在它所在的列表项、引用块里；非流式下未闭合的定界符按普通文本
 */
fun parseMarkdown(markdown: String, streaming: Boolean = false): MdDocument {
    val extraction = extractMath(markdown, streaming)
    if (extraction.spans.isEmpty()) return parseMarkdownDialect(markdown)
    return restoreMath(parseMarkdownDialect(extraction.markdown), extraction)
}

/** 平台方言解析器：Android 映射自 commonmark-java，iOS 映射自 cmark-gfm，只认 CommonMark + GFM 表格。 */
internal expect fun parseMarkdownDialect(markdown: String): MdDocument

/** 拍平 inline 子树的全部文本字面量（图片 alt、纯文本降级用）。 */
fun List<MdInline>.plainText(): String = buildString { appendPlainText(this@plainText) }

private fun StringBuilder.appendPlainText(nodes: List<MdInline>) {
    nodes.forEach { node ->
        when (node) {
            is MdInline.Text -> append(node.literal)
            is MdInline.Code -> append(node.literal)
            is MdInline.Emphasis -> appendPlainText(node.children)
            is MdInline.Strong -> appendPlainText(node.children)
            is MdInline.Link -> appendPlainText(node.children)
            is MdInline.Image -> appendPlainText(node.children)
            is MdInline.Math -> append(node.latex)
            MdInline.SoftBreak -> append(" ")
            MdInline.HardBreak -> append("\n")
        }
    }
}

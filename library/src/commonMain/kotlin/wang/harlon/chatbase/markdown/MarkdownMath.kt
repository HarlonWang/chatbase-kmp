package wang.harlon.chatbase.markdown

/**
 * LaTeX 公式的抽出与回填：解析前把源串里的公式换成占位符，解析后在 AST 上换回 Math 节点。
 *
 * 识别四种定界符：`$$…$$`、`\[…\]` 为块级，`\(…\)`、`$…$` 为行内。
 * 围栏代码块与行内代码原样跳过；未闭合的定界符一律按普通文本处理，流式中间态不会闪成公式。
 * 单 `$` 走 Pandoc 式规则防金额误伤：开头后不能是空白，结尾前不能是空白、结尾后不能跟数字。
 */
internal class MathSpan(val latex: String, val display: Boolean, val source: String)

/**
 * 抽出结果。[placeholder] 是本次使用的占位符模式：占位符由一对私用区字符夹一个序号构成，
 * 两端方言解析器都会原样当文本透传；字符对按源文本动态挑选，源文本里本来就有的同形序列不会被误当占位符。
 */
internal class MathExtraction(val markdown: String, val spans: List<MathSpan>, val placeholder: Regex?)

/** 候选的占位符字符对，全部在 Unicode 私用区；逐对挑第一对源文本里都不含的。 */
private val placeholderCandidates = listOf('\uE000' to '\uE001', '\uE010' to '\uE011', '\uF8F0' to '\uF8F1')

internal fun extractMath(markdown: String): MathExtraction {
    if (markdown.indexOf('$') < 0 && markdown.indexOf('\\') < 0) return MathExtraction(markdown, emptyList(), null)
    val (start, end) = placeholderCandidates.firstOrNull { (s, e) -> markdown.indexOf(s) < 0 && markdown.indexOf(e) < 0 }
        // 三对都撞上几乎不可能；真撞上就放弃抽公式，按接入前的行为原样显示
        ?: return MathExtraction(markdown, emptyList(), null)
    return MathScanner(markdown, start, end).run()
}

private class MathScanner(private val src: String, private val start: Char, private val end: Char) {
    private val out = StringBuilder(src.length)
    private val spans = ArrayList<MathSpan>()
    private var pos = 0
    private var atLineStart = true

    /** 当前所在围栏代码块的开栏串（如 "```"），不在围栏内为 null。 */
    private var fence: String? = null

    fun run(): MathExtraction {
        while (pos < src.length) {
            if (atLineStart && handleLineStart()) continue
            when (val c = src[pos]) {
                '\n' -> {
                    out.append(c)
                    pos++
                    atLineStart = true
                }
                '\\' -> handleBackslash()
                '`' -> handleBacktick()
                '$' -> handleDollar()
                else -> {
                    out.append(c)
                    pos++
                }
            }
        }
        val placeholder = if (spans.isEmpty()) null else Regex("$start(\\d+)$end")
        return MathExtraction(out.toString(), spans, placeholder)
    }

    /** 行首：维护围栏代码块状态；在围栏内整行原样复制。返回 true 表示已消费整行。 */
    private fun handleLineStart(): Boolean {
        atLineStart = false
        val lineEnd = src.indexOf('\n', pos).let { if (it < 0) src.length else it }
        val line = stripQuotePrefix(src.substring(pos, lineEnd))
        val run = fenceRun(line)
        val current = fence
        if (current == null) {
            if (run == null || !opensFence(line, run)) return false
            fence = run
        } else if (run != null && run[0] == current[0] && run.length >= current.length && closesFence(line, run)) {
            fence = null
        }
        out.append(src, pos, lineEnd)
        if (lineEnd < src.length) out.append('\n')
        pos = lineEnd + 1
        atLineStart = true
        return true
    }

    /** 行首最多 3 个空格后连续 3 个及以上的 ` 或 ~ 即围栏；返回围栏串本身。 */
    private fun fenceRun(line: String): String? {
        var i = 0
        while (i < line.length && i < 3 && line[i] == ' ') i++
        if (i >= line.length || (line[i] != '`' && line[i] != '~')) return null
        val ch = line[i]
        var end = i
        while (end < line.length && line[end] == ch) end++
        return if (end - i >= 3) line.substring(i, end) else null
    }

    /** 引用块里的围栏带 `> ` 前缀（可嵌套），去掉前缀再判围栏，否则引用里的代码块会被当成正文扫公式。 */
    private fun stripQuotePrefix(line: String): String {
        var rest = line
        while (true) {
            var i = 0
            while (i < rest.length && i < 3 && rest[i] == ' ') i++
            if (i >= rest.length || rest[i] != '>') return rest
            i++
            if (i < rest.length && rest[i] == ' ') i++
            rest = rest.substring(i)
        }
    }

    /** CommonMark：反引号开栏的 info string 不能再含反引号，"```foo``` bar" 是行内代码不是围栏。 */
    private fun opensFence(line: String, run: String): Boolean =
        run[0] != '`' || line.indexOf('`', line.indexOf(run) + run.length) < 0

    /** CommonMark：闭栏行的围栏串之后只能是空白，"``` text" 这种是代码内容不是闭栏。 */
    private fun closesFence(line: String, run: String): Boolean {
        val after = line.indexOf(run) + run.length
        return line.substring(after).isBlank()
    }

    private fun handleBackslash() {
        val next = src.getOrNull(pos + 1)
        when (next) {
            '[' -> if (tryDelimited(open = "\\[", close = "\\]", display = true)) return
            '(' -> if (tryDelimited(open = "\\(", close = "\\)", display = false)) return
        }
        // 其他转义（含 \$、\\）原样透传，连同被转义的字符一起跳过，避免把 \$ 当公式开头
        out.append('\\')
        pos++
        if (next != null) {
            out.append(next)
            pos++
        }
    }

    /** 行内代码：找到长度完全相同的闭合反引号串后整段原样复制；找不到则把反引号当普通字符。 */
    private fun handleBacktick() {
        val runLength = runLengthAt(pos, '`')
        var i = pos + runLength
        while (i < src.length) {
            if (src[i] != '`') {
                i++
                continue
            }
            val closing = runLengthAt(i, '`')
            if (closing == runLength) {
                out.append(src, pos, i + closing)
                pos = i + closing
                return
            }
            i += closing
        }
        out.append(src, pos, pos + runLength)
        pos += runLength
    }

    private fun handleDollar() {
        if (src.getOrNull(pos + 1) == '$') {
            if (tryDelimited(open = "$$", close = "$$", display = true)) return
        } else if (tryInlineDollar()) {
            return
        }
        out.append('$')
        pos++
    }

    /** `\[…\]`、`\(…\)`、`$$…$$`：找最近的未转义闭合串，内容非空即抽出。 */
    private fun tryDelimited(open: String, close: String, display: Boolean): Boolean {
        val start = pos + open.length
        var end = src.indexOf(close, start)
        while (end >= 0 && src[end - 1] == '\\') end = src.indexOf(close, end + 1)
        if (end < 0) return false
        val latex = src.substring(start, end)
        // 公式不跨段：跨空行的闭合串多半是后文另一个公式或代码里的 $$，配对会吞掉中间的段落、列表和围栏
        if (latex.isBlank() || crossesBlankLine(latex)) return false
        emit(latex, display, source = src.substring(pos, end + close.length))
        pos = end + close.length
        return true
    }

    /**
     * 单 `$`：只看最近一个未转义的 `$`，它不满足闭合条件就把开头的 `$` 当普通字符，
     * 不继续往后找——否则「$5 和 $3 和 $x$」会被整段吞成公式。
     */
    private fun tryInlineDollar(): Boolean {
        val first = src.getOrNull(pos + 1) ?: return false
        if (first.isWhitespace()) return false
        var end = src.indexOf('$', pos + 1)
        while (end >= 0 && src[end - 1] == '\\') end = src.indexOf('$', end + 1)
        if (end < 0) return false
        if (src[end - 1].isWhitespace()) return false
        if (src.getOrNull(end + 1)?.isDigit() == true) return false
        val latex = src.substring(pos + 1, end)
        if (crossesBlankLine(latex) || !looksLikeMath(latex)) return false
        emit(latex, display = false, source = src.substring(pos, end + 1))
        pos = end + 1
        return true
    }

    /** 公式内部（去掉首尾换行后）出现空行，即跨了 Markdown 段落边界。 */
    private fun crossesBlankLine(latex: String): Boolean = latex.trim().lineSequence().any { it.isBlank() }

    /** 含运算符、命令或结构符号，或本身就是一个不含空白的 token（变量名、数字），才当公式。 */
    private fun looksLikeMath(latex: String): Boolean =
        latex.any { it in MATH_HINT_CHARS } || latex.none { it.isWhitespace() }

    private fun emit(latex: String, display: Boolean, source: String) {
        out.append(start).append(spans.size).append(end)
        spans.add(MathSpan(latex.trim(), display, source))
    }

    private fun runLengthAt(index: Int, ch: Char): Int {
        var end = index
        while (end < src.length && src[end] == ch) end++
        return end - index
    }

    private companion object {
        const val MATH_HINT_CHARS = "\\^_={}+*/<>|[]"
    }
}

/** 把占位符换回 Math 节点；落进代码字面量或链接地址的占位符换回原文，不留私用区字符。 */
internal fun restoreMath(document: MdDocument, extraction: MathExtraction): MdDocument {
    val placeholder = extraction.placeholder ?: return document
    val restorer = MathRestorer(extraction.spans, placeholder)
    return MdDocument(document.blocks.map { restorer.restoreBlock(it) })
}

private class MathRestorer(private val spans: List<MathSpan>, private val placeholder: Regex) {

    fun restoreBlock(block: MdBlock): MdBlock = when (block) {
        is MdBlock.Heading -> block.copy(inline = restoreInlines(block.inline))
        is MdBlock.Paragraph -> {
            val inline = restoreInlines(block.inline)
            promoteDisplayMath(inline) ?: MdBlock.Paragraph(inline)
        }
        is MdBlock.CodeBlock -> block.copy(literal = restoreSource(block.literal))
        is MdBlock.BulletList -> block.copy(items = block.items.map { item -> item.map { restoreBlock(it) } })
        is MdBlock.OrderedList -> block.copy(items = block.items.map { item -> item.map { restoreBlock(it) } })
        is MdBlock.Quote -> block.copy(blocks = block.blocks.map { restoreBlock(it) })
        is MdBlock.Table -> block.copy(
            header = block.header.map { it.copy(inline = restoreInlines(it.inline)) },
            rows = block.rows.map { row -> row.map { it.copy(inline = restoreInlines(it.inline)) } },
        )
        MdBlock.ThematicBreak, is MdBlock.Math -> block
    }

    private fun restoreInlines(nodes: List<MdInline>): List<MdInline> =
        nodes.flatMap { node ->
            when (node) {
                is MdInline.Text -> splitText(node.literal)
                is MdInline.Emphasis -> listOf(node.copy(children = restoreInlines(node.children)))
                is MdInline.Strong -> listOf(node.copy(children = restoreInlines(node.children)))
                is MdInline.Code -> listOf(node.copy(literal = restoreSource(node.literal)))
                is MdInline.Link -> listOf(
                    node.copy(destination = restoreSource(node.destination), children = restoreInlines(node.children)),
                )
                is MdInline.Image -> listOf(
                    node.copy(destination = restoreSource(node.destination), children = restoreInlines(node.children)),
                )
                is MdInline.Math, MdInline.SoftBreak, MdInline.HardBreak -> listOf(node)
            }
        }

    private fun splitText(literal: String): List<MdInline> {
        val result = ArrayList<MdInline>()
        var last = 0
        placeholder.findAll(literal).forEach { match ->
            if (match.range.first > last) result.add(MdInline.Text(literal.substring(last, match.range.first)))
            val span = spans.getOrNull(match.groupValues[1].toInt())
            result.add(if (span != null) MdInline.Math(span.latex, span.display) else MdInline.Text(match.value))
            last = match.range.last + 1
        }
        if (result.isEmpty()) return listOf(MdInline.Text(literal))
        if (last < literal.length) result.add(MdInline.Text(literal.substring(last)))
        return result
    }

    private fun restoreSource(text: String): String =
        placeholder.replace(text) { match -> spans.getOrNull(match.groupValues[1].toInt())?.source ?: match.value }
}

/** 段落只有一个块级公式（允许周围空白与软换行）时提升为块级节点。 */
private fun promoteDisplayMath(inline: List<MdInline>): MdBlock.Math? {
    var math: MdInline.Math? = null
    inline.forEach { node ->
        when {
            node is MdInline.Math && node.display && math == null -> math = node
            node is MdInline.Text && node.literal.isBlank() -> Unit
            node == MdInline.SoftBreak -> Unit
            else -> return null
        }
    }
    return math?.let { MdBlock.Math(it.latex) }
}


package wang.harlon.chatbase.markdown

/**
 * 可能成为 setext 下划线的行：行首只有空白和引用符 `>`（覆盖引用块、嵌套列表），之后一串 `=` 或一串 `-`。
 * 故意放宽、不看上下文：误判只是让一行还没写完的内容晚一块显示（如未闭合代码块里的 `---`），
 * 漏判才会让标题一跳一落。
 */
private val SETEXT_UNDERLINE = Regex("^[ \\t>]*(=+|-+)[ \\t]*$")

/**
 * 流式中途，末尾还没写完的一行若恰好只有 `=` 或 `-`，会被 CommonMark 当成 setext 下划线，
 * 把上一行整段升格成标题；下一块文字到达（如 `= \left(`、`- 列表项`）又降回正文，内容高度一跳一落。
 * 这一行先不交给解析器，等它写完再说。只看最后一行：已换行结束的行是定稿内容，按原样解析。
 */
internal fun String.withoutPendingSetextUnderline(): String {
    val lineStart = lastIndexOf('\n') + 1
    if (lineStart == 0) return this
    return if (SETEXT_UNDERLINE.matches(substring(lineStart))) substring(0, lineStart) else this
}

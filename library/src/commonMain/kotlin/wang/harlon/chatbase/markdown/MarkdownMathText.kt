package wang.harlon.chatbase.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.LatexAutoWrap
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

/**
 * 公式渲染接线（huarangmeng/latex，纯 Compose）。
 *
 * - 行内公式由 [InlineText] 经 `InlineTextContent` 嵌进 `Text`，与周围文字共享基线；
 * - 块级公式走 [MathBlock]：`LatexAutoWrap` 在关系符、加减号处换行，不横向滚动；
 * - 两条路径的尺寸都由 [LatexMeasurerState] 同步预测量，解析或测量失败时退回源码，不留空白。
 *
 * 主题只给前景色、背景透明，公式不会在气泡里画出一块色底。
 */
@Composable
internal fun rememberMathConfig(textStyle: TextStyle, scale: Float = 1f): LatexConfig {
    val color = textStyle.color.takeOrElse { LocalContentColor.current }
    val base = if (textStyle.fontSize.isSpecified) textStyle.fontSize else 16.sp
    val fontSize = base * scale
    return remember(fontSize, color) {
        LatexConfig(
            fontSize = fontSize,
            theme = LatexTheme.light(color = color),
            // 不开 enableLayoutCache：它以 AST 子树 + 渲染上下文为哈希键，每个节点查缓存都要递归哈希整棵子树，
            // Redmi 8A 实测单个公式的测量从 1 ms 涨到 15 到 44 ms，是滚动长帧的主因
            enableLayoutCache = false,
        )
    }
}

@Composable
internal fun rememberMathMeasurer(config: LatexConfig): LatexMeasurerState = rememberLatexMeasurer(config)

/** 块级公式比正文略大一号，和 KaTeX display 模式观感一致。 */
private const val BLOCK_MATH_SCALE = 1.05f

@Composable
internal fun MathBlock(latex: String, textStyle: TextStyle) {
    val config = rememberMathConfig(textStyle, scale = BLOCK_MATH_SCALE)
    val measurer = rememberMathMeasurer(config)
    val dimensions = remember(latex, measurer, config) { measurer.measure(latex, config) }
    if (dimensions == null) {
        // 解析或测量失败：按代码块展示源码，复制按钮顺带可用
        CodeBlock(code = latex, language = "latex")
        return
    }
    // Latex() 在后台解析，首帧是空文档；用预测量高度托底，列表里不会先塌成 0 再撑开
    val minHeight = with(LocalDensity.current) { dimensions.heightPx.toDp() }
    Box(Modifier.fillMaxWidth().heightIn(min = minHeight).padding(vertical = 2.dp)) {
        LatexAutoWrap(latex = latex, config = config)
    }
}

/**
 * 流式中还没写完的块级公式（pending 的 Math 节点）：不显示半截源码，先占一条骨架。
 * 高度只有一个字高，比任何公式都矮，公式闭合时内容只往上推。
 */
@Composable
internal fun PendingMathBlock(textStyle: TextStyle) {
    val base = if (textStyle.fontSize.isSpecified) textStyle.fontSize else 16.sp
    val barHeight = with(LocalDensity.current) { base.toDp() }
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(
            Modifier
                .fillMaxWidth(0.4f)
                .height(barHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
        )
    }
}

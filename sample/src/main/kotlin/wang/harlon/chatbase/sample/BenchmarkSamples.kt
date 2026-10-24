package wang.harlon.chatbase.sample

import wang.harlon.chatbase.model.ChatMessage
import wang.harlon.chatbase.model.Role

/**
 * 性能基准用的固定会话：滚动、流式、内存三类测量共用同一批样本，前后两次测量才可比。
 *
 * 不依赖 [SampleData]：基准要能原样搬到没有公式支持的旧分支上跑对照，
 * 那边同样的文本会按原文显示，正好是「接入前」的成本。
 */
object BenchmarkSamples {

    /** 一轮问答的数量；30 轮约 60 条消息，低端机上要滚好几屏。 */
    private const val TURNS = 30

    /** 纯文本会话：段落 + 列表 + 行内代码，不含任何 `$` 和 `\`，走公式抽出的早退路径。 */
    val plain: List<ChatMessage> by lazyConversation { i ->
        """
        ## 第 $i 节：结构化并发

        协程的**作用域**决定了子任务的生命周期，父作用域取消时所有子协程一并取消，`Job` 之间形成树。
        调度器决定代码跑在哪个线程：`Dispatchers.IO` 用于网络与磁盘，`Default` 用于计算密集任务。

        - 用 `withContext` 切换调度器，不要在主线程做阻塞调用
        - 用 `supervisorScope` 让单个子任务失败不影响兄弟任务
        - 用 `Flow` 表达随时间变化的一串值，冷流在收集时才开始执行

        > 小结：先想清楚谁拥有这个任务、谁负责取消它，再决定用哪个作用域。

        最后补充一点：异常沿着 Job 树向上传播，`CoroutineExceptionHandler` 只在根协程上生效，子协程里装了也不会被调用。
        """.trimIndent()
    }

    /** 公式会话：每条回复 10 个行内公式 + 3 个块级公式，覆盖分数、根式、求和、积分、矩阵和定界符。 */
    val math: List<ChatMessage> by lazyConversation { i ->
        """
        ## 第 $i 题：级数与积分

        设 ${'$'}a_n = \frac{1}{n^2}${'$'}，则 ${'$'}\sum_{n=1}^{\infty} a_n = \frac{\pi^2}{6}${'$'}，且 ${'$'}a_n > 0${'$'} 对所有 ${'$'}n \ge 1${'$'} 成立。
        对于 \(f(x) = e^{-x^2}\)，有 \(f'(x) = -2x e^{-x^2}\)，在 \(x = 0\) 处取极大值 \(f(0) = 1\)。
        判别式 ${'$'}\Delta = b^2 - 4ac${'$'} 决定二次方程根的个数，${'$'}\sqrt{\Delta}${'$'} 为实数时有两个实根。

        $$
        \int_{-\infty}^{\infty} e^{-x^2}\,dx = \sqrt{\pi}
        $$

        \[
        x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
        \]

        $$
        \begin{pmatrix} a & b \\ c & d \end{pmatrix}
        \begin{pmatrix} x \\ y \end{pmatrix}
        = \left( \begin{matrix} ax + by \\ cx + dy \end{matrix} \right)
        $$
        """.trimIndent()
    }

    /** 混合会话：真实回复形态，富文本、代码块、表格与公式交替出现，公式密度低于 [math]。 */
    val mixed: List<ChatMessage> by lazyConversation { i ->
        when (i % 3) {
            0 -> SAMPLE_RICH
            1 -> plain[1].content
            else -> """
                ## 第 $i 题：求根公式

                对于 \(ax^2 + bx + c = 0\)，判别式 ${'$'}\Delta = b^2 - 4ac${'$'}：

                \[
                x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
                \]

                价格从 ${'$'}5 涨到 ${'$'}10 不应被当成公式，代码里的 `${'$'}HOME` 也不应。

                ```kotlin
                fun roots(a: Double, b: Double, c: Double) = (-b + sqrt(b * b - 4 * a * c)) / (2 * a)
                ```
            """.trimIndent()
        }
    }

    /** 流式基准的长回复：一次吐完约 30 个公式，模拟真实模型推导题的输出。 */
    val streamMath: String by lazy { (1..3).joinToString("\n\n") { math[1].content.replace("第 1 题", "第 $it 段") } }

    /** 流式基准的对照回复：长度相当的纯文本。 */
    val streamPlain: String by lazy { (1..3).joinToString("\n\n") { plain[1].content.replace("第 1 节", "第 $it 段") } }

    fun messagesFor(scenario: String?): List<ChatMessage>? = when (scenario) {
        "plain" -> plain
        "math" -> math
        "mixed" -> mixed
        "empty" -> emptyList()
        else -> null
    }

    fun repliesFor(scenario: String?): List<String>? = when (scenario) {
        "stream-math" -> listOf(streamMath)
        "stream-plain" -> listOf(streamPlain)
        else -> null
    }

    /** 各场景用到才生成：正常启动和单个场景的基准都不替其余样本付构建成本 */
    private fun lazyConversation(reply: (Int) -> String) = lazy { conversation(reply) }

    private fun conversation(reply: (Int) -> String): List<ChatMessage> =
        (1..TURNS).flatMap { i ->
            listOf(
                ChatMessage(SAMPLE_MESSAGE_ID_BASE + i * 2 - 1, Role.USER, "第 $i 个问题"),
                ChatMessage(SAMPLE_MESSAGE_ID_BASE + i * 2, Role.ASSISTANT, reply(i)),
            )
        }

    private val SAMPLE_RICH = """
        # Kotlin 协程速览

        协程是 Kotlin 的**轻量级并发**方案，核心是 `suspend` 函数与结构化并发。

        ```kotlin
        suspend fun loadUser(id: String): User = withContext(Dispatchers.IO) {
            api.fetch(id).toUser()
        }
        ```

        | 调度器 | 用途 | 线程池 |
        | :--- | :---: | ---: |
        | Main | UI 更新 | 主线程 |
        | IO | 网络 / 磁盘 | 弹性 |
        | Default | CPU 密集 | 核数 |
    """.trimIndent()
}

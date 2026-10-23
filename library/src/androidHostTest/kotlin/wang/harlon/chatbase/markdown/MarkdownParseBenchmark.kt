package wang.harlon.chatbase.markdown

import java.io.File
import kotlin.test.Test

/**
 * parseMarkdown 的耗时微基准：流式期间每个 delta 都要重解析整条回复，这里量的就是那一次的成本。
 *
 * 默认跳过，`MD_BENCH=1 ./gradlew :library:testAndroidHostTest --tests '*MarkdownParseBenchmark*'` 才跑，
 * 结果写到 library/build/md-parse-bench.txt。只依赖 parseMarkdown，能原样搬到没有公式支持的旧分支上做对照。
 * 宿主 JVM 上的绝对值不代表手机，只看同一台机器上两个分支的相对差。
 */
class MarkdownParseBenchmark {

    @Test
    fun parse_cost_per_reply() {
        if (System.getenv("MD_BENCH") != "1") return
        val report = buildString {
            appendLine("sample\tchars\tmedian_us\tp90_us")
            SAMPLES.forEach { (name, text) ->
                // 累加块数并校验：解析结果被用到，JIT 不会把解析当死代码删掉，样本解析成空也会立刻暴露
                var blocks = 0
                repeat(WARMUP) { blocks += parseMarkdown(text).blocks.size }
                val samples = LongArray(RUNS) {
                    val start = System.nanoTime()
                    blocks += parseMarkdown(text).blocks.size
                    System.nanoTime() - start
                }.sorted()
                check(blocks > 0) { "$name 解析结果为空" }
                val median = samples[RUNS / 2] / 1000.0
                val p90 = samples[RUNS * 9 / 10] / 1000.0
                appendLine("$name\t${text.length}\t${"%.1f".format(median)}\t${"%.1f".format(p90)}")
            }
        }
        File("build/md-parse-bench.txt").writeText(report)
        println(report)
    }

    private companion object {
        const val WARMUP = 500
        const val RUNS = 3000

        val PLAIN = """
            ## 结构化并发

            协程的**作用域**决定了子任务的生命周期，父作用域取消时所有子协程一并取消，`Job` 之间形成树。
            调度器决定代码跑在哪个线程：`Dispatchers.IO` 用于网络与磁盘，`Default` 用于计算密集任务。

            - 用 `withContext` 切换调度器，不要在主线程做阻塞调用
            - 用 `supervisorScope` 让单个子任务失败不影响兄弟任务
            - 用 `Flow` 表达随时间变化的一串值，冷流在收集时才开始执行

            > 小结：先想清楚谁拥有这个任务、谁负责取消它，再决定用哪个作用域。
        """.trimIndent()

        val MATH = """
            ## 级数与积分

            设 ${'$'}a_n = \frac{1}{n^2}${'$'}，则 ${'$'}\sum_{n=1}^{\infty} a_n = \frac{\pi^2}{6}${'$'}，且 ${'$'}a_n > 0${'$'} 对所有 ${'$'}n \ge 1${'$'} 成立。
            对于 \(f(x) = e^{-x^2}\)，有 \(f'(x) = -2x e^{-x^2}\)，在 \(x = 0\) 处取极大值 \(f(0) = 1\)。
            判别式 ${'$'}\Delta = b^2 - 4ac${'$'} 决定二次方程根的个数，${'$'}\sqrt{\Delta}${'$'} 为实数时有两个实根。

            $$
            \int_{-\infty}^{\infty} e^{-x^2}\,dx = \sqrt{\pi}
            $$

            \[
            x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            \]
        """.trimIndent()

        val CODE = """
            价格从 ${'$'}5 涨到 ${'$'}10，代码里的 `${'$'}HOME` 不是公式：

            ```bash
            for f in ${'$'}(ls); do echo "${'$'}f costs ${'$'}5"; done
            export PATH=${'$'}HOME/bin:${'$'}PATH
            ```
        """.trimIndent()

        /** 流式里最长的一档：一条约 6000 字的长回复，三类内容混排。 */
        val LONG = List(6) { "$PLAIN\n\n$MATH\n\n$CODE" }.joinToString("\n\n")

        val SAMPLES = listOf("plain" to PLAIN, "math" to MATH, "code_dollar" to CODE, "long_mixed" to LONG)
    }
}

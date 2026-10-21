package wang.harlon.chatbase.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "wang.harlon.chatbase.sample"
private const val ACTIVITY = "$PACKAGE.ChatDemoActivity"
private const val ITERATIONS = 5

/**
 * 公式渲染的性能基准，场景成对出现：同一套交互分别跑纯文本与公式会话，差值就是公式的成本。
 *
 * 样本见 sample 的 BenchmarkSamples。CompilationMode.Full 消除 JIT 预热的噪声，
 * 两个分支的对照只比相对差值，不当作用户实际体验的绝对值。
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ChatBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test fun scrollPlain() = scroll("plain")
    @Test fun scrollMath() = scroll("math")
    @Test fun scrollMixed() = scroll("mixed")

    @Test fun streamPlain() = stream("stream-plain")
    @Test fun streamMath() = stream("stream-math")

    @Test fun startupPlain() = startup("plain")
    @Test fun startupMath() = startup("math")

    /** 冷启动进会话后上下各甩三次：覆盖新消息进入视口时的首次测量，以及滚回已测量内容的复用。 */
    private fun scroll(scenario: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait(intentFor(scenario))
            messageList()
        },
    ) {
        val list = messageList()
        repeat(3) {
            list.fling(Direction.UP)
            device.waitForIdle()
        }
        repeat(3) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }

    /** 空会话里发一条消息，假引擎按 30 ms 一块吐出约 30 个公式的长回复，测流式期间的帧时间。 */
    private fun stream(scenario: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait(intentFor(scenario))
            val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
                ?: error("找不到输入框")
            input.text = "go"
        },
    ) {
        device.findObject(By.desc(SEND))?.click() ?: error("找不到发送按钮")
        // 回复约 2000 字、每 30 ms 吐 24 字，3 秒内吐完；多等 2 秒覆盖终态的最后几次重组
        Thread.sleep(5_000)
    }

    /** 冷启动到首帧：会话打开时首屏的行内公式在主线程同步测量，会直接计入首帧时间。 */
    private fun startup(scenario: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait(intentFor(scenario))
    }

    private fun intentFor(scenario: String) = Intent().apply {
        setClassName(PACKAGE, ACTIVITY)
        putExtra("scenario", scenario)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    /** 消息列表是最高的那个可滚动节点；代码块和表格的横向滚动也是 scrollable，要排除掉。 */
    private fun MacrobenchmarkScope.messageList(): UiObject2 {
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        val list = device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() }
            ?: error("找不到消息列表")
        // 留出边距，甩动不要从输入栏或状态栏起手
        list.setGestureMargin(device.displayWidth / 5)
        return list
    }

    private companion object {
        val SEND: Pattern = Pattern.compile("Send|发送")
    }
}

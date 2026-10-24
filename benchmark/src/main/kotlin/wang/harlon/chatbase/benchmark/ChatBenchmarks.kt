package wang.harlon.chatbase.benchmark

import android.content.Intent
import android.graphics.Rect
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
        // 不能用 StartupMode.COLD：它会在 setup 与测量之间杀进程，测量时前台就回到了桌面
        startupMode = null,
        iterations = ITERATIONS,
        setupBlock = { freshLaunch(scenario) },
    ) {
        val bounds = messageListBounds()
        // 列表节点在甩动后会被 Compose 替换，持有 UiObject2 会失效；改用列表中部的坐标手势
        val x = bounds.centerX()
        val top = bounds.top + bounds.height() / 5
        val bottom = bounds.bottom - bounds.height() / 5
        repeat(3) {
            requireForeground()
            device.swipe(x, top, x, bottom, FLING_STEPS)
            device.waitForIdle()
        }
        repeat(3) {
            requireForeground()
            device.swipe(x, bottom, x, top, FLING_STEPS)
            device.waitForIdle()
        }
    }

    /** 空会话里发一条消息，假引擎按 30 ms 一块吐出约 30 个公式的长回复，测流式期间的帧时间。 */
    private fun stream(scenario: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = null,
        iterations = ITERATIONS,
        setupBlock = {
            freshLaunch(scenario)
            requireForeground()
            val input = device.wait(Until.findObject(By.pkg(PACKAGE).clazz("android.widget.EditText")), 5_000)
                ?: error("找不到输入框")
            input.text = "go"
        },
    ) {
        requireForeground()
        device.wait(Until.findObject(By.pkg(PACKAGE).desc(SEND)), 5_000)?.click() ?: error("找不到发送按钮")
        awaitStreamEnd()
    }

    /** 冷启动到首帧：会话打开时首屏的行内公式在主线程同步测量，会直接计入首帧时间。 */
    private fun startup(scenario: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            resetSystemUi()
            pressHome()
        },
    ) {
        startActivityAndWait(intentFor(scenario))
    }

    private fun intentFor(scenario: String) = Intent().apply {
        setClassName(PACKAGE, ACTIVITY)
        putExtra("scenario", scenario)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    /**
     * 消息列表是 sample 里最高的那个可滚动节点；代码块和表格的横向滚动也是 scrollable，要排除掉。
     * 必须限定包名：通知栏一旦打开，它就是全屏最高的可滚动节点。
     */
    private fun MacrobenchmarkScope.messageListBounds(): Rect {
        requireForeground()
        val selector = By.pkg(PACKAGE).scrollable(true)
        device.wait(Until.hasObject(selector), 5_000)
        return device.findObjects(selector).maxByOrNull { it.visibleBounds.height() }?.visibleBounds
            ?: error("找不到消息列表")
    }

    /**
     * 手势前确认 sample 在前台。MIUI 在桌面下滑会拉出通知栏，应用没起来时坐标手势就会滚到别处；
     * 宁可这一轮直接失败，也不产出测的是别的界面的数字。
     */
    private fun MacrobenchmarkScope.requireForeground() {
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 3_000)
        val current = device.currentPackageName
        check(current == PACKAGE) { "前台不是 sample 而是 $current，本轮作废" }
    }

    /** 每轮从干净进程冷启动进会话；进程由这里杀而不是交给 StartupMode，测量开始时应用才在前台。 */
    private fun MacrobenchmarkScope.freshLaunch(scenario: String) {
        resetSystemUi()
        killProcess()
        startActivityAndWait(intentFor(scenario))
        requireForeground()
    }

    /**
     * 等流式回复吐完。回复期间发送按钮禁用（输入非空且不忙才可发），所以先等它因发送变灰，
     * 再往输入框填字，等它重新可用即回复结束；慢机上吐字跟不上节奏时也会等到真正结束，不会截掉尾段。
     */
    private fun MacrobenchmarkScope.awaitStreamEnd() {
        check(device.wait(Until.hasObject(sendButton(enabled = false)), 5_000)) { "发送后按钮没有变灰" }
        device.findObject(By.pkg(PACKAGE).clazz("android.widget.EditText"))?.text = "next"
        check(device.wait(Until.hasObject(sendButton(enabled = true)), STREAM_TIMEOUT_MS)) {
            "流式回复 $STREAM_TIMEOUT_MS ms 内没有结束"
        }
        // 终态还有几次重组（落库换 id、待定公式换成渲染结果），多等 1 秒一并计入
        Thread.sleep(1_000)
    }

    /** 发送按钮：描述挂在图标上，可用状态在外层的按钮节点上 */
    private fun sendButton(enabled: Boolean) = By.pkg(PACKAGE).enabled(enabled).hasChild(By.desc(SEND))

    /** 上一轮残留的通知栏或下拉面板会盖住界面，每轮开始前收起。 */
    private fun MacrobenchmarkScope.resetSystemUi() {
        device.executeShellCommand("cmd statusbar collapse")
    }

    private companion object {
        val SEND: Pattern = Pattern.compile("Send|发送")

        /** swipe 每步约 5 ms，8 步约 40 ms 划完一段，速度足够触发甩动惯性。 */
        const val FLING_STEPS = 8

        /** 正常 2 秒左右吐完，给慢机留足余量；超时说明回复卡住，本轮作废 */
        const val STREAM_TIMEOUT_MS = 20_000L
    }
}

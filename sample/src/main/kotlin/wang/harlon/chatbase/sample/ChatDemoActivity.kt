package wang.harlon.chatbase.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import wang.harlon.chatbase.host.chatHost
import wang.harlon.chatbase.host.isChatHostInstalled
import wang.harlon.chatbase.ui.ChatScreen

/**
 * 离线 Demo 入口：内置示例会话 + 假引擎，无需接 API 即可验收展示与交互效果。
 *
 * adb 传 `--ez real true` 用真实 ChatApi 连 [DemoChatHost.apiBaseUrl] 联调；否则走假引擎。
 */
class ChatDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isChatHostInstalled()) chatHost = DemoChatHost
        val real = intent.getBooleanExtra("real", false)
        val bench = BenchConfig(
            scenario = intent.getStringExtra("scenario"),
            chunkDelayMillis = intent.getLongExtra("chunkDelay", 30L),
        )
        if (intent.getBooleanExtra("strictMode", false)) enableStrictMode()
        setContent { DemoTheme { DemoContent(real = real, bench = bench, onBack = ::finish) } }
    }
}

/**
 * 基准与性能排查用的启动参数（adb / Macrobenchmark 通过 intent extra 传入）：
 * - `scenario`：plain / math / mixed 换成 [BenchmarkSamples] 里的固定会话；stream-math / stream-plain 让假引擎固定回复长文
 * - `chunkDelay`：假引擎每块输出的间隔毫秒，默认 30
 * - `strictMode`：打开主线程磁盘读写检测，违规打到 logcat 的 StrictMode 标签
 */
private data class BenchConfig(val scenario: String?, val chunkDelayMillis: Long)

private fun enableStrictMode() {
    android.os.StrictMode.setThreadPolicy(
        android.os.StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .penaltyLog()
            .build(),
    )
}

@Composable
private fun DemoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@Composable
private fun DemoContent(real: Boolean, bench: BenchConfig, onBack: () -> Unit) {
    if (real) {
        // 真实引擎（ChatScreen 默认即 ChatApi），空会话连生产
        ChatScreen(onBack = onBack)
    } else {
        ChatScreen(
            onBack = onBack,
            engine = androidx.compose.runtime.remember {
                FakeChatEngine(bench.chunkDelayMillis, BenchmarkSamples.repliesFor(bench.scenario))
            },
            initialMessages = BenchmarkSamples.messagesFor(bench.scenario)
                ?: if (bench.scenario?.startsWith("stream") == true) emptyList() else SampleData.messages,
            // 纯内存：假引擎的样例会话不落真库
            persistent = false,
            transcriber = null,
        )
    }
}

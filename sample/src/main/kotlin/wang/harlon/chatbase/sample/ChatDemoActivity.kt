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
        setContent { DemoTheme { DemoContent(real = real, onBack = ::finish) } }
    }
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
private fun DemoContent(real: Boolean, onBack: () -> Unit) {
    if (real) {
        // 真实引擎（ChatScreen 默认即 ChatApi），空会话连生产
        ChatScreen(onBack = onBack)
    } else {
        ChatScreen(
            onBack = onBack,
            engine = androidx.compose.runtime.remember { FakeChatEngine() },
            initialMessages = SampleData.messages,
            // 纯内存：假引擎的样例会话不落真库
            persistent = false,
            transcriber = null,
        )
    }
}

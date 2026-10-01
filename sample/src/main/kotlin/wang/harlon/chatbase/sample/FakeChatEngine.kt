package wang.harlon.chatbase.sample

import kotlinx.coroutines.delay
import wang.harlon.chatbase.engine.ChatEngine
import wang.harlon.chatbase.model.ChatMessage

/**
 * 离线假引擎：不接 API，模拟逐字流式输出预置 Markdown，
 * 用于跑通「输入 → 发送 → 流式渲染」完整交互链路。
 */
class FakeChatEngine(
    private val chunkDelayMillis: Long = 30L,
) : ChatEngine {

    private val replies = listOf(
        SampleData.richMarkdown,
        "收到 👍 这是一段**纯文本 + `行内代码`**的简短回复，用来演示不同长度消息的排版。\n\n" +
            "- 列表项一\n- 列表项二\n\n```bash\n# 也能渲染命令\n./gradlew :sample:assembleDebug\n```",
    )
    private var index = 0

    override suspend fun send(
        history: List<ChatMessage>,
        onDelta: (String) -> Unit,
        search: Boolean,
        onSearch: (wang.harlon.chatbase.model.SearchEvent) -> Unit,
        imageGeneration: Boolean,
        onImageGeneration: (wang.harlon.chatbase.model.ImageGenerationEvent) -> Unit,
        ): String {
        if (search) {
            onSearch(wang.harlon.chatbase.model.SearchEvent.Started)
            delay(600)
            onSearch(wang.harlon.chatbase.model.SearchEvent.Done("demo query"))
            onSearch(wang.harlon.chatbase.model.SearchEvent.Source("Kotlin", "https://kotlinlang.org"))
        }
        val reply = replies[index % replies.size]
        index++
        return streamOut(reply, onDelta)
    }

    /** 按小块模拟逐字输出 */
    private suspend fun streamOut(reply: String, onDelta: (String) -> Unit): String {
        reply.chunked(24).forEach { chunk ->
            delay(chunkDelayMillis)
            onDelta(chunk)
        }
        return reply
    }
}

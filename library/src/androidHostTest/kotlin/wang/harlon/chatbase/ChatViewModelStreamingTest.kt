package wang.harlon.chatbase

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import wang.harlon.chatbase.host.ChatAiOutcome
import wang.harlon.chatbase.host.ChatAiEvent
import wang.harlon.chatbase.engine.ChatEngine
import wang.harlon.chatbase.engine.ChatException
import wang.harlon.chatbase.model.ChatError
import wang.harlon.chatbase.model.ChatModelsResponse
import wang.harlon.chatbase.model.ChatErrorCategory
import wang.harlon.chatbase.model.ChatMessage
import wang.harlon.chatbase.model.Role

/** ChatViewModel 流式渲染、失败重试与埋点配对。 */
class ChatViewModelStreamingTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** 可编程假引擎：记录调用、按脚本流式吐块或抛错 */
    private class ScriptedEngine(
        var chatChunks: List<String> = listOf("你", "好"),
        var failWith: ChatError? = null,
    ) : ChatEngine {
        var chatCalls = 0

        override suspend fun send(
            history: List<ChatMessage>,
            onDelta: (String) -> Unit,
            search: Boolean,
            onSearch: (wang.harlon.chatbase.model.SearchEvent) -> Unit,
            imageGeneration: Boolean,
            onImageGeneration: (wang.harlon.chatbase.model.ImageGenerationEvent) -> Unit,
            ): String {
            chatCalls++
            // 先吐块再抛错：失败用例覆盖的是「已渲染部分被丢弃」的半途断流路径
            chatChunks.forEach(onDelta)
            failWith?.let { throw ChatException(it) }
            return chatChunks.joinToString("")
        }
    }

    private fun vm(engine: ChatEngine, track: (ChatAiEvent) -> Unit = {}) =
        ChatViewModel(engine, loadModels = { ChatModelsResponse() }, track = track)

    @Test
    fun `发送后流式定稿：assistant 消息内容为全文，isSending 复位`() = runTest(dispatcher) {
        val viewModel = vm(ScriptedEngine())
        viewModel.sendText("hi")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("你好", state.messages.last().content)
        assertNull(state.messages.last().error)
        assertTrue(!state.isSending)
    }

    @Test
    fun `流式期间消息标记 streaming，定稿后清除`() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val engine = object : ChatEngine {
            override suspend fun send(
                history: List<ChatMessage>,
                onDelta: (String) -> Unit,
                search: Boolean,
                onSearch: (wang.harlon.chatbase.model.SearchEvent) -> Unit,
                imageGeneration: Boolean,
                onImageGeneration: (wang.harlon.chatbase.model.ImageGenerationEvent) -> Unit,
            ): String {
                onDelta("你")
                release.await()
                return "你好"
            }
        }
        val viewModel = vm(engine)
        viewModel.sendText("hi")
        advanceUntilIdle()
        val streamingReply = viewModel.uiState.value.messages.last()
        assertEquals(Role.ASSISTANT, streamingReply.role)
        assertEquals("你", streamingReply.content)
        assertTrue(streamingReply.streaming)
        release.complete(Unit)
        advanceUntilIdle()
        val finalReply = viewModel.uiState.value.messages.last()
        assertEquals(Role.ASSISTANT, finalReply.role)
        assertEquals("你好", finalReply.content)
        assertTrue(!finalReply.streaming)
        assertEquals(2, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `流式到定稿 key 不变：id 换成落库行，列表项身份保持`() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val engine = object : ChatEngine {
            override suspend fun send(
                history: List<ChatMessage>,
                onDelta: (String) -> Unit,
                search: Boolean,
                onSearch: (wang.harlon.chatbase.model.SearchEvent) -> Unit,
                imageGeneration: Boolean,
                onImageGeneration: (wang.harlon.chatbase.model.ImageGenerationEvent) -> Unit,
            ): String {
                onDelta("你")
                release.await()
                return "你好"
            }
        }
        val viewModel = vm(engine)
        viewModel.sendText("hi")
        advanceUntilIdle()
        val streamingReply = viewModel.uiState.value.messages.last()
        release.complete(Unit)
        advanceUntilIdle()
        val finalReply = viewModel.uiState.value.messages.last()
        assertTrue(finalReply.id != streamingReply.id)
        assertEquals(streamingReply.key, finalReply.key)
    }

    @Test
    fun `失败的错误条也沿用占位 key`() = runTest(dispatcher) {
        val engine = ScriptedEngine(failWith = ChatError(ChatErrorCategory.SERVER, code = "stream_interrupted"))
        val keys = mutableListOf<Long>()
        val viewModel = vm(engine)
        viewModel.sendText("hi")
        // 占位刚追加、引擎尚未跑完时记下它的 key
        dispatcher.scheduler.runCurrent()
        viewModel.uiState.value.messages.lastOrNull { it.role == Role.ASSISTANT }?.let { keys += it.key }
        advanceUntilIdle()
        val failed = viewModel.uiState.value.messages.last()
        assertNotNull(failed.error)
        assertEquals(keys.single(), failed.key)
    }

    @Test
    fun `连续两轮回复的 key 互不相同，列表 key 唯一`() = runTest(dispatcher) {
        val viewModel = vm(ScriptedEngine())
        viewModel.sendText("一")
        advanceUntilIdle()
        viewModel.sendText("二")
        advanceUntilIdle()
        val keys = viewModel.uiState.value.messages.map { it.key }
        assertEquals(4, keys.size)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `失败时已渲染部分丢弃：content 清空、挂错误条`() = runTest(dispatcher) {
        val engine = ScriptedEngine(failWith = ChatError(ChatErrorCategory.SERVER, code = "stream_interrupted"))
        val viewModel = vm(engine)
        viewModel.sendText("hi")
        advanceUntilIdle()
        val failed = viewModel.uiState.value.messages.last()
        assertEquals("", failed.content)
        assertNotNull(failed.error)
    }

    @Test
    fun `重试：移除错误条并重打请求，成功后全文到位`() = runTest(dispatcher) {
        val engine = ScriptedEngine(failWith = ChatError(ChatErrorCategory.SERVER, code = "stream_interrupted"))
        val viewModel = vm(engine)
        viewModel.sendText("hi")
        advanceUntilIdle()
        engine.failWith = null // 网络恢复
        viewModel.retry(viewModel.uiState.value.messages.last())
        advanceUntilIdle()
        assertEquals(2, engine.chatCalls)
        assertEquals("你好", viewModel.uiState.value.messages.last().content)
        // 错误条已被成功回复替换，不残留
        assertTrue(viewModel.uiState.value.messages.none { it.error != null })
    }

    @Test
    fun `quota_device 不可重试类别但享受放行例外（登录后 retry 续上）`() = runTest(dispatcher) {
        val engine = ScriptedEngine(
            failWith = ChatError(
                ChatErrorCategory.QUOTA, // 429 归类不可重试
                code = ChatError.CODE_QUOTA_DEVICE,
                tier = ChatError.TIER_ANONYMOUS,
            ),
        )
        val viewModel = vm(engine)
        viewModel.sendText("hi")
        advanceUntilIdle()
        engine.failWith = null // 模拟登录/次日额度恢复
        viewModel.retry(viewModel.uiState.value.messages.last())
        advanceUntilIdle()
        assertEquals("你好", viewModel.uiState.value.messages.last().content)
        assertEquals(2, engine.chatCalls)
    }

    @Test
    fun `埋点：requested 与 completed 成对，失败带 reason`() = runTest(dispatcher) {
        val events = mutableListOf<ChatAiEvent>()
        val viewModel = vm(ScriptedEngine()) { events.add(it) }
        viewModel.sendText("hi")
        advanceUntilIdle()
        assertEquals(1, events.filterIsInstance<ChatAiEvent.Requested>().size)
        assertEquals(
            listOf(ChatAiOutcome.OK),
            events.filterIsInstance<ChatAiEvent.Completed>().map { it.outcome },
        )

        val gated = ScriptedEngine(
            failWith = ChatError(ChatErrorCategory.QUOTA, code = ChatError.CODE_QUOTA_DEVICE),
        )
        val events2 = mutableListOf<ChatAiEvent>()
        val vm2 = vm(gated) { events2.add(it) }
        vm2.sendText("hi")
        advanceUntilIdle()
        assertEquals(1, events2.filterIsInstance<ChatAiEvent.Requested>().size)
        assertEquals(
            listOf(ChatAiOutcome.ERROR to ChatError.CODE_QUOTA_DEVICE),
            events2.filterIsInstance<ChatAiEvent.Completed>().map { it.outcome to it.reason },
        )
    }
}

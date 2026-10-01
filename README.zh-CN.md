# chatbase-kmp

> [chatbase](https://github.com/HarlonWang/chatbase) 的 Compose Multiplatform 聊天 UI 与客户端——流式回复、模型选择、语音输入、附图、生图、联网搜索与本地历史。

[English](README.md) | **简体中文**

[![Maven Central](https://img.shields.io/maven-central/v/wang.harlon/chatbase-kmp)](https://central.sonatype.com/artifact/wang.harlon/chatbase-kmp)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

服务端那一半是 [chatbase](https://github.com/HarlonWang/chatbase)，一个挂进你自己 Cloudflare Worker 的 npm 库。本库是完整的客户端：页面、SSE 引擎、Room 历史与线上协议。宿主 App 通过一个接口提供登录、Pro 裁决和几项偏好；SDK 不认识用户，也不认识订阅。

| 平台 | 状态 |
|---|---|
| Android | 已在生产环境（[TrendingAI](https://github.com/HarlonWang/TrendingAI)） |
| iOS | 已在生产环境，由 Kotlin Multiplatform 宿主消费 |

## 能力

- **一行拿到整页聊天。** `ChatScreen(onBack = ...)` 渲染会话列表、输入栏、模型选择器、带代码高亮的 Markdown、图片，以及额度与 Pro 门槛。宿主只画外面的壳。
- **协议留在 SDK 里。** chat、transcribe、models、quota 四个端点都在这里对 chatbase 的 `/api/chat/*` 说话，宿主不拼任何请求。
- **历史随升级保留。** 会话与消息落在 Room 库 `chat.db`，schema 有版本，迁移有测试。
- **身份与收款归你。** `ChatHost` 向 App 询问是否登录、是否 Pro，并请 App 打开登录页与付费页。SDK 不存 token，不知道商品。
- **埋点是事件，不是依赖。** 请求、完成、语音输入以 `ChatAiEvent` 上报，宿主映射成自己的词汇。

## 快速开始

**1. 加依赖。** SDK 自带各平台的 Ktor engine。

```kotlin
dependencies {
    implementation("wang.harlon:chatbase-kmp:<version>")
}
```

**2. 实现 `ChatHost`，在任何聊天 UI 被触达之前安装**：Android 放 `Application.onCreate`，iOS 放 `MainViewController` 工厂。

```kotlin
object MyChatHost : ChatHost {
    override val apiBaseUrl = "https://api.example.com/api"

    override val canSignIn = true
    override fun isLoggedInNow() = auth.state.value is LoggedIn
    override val isLoggedIn = auth.state.map { it is LoggedIn }
    override fun signIn(source: String) = auth.signIn(source)
    override fun openPaywall(source: String) = Paywall.open(source)
    override fun reportGeneratedImage(imageUrl: String) = Feedback.open(imageUrl)

    override fun currentIsPro() = settings.isPro
    override val isPro = settings.isProFlow

    override val chatModelChoice = settings.chatModelFlow
    override fun currentChatModelChoice() = settings.chatModel
    override fun pinChatModel(id: String) = settings.setChatModel(id)
    override fun followServerDefault() = settings.setChatModel(FOLLOW_SERVER_DEFAULT)

    override fun imagesMaxCount() = 9
    override fun imagesPerImageJpegKb() = 300
    override fun installId() = settings.installId
    override suspend fun requestLang() = if (settings.language == "zh") "zh" else "en"

    override fun configureHttpAuth(config: HttpClientConfig<*>) = config.installAuth(auth)
    override fun onAiEvent(event: ChatAiEvent) = analytics.track(event)
    override val proBadge: (@Composable () -> Unit)? = { ProBadge() }
}

fun installChatHost() { chatHost = MyChatHost }
```

**3. 启动时预热模型目录，然后挂页面。**

```kotlin
ChatModelsProvider.warmUp(appScope)

ChatScreen(
    onBack = { navController.popBackStack() },
    suggestions = listOf(ChatSuggestion(label = "你能做什么？", prompt = "你能做什么？")),
)
```

不接任何后端也能跑的最小宿主见 [`sample/`](sample/src/main/kotlin/wang/harlon/chatbase/sample/DemoChatHost.kt)。

## 线上协议

与服务端一起维护：[chatbase `docs/chat-protocol.md`](https://github.com/HarlonWang/chatbase/blob/main/docs/chat-protocol.md)。端点相对 `ChatHost.apiBaseUrl`。

## 构建

- Android：`./gradlew :library:testAndroidHostTest` 在 Robolectric 下跑 common 与 Android 测试。
- iOS：Markdown 解析绑 [cmark-gfm](https://github.com/apple/swift-cmark)。`library/native/build-cmark.sh` 首次用 CMake 编出静态库并打进 klib，消费方无需额外动作。
- App 吃本地源码：若 App 通过 `gradle/composite-substitutions` 支持 composite build，在它的 `local.properties` 加 `chatbase-kmp.dir=<路径>` 即可。

## 许可

MIT。cmark-gfm 按其自身许可随库分发，见 `library/native/cmark-gfm-COPYING`。
